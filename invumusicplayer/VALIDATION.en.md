# Verification results

[Русский](VALIDATION.md) · **English**

invumusicplayer 0.3.0 port for Minecraft 1.20.4 / Fabric / Java 17.

The installation archive reports its own CI OS, test counts, run link and JAR SHA-256. The older 26.3 build's statistics do not apply to this port.

- Windows/Linux build a Java 17 JAR against the 1.20.4 APIs, retaining nested MP3/Vorbis/WebP decoders and the Spotify engine. The verification mod is excluded from the installable JAR.
- Core tests cover queue, Previous, shuffle, My Wave, local artwork, authorization and API errors using fixtures.
- The real native Spotify process sends synthetic PCM through the production IPC/JavaSound pipe. Position, gain and gain-independent FFT are checked; no real Premium account is used.
- The actual 1.20.4 client runs under Linux/Xvfb: RU/EN, artwork/decoders, mini player, playback controls, queue, library and settings are checked. Screenshots and the success marker are separate CI artifacts.
- Windows DPAPI is checked on Windows. Changed real Yandex/Spotify account operations, physical sound on the user's device and playback of a particular YouTube video remain unconfirmed by these automated checks.

Service/subscription requirements are documented in README. Unreliable synchronized 3D lyrics were removed at the user's request and are not reintroduced by the port.
