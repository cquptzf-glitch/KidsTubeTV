$ErrorActionPreference = 'Stop'
$version = '0.3.13'
$expected = 'c6c2787e5dd888a8f9193501506dfa3a4f8a38fbd51adefccf67d84d0c8e0fb5'
$directory = 'app/libs'
New-Item -ItemType Directory -Force $directory | Out-Null
$destination = Join-Path $directory "libmihomo-android-v$version.aar"
$url = "https://github.com/oviron/libmihomo-android/releases/download/v$version/libmihomo-android-v$version.aar"
Invoke-WebRequest -Uri $url -OutFile $destination
$actual = (Get-FileHash -Algorithm SHA256 $destination).Hash.ToLowerInvariant()
if ($actual -ne $expected) {
    Remove-Item $destination
    throw "Checksum mismatch: $actual"
}
Write-Host "Verified Mihomo AAR: $destination"
