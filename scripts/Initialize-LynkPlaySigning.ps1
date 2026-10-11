[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$configPath = Join-Path $root 'release-signing.properties'
$signingDirectory = Join-Path $root '.private/signing'
$keyPath = Join-Path $signingDirectory 'lynkplay.p12'
if (Test-Path -LiteralPath $configPath) {
    if (-not (Test-Path -LiteralPath $keyPath)) { throw '签名配置存在但密钥缺失，请恢复备份，不要重新生成。' }
    Write-Host '已有本地签名；保留原密钥和配置。构建时会核验证书指纹。'
    return
}
if (Test-Path -LiteralPath $keyPath) { throw '密钥已存在，请恢复签名配置；禁止覆盖已有密钥。' }
$keytool = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/keytool.exe' } else { 'keytool' }
New-Item -ItemType Directory -Force -Path $signingDirectory | Out-Null
$random = New-Object byte[] 32
$generator = [Security.Cryptography.RandomNumberGenerator]::Create()
try { $generator.GetBytes($random) } finally { $generator.Dispose() }
$password = [Convert]::ToBase64String($random)
$previousPassword = $env:LYNKPLAY_STORE_PASSWORD
try {
    # 密码通过环境变量传给 keytool，避免出现在命令参数与输出中。
    $env:LYNKPLAY_STORE_PASSWORD = $password
    & $keytool -genkeypair -keystore $keyPath -storetype PKCS12 -alias lynkplay -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=LynkPlay,OU=Local Build,O=LynkPlay,C=CN' -storepass:env LYNKPLAY_STORE_PASSWORD -keypass:env LYNKPLAY_STORE_PASSWORD
    if ($LASTEXITCODE -ne 0) { throw '生成 APK 签名失败。' }
    $certificatePath = Join-Path $signingDirectory 'lynkplay-signing.cer'
    & $keytool -exportcert -keystore $keyPath -alias lynkplay -storepass:env LYNKPLAY_STORE_PASSWORD -file $certificatePath
    if ($LASTEXITCODE -ne 0) { throw '导出签名证书失败。' }
    $fingerprint = (Get-FileHash -LiteralPath $certificatePath -Algorithm SHA256).Hash.ToLowerInvariant()
    $properties = "storeFile=.private/signing/lynkplay.p12`nstorePassword=$password`nkeyAlias=lynkplay`nkeyPassword=$password`nsignerSha256=$fingerprint`n"
    [IO.File]::WriteAllText($configPath, $properties, [Text.UTF8Encoding]::new($false))
    Write-Host "已生成 LynkPlay 长期签名，证书 SHA256：$fingerprint"
    Write-Host '请一起备份 .private/signing/lynkplay.p12 和 release-signing.properties；后续版本必须使用同一密钥。'
} finally { $env:LYNKPLAY_STORE_PASSWORD = $previousPassword }
