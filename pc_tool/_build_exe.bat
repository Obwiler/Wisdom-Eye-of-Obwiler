@echo off
cd /d "F:\TOOLS\Wisdom Eye of Obwiler\pc_tool"
echo === Building WEO-PC??.exe ===
call python -m PyInstaller "WEO-PC??.spec" --noconfirm --distpath dist
if %ERRORLEVEL% NEQ 0 (
    echo === PyInstaller FAILED ===
    pause
    exit /b 1
)
echo === Build OK. Syncing APK... ===
if not exist "dist\lib\apk" mkdir "dist\lib\apk"
copy /Y "F:\TOOLS\Wisdom Eye of Obwiler\apk\build\outputs\apk\debug\WEO-debug.apk" "dist\lib\apk\"
echo === Done! ===
echo dist\WEO-PC??.exe updated.
pause
