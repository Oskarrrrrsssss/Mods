# User requirements audit — 0.2.9

[Русский](FEATURES.md) · **English**

This audit covers the resulting code and automated checks. Later requests supersede earlier ones: audio continues on world exit, native Spotify is added, and unreliable 3D lyrics and manually assigned Liked moods are removed.

| Request | Result and verification |
|---|---|
| Minecraft 26.3, Fabric, Windows; invumusicplayer name | Project/JAR renamed; Java 25 builds on Windows/Linux and an actual 26.3 client test. Client-only mod. |
| Yandex Music playback, search and My Wave inside the mod | Token authentication, audio downloads, recommendations and feedback implemented. The user previously confirmed playback/Wave; changed network operations use HTTP fixtures, without a real account. |
| Continuous Wave beyond five tracks, list updated on every switch | New batches load automatically; recent tracks are filtered. Current/upcoming list advances on switching without overwriting separately opened search/library lists. Queue/protocol tests. |
| My Wave mood in the main interface | Dedicated icon in the title-menu mini player and full player. Mood, diversity and language; no wave tab in general settings. Native client checks. |
| Local songs and text without artwork | MP3/WAV/Ogg in `music`, adjacent artwork/JSON metadata; artwork can be disabled. Real decoders and toggle verified. |
| Track/library artwork | Fixed CDN preset and metadata refresh; JPEG/PNG/WebP and retry. Native textures/thumbnails verified; public Yandex CDN artwork used when network access permits. |
| Queue, playlists, Liked and playlist deletion | Explicit queue priority, saved queue/current track, Yandex URL import and local playlists. Confirmed deletion of local/owned Yandex playlists. Liked pages and Play all across every page. Core/native tests. |
| Like/dislike symbols and second like only removes like | Separate stateful icons, toggle removal. Dislike auto-skips only in Wave. Verified. |
| Previous without jumping across songs | Selection-history navigation rather than list adjacency; explicit queue, loading, shuffle and Wave batches. Next retraces forward; no first-to-last wrapping. Regression tests, mouse events and hotkey in Minecraft. |
| Shuffle and custom hotkeys | Persisted shuffle, explicit queue priority; Minecraft key bindings including like/dislike. Registration/playback tests. |
| Custom interface, album colors and active navigation | Custom panels/buttons, album card, highlighted current section/track, adaptive colors. RU/EN screenshots. |
| Move, scale, colors and opacity | Separate HUD/menu editors, dragging, four corners and persisted settings. Native checks. |
| Title/Escape/F1 mini players, remove large header button | Title/Escape contain only mini-player controls; standalone branded button removed. Optional F1 HUD visibility. Native checks. |
| Independently hide players or hide all | Settings → Mini player: game, title, Escape and Hide all. Panels and controls disappear while music continues. Recovery through Minecraft Options. Native/persistence tests. |
| Mouse wheel in full player | Pages in both directions; boundary/outside-list scrolling leaves playback unchanged. Actual client test. |
| Volume sliders on full/mini players, separated from buttons | Dedicated row/gap; removed from settings. Click and spacing checks. |
| Equalizer together with time, independent of volume | 24 FFT bands from decoded audio before output gain. Identical spectrum at 100%, 10% and 0%; audible output changes. Pause/source silence fade it. Decoder/controlled-output tests. |
| Settings before entering a world, remembered token, continued audio after world exit | Access via mini player/Minecraft Options; encrypted DPAPI token restored automatically, queue/settings persisted. Windows DPAPI checked on Windows; world exit checked in Minecraft. |
| YouTube audio only, title and no artwork | Import and Windows yt-dlp/Deno/FFmpeg preparation implemented. Real tools downloaded/started; synthetic MP3 conversion/decoding checked. **No real video was played in automated verification; a particular link's playback remains unconfirmed.** |
| Current-line 3D lyrics without outlines | Entire feature removed with user authorization: reliable Yandex synchronization was not established. No lyrics requests or broken hidden toggle remain. |
| Liked mood without manual assignment | Filter/manual tags removed as later requested: supported Yandex metadata lacks reliable per-track moods. My Wave mood retained. |
| Spotify | Service selection, two PKCE logins, encrypted persistence, Web API catalogue/library and bundled librespot PCM playback inside the mod. Native pipe tested with synthetic audio; real Premium playback remains unconfirmed. |
| Russian/English instructions | UI, README, audit, verification report and dependencies in both languages included in ZIP. |

Verification limits: physical audio on the user's computer, particular YouTube videos and changed real-account Yandex operations need testing in that environment. Automated tests use synthetic audio/protocol fixtures and require no user token. Details: [VALIDATION.en.md](VALIDATION.en.md).

Added in 0.2.8: main-screen queue, library search, seek committed once on release; removed Stop and ±10, restored main-screen Pause; manual-queue shuffle and embedded/local artwork. Details and Spotify setup: [README](README.en.md).

0.2.9: queue tooltips, main-player pause, mini-player shuffle including forward history, hide-time button, FFT width/height, Mini player tab and removal of the two settings move buttons. F1 is checked using actual geometry-submission counts and a screenshot, not only the visibility predicate.

0.2.9 design: album-adaptive main/library surfaces and buttons, fixed settings palette, separate background opt-out, larger centered cover, right-column volume below transport and optional subtle halo. Client checks toggle the backdrop, verify that settings colors stay fixed, and capture another GUI scale.
