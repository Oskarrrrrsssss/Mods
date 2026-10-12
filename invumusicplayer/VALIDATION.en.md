# Verification results

[Русский](VALIDATION.md) · **English**

Release 0.2.5 checks queue, artwork and spectrum analyzer fixes.
The installation ZIP prefixes this report with the particular CI run, test counts and JAR SHA-256.

- Shuffle tests cover chosen-track preservation, unique traversal, explicit queue priority, restoring order and persisted mode. Rating tests verify that a second like only removes the like. Liked-list race protection, wave-list updates after each skip and playlist deletion with current playback retained are checked.
- Native client navigation additionally opens My Wave settings from the title menu, toggles like/dislike/shuffle icons, pages through Liked, plays the full list, verifies active sections and separated volume controls, moves/scales the menu player, checks library artwork, cancels/confirms local playlist deletion and retains playing audio when leaving the world.
- Client checks also ensure that the wave tab is absent from general settings, all menu corners work and opening Account from Escape retains pause. Restored wave section/list are covered by core tests.
- Queue tests verify playing an added item without duplicating the remainder, restoring the current item after restart, natural advancement, radio continuation and stale-request protection.
- HTTP fixtures verify the artwork preset, metadata refresh after 404, `error: null`, audio signatures, recommendations and absent CDN authorization headers.
- FFT tests use synthetic low/high tones, stereo audio and source silence. A real AudioEngine decoder with a controlled output line verifies identical spectral levels at 100%, 10% and 0% output gain, while the written audio is attenuated/muted.
- Client verification additionally presses real Escape/F1 keys, checks pause-menu transport controls, return paths from player/settings, continued singleplayer pause in settings and both enabled/disabled F1 visibility. Screenshots record actual rendering; pause controls are checked for overlap with vanilla buttons.
- The actual Minecraft client on Linux verifies Russian/English screens, JPEG/PNG/WebP textures, three ready track thumbnails, settings, HUD and leaving a world. The audio device is replaced by a test fixture; this does not verify audible playback on Windows.
- CI attempts to download a public Yandex CDN artwork without credentials. If available, the client test uses it, and the installation report states the result. An unavailable CDN is not represented as a successful network check.
- Windows CI prepares real yt-dlp, Deno, FFmpeg/ffprobe, verifies downloads, runs each binary and converts a synthetic tone to MP3 for JavaSound decoding. This does not verify playback of a particular YouTube video.
- Windows DPAPI token persistence is checked on Windows and skipped on Linux.

No actual OAuth token is used in automated tests. Changed integration with a real Yandex account and playback of particular YouTube videos are not confirmed by these tests.
3D lyrics are removed: no UI, shortcut, renderer or network lyrics requests remain.

[CI runs and artifacts](https://github.com/Oskarrrrrsssss/Mods/actions/workflows/invumusicplayer.yml).
