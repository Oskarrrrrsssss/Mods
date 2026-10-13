@echo off
setlocal
chcp 65001 >nul
cd /d "%~dp0"
echo InvuMusicPlayer: Minecraft 26.3 + Fabric. Requires JDK 25.
echo InvuMusicPlayer: Minecraft 26.3 + Fabric. Требуется JDK 25.
call gradlew.bat build --no-daemon
if errorlevel 1 (
    echo Build failed. Read the error above; no mod was installed.
    echo Сборка не удалась. Прочитайте ошибку выше; мод не установлен.
    exit /b 1
)
echo Built / Собрано: build\libs\invumusicplayer-0.2.6-alpha.jar
endlocal
