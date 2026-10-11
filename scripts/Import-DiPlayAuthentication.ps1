[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$ApkPath,
    [Parameter(Mandatory = $true)][ValidatePattern('^[0-9a-fA-F]{64}$')][string]$ExpectedSha256,
    [Parameter(Mandatory = $true)][string]$SourceUrl
)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$apk = (Resolve-Path -LiteralPath $ApkPath).Path
$actualHash = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actualHash -ne $ExpectedSha256.ToLowerInvariant()) { throw 'APK SHA256 与指定的官方校验值不一致，拒绝导入。' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($apk)
$target = Join-Path $root '.private/auth-assets/offline-mfi'
try {
    $inputs = @{}
    foreach ($name in @('identity.pk8', 'certificate.p7b')) {
        $entries = @($archive.Entries | Where-Object { $_.FullName -ceq "assets/offline-mfi/$name" })
        if ($entries.Count -ne 1 -or $entries[0].Length -lt 1 -or $entries[0].Length -gt 16384) {
            throw 'APK 缺少唯一、有效大小的认证条目，拒绝导入。'
        }
        $stream = $entries[0].Open()
        $memory = [IO.MemoryStream]::new()
        try { $stream.CopyTo($memory); $inputs[$name] = $memory.ToArray() }
        finally { $stream.Dispose(); $memory.Dispose() }
        $existingPath = Join-Path $target $name
        if (Test-Path -LiteralPath $existingPath) {
            $existing = [IO.File]::ReadAllBytes($existingPath)
            if ([Convert]::ToBase64String($existing) -cne [Convert]::ToBase64String($inputs[$name])) {
                throw '已有不同认证身份；请先检查原有输入，禁止自动覆盖。'
            }
        }
    }
    New-Item -ItemType Directory -Force -Path $target | Out-Null
    foreach ($name in $inputs.Keys) { [IO.File]::WriteAllBytes((Join-Path $target $name), $inputs[$name]) }
    $record = @{ sourceUrl = $SourceUrl; apkSha256 = $actualHash; importedAtUtc = [DateTime]::UtcNow.ToString('o'); experimental = $true } | ConvertTo-Json
    [IO.File]::WriteAllText((Join-Path $root '.private/auth-source.json'), $record, [Text.UTF8Encoding]::new($false))
    Write-Host '已导入本地实验性认证输入；未输出密钥内容。格式与配对会在 Gradle 构建时检查。'
} finally {
    $archive.Dispose()
    foreach ($bytes in $inputs.Values) { [Array]::Clear($bytes, 0, $bytes.Length) }
}
