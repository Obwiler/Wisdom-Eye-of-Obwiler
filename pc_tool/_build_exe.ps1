Set-Location "F:\TOOLS\Wisdom Eye of Obwiler\pc_tool"
Write-Host ">>> Building WEO-PC工具.exe..." -ForegroundColor Green
python -m PyInstaller "WEO-PC工具.spec" --noconfirm --distpath dist
if ($LASTEXITCODE -eq 0) {
    Write-Host ">>> Build OK. Syncing APK..." -ForegroundColor Green
    New-Item -ItemType Directory -Force -Path "dist\lib\apk" | Out-Null
    Copy-Item "F:\TOOLS\Wisdom Eye of Obwiler\apk\build\outputs\apk\debug\WEO-debug.apk" "dist\lib\apk\" -Force
    Write-Host ">>> Done! dist\WEO-PC工具.exe 已更新" -ForegroundColor Green
} else {
    Write-Host ">>> PyInstaller failed with exit code $LASTEXITCODE" -ForegroundColor Red
}
Write-Host "Press any key to close..." 
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
