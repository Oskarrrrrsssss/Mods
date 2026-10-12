# Third-party components

[Русский](THIRD_PARTY.md) · **English**

InvuMusicPlayer source is [MIT-licensed](LICENSE).
Dependencies retain their own licenses. Original legal license texts
are preserved; this documentation does not translate or replace them.

| Component | Version | License / source |
|---|---|---|
| Fabric Loader | 0.19.5 | Apache-2.0; https://github.com/FabricMC/fabric-loader |
| Fabric API | 0.162.0+26.3 | Apache-2.0; https://github.com/FabricMC/fabric-api |
| Fabric Loom | 1.18.4 | MIT; https://github.com/FabricMC/fabric-loom |
| Gradle Wrapper | 9.7.1 | Apache-2.0; https://github.com/gradle/gradle |
| Gson | provided by Minecraft | Apache-2.0; https://github.com/google/gson |
| javasound-mp3 | 1.9.8 | LGPL-2.0; https://github.com/Tianscar/javasound-mp3 |
| javasound-vorbis | 2.1.0 | LGPL-2.0 and Xiph BSD; https://github.com/Tianscar/javasound-vorbis |
| tritonus-share | 0.3.7.4 | LGPL; https://github.com/umjammer/tritonus |
| TwelveMonkeys ImageIO (WebP, core, common) | 3.12.0 | BSD-3-Clause; https://github.com/haraldk/TwelveMonkeys |
| JNA / JNA Platform | 5.18.1 | LGPL-2.1 / Apache-2.0; https://github.com/java-native-access/jna |
| JUnit Jupiter | 5.13.4 | EPL-2.0; https://github.com/junit-team/junit-framework |

Audio providers are separate nested JAR dependencies, without source modifications.
When distributing compiled binaries, preserve dependency notices and license texts,
and provide the corresponding source and replacement mechanism required by their licenses.
Versions are pinned in `build.gradle`; source repositories are linked above.
The Gradle Wrapper scripts and JAR came from the official Fabric example.

Minecraft is proprietary software and is not bundled in the project or build.
The MP3 and OGG test tones were generated specifically for the tests: a 440 Hz sine, 0.25 seconds.
No third-party music, artwork, lyrics, reference mod files, or account data are included.
