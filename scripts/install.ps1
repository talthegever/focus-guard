# Builds the debug APK and installs it on the connected phone or emulator.
param([string]$Serial)
$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot

# Prefer the Android SDK's adb: an older adb on PATH keeps restarting the adb server.
$adbPath = $null
$candidates = @()
if ($env:ANDROID_SDK_ROOT) { $candidates += (Join-Path $env:ANDROID_SDK_ROOT "platform-tools\adb.exe") }
if ($env:ANDROID_HOME) { $candidates += (Join-Path $env:ANDROID_HOME "platform-tools\adb.exe") }
if ($env:LOCALAPPDATA) { $candidates += (Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe") }
foreach ($candidate in $candidates) {
    if (Test-Path -LiteralPath $candidate -PathType Leaf) { $adbPath = $candidate; break }
}
if (-not $adbPath) {
    $onPath = Get-Command adb -ErrorAction SilentlyContinue
    if ($onPath) { $adbPath = $onPath.Source }
}
if (-not $adbPath) { throw "adb was not found. Install Android platform-tools." }

$deviceArgs = @()
if ($Serial) { $deviceArgs = @("-s", $Serial) }
$devices = & $adbPath devices
$authorized = @($devices | Select-String "\tdevice$")
if ($authorized.Count -eq 0) { throw "No authorized Android device. Connect it and approve USB debugging." }
if (-not $Serial -and $authorized.Count -gt 1) { throw "Several devices are connected. Pass -Serial with one device ID." }

Push-Location $ProjectRoot
try {
    & .\gradlew.bat --console=plain -q :app:assembleDebug
    if ($LASTEXITCODE -ne 0) { throw "Build failed." }
} finally {
    Pop-Location
}

$apk = Join-Path $ProjectRoot "app\build\outputs\apk\debug\app-debug.apk"
& $adbPath @deviceArgs install -r $apk
if ($LASTEXITCODE -ne 0) { throw "Install failed." }
Write-Host "Installed Focus Guard. Switch it on under Settings > Accessibility > Downloaded apps."
