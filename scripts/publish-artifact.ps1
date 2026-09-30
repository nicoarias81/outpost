param([switch]$Verify)
# Publishes the built debug APK into dist/ with a SHA-256 sidecar.
# The sidecar is written with LF endings on purpose: earlier sidecars had CRLF and
# therefore failed `sha256sum -c` on non-Windows hosts even though the hash was right.
# Reads versionName/versionCode from app/build.gradle so the artifact name always
# matches the declared version.
$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
$gradle = Join-Path $project 'app/build.gradle'
$apk = Join-Path $project 'app/build/outputs/apk/debug/app-debug.apk'
$dist = Join-Path $project 'dist'
if (-not (Test-Path -LiteralPath $gradle)) { throw "Cannot find $gradle" }
if (-not (Test-Path -LiteralPath $apk)) { throw "Build the debug APK first: pwsh -File scripts/build.ps1 -Offline" }
$text = [System.IO.File]::ReadAllText($gradle)
$name = [regex]::Match($text, "versionName\s+'([^']+)'").Groups[1].Value
$code = [regex]::Match($text, 'versionCode\s+(\d+)').Groups[1].Value
if (-not $name -or -not $code) { throw "Cannot read versionName/versionCode from $gradle" }
$artifact = "outpost-$name-emulator-debug.apk"
$target = Join-Path $dist $artifact
$sidecar = "$target.sha256"
if ($Verify) {
    if (-not (Test-Path -LiteralPath $target)) { throw "Nothing published for $name; run without -Verify to publish." }
    $expected = (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant()
    $declared = ([System.IO.File]::ReadAllText($sidecar)).Split(' ')[0].Trim()
    if ($expected -ne $declared) { throw "Sidecar disagrees with the artifact for $name" }
    Write-Output "verified $artifact"
    Write-Output "sha256 $declared"
    exit 0
}
New-Item -ItemType Directory -Force -Path $dist | Out-Null
Copy-Item -LiteralPath $apk -Destination $target -Force
$hash = (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant()
[System.IO.File]::WriteAllText($sidecar, "$hash  $artifact`n")
Write-Output "published $artifact (versionCode $code), $( (Get-Item -LiteralPath $target).Length ) bytes"
Write-Output "sha256 $hash"
