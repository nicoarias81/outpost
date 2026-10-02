param(
    [Parameter(Mandatory=$true)][string]$KeyStore,
    [Parameter(Mandatory=$true)][string]$KeyAlias,
    [Parameter(Mandatory=$true)][ValidatePattern('^[a-fA-F0-9]{64}$')][string]$ExpectedCertificateSha256
)
$ErrorActionPreference='Stop'
$project=Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'environment.ps1')
$settings=Get-OutpostSettings
$env:JAVA_HOME=$settings.javaHome
if(-not $env:OUTPOST_STORE_PASSWORD -or -not $env:OUTPOST_KEY_PASSWORD){throw 'Set OUTPOST_STORE_PASSWORD and OUTPOST_KEY_PASSWORD in this process; never put passwords in command arguments or Git.'}
$store=(Resolve-Path -LiteralPath $KeyStore).Path
if($store.StartsWith($project+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){
    & git -C $project check-ignore -q -- $store
    if($LASTEXITCODE -ne 0){throw 'Signing key inside this repository must be ignored'}
}
$receipt=Get-Content -LiteralPath "$project/.local/release-build-receipt.json" -Raw|ConvertFrom-Json
if($receipt.mainSourcesSha256 -ne (Get-OutpostSourceFingerprint main)){throw 'Rebuild release: source receipt is stale'}
$audit=Get-Content -LiteralPath "$project/.local/$($receipt.runId)/audit.json" -Raw|ConvertFrom-Json
if(-not $audit.passed){throw 'Static release audit required'}
$entry=@($receipt.artifacts|Where-Object {$_.path -eq 'app/build/outputs/apk/release/app-release-unsigned.apk'})
if($entry.Count -ne 1){throw 'One unsigned APK required'}
$unsigned=Join-Path $project $entry[0].path
if((Get-FileHash -LiteralPath $unsigned -Algorithm SHA256).Hash.ToLowerInvariant() -ne $entry[0].sha256){throw 'Unsigned APK hash changed'}
$buildTools=Join-Path (Resolve-OutpostSdk '') 'build-tools/35.0.0'
$metadata=(& "$buildTools/aapt.exe" dump badging $unsigned)-join "`n"
if($LASTEXITCODE -ne 0 -or $metadata -match 'application-debuggable'){throw 'Non-debuggable APK required'}
$version=[regex]::Match($metadata,"versionName='([^']+)'").Groups[1].Value
if($version -notmatch '^[0-9A-Za-z.-]+$'){throw 'Invalid artifact version'}
$target=Join-Path $project "dist/outpost-$version-release.apk"
if(Test-Path -LiteralPath $target){throw 'Frozen signed artifact already exists; use a new version instead of replacing it'}
$stage=Join-Path $project ('.local/signing-'+[guid]::NewGuid().ToString('N'));New-Item -ItemType Directory -Path $stage|Out-Null
$signed=Join-Path $stage 'candidate.apk'
& "$buildTools/apksigner.bat" sign --ks $store --ks-key-alias $KeyAlias --ks-pass env:OUTPOST_STORE_PASSWORD --key-pass env:OUTPOST_KEY_PASSWORD --out $signed $unsigned
if($LASTEXITCODE -ne 0){throw 'Signing failed; nothing published'}
$verification=(& "$buildTools/apksigner.bat" verify --verbose --print-certs $signed)-join "`n"
if($LASTEXITCODE -ne 0){throw 'Signed APK verification failed'}
$certs=[regex]::Matches($verification,'Signer #[0-9]+ certificate SHA-256 digest: ([a-fA-F0-9]{64})')
if($certs.Count -ne 1 -or $certs[0].Groups[1].Value.ToLowerInvariant() -ne $ExpectedCertificateSha256.ToLowerInvariant() -or $verification -match 'CN=Android Debug'){throw 'Certificate mismatch or forbidden debug signing identity'}
& "$buildTools/zipalign.exe" -c -P 16 4 $signed
if($LASTEXITCODE -ne 0){throw 'Signed APK alignment failed'}
New-Item -ItemType Directory -Force -Path (Split-Path $target -Parent)|Out-Null
Copy-Item -LiteralPath $signed -Destination $target
$hash=(Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant()
[IO.File]::WriteAllText("$target.sha256","$hash  $([IO.Path]::GetFileName($target))`n")
[ordered]@{version=$version;sha256=$hash;certificateSha256=$ExpectedCertificateSha256.ToLowerInvariant();unsignedSha256=$entry[0].sha256;buildRun=$receipt.runId;status='Signed locally; exact signed-device acceptance and distribution approval still required'}|ConvertTo-Json|Set-Content -LiteralPath "$target.json" -Encoding utf8
Write-Output "Signed local candidate: $target"
Write-Output "SHA256: $hash"
