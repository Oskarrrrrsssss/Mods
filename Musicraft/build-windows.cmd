@echo off
setlocal
cd /d "%~dp0"
echo Musicraft for Minecraft 26.3 + Fabric. Requires JDK 25.
call gradlew.bat build --no-daemon
if errorlevel 1 (
    echo Build failed. Read the error above; no mod was installed.
    exit /b 1
)
echo Built: build\libs\musicraft-0.1.0-alpha.jar
endlocal
