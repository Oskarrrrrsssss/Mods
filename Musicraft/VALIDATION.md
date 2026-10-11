# Verification status

Checked on 11 October 2026 (UTC).

## Passed

- Full Minecraft 26.3 + Fabric compilation and installable JAR generation in GitHub Actions
  on Windows and Linux, using Gradle 9.7.1 / Loom 1.18.4 / JDK 25.
- Windows: **62 tests passed, 0 failed, 0 skipped**.
- Linux: **61 tests passed, 0 failed, 1 Windows-only DPAPI test skipped**.
- 13 core tests: LRC timestamps/offsets/blank lines, local library/metadata, persistence,
  bounds, PCM volume and actual synthetic WAV/MP3/OGG decoding.
- 21 Yandex protocol tests: exact request signatures/headers, account rejection handling,
  current desktop full-track API, restricted legacy fallback, My Wave session creation,
  queue continuation and renewal, correct batch feedback, Android LRC signatures,
  bounded/cancellable HTTP and absence of credentials in CDN requests.
- 15 Player tests: rapid switching/cancellation, stale natural end, retry after failure,
  pauses and seeks, actual listening time for feedback, session progression,
  preserved search/library views and authorization races.
- 4 AudioEngine tests: device-based timeline, pause/seek clock behavior and device errors.
- 9 account tests on Windows: token/redirect parsing, bounded ciphertext storage,
  safe error handling, real native DPAPI protection/current-user decryption/deletion,
  and preservation of the previous credential when a replacement save is rejected.
- Compiled production JAR includes all five audio/JNA dependencies as nested JARs.
  Its mod ID, entrypoint and expanded version were inspected. Test-only classes are absent.

Evidence: [Windows/Linux build, tests and client run](https://github.com/Oskarrrrrsssss/Mods/actions/runs/38181353522),
source commit `84e0cebf0bc1bc4e79ac86db19cbe7c42f191635`.
The HTML reports include each class and the native DPAPI test result.

## Client game test

A separate test mod starts the actual Minecraft client, opens a local synthetic library,
checks the registered hotkeys and native screens, moves/scales the HUD, toggles covers and
lyrics, renders a singleplayer world, and verifies codec SPI discovery under Knot.
It uses generated WAV/LRC/PNG plus synthetic test tones, never user music or credentials.
The test mod is excluded from the production JAR.

**Passed in the actual Minecraft 26.3 client on Linux.** The run loaded the production JAR,
Fabric and its nested codecs, rendered a singleplayer world, and completed without a Musicraft
exception. It used Mesa llvmpipe's Vulkan backend in Xvfb. Nine screenshots and `latest.log`
are in the `minecraft-client-smoke` artifact of the linked successful workflow run.

Assertions cover five registered hotkeys and their actions, non-pausing player screens,
local track/cover/LRC loading, account/settings navigation, cover visibility, HUD corner and
scale buttons, and bundled codec discovery/PCM decoding in Minecraft's Knot classloader.
The captured world/player/settings/HUD screenshots were also visually inspected: the cover,
text-only mode and upright lyrics in front of the player are visible, and controls fit a
426 × 240 GUI (1280 × 720 window, GUI scale 3).

The fixture injects silent playback with a fixed 500 ms clock and disables the sprint-only
condition. This checks the real rendering/UI path, not physical sound output or running
motion/time progression. Mouse dragging, the sprint-only path and lyric transitions over time
still need a manual gameplay check. Windows CI builds and tests the mod but does not launch
its graphical client. Vanilla offline/headless auth, narrator and audio-device warnings are
present in the Linux log; they did not prevent the client smoke test from completing.

Windows JAR SHA-256: `927c4f39489f54092648e58c36083ae3f67cbe52d831438fa3a4f28d62e7c4c4`.

## Remaining live checks

- Actual authorization and full-track/My Wave playback using a user's Music account.
  No real credentials were provided. Mocked protocol tests are not evidence of live account success.
- Sound output through a physical Windows audio device. The decoders and output-clock logic
  are tested; the real Windows mixer/speakers were not available in this environment.
- Rendering with the user's graphics drivers, resource packs and other installed mods.

The integration uses an unofficial Yandex API. A valid Music token, service entitlement,
track availability and network access on the user's PC are necessary. Encrypted/preview-only
responses are not silently bypassed or represented as full-track audio.

## Reproduction

```bat
build-windows.cmd
```

Core/protocol verification without Loom (downloads pinned dependencies and checks SHA-256):

```powershell
./scripts/verify-core.ps1 -JdkHome 'C:\Path\To\jdk-25'
```

Client verification on Linux uses the workflow's Xvfb/Mesa setup and a separate
`runProductionClientGameTest` task. The dedicated-server tests are disabled and EULA acceptance
is not configured. Codec source/license information is in `THIRD_PARTY.md`.
