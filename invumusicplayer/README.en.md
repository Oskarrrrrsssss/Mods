# InvuMusicPlayer — Minecraft Java 26.3 / Fabric / Windows

[Русский](README.md) · **English**

A client music mod: local tracks, Yandex Music search, My Wave,
album artwork, and synchronized 3D lyrics in front of the player. Move and resize the player HUD.
**The interface follows Minecraft's language:** Russian for Russian, English for other languages.
Track names, artists, and lyrics retain the source language.

The previous release passed **62 Windows tests**, including DPAPI token protection. See CI and the included validation report for this updated build.
The interface and rendering were checked in the actual Minecraft 26.3 client on Linux.
The user confirmed Yandex playback and My Wave before this update. New service features still need a live account check.
See [verification results](VALIDATION.en.md).

## Changes in 0.2.0

- Flat player controls replace Minecraft-style buttons. Open the player from the title screen before entering a world.
- A volume slider adjusts audio immediately; HUD sliders customize opacity, RGB accent/background/text colors, and scale.
- Toggle the real PCM spectrum instead of the HUD time/progress bar. YouTube tracks always hide artwork and show a shortened video title; only audio plays.
- 3D lyrics show only the currently sung synchronized line, without a text shadow or background. Sprint-only is optional; the player shows lyrics availability or a download error.
- Selecting a My Wave track keeps radio mode, and the end of a batch requests new recommendations instead of looping the same five tracks.
- Artwork also falls back to album metadata and supports JPEG, PNG and WebP. Artwork/lyrics load independently.
- Like/dislike the current Yandex track, view Liked, browse account playlists or import a playlist URL. The + next to a track adds it to the queue; Library lets you clear or save the queue as a named **local playlist**. Creating a local playlist does not publish it to Yandex.
- My Wave settings: happy, sad, calm, energetic or any mood; balanced, favorites, popular or discovery. Apply starts a new wave with these preferences.
- F10 starts My Wave. All shortcuts, including initially unbound like/dislike, are configurable in the Key bindings button or Minecraft Controls.
- Leaving a world stops audio while retaining queue and playlists. The Windows-encrypted token is restored on startup. Old Musicraft settings and tokens are migrated automatically; remove the old mod JAR before installing this one.

### Optional YouTube audio

For a public HTTPS YouTube video, put **yt-dlp.exe** and **ffmpeg.exe** in the current game's `tools` directory, or install yt-dlp and ffmpeg in PATH. Use official releases: https://github.com/yt-dlp/yt-dlp and https://ffmpeg.org/download.html. Tools are optional and are not bundled or automatically installed.
Paste a video URL into **Library → link field → Import link**, then select it in the player or play the queue. The mod downloads the audio, converts it to WAV and deletes temporary files after loading. It does not display video or thumbnails. Playlists, streams, login/cookies and restricted videos are not supported. Full audio is limited to 128 MiB and extraction to 3 minutes; a tool/service failure shows an error.

## Installation

Required: **Minecraft Java 26.3**, **Java 25**, **Fabric Loader 0.19.5+**, and
**Fabric API 0.162.0+26.3**. Install the mod on the client only.

1. Open [GitHub Actions](https://github.com/Oskarrrrrsssss/Mods/actions/workflows/invumusicplayer.yml)
   and select a successful run. A private repository requires access and a GitHub sign-in.
2. Download the `invumusicplayer-fabric-26.3-windows-latest` artifact and extract its ZIP.
3. Put `invumusicplayer-0.2.0-alpha.jar` in the selected Minecraft instance's `mods` folder.
   Fabric API must also be installed in `mods`.
4. Start the game and press **M**. Add local files or connect your Yandex Music account.

The ZIP includes Russian and English `README` guides, `VALIDATION` results, and `THIRD_PARTY` dependency notices alongside the JAR.

The standard launcher's game folder is `%APPDATA%\.minecraft\`.
A separate launcher instance uses its own folder.
Do not install `-sources.jar` or `-gametest.jar`.

## Controls and appearance

| Key | Action |
|---|---|
| M | Open player |
| F7 | Previous track |
| F8 | Play / pause |
| F9 | Next track |
| K | Toggle 3D lyrics |

Rebind keys under **Minecraft Options → Controls → Key Binds → InvuMusicPlayer**.
The shortcuts work without opening the player. InvuMusicPlayer screens do not pause the game.

- The HUD starts in the upper-left corner and shows artwork, title, artist, and progress.
- **Player → Settings → Move HUD:** drag the panel anywhere,
  choose any of the four corners, adjust scale or opacity, then select Done.
- Settings can hide the HUD, turn off artwork for a text-only display,
  change volume, and change the accent color.
- They also control 3D lyrics, sprint-only display, distance, height, size,
  and timing offset in milliseconds. **K** quickly toggles lyrics.
  Disabling them keeps music playing. Rendering is local, without entities or server traffic.
- Change the language under **Minecraft Options → Language**. Select Russian or English;
  InvuMusicPlayer does not require a separate language switch.

## Local tracks

Put **MP3, WAV, or OGG/Vorbis** files in `music` inside the current game folder.
For the standard launcher, this is `%APPDATA%\.minecraft\music\`.
Scanning includes files up to four levels deep from `music`.
After adding files, select **Local** in the player to refresh the list, then choose a track.
Local playback does not require an account or internet access.

Optional artwork, metadata, and lyrics must share the track's filename stem:

```text
music/
  Artist - Title.mp3
  Artist - Title.lrc
  Artist - Title.png
  Artist - Title.json
```

UTF-8 JSON:

```json
{"title":"Title", "artist":"Artist"}
```

Without JSON, the title and artist come from the `Artist - Title` filename.
Without the separator, the filename becomes the title. Artwork is an adjacent PNG or JPG;
embedded ID3 artwork is not extracted. Music also works without artwork or lyrics.
OGG must contain **Vorbis**, and WAV must be supported by JavaSound.

Synchronized lyrics use an adjacent UTF-8 `.lrc` file. Example original demonstration text:

```lrc
[offset:0]
[00:02.00]First line
[00:05.50]Next line
[00:09.00]
```

An empty timestamped line hides lyrics during an instrumental section.

## Yandex Music and My Wave

1. Open **Player → Account**.
2. Paste your own Yandex Music OAuth token or an official page URL
   containing `access_token`, then select **Connect**.
3. Return to the player. Search and choose a result, or select **My Wave**.
   **F9** advances to the next wave track; **F8** pauses playback.

The mod does not request your password. The help button opens InvuMusicPlayer's instructions
in Russian and English. They link to the
[yandex-music library instructions](https://yandex-music.readthedocs.io/en/main/token.html);
these external instructions are not part of InvuMusicPlayer and may use their own language.
Automatic browser sign-in with a separately registered OAuth application
is not implemented. Enter the token locally inside the mod; do not send it in chat or to the author.

This is an **unofficial API**. You need a valid Music token, service access, and the required
subscription entitlements; Yandex determines regional restrictions and track availability.
Encrypted or preview-only responses are not represented as full tracks.
If synchronized lyrics are unavailable, music continues without them.

Audio is loaded into memory before playback, with a **64 MiB per-track** limit,
and is not saved to the local library. Switching tracks cancels the previous download.
My Wave uses a recommendation session and submits start, finish, and skip events,
accounting for listened time and pauses; seeking does not count as listening.

The masked token is stored in `config/invumusicplayer-auth.dat`, encrypted with Windows DPAPI
for the current user. Ordinary settings are in `config/invumusicplayer.json`.
The token is not sent to CDNs. To remove the saved sign-in, select **Disconnect**
on the account screen. Copying the authorization file to a different PC or user
is not a substitute for signing in again. Account persistence is intended for Windows.

## Troubleshooting

| Issue | What to check |
|---|---|
| Mod does not load | Minecraft, Java, Fabric Loader, and Fabric API versions; install the regular JAR. |
| Shortcut does not work | Key conflicts in control settings; rebind the key if needed. |
| Local tracks are missing | The current instance's folder and MP3/WAV/OGG extensions; select Local to refresh. |
| Artwork is missing | The Covers setting and an adjacent PNG/JPG with the same stem; embedded ID3 artwork is unused. |
| Lyrics are missing | The 3D lyrics setting, K, Sprint only, and the local `.lrc` or Yandex lyrics availability. |
| No sound | InvuMusicPlayer volume, the Windows mixer/output device, and the file format's compatibility. |
| Sign-in or My Wave fails | The player error, token validity, subscription, service availability, and internet access. |
| Saved sign-in cannot be read | Reconnect under the current Windows user. |

The player status tooltip shows the full error message.
The game log is `logs/latest.log` inside the current instance's folder.
When requesting help, include versions and the error text; omit tokens and URLs containing `access_token`.

## Building from source

Open the `invumusicplayer` folder. You need **JDK 25** through `JAVA_HOME` or `PATH`,
and internet access for dependencies.
Gradle Wrapper 9.7.1 uses Loom 1.18.4; a separate Gradle installation is unnecessary.
Minecraft 26.3 is unobfuscated, so Yarn/mappings are not used.

Windows:

```bat
build-windows.cmd
```

Or:

```bat
gradlew.bat build
```

Linux:

```sh
bash ./gradlew build
```

Output: `build/libs/invumusicplayer-0.2.0-alpha.jar`.

## Verification

Core and protocol tests:

```bat
gradlew.bat test
```

On Linux: `bash ./gradlew test`.
Alternative verification without Loom downloads dependencies pinned by SHA-256:

```powershell
./scripts/verify-core.ps1 -JdkHome 'C:\Path\To\jdk-25'
```

The actual client with a separate test mod on Windows:

```bat
gradlew.bat runProductionClientGameTest
```

On Linux, use a graphical display or Xvfb/Mesa as configured in
[CI](https://github.com/Oskarrrrrsssss/Mods/blob/codex/musicraft-playback/.github/workflows/invumusicplayer.yml). Tests use synthetic local files,
without a real account or music. [Results and limitations](VALIDATION.en.md).
[Verified client screenshots](https://github.com/Oskarrrrrsssss/Mods/actions/runs/38181353522/artifacts/11717612730).

## Sources and licenses

- [Fabric 26.3 information](https://www.fabricmc.net/2026/09/15/263.html).
- [Official Fabric 26.3 example](https://github.com/FabricMC/fabric-example-mod/tree/26.3).
- [Fabric API 26.3](https://github.com/FabricMC/fabric-api/tree/26.3).
- [Unofficial yandex-music protocol](https://github.com/MarshalX/yandex-music-api).

The supplied MusicSubtitles mod served as a behavior reference; its code, resources, and JAR are not included.
InvuMusicPlayer is [MIT-licensed](LICENSE). [Dependencies and their licenses](THIRD_PARTY.en.md).
