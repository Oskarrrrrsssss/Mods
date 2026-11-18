@echo off
setlocal
chcp 65001 >nul
cd /d "%~dp0"
echo InvuMusicPlayer: Minecraft 1.20.4 + Fabric. Requires JDK 17, Rust (cargo), Python 3.
echo InvuMusicPlayer: Minecraft 1.20.4 + Fabric. Требуются JDK 17, Rust (cargo), Python 3.
call gradlew.bat build --no-daemon
if errorlevel 1 (
    echo Build failed. Read the error above; no mod was installed.
    echo Сборка не удалась. Прочитайте ошибку выше; мод не установлен.
    exit /b 1
)
echo Built / Собрано: build\libs\invumusicplayer-0.3.0-mc1.20.4-alpha.jar
endlocal
