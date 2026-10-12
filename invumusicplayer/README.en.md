# invumusicplayer — Minecraft Java 26.3 / Fabric / Windows

[Русский](README.md) · **English**

A client music player: Yandex Music, My Wave, local music and YouTube audio.
Move and scale the in-game panel and customize its colors and opacity.
The interface follows Minecraft's language: Russian or English.

## Changes in 0.2.2

- Escape now shows a compact player in the top-left free area of the pause menu: current track, artwork where space allows, equalizer, previous/play-pause/next controls and an Invu Player entry.
- A separate Settings button opens player settings directly from Escape; Back returns to the pause menu. Singleplayer remains paused while editing these settings.
- F1 can hide Minecraft's HUD while retaining the music player at its saved position and scale. This is enabled by default; **Settings → Audio → Player with F1** turns it off. The main HUD visibility setting still controls the player itself.
- Pause-menu entries have rounded corners, drawn icons and subtle hover accents. Their width adapts to the free space left of Minecraft's menu buttons.

## Earlier fixes


- Artwork uses Yandex's standard `200x200` preset; saved `256x256` addresses are normalized. Stale artwork addresses trigger a full track metadata refresh. Artwork starts loading immediately, independently of audio. Errors are visible and failed thumbnails no longer remain empty permanently. JPEG, PNG and WebP are supported.
- Clicking a queued track plays it without copying and duplicating the remaining queue. The current track, upcoming order and My Wave continuation are retained. Entered links and playlist names survive list updates.
- A smooth equalizer is enabled by default in the HUD and player. FFT measures 24 frequency bands from real audio. It responds to volume, fades on pause and does not invent movement in silence. Settings can restore time and the progress bar.
- On Windows x64, YouTube helpers — yt-dlp, Deno, FFmpeg and ffprobe — are prepared automatically on the first link import. Downloads are verified with SHA-256. Audio converts to MP3 instead of a large WAV. Preparation progress and access errors are shown.
- **3D lyrics are fully removed**: no settings tab, shortcut, renderer or Yandex lyrics requests. Reliable synchronized lyrics with a real account have not been established.

## Installation

1. Download the Windows artifact from a successful [GitHub Actions](https://github.com/Oskarrrrrsssss/Mods/actions/workflows/invumusicplayer.yml) run. Sign in to GitHub to access the private repository.
2. Extract the ZIP. Put **invumusicplayer-0.2.2-alpha.jar** in the selected Minecraft instance's `mods` folder.
3. Install Fabric Loader and Fabric API for **26.3**, and use Java 25.
4. Remove the previous Musicraft/invumusicplayer JAR from `mods` to avoid loading two copies. Settings, queue and authorization are retained.
5. Open **invumusicplayer** on the title screen or press **M** in a world.

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
The mod's screens do not pause gameplay.

**Settings → Appearance**: artwork, equalizer, opacity and RGB background/text/accent colors.
**Move HUD**: drag the panel, choose a corner or change its scale. Volume uses a slider.
**Settings → Audio → Retry artwork** retries the current artwork download.
Hover over the player's status to read the full error and artwork status. Import errors also appear in Library.

## Yandex Music and the queue

Open **Account**, paste your own Yandex Music OAuth token or an official URL containing `access_token`, then select **Connect**.
A password is not needed. Token instructions are available in the [unofficial library documentation](https://yandex-music.readthedocs.io/en/main/token.html).
Enter the token locally inside the mod.

Search, My Wave, likes and dislikes use an unofficial API. A valid token, service access and appropriate subscription are required. Yandex determines regional restrictions and track availability.
The user confirmed playback and My Wave in the preceding release; changed network features still need verification with a real account.

**Settings → My Wave**: happy, sad, calm, energetic or any mood; favorites, popular, discovery or balanced mode; language. Select **Apply** to start a wave.
After a recommendation batch ends, a new batch is requested. Recently issued tracks are filtered after session renewal too.

**+** adds a track to the upcoming queue. **Library → Queue** shows upcoming tracks without the current one: click a title to play it or **×** to remove it. Manually added tracks play before the playlist/wave continues.
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

Without JSON, the filename is used. Embedded ID3 artwork is not extracted. Old `.lrc` files are unused.
Settings are available before entering a world. Leaving a world stops audio while retaining the current track and queue. Music can be resumed from the title screen.
The token is stored in `config/invumusicplayer-auth.dat`, encrypted with Windows DPAPI for the current user; **Disconnect** deletes it. Settings: `config/invumusicplayer.json`. Queue/local playlists: `config/invumusicplayer-session.json`.

## Building and verification

JDK 25 and internet access are required. In the `invumusicplayer` folder:

```bat
build-windows.cmd
```

Alternatively, `gradlew.bat build`; on Linux, `bash ./gradlew build`. Output: `build/libs/invumusicplayer-0.2.2-alpha.jar`.
Gradle Wrapper 9.7.1, Loom 1.18.4, Fabric Loader 0.19.5, Fabric API 0.162.0+26.3. Minecraft 26.3 uses unobfuscated names; Yarn is unnecessary.
Tests: `gradlew.bat test`. Actual-client verification: `gradlew.bat runProductionClientGameTest`; the test mod is excluded from the installable JAR.
Windows helper verification without downloading a video: `gradlew.bat verifyYouTubeTools`.
Results and verification limits: [VALIDATION.en.md](VALIDATION.en.md). Dependencies: [THIRD_PARTY.en.md](THIRD_PARTY.en.md).
If a problem remains, include the version and status error text; omit your token. The game log is `logs/latest.log`.
