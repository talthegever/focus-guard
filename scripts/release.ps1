# Builds the signed release APK and copies it to dist\focus-guard-<version>.apk, the file
# to send to other people. Needs keystore.properties in the project root (see CLAUDE.md).
$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot

if (-not (Test-Path -LiteralPath (Join-Path $ProjectRoot "keystore.properties") -PathType Leaf)) {
    throw "keystore.properties is missing, so the APK would be unsigned and would not install."
}

Push-Location $ProjectRoot
try {
    & .\gradlew.bat --console=plain -q :app:testDebugUnitTest :app:assembleRelease
    if ($LASTEXITCODE -ne 0) { throw "Build failed." }
} finally {
    Pop-Location
}

$outputDir = Join-Path $ProjectRoot "app\build\outputs\apk\release"
$metadata = Get-Content -Raw (Join-Path $outputDir "output-metadata.json") | ConvertFrom-Json
$element = $metadata.elements[0]
$built = Join-Path $outputDir $element.outputFile

# Refuse to hand out an APK whose signature does not verify.
$sdk = (Get-Content (Join-Path $ProjectRoot "local.properties") |
    Where-Object { $_ -match "^sdk\.dir=" }) -replace "^sdk\.dir=", "" -replace "\\\\", "\" -replace "\\:", ":"
$apksigner = Get-ChildItem (Join-Path $sdk "build-tools\*\apksigner.bat") | Sort-Object FullName | Select-Object -Last 1
if (-not $apksigner) { throw "apksigner was not found under $sdk\build-tools." }
& $apksigner.FullName verify $built
if ($LASTEXITCODE -ne 0) { throw "The APK signature did not verify." }

$dist = Join-Path $ProjectRoot "dist"
New-Item -ItemType Directory -Force $dist | Out-Null
$target = Join-Path $dist "focus-guard-$($element.versionName).apk"
Copy-Item -LiteralPath $built -Destination $target -Force
# The install guide travels with the APK.
Copy-Item -LiteralPath (Join-Path $ProjectRoot "INSTALL.md") -Destination $dist -Force

Write-Host "Release APK: $target"
Write-Host "SHA-256:     $((Get-FileHash -Algorithm SHA256 -LiteralPath $target).Hash.ToLower())"
