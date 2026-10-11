[CmdletBinding()]
param([string]$AuthenticationAssetsDirectory)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
if (-not $AuthenticationAssetsDirectory) { $AuthenticationAssetsDirectory = Join-Path $root '.private/auth-assets' }
$inputDirectory = (Resolve-Path -LiteralPath $AuthenticationAssetsDirectory).Path
$sdk = $env:ANDROID_HOME
if (-not $sdk) { throw '请设置 ANDROID_HOME。' }
$tools = Get-ChildItem -LiteralPath (Join-Path $sdk 'build-tools') -Directory |
    Where-Object { $_.Name -match '^\d+\.\d+\.\d+$' } |
    Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
if (-not $tools) { throw '找不到 Android SDK build-tools。' }
$previousInput = $env:DIPLAY_AUTH_ASSETS_DIR
Push-Location $root
try {
    $env:DIPLAY_AUTH_ASSETS_DIR = $inputDirectory
    & .\gradlew.bat :mobile:assembleCarTest --console=plain
    if ($LASTEXITCODE -ne 0) { throw '车测构建失败，未通过校验的安装包不可用于车测。' }
    $apk = Join-Path $root 'mobile/build/outputs/apk/carTest/mobile-carTest.apk'
    $verification = & (Join-Path $tools.FullName 'apksigner.bat') verify --min-sdk-version 28 --verbose --print-certs $apk
    if ($LASTEXITCODE -ne 0) { throw 'APK 签名无法通过 Android 9 校验。' }
    $pin = $env:LYNKPLAY_SIGNER_SHA256
    if (-not $pin) {
        $line = Get-Content -LiteralPath (Join-Path $root 'release-signing.properties') | Where-Object { $_ -match '^signerSha256=' }
        $pin = ($line -split '=', 2)[1]
    }
    # build-tools 37 使用 V2 Signer 文案，旧版本使用 Signer #1。
    $fingerprints = @($verification | ForEach-Object {
        if ($_ -match '^(?:Signer #1 |V\d+(?:\.\d+)? Signer: )certificate SHA-256 digest: ([0-9a-fA-F]{64})$') { $Matches[1].ToLowerInvariant() }
    })
    if ($verification -notcontains 'Number of signers: 1' -or $fingerprints.Count -eq 0 -or
        @($fingerprints | Where-Object { $_ -ne $pin.ToLowerInvariant() }).Count -gt 0) {
        throw '最终 APK 签名与长期签名不一致。'
    }
    $badging = & (Join-Path $tools.FullName 'aapt2.exe') dump badging $apk
    if ($LASTEXITCODE -ne 0 -or $badging[0] -notmatch "^package: name='com.leth0x01.lynkplay'") { throw '最终 APK 包名校验失败。' }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [IO.Compression.ZipFile]::OpenRead($apk)
    try {
        foreach ($name in @('identity.pk8', 'certificate.p7b')) {
            $entry = $archive.GetEntry("assets/offline-mfi/$name")
            if (-not $entry) { throw '最终 APK 缺少认证资产。' }
            $stream = $entry.Open()
            $algorithm = [Security.Cryptography.SHA256]::Create()
            try { $digest = [BitConverter]::ToString($algorithm.ComputeHash($stream)).Replace('-', '') }
            finally { $stream.Dispose(); $algorithm.Dispose() }
            if ($digest -ne (Get-FileHash -LiteralPath (Join-Path $inputDirectory "offline-mfi/$name") -Algorithm SHA256).Hash) {
                throw '最终 APK 认证资产与选定输入不同。'
            }
        }
    } finally { $archive.Dispose() }
    $hash = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
    [IO.File]::WriteAllText((Join-Path (Split-Path $apk) 'SHA256SUMS.txt'), "$hash  mobile-carTest.apk`n", [Text.UTF8Encoding]::new($false))
    Write-Host $badging[0]
    Write-Host "签名证书 SHA256：$pin"
    Write-Host "APK SHA256：$hash"
    Write-Host "车测包：$apk"
} finally {
    $env:DIPLAY_AUTH_ASSETS_DIR = $previousInput
    Pop-Location
}
