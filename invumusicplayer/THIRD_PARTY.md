# Сторонние компоненты

**Русский** · [English](THIRD_PARTY.en.md)

Исходный код InvuMusicPlayer распространяется по [лицензии MIT](LICENSE).
Зависимости сохраняют собственные лицензии. Оригинальные юридические тексты лицензий
не переводятся и не заменяются этой русской справкой.

| Компонент | Версия | Лицензия / исходный код |
|---|---|---|
| Fabric Loader | 0.19.5 | Apache-2.0; https://github.com/FabricMC/fabric-loader |
| Fabric API | 0.162.0+26.3 | Apache-2.0; https://github.com/FabricMC/fabric-api |
| Fabric Loom | 1.18.4 | MIT; https://github.com/FabricMC/fabric-loom |
| Gradle Wrapper | 9.7.1 | Apache-2.0; https://github.com/gradle/gradle |
| Gson | поставляется Minecraft | Apache-2.0; https://github.com/google/gson |
| javasound-mp3 | 1.9.8 | LGPL-2.0; https://github.com/Tianscar/javasound-mp3 |
| javasound-vorbis | 2.1.0 | LGPL-2.0 и Xiph BSD; https://github.com/Tianscar/javasound-vorbis |
| tritonus-share | 0.3.7.4 | LGPL; https://github.com/umjammer/tritonus |
| TwelveMonkeys ImageIO (WebP, core, common) | 3.12.0 | BSD-3-Clause; https://github.com/haraldk/TwelveMonkeys |
| JNA / JNA Platform | 5.18.1 | LGPL-2.1 / Apache-2.0; https://github.com/java-native-access/jna |
| JUnit Jupiter | 5.13.4 | EPL-2.0; https://github.com/junit-team/junit-framework |

Аудиокодеки включены отдельными вложенными JAR без изменения исходного кода.
При распространении скомпилированных файлов сохраняй уведомления и тексты лицензий
зависимостей и предоставляй исходный код и возможность замены, предусмотренные их лицензиями.
Версии зафиксированы в `build.gradle`, исходные репозитории указаны выше.
Скрипты и JAR Gradle Wrapper взяты из официального примера Fabric.

Minecraft — проприетарное ПО; игра не включена в проект или сборку.
Тестовые MP3 и OGG созданы специально для тестов: синусоидальный сигнал 440 Гц, 0,25 секунды.
Сторонняя музыка, обложки, слова песен, файлы референсного мода и данные аккаунтов не включены.


## Отдельно загружаемые инструменты YouTube

Мод автоматически подготавливает на Windows x64 официальные yt-dlp (Unlicense; компоненты исполняемого файла имеют отдельные лицензии), Deno (MIT), FFmpeg/ffprobe LGPL из BtbN/FFmpeg-Builds. Исполняемые файлы не встроены в JAR. Их источники, условия и сборочные скрипты доступны в https://github.com/yt-dlp/yt-dlp, https://github.com/denoland/deno и https://github.com/BtbN/FFmpeg-Builds. Информация сохраняется рядом с инструментами в NOTICE.txt.
