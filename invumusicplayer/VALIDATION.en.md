# 0.2.0 validation status

Build/UI verification for this update is in progress. The user confirmed Yandex audio and My Wave on their PC before this update. New endpoints and optional YouTube are not yet tested with a live account/service. Historical results below belong to version 0.1.0, before the rename.

# Verification results

[Русский](VALIDATION.md) · **English**

Checked on 11 October 2026 (UTC). These results refer to
[GitHub Actions run 38181353522](https://github.com/Oskarrrrrsssss/Mods/actions/runs/38181353522),
source commit `84e0cebf0bc1bc4e79ac86db19cbe7c42f191635`.
Later documentation or translation changes do not change this verification date.

## Passed

- Full Minecraft 26.3 + Fabric build and installable JAR generation on Windows and Linux:
  Gradle 9.7.1, Loom 1.18.4, JDK 25.
- Windows: **62 tests passed, 0 failed, 0 skipped**.
- Linux: **61 tests passed, 0 failed, 1 Windows-only DPAPI test skipped**.
- 13 core tests: LRC timestamps, offsets, blank lines, local library and metadata,
  settings persistence, bounds, PCM volume, and actual synthetic WAV/MP3/OGG decoding.
- 21 Yandex protocol tests: request signatures and headers, rejected sign-in, current desktop
  full-track API, restricted fallback, My Wave session creation/continuation/renewal,
  feedback for the correct batch, Android LRC signatures, bounded and cancellable HTTP,
  and absence of authorization in CDN requests.
- 15 Player tests: rapid switching and cancellation, stale natural end, retry after failure,
  pause, seek, actual listening-time accounting, session progression,
  preserved search/library views, and authorization races.
- 4 AudioEngine tests: device-based timeline, pause/seek clocks, and device errors.
- 9 account tests on Windows: token and redirect parsing, bounded ciphertext storage,
  safe errors, real native DPAPI encryption/decryption/deletion,
  and preservation of the previous credential when a replacement is rejected.
- The production JAR's mod ID, entrypoint, version, and all five nested audio/JNA dependencies
  were inspected. The main JAR contains no test classes.

HTML reports in `tests-windows-latest` and `tests-ubuntu-latest` list results by class,
including the native DPAPI test.

## Actual client verification

**Passed in Minecraft 26.3 on Linux.** A separate test mod loaded the main JAR,
Fabric, and nested codecs, opened a local library, and rendered a singleplayer world.
It used Vulkan through Mesa llvmpipe in Xvfb. Nine screenshots and `latest.log` are in
`minecraft-client-smoke` for the linked successful run.

Assertions covered five registered shortcuts and their actions, non-pausing player screens,
local track/artwork/LRC, account and settings navigation, artwork visibility,
HUD corners and scale, codec discovery, and PCM decoding in Minecraft's Knot classloader.
The world, player, settings, and HUD screenshots were also visually inspected: artwork,
text-only mode, and upright lyrics in front of the player are visible; controls fit a
426 × 240 GUI (1280 × 720 window, GUI scale 3).

Tests use generated WAV/LRC/PNG and synthetic tones,
without user tracks or tokens. The test mod is excluded from the production JAR.
It injects silent playback with a fixed 500 ms clock and disables the sprint-only condition.
This verifies the real interface and rendering, but not physical sound,
sprinting motion, or lyric transitions over time. Mouse dragging, sprint-only mode,
and changing lyrics over time still require a manual gameplay check.
Windows CI builds and tests the mod but does not launch its graphical client.
The Linux log contains vanilla warnings about offline authentication,
narration, and the audio device; they did not prevent the client test from completing.

SHA-256 of the verified Windows JAR:
`927c4f39489f54092648e58c36083ae3f67cbe52d831438fa3a4f28d62e7c4c4`.
This hash belongs to the specified build; a later build may have a different hash.

## Remaining checks

- Real sign-in, full-track playback, and My Wave with a user's account.
  No real tokens were provided; tests with a simulated server do not establish live account success.
- Sound through a physical Windows audio device. Decoders and output clocks are tested,
  but the actual mixer and speakers were unavailable in this environment.
- Rendering with the user's drivers, resource packs, and other mods.

The integration uses an unofficial Yandex API. A valid Music token,
subscription entitlements, track availability, and network access on the user's PC are required.
Encrypted and preview-only responses are not bypassed or represented as full tracks.

## Reproduction

In `invumusicplayer` on Windows with JDK 25:

```bat
build-windows.cmd
```

Core and protocol verification without Loom downloads dependencies pinned by SHA-256:

```powershell
./scripts/verify-core.ps1 -JdkHome 'C:\Path\To\jdk-25'
```

Actual client:

```bat
gradlew.bat runProductionClientGameTest
```

On Linux, use Xvfb/Mesa and the same task as configured in the
[workflow](https://github.com/Oskarrrrrsssss/Mods/blob/codex/musicraft-playback/.github/workflows/invumusicplayer.yml). Dedicated-server tests are disabled;
automatic EULA acceptance is not configured.
[Complete instructions](README.en.md), [codec sources and licenses](THIRD_PARTY.en.md).
