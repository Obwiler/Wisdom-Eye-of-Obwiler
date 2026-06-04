@echo off
cd /d "%~dp0"
echo === Building WEO-PC??.exe ===
call python -m PyInstaller "WEO-PC??.spec" --noconfirm --distpath dist
if %ERRORLEVEL% NEQ 0 (
    echo === PyInstaller FAILED ===
    pause
    exit /b 1
)
echo === Build OK. Syncing APK... ===
if not exist "dist\lib\apk" mkdir "dist\lib\apk"
copy /Y "..\apk\build\outputs\apk\debug\WEO-debug.apk" "dist\lib\apk\"
echo === Done! ===
echo dist\WEO-PC??.exe updated.
pause
