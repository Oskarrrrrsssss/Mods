# Verification status

Checked on 12 October 2026 (Europe/Moscow).

## Passed

- All eight core Java classes compiled on JDK 25.0.4.1.
- JUnit Platform Console 1.13.4 ran 13 tests: 13 passed, 0 skipped, 0 failed.
- LRC parsing: BOM, offsets, fractional timestamps, repeated timestamps,
  chronological sorting, blank lines, enhanced word timing removal and invalid metadata.
- Local scanning: nested audio, filename/JSON metadata, corrupt sidecars,
  cover/LRC discovery, rejection of symlinks pointing outside the library.
- Configuration: persistence, invalid numeric values and corruption backup.
- PCM volume: signed samples and mute.
- Remote resource origins and size bounds.
- Actual PCM decoding of generated WAV, MP3 and OGG/Vorbis test audio.

Final codec set: javasound-mp3 1.9.8, javasound-vorbis 2.1.0,
tritonus-share 0.3.7.4. Older vorbisspi failed the short OGG fixture and is not included.

Independent reproduction on Windows without loading Loom:

```powershell
./scripts/verify-core.ps1 -JdkHome 'C:\Path\To\jdk-25'
```

This downloads pinned Maven Central dependencies, checks SHA-256, compiles the core,
and runs the tests. It does not compile or validate Minecraft-specific classes.

## Blocked / not verified

The Gradle 9.7.1 wrapper downloaded and started successfully, but full build failed
before source compilation. A diagnostic run returned:

```text
HTTP 403:
https://maven.fabricmc.net/net/fabricmc/fabric-loom/net.fabricmc.fabric-loom.gradle.plugin/1.18.4/net.fabricmc.fabric-loom.gradle.plugin-1.18.4.pom
```

This is a proxy destination-policy block, not a demonstrated Java compilation error.
No attempt was made to bypass the proxy. Minecraft's official metadata/artifact hosts
were also blocked earlier. The uploaded 26.3.jar exceeded the tool's 32 MiB transfer limit.

Consequently these remain unverified:

- Compilation of client UI and rendering classes against Minecraft 26.3 / Fabric API.
- Startup and playback on Windows; no real audio output device was tested here.
- HUD drag/scale, hotkeys, texture rendering and 3D lyric positioning inside the game.
- JavaSound service-provider discovery in Fabric's nested-JAR runtime.
- Windows DPAPI credential storage and removal on Windows.
- Live Yandex account authorization, full-track audio, My Wave and timed lyrics.

No account credentials were supplied or used. Live API features remain experimental.
No installable, tested mod JAR has been produced. Do not label this source package as a
finished release until a full build and the Windows/game checks above pass.
