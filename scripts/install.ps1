# Builds the debug APK, installs it on the connected phone, and does as much of the
# one-time setup as adb allows: switches the accessibility service on, lifts Android's
# battery limit, and opens the app's setup screen for whatever is left.
param([string]$Serial)
$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$AppPackage = "com.focusguard.app"
$Service = "$AppPackage/$AppPackage.BlockerService"

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
Write-Host "Installed Focus Guard."

# Switch the accessibility service on. The existing list is kept and ours is appended,
# so other accessibility services on the phone stay enabled.
function Get-EnabledServices {
    $value = (& $adbPath @deviceArgs shell settings get secure enabled_accessibility_services) -join ""
    $value = $value.Trim()
    if ($value -eq "null") { return "" }
    return $value
}
$enabled = Get-EnabledServices
if (($enabled -split ":") -notcontains $Service) {
    $updated = if ($enabled) { "$enabled`:$Service" } else { $Service }
    & $adbPath @deviceArgs shell settings put secure enabled_accessibility_services $updated | Out-Null
    & $adbPath @deviceArgs shell settings put secure accessibility_enabled 1 | Out-Null
}
if (((Get-EnabledServices) -split ":") -contains $Service) {
    Write-Host "Accessibility service: on."
} else {
    Write-Warning ("Could not switch the accessibility service on over adb. On Xiaomi/POCO this needs " +
        "'USB debugging (Security settings)' in Developer options. Use the Turn on button in the app instead.")
}

# Lift Android's standard battery limit (Doze allowlist).
& $adbPath @deviceArgs shell dumpsys deviceidle whitelist "+$AppPackage" | Out-Null
Write-Host "Android battery limit: lifted."

# Open the setup screen for the steps adb cannot do (Xiaomi/POCO Autostart and battery saver).
& $adbPath @deviceArgs shell am start -n "$AppPackage/.MainActivity" | Out-Null
Write-Host "Opened Focus Guard on the phone. Finish any step there that is not ticked."
