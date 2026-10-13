#![cfg_attr(target_os="windows", windows_subsystem="windows")]
//! A private pipe from the mod to librespot. No Spotify app, HTTP controller, disk audio cache or logs.
use std::{io::{self, Write}, sync::Mutex, time::Duration};
use base64::{Engine, engine::general_purpose::STANDARD};
use librespot_core::{Session, SessionConfig, SpotifyUri, authentication::Credentials};
use librespot_playback::{audio_backend::{Sink, SinkError, SinkResult}, config::PlayerConfig, convert::Converter, decoder::AudioPacket, mixer::NoOpVolume, player::{Player, PlayerEvent}};
use librespot_protocol::authentication::AuthenticationType;
use serde_json::{Value, json};
use tokio::io::{AsyncBufReadExt, BufReader};

static OUTPUT: Mutex<()> = Mutex::new(());
fn emit(value: Value) -> io::Result<()> { let _guard=OUTPUT.lock().map_err(|_|io::Error::other("output"))?;let mut out=io::stdout().lock();serde_json::to_writer(&mut out,&value)?;out.write_all(b"\n")?;out.flush() }
fn pcm(samples:&[f64])->Vec<u8>{samples.iter().flat_map(|s|{let value=if s.is_finite(){s.clamp(-1.0,1.0)}else{0.0};((value*32767.0).round() as i16).to_le_bytes()}).collect()}
struct ModSink;
impl Sink for ModSink { fn write(&mut self,packet:AudioPacket,_converter:&mut Converter)->SinkResult<()> {match packet { AudioPacket::Samples(samples)=>{for chunk in samples.chunks(8192){emit(json!({"event":"pcm","data":STANDARD.encode(pcm(chunk))})).map_err(|_|SinkError::OnWrite("mod pipe closed".into()))?;}Ok(())},AudioPacket::Raw(_)=>Err(SinkError::InvalidParams("decoded PCM required".into())) }} }
fn identity()->Value{json!({"event":"identity","protocol":1,"client_id":SessionConfig::default().client_id,"revision":"4a005903e078d512456fb008aaa9ba6246881bd9"})}
fn fault(code:&str){let _=emit(json!({"event":"error","code":code}));}

#[tokio::main]
async fn main(){
    // Intentionally do not initialize a logger: upstream tracing can include credentials.
    if std::env::args().any(|a|a=="--identity"){let _=emit(identity());return;}
    if std::env::args().any(|a|a=="--self-test"){self_test().await;return;}
    if run().await.is_err(){fault("network");}
}
async fn run()->Result<(),Box<dyn std::error::Error>>{
    let mut lines=BufReader::new(tokio::io::stdin()).lines();let first=lines.next_line().await?.ok_or("missing command")?;
    if first.len()>16384{fault("protocol");return Ok(());}
    let command:Value=serde_json::from_str(&first)?;let mode=command["mode"].as_str().unwrap_or("");
    if mode!="authenticate"&&mode!="play"{fault("protocol");return Ok(());}
    let credentials=if mode=="authenticate"{Credentials::with_access_token(command["access_token"].as_str().ok_or("missing token")?)}else{serde_json::from_value::<Credentials>(command["credentials"].clone())?};
    let session=Session::new(SessionConfig::default(),None);
    match tokio::time::timeout(Duration::from_secs(40),session.connect(credentials,false)).await{Ok(Ok(()))=>{},_=>{fault("authorization");return Ok(());}}
    // August 2026: AP login alone does not establish a valid playback credential/client identity.
    match tokio::time::timeout(Duration::from_secs(25),session.login5().auth_token()).await{Ok(Ok(_))=>{},_=>{fault("authorization");session.shutdown();return Ok(());}}
    if mode=="authenticate"{
        let reusable=Credentials{username:Some(session.username()),auth_type:AuthenticationType::AUTHENTICATION_STORED_SPOTIFY_CREDENTIALS,auth_data:session.auth_data()};
        emit(json!({"event":"authenticated","credentials":reusable}))?;session.shutdown();return Ok(());
    }
    let uri=SpotifyUri::from_uri(command["uri"].as_str().ok_or("missing uri")?)?;
    if !matches!(uri,SpotifyUri::Track{..}){fault("protocol");return Ok(());}
    let position=command["position"].as_u64().unwrap_or(0).min(u32::MAX as u64) as u32;
    let paused=command["paused"].as_bool().unwrap_or(false);
    let config=PlayerConfig{position_update_interval:Some(Duration::from_secs(1)),gapless:false,..PlayerConfig::default()};
    let player=Player::new(config,session.clone(),Box::new(NoOpVolume),||Box::new(ModSink));
    let mut events=player.get_player_event_channel();player.load(uri,!paused,position);
    loop {tokio::select!{
        event=events.recv()=>{let Some(event)=event else{break};match event{
            PlayerEvent::Playing{position_ms,..}=>emit(json!({"event":"playing","position":position_ms}))?,
            PlayerEvent::Paused{position_ms,..}=>emit(json!({"event":"paused","position":position_ms}))?,
            PlayerEvent::PositionChanged{position_ms,..}|PlayerEvent::Seeked{position_ms,..}=>emit(json!({"event":"position","position":position_ms}))?,
            PlayerEvent::EndOfTrack{..}=>{emit(json!({"event":"ended"}))?;break;},
            PlayerEvent::Unavailable{..}=>{fault("unavailable");break;},_=>{}
        }},
        line=lines.next_line()=>{let Some(line)=line? else{break};if line.len()>1024{fault("protocol");break;}let cmd:Value=serde_json::from_str(&line)?;match cmd["cmd"].as_str().unwrap_or(""){"pause"=>player.pause(),"resume"=>player.play(),"stop"=>{player.stop();break;},_=>{fault("protocol");break;}}}
    }}
    player.stop();session.shutdown();Ok(())
}
async fn self_test(){
    let _=emit(json!({"event":"playing","position":0}));
    let samples:Vec<f64>=(0..44100).flat_map(|i|{let value=(2.0*std::f64::consts::PI*700.0*i as f64/44100.0).sin()*0.5;[value,value]}).collect();
    let mut sink=ModSink;let mut converter=Converter::new(None);let _=sink.write(AudioPacket::Samples(samples),&mut converter);let _=emit(json!({"event":"ended"}));
}
#[cfg(test)]mod tests{use super::*;#[test]fn conversion_is_stereo_safe_clamped_and_finite(){let bytes=pcm(&[0.0,1.0,-1.0,2.0,f64::NAN]);let values:Vec<i16>=bytes.chunks_exact(2).map(|b|i16::from_le_bytes([b[0],b[1]])).collect();assert_eq!(values,vec![0,32767,-32767,32767,0]);}#[test]fn identity_matches_session_instead_of_web_developer_client(){let value=identity();assert_eq!(value["client_id"].as_str().unwrap(),SessionConfig::default().client_id);assert_eq!(value["protocol"],1);}}
