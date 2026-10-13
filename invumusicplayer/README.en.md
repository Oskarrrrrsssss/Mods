# invumusicplayer — Minecraft Java 26.3 / Fabric / Windows

[Русский](README.md) · **English**

A client music player: Yandex Music, My Wave, local music and YouTube audio.
Move and scale the in-game panel and customize its colors and opacity.
The interface follows Minecraft's language: Russian or English.


## Changes in 0.2.8

The **queue is on the main player screen**. Search is in **Library → Search**; **Local** searches files in `music`. Scroll over a queue/library list to page through tracks. Click a queued title to play it, or × to remove it.

The position slider shows elapsed and total time. Dragging previews the position; release seeks once. Switching tracks during a drag cancels that seek. Seeking preserves pause. Stop, ±10-second buttons and the separate Pause button are removed from the full screen; pause remains in the mini player and assigned hotkey. Time and the enabled equalizer appear together in the HUD, menus and full player.

Shuffle changes upcoming tracks, including manually queued tracks, without restarting the current audio. With multiple distinct tracks the order always changes. Manual additions retain priority over the playlist. Turning shuffle off restores the remaining order while preserving Previous history.

Local artwork: first `Title.png/.jpg/.jpeg/.webp`, then `cover.png/.jpg/.jpeg/.webp` or `folder.jpg` in the same directory, then embedded MP3 ID3 APIC/PIC or Ogg/Vorbis METADATA_BLOCK_PICTURE/COVERART. Artwork loads with the song and appears in the library and player; disabling covers retains text. Broken tags do not prevent audio playback.

<a id="spotify"></a>

## Spotify: audio inside the mod (experimental)

Open **Settings → Controls → Music service** or **Account** to choose Spotify / Yandex Music. Spotify uses the bundled native librespot decoder and sends PCM to the default audio device through JavaSound; the Spotify app is not required. This is an unofficial integration: playback with a real Premium account has **not yet been confirmed**. Automated checks cover fixture-based authorization/catalogue operations and the native audio pipe using a synthetic tone.

You need **Spotify Premium**, access to Spotify and your own Client ID from the [Developer Dashboard](https://developer.spotify.com/dashboard). Do not enter a Client Secret, password or tokens in chat.

1. Create a Web API application; register the exact redirect `http://127.0.0.1:43827/callback`. In Development Mode allowlist your account. Spotify controls application availability and quotas.
2. Select Spotify in the mod, enter **Client ID**, and choose **Sign in to catalogue**. Approve the catalogue/library authorization in your browser.
3. Select **Enable built-in audio** and authorize the same Premium account separately. Playback uses librespot's native client identity, not your Web API Client ID. Its callback is `http://127.0.0.1:8898/login`; you do not register it in your own application. Login expires after five minutes; occupied ports show an error.
4. Find a track under **Library → Search** and click its title. Liked tracks, playlists, queue, pause, seek and volume are available. HTTPS `open.spotify.com/track/...` and `/playlist/...` links can be imported.

Spotify Development Mode limits each search request to ten results; playlist contents may require ownership/collaboration. A second like removes the like. Removing a Spotify playlist in the mod removes it from your library, without deleting its author's playlist. My Wave and dislike belong to Yandex Music and are disabled for Spotify. Switching services retains the queue and stops current audio until a track is selected/resumed.

On Windows both logins are saved separately with DPAPI encryption: `config/invumusicplayer-spotify-auth.dat` and `config/invumusicplayer-spotify-playback.dat`. Settings store Client ID, never credentials. **Disconnect** removes both logins. Spotify audio is neither exported to files nor cached on disk. The SHA-256-verified native engine is extracted from the JAR to `tools/invumusicplayer/spotify`; installing the finished mod does not require Rust. Spotify integration targets Windows x64; Linux x64 is used to verify the engine and UI in CI.

Building from source requires **JDK 25, Rust/Cargo and Python 3**. Gradle builds and packages the engine automatically; dependencies are pinned in `native-spotify/Cargo.lock`. The initial build downloads Rust dependencies. Sources/limits: [librespot](https://github.com/librespot-org/librespot), [Spotify PKCE](https://developer.spotify.com/documentation/web-api/tutorials/code-pkce-flow), [Development Mode](https://developer.spotify.com/documentation/web-api/concepts/quota-modes).


## Previous track fix (0.2.7)

Each Previous click returns one step through the track selection history, including explicit queue entries, shuffle, manual selection and My Wave batch boundaries. Rapid selections are remembered even while audio downloads. At the beginning, the first track restarts instead of wrapping to the last.

Next retraces forward before resuming the playlist/Wave; this continuation also appears in the queue and is saved on restart. Buttons in the full/Escape players and the Previous hotkey use the same navigation.

## Player visibility and mouse wheel (0.2.6)

**Player settings → Visibility** has separate switches for the in-game HUD, title-menu mini player and Escape-menu mini player. **Hide all** temporarily hides all three, including their controls, without stopping music or changing individual switches. The title menu contains only the mini player: no separate invumusic button.

To restore hidden players, open **Minecraft Options → Player settings → Visibility**. This entry is available before entering a world. The full player remains available through the assigned hotkey in game.

Scroll over the full player’s track list to change pages; scrolling outside the list leaves its page unchanged. Scrolling does not change playback.

[Previous requirements and verification limits](FEATURES.en.md).

## Equalizer and navigation (0.2.6)

The FFT reads decoded audio **before output volume is applied**. Reducing the volume, including muting it with the slider, does not shrink the spectrum. Pause and silence in the source still stop/fade it. Volume controls continue to change the actual sound.

General settings contain **Controls**, **Style** and **Visibility**. My Wave preferences are available from their separate button in the main menu and full player. No duplicate wave tab or volume slider remains in general settings.

The current track is also highlighted in the full player; inactive page arrows are disabled. Restoring a wave restores the correct section and current/upcoming list. The menu editor supports all four corners and free dragging, and the Library icon is an open book. Opening Account from a paused player keeps singleplayer paused.

## Compact menu player (0.2.6)

The title and Escape menus show a compact album card. The default scale is 50%; the large top button is removed. The music-note icon below the card opens the full player. The other icons open Settings, My Wave preferences, Library and Menu player position. Music continues when leaving a world.

The menu volume slider has its own row with an empty gap above it. The full player also has a volume slider separated from track navigation; volume is removed from Settings. Changes apply immediately and are saved.

**Menu player position** opens a separate editor: drag the card, change its scale, choose a corner or reset it. Position/scale apply to the title and Escape menus and persist between sessions. The gameplay HUD has its own independent editor. **Album colors** applies artwork colors to both the menu and gameplay HUD; turn it off for your custom colors.

Current Settings/Library tabs and the selected track are highlighted. Library lists show artwork, a page counter and enabled previous/next page arrows. Scroll over a track list to change pages. **Liked → Play all liked** plays the entire liked list, including tracks on other pages. Deleting a local/owned Yandex playlist still opens an explicit confirmation.

Manual mood tagging and the Liked mood filter are removed. The supported Yandex track metadata does not expose a reliable per-track mood suitable for this filter. Mood remains available in **My Wave preferences**, directly from the main menu. On each wave track change, the visible list moves forward to show the current and upcoming tracks, without overwriting a separately opened search/library list; new recommendation batches still load automatically.

Shuffle, repeat, like/dislike toggles and configurable hotkeys are retained. A second like only removes that like. The token, queue, playlists and settings remain saved locally.

## Earlier fixes


- Artwork uses Yandex's standard `200x200` preset; saved `256x256` addresses are normalized. Stale artwork addresses trigger a full track metadata refresh. Artwork starts loading immediately, independently of audio. Errors are visible and failed thumbnails no longer remain empty permanently. JPEG, PNG and WebP are supported.
- Clicking a queued track plays it without copying and duplicating the remaining queue. The current track, upcoming order and My Wave continuation are retained. Entered links and playlist names survive list updates.
- A smooth equalizer is enabled by default in the HUD and player. FFT measures 24 frequency bands from real audio. It is independent of the volume slider and fades on pause and does not invent movement in silence. Settings can restore time and the progress bar.
- On Windows x64, YouTube helpers — yt-dlp, Deno, FFmpeg and ffprobe — are prepared automatically on the first link import. Downloads are verified with SHA-256. Audio converts to MP3 instead of a large WAV. Preparation progress and access errors are shown.
- **3D lyrics are fully removed**: no settings tab, shortcut, renderer or Yandex lyrics requests. Reliable synchronized lyrics with a real account have not been established.

## Installation

1. Download the Windows artifact from a successful [GitHub Actions](https://github.com/Oskarrrrrsssss/Mods/actions/workflows/invumusicplayer.yml) run. Sign in to GitHub to access the private repository.
2. Extract the ZIP. Put **invumusicplayer-0.2.8-alpha.jar** in the selected Minecraft instance's `mods` folder.
3. Install Fabric Loader and Fabric API for **26.3**, and use Java 25.
4. Remove the previous Musicraft/invumusicplayer JAR from `mods` to avoid loading two copies. Settings, queue and authorization are retained.
5. Select the music-note icon in the title-screen mini player or press **M** in a world.

The standard game folder is `%APPDATA%\.minecraft\`. Other launchers may use separate instance folders.
Do not install `-sources.jar` or `-gametest.jar`.

## Controls

| Key | Action |
|---|---|
| M | Open player |
| F7 | Previous track |
| F8 | Play / pause |
| F9 | Next track |
| F10 | Start My Wave |

Like/dislike have buttons; their shortcuts are initially unbound.
Configure all shortcuts under **Player Settings → Key bindings** or Minecraft Controls.
Opening the player during gameplay keeps the world running. Screens opened from Escape retain the existing singleplayer pause.

**Settings → Style**: artwork, equalizer, opacity and RGB background/text/accent colors.
**Move HUD**: drag the panel, choose a corner or change its scale. Volume uses a slider.
**Settings → Controls → Retry artwork** retries the current artwork download.
Hover over the player's status to read the full error and artwork status. Import errors also appear in Library.

## Yandex Music and the queue

Open **Account**, paste your own Yandex Music OAuth token or an official URL containing `access_token`, then select **Connect**.
A password is not needed. Token instructions are available in the [unofficial library documentation](https://yandex-music.readthedocs.io/en/main/token.html).
Enter the token locally inside the mod.

Search, My Wave, likes and dislikes use an unofficial API. A valid token, service access and appropriate subscription are required. Yandex determines regional restrictions and track availability.
The user confirmed playback and My Wave in the preceding release; changed network features still need verification with a real account.

**My Wave settings**: happy, sad, calm, energetic or any mood; favorites, popular, discovery or balanced mode; language. Select **Apply** to start a wave.
After a recommendation batch ends, a new batch is requested. Recently issued tracks are filtered after session renewal too.

**+** adds a track to the upcoming queue. **Main player → Queue** shows upcoming tracks without the current one: click a title to play it or **×** to remove it. Manually added tracks play before the playlist/wave continues.
**Clear** keeps the current audio and cancels upcoming tracks, including automatic wave refills.
**Liked** loads your account's liked tracks. **Playlists** shows account playlists and saved local playlists.
Import a Yandex URL with `/users/name/playlists/number` or `/playlists/uuid`.
Enter a name and select **Save queue** to create a local playlist; it is not published to Yandex.

## YouTube — audio only

In **Library**, paste an HTTPS link to a regular public video, Short or `youtu.be` video and select **Import**.
The track appears under **Tracks** and in the queue. Click its title to listen.
Artwork is hidden and long titles are shortened; the player does not display video.

On first use on **Windows x64**, the mod downloads approximately 240 MB of helpers to `tools/invumusicplayer` inside this game instance. GitHub access is required. Preparation runs in the background with status visible in Library; subsequent imports reuse the installed tools.
yt-dlp and Deno versions are pinned; the FFmpeg LGPL build uses a verified SHA-256 from BtbN's official release metadata.
Helpers receive neither the Yandex token nor browser cookies.

You can provide your own `yt-dlp.exe`, `deno.exe`, `ffmpeg.exe` and `ffprobe.exe` directly in `tools`: a complete set takes priority over auto-install. On Linux/macOS, install yt-dlp, Deno and FFmpeg in PATH or `tools`; automatic installation targets Windows x64.
Sources: [yt-dlp](https://github.com/yt-dlp/yt-dlp), [Deno](https://github.com/denoland/deno), [FFmpeg](https://github.com/BtbN/FFmpeg-Builds).

Private videos, sign-in/bot checks, live streams and regional restrictions are not bypassed. YouTube access failures show their category. Playback of a particular video depends on YouTube and network access.
Audio loads fully before playback: up to **128 MiB** per YouTube track and **64 MiB** per Yandex track. YouTube temporary files are removed after download or cancellation.

## Local music and persistence

Put MP3, WAV or OGG/Vorbis files in `music` inside the game folder; select **Local** to refresh. Artwork is an adjacent PNG/JPG with the same stem. Optional UTF-8 JSON provides title and artist:

```text
music/
  Artist - Title.mp3
  Artist - Title.png
  Artist - Title.json
```

```json
{"title":"Title", "artist":"Artist"}
```

Without JSON, the filename is used. Embedded MP3 ID3 and Ogg picture-comment artwork is read when adjacent artwork is absent. Old `.lrc` files are unused.
Settings are available before entering a world. Leaving a world keeps audio playing while retaining the current track and queue. Music controls remain available on the title screen.
The token is stored in `config/invumusicplayer-auth.dat`, encrypted with Windows DPAPI for the current user; **Disconnect** deletes it. Settings: `config/invumusicplayer.json`. Queue/local playlists: `config/invumusicplayer-session.json`.

## Building and verification

JDK 25, Rust/Cargo, Python 3 and internet access are required. In the `invumusicplayer` folder:

```bat
build-windows.cmd
```

Alternatively, `gradlew.bat build`; on Linux, `bash ./gradlew build`. Output: `build/libs/invumusicplayer-0.2.8-alpha.jar`.
Gradle Wrapper 9.7.1, Loom 1.18.4, Fabric Loader 0.19.5, Fabric API 0.162.0+26.3. Minecraft 26.3 uses unobfuscated names; Yarn is unnecessary.
Tests: `gradlew.bat test`. Actual-client verification: `gradlew.bat runProductionClientGameTest`; the test mod is excluded from the installable JAR.
Windows helper verification without downloading a video: `gradlew.bat verifyYouTubeTools`.
Results and verification limits: [VALIDATION.en.md](VALIDATION.en.md). Dependencies: [THIRD_PARTY.en.md](THIRD_PARTY.en.md).
If a problem remains, include the version and status error text; omit your token. The game log is `logs/latest.log`.
