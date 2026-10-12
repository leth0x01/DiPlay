# 领克 06 E02 仪表、方向盘与媒体语音专项采集，兼容 Windows PowerShell 5.1。
param (
    [string]$AdbPath = "",
    [string]$Serial = "",
    [string]$OutputRoot = "",
    [ValidateRange(1, 22)][int]$StartPhase = 1,
    [switch]$SkipComponents
)

$ErrorActionPreference = "Continue"
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

if (-not $AdbPath) {
    $NearbyAdb = Join-Path $PSScriptRoot "adb.exe"
    if (Test-Path -LiteralPath $NearbyAdb) {
        $AdbPath = $NearbyAdb
    } else {
        $InstalledAdb = Get-Command adb -ErrorAction SilentlyContinue
        if ($InstalledAdb) { $AdbPath = $InstalledAdb.Source }
    }
}
if (-not $AdbPath -or -not (Test-Path -LiteralPath $AdbPath)) {
    throw "找不到 adb。请将脚本放在 adb.exe 同目录，或通过 -AdbPath 指定路径。"
}
$script:ADB = (Resolve-Path -LiteralPath $AdbPath).Path
$script:AdbPrefix = @()
$script:PulledFiles = New-Object 'System.Collections.Generic.List[object]'
$script:ConnectionLost = $false

function Invoke-Adb {
    param ([string[]]$Arguments)
    $Prefix = $script:AdbPrefix
    $Lines = @(& $script:ADB @Prefix @Arguments 2>&1)
    return [pscustomobject]@{
        ExitCode = $LASTEXITCODE
        Lines = @($Lines | ForEach-Object { $_.ToString() })
    }
}

$Devices = Invoke-Adb -Arguments @("devices", "-l")
$Connected = @($Devices.Lines | ForEach-Object {
    if ($_ -match '^([^\s]+)\s+device(?:\s|$)') { $Matches[1] }
})
if (-not $Serial) {
    if ($Connected.Count -ne 1) {
        $Devices.Lines | Write-Host
        throw "需要连接一台已授权车机；有多台设备时请通过 -Serial 指定车机序列号。"
    }
    $Serial = $Connected[0]
}
if ($Serial -notin $Connected) { throw "指定的车机尚未连接或未完成 ADB 授权：$Serial" }
$script:AdbPrefix = @("-s", $Serial)
if (-not $OutputRoot) { $OutputRoot = $PSScriptRoot }
$Timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$script:OutputDir = Join-Path $OutputRoot "lynk06_cluster_$Timestamp"
New-Item -ItemType Directory -Force -Path $script:OutputDir | Out-Null

function Save-Text {
    param ([string]$RelativePath, [object]$Content)
    $Destination = Join-Path $script:OutputDir $RelativePath
    New-Item -ItemType Directory -Force -Path (Split-Path $Destination -Parent) | Out-Null
    $Content | Out-File -LiteralPath $Destination -Encoding utf8
}

function Save-Shell {
    param ([string]$RelativePath, [string]$Command)
    Write-Host "[采集] $RelativePath"
    if ($script:ConnectionLost) {
        Save-Text $RelativePath "采集已停止：ADB 连接中断。"
        return [pscustomobject]@{ ExitCode = -1; Lines = @() }
    }
    try {
        # 远端命令整段传给 adb，管道和重定向由车机 shell 处理。
        $Result = Invoke-Adb -Arguments @("shell", $Command)
        Save-Text $RelativePath $Result.Lines
        if ($Result.ExitCode -ne 0 -and ($Result.Lines -join "`n") -match 'device offline|device .* not found|no devices/emulators found|device disconnected') {
            $script:ConnectionLost = $true
            Write-Host "ADB 连接已中断，停止后续采集。恢复连接后可通过 -StartPhase 从当前阶段重新采集。"
        }
        if ($Result.ExitCode -ne 0) {
            Add-Content -LiteralPath (Join-Path $script:OutputDir $RelativePath) -Encoding utf8 -Value "ADB_EXIT_CODE=$($Result.ExitCode)"
        }
        return $Result
    } catch {
        Save-Text $RelativePath "ERROR: $($_.Exception.Message)"
        return [pscustomobject]@{ ExitCode = -1; Lines = @() }
    }
}

function Save-RemoteFile {
    param ([string]$RemotePath, [string]$RelativePath)
    $Destination = Join-Path $script:OutputDir $RelativePath
    New-Item -ItemType Directory -Force -Path (Split-Path $Destination -Parent) | Out-Null
    $Result = Invoke-Adb -Arguments @("pull", $RemotePath, $Destination)
    Save-Text ($RelativePath + ".pull.txt") $Result.Lines
    if ($Result.ExitCode -eq 0 -and (Test-Path -LiteralPath $Destination -PathType Leaf)) {
        $Item = Get-Item -LiteralPath $Destination
        $script:PulledFiles.Add([pscustomobject]@{
            RemotePath = $RemotePath
            LocalPath = $RelativePath
            Bytes = $Item.Length
            SHA256 = (Get-FileHash -LiteralPath $Destination -Algorithm SHA256).Hash
        })
        return $true
    }
    Add-Content -LiteralPath (Join-Path $script:OutputDir ($RelativePath + ".pull.txt")) -Encoding utf8 -Value "ADB_EXIT_CODE=$($Result.ExitCode)"
    return $false
}

function Save-Screen {
    param ([string]$Phase)
    $Remote = "/sdcard/LynkPlay-cluster-$Timestamp-$Phase.png"
    $Result = Save-Shell "$Phase/screen-command.txt" "screencap -p $Remote"
    if ($Result.ExitCode -ne 0) { return }
    if (-not (Save-RemoteFile $Remote "$Phase/screen.png")) { return }
    $Path = Join-Path $script:OutputDir "$Phase/screen.png"
    $Stream = [System.IO.File]::OpenRead($Path)
    try {
        $Header = New-Object byte[] 8
        $Count = $Stream.Read($Header, 0, 8)
        $Magic = [BitConverter]::ToString($Header)
    } finally { $Stream.Dispose() }
    if ($Count -eq 8 -and $Magic -eq "89-50-4E-47-0D-0A-1A-0A") {
        $null = Save-Shell "$Phase/screen-cleanup.txt" "rm $Remote"
    } else {
        Save-Text "$Phase/screen-error.txt" "ERROR: 截图未通过 PNG 文件头检查。"
    }
}

Write-Host "正在采集车机标识、系统组件与接口文件……"
Save-Text "00-devices.txt" $Devices.Lines
Save-Text "00-selected-device.txt" $Serial
$null = Save-Shell "01-system-properties.txt" "getprop"
$null = Save-Shell "02-system-services.txt" "service list"
$null = Save-Shell "03-shared-libraries.txt" "dumpsys package libraries"
$null = Save-Shell "04-installed-packages.txt" "pm list packages -f"
$null = Save-Shell "05-lynkplay-package.txt" "dumpsys package com.leth0x01.lynkplay"
$null = Save-Shell "06-gesture-access.txt" "settings get secure enabled_accessibility_services"

$RootCheck = Save-Shell "08-root.txt" "su -c id -u"
$script:RootAvailable = $RootCheck.ExitCode -eq 0 -and @($RootCheck.Lines | Where-Object { $_.Trim() -eq "0" }).Count -gt 0
$InputCommand = if ($script:RootAvailable) { "su -c 'getevent -lp'" } else { "getevent -lp" }
$null = Save-Shell "09-input-devices.txt" $InputCommand
$null = Save-Shell "10-audio-policy.txt" "dumpsys audio"
$null = Save-Shell "11-media-sessions.txt" "dumpsys media_session"
$null = Save-Shell "12-interactive-service.txt" "dumpsys -t 5 ecarx_interactive"

$Packages = @(
    "com.ecarx.eas.carservice", "ecarx.naviservice", "ecarx.xsf.gestureservice", "com.ecarx.naviwidget",
    "android.car.input.service", "ecarx.xsf.inputservice", "com.ecarx.multimedia", "ecarx.xsf.mediacenter",
    "com.tencent.wecarspeech", "com.ecarx.btphone", "com.ecarx.hmi", "com.ecarx.systemui.plugin",
    "ecarx.power.service", "com.neusoft.extraservice", "com.ecarx.ecarxinteractiveservice"
)
if (-not $SkipComponents) {
foreach ($Package in $Packages) {
    $null = Save-Shell "components/$Package/package.txt" "dumpsys package $Package"
    $Paths = Save-Shell "components/$Package/apk-paths.txt" "pm path $Package"
    foreach ($Line in $Paths.Lines) {
        if ($Line -notmatch '^package:(/[^\r\n]+)$') { continue }
        $Remote = $Matches[1].Trim()
        $null = Save-RemoteFile $Remote "components/$Package/$(Split-Path $Remote -Leaf)"
        # 部分系统 APK 将代码预编译到 oat 目录，一并保留供接口核对。
        if ($Remote -match '^/[A-Za-z0-9_./+@=-]+$') {
            $Parent = $Remote.Substring(0, $Remote.LastIndexOf('/'))
            $OatFiles = Save-Shell "components/$Package/oat-files.txt" "find $Parent/oat -type f 2>/dev/null"
            foreach ($Oat in $OatFiles.Lines) {
                if ($Oat -match '^/[^\r\n]+\.(odex|vdex)$') {
                    $null = Save-RemoteFile $Oat "components/$Package/oat/$(($Oat -replace '/', '_').TrimStart('_'))"
                }
            }
        }
    }
}
$Frameworks = Save-Shell "07-framework-files.txt" "find /system/framework /vendor/framework /product/framework /system_ext/framework -maxdepth 3 -type f 2>/dev/null"
foreach ($Remote in $Frameworks.Lines) {
    if ($Remote -match '^/[^\r\n]+\.(jar|odex|vdex)$' -and $Remote -match '(?i)(ecarx|adapt|geely|eas)') {
        $null = Save-RemoteFile $Remote "framework/$(($Remote -replace '/', '_').TrimStart('_'))"
    }
}

}

Write-Host ""
Write-Host "下面按提示操作原车导航和方向盘，每一步完成后回车；可同时输入现象作为备注。"
Write-Host "某个按键未配备时，在该步骤输入「无此按键」并回车。"
Write-Host "先关闭 LynkPlay 的仪表地图开关，再进行原车操作。"
$Phases = @(
    @{ Name = "01-idle"; Instruction = "打开原车地图，保持未导航" },
    @{ Name = "02-navigation-simple"; Instruction = "在原车地图开始导航，记录仪表简洁箭头提示" },
    @{ Name = "03-three-finger-left"; Instruction = "在原车地图三指向左划，记录仪表切换到地图的结果" },
    @{ Name = "04-menu-simple"; Instruction = "通过仪表菜单切回简洁模式，保持导航继续" },
    @{ Name = "05-menu-map"; Instruction = "通过仪表菜单切换到全景（地图）模式，保持导航继续" },
    @{ Name = "06-navigation-stop"; Instruction = "在原车地图退出导航，记录仪表导航显示结束后的状态" },
    @{ Name = "07-media-ready"; Instruction = "打开原车音乐应用并播放一首歌，记录方向盘按钮布局照片" },
    @{ Name = "08-wheel-next"; Instruction = "短按方向盘下一曲键一次" },
    @{ Name = "09-wheel-previous"; Instruction = "短按方向盘上一曲键一次" },
    @{ Name = "10-wheel-pause"; Instruction = "使用方向盘播放/暂停键暂停音乐" },
    @{ Name = "11-wheel-play"; Instruction = "使用同一按键恢复音乐播放" },
    @{ Name = "12-wheel-volume-up"; Instruction = "短按方向盘音量加键一次" },
    @{ Name = "13-wheel-volume-down"; Instruction = "短按方向盘音量减键一次" },
    @{ Name = "14-wheel-voice-short"; Instruction = "短按方向盘语音键一次，记录唤醒的界面和音乐变化" },
    @{ Name = "15-wheel-voice-long"; Instruction = "关闭刚才的语音界面，再长按语音键约 2 秒，记录界面和音乐变化" },
    @{ Name = "16-carplay-ready"; Instruction = "关闭原车语音界面，打开 LynkPlay 连接 iPhone 并播放音乐，保持仪表地图开关关闭；连接失败时输入「无法连接」" },
    @{ Name = "17-carplay-next"; Instruction = "保持 CarPlay 在前台，短按方向盘下一曲键一次，记录响应" },
    @{ Name = "18-carplay-previous"; Instruction = "保持 CarPlay 在前台，短按方向盘上一曲键一次，记录响应" },
    @{ Name = "19-carplay-pause"; Instruction = "使用方向盘播放/暂停键暂停 CarPlay 音乐，记录响应" },
    @{ Name = "20-carplay-play"; Instruction = "使用同一按键恢复 CarPlay 音乐播放，记录响应" },
    @{ Name = "21-carplay-voice-short"; Instruction = "短按方向盘语音键一次，记录唤醒的是 Siri 还是原车语音，以及音乐变化" },
    @{ Name = "22-carplay-voice-long"; Instruction = "关闭刚才的语音界面，再长按语音键约 2 秒，记录唤醒界面和音乐变化" }
)
$SkipCarPlay = $false
foreach ($Phase in $Phases) {
    $Name = $Phase.Name
    $PhaseNumber = [int]$Name.Substring(0, 2)
    if ($PhaseNumber -lt $StartPhase) { continue }
    $Connection = Invoke-Adb -Arguments @("get-state")
    if ($Connection.ExitCode -ne 0 -or "device" -notin $Connection.Lines) {
        Save-Text "connection-error.txt" @("中断阶段=$Name", $Connection.Lines)
        Write-Host "ADB 未连接，停止在第 $PhaseNumber 阶段。恢复连接后使用 -StartPhase $PhaseNumber 重新采集。"
        break
    }
    if ($SkipCarPlay -and $Name -match '^((1[7-9])|(2[0-2]))-carplay') { continue }
    $Begin = Invoke-Adb -Arguments @("shell", "log -p i -t LynkPlayClusterProbe ${Name}_BEFORE")
    # 输入设备事件与厂商服务日志同时采集；CAN 按键可能只出现在厂商日志。
    $EventProcess = $null
    $EventFolder = Join-Path $script:OutputDir $Name
    New-Item -ItemType Directory -Force -Path $EventFolder | Out-Null
    $EventCommand = if ($script:RootAvailable) { "su -c 'timeout 120 getevent -lt'" } else { "timeout 120 getevent -lt" }
    try {
        # Start-Process 会拼接参数，显式引用远端命令以保留 su 的单引号。
        $EventArgument = '"' + $EventCommand + '"'
        $EventProcess = Start-Process -FilePath $script:ADB -ArgumentList @("-s", $Serial, "shell", $EventArgument) `
            -NoNewWindow -PassThru -RedirectStandardOutput (Join-Path $EventFolder "input-events.txt") `
            -RedirectStandardError (Join-Path $EventFolder "input-events-error.txt") -ErrorAction Stop
    } catch {
        Save-Text "$Name/input-events-error.txt" "ERROR: $($_.Exception.Message)"
    }
    $Note = Read-Host "$($Phase.Instruction)。操作完成后回车（可输入备注）"
    if ($EventProcess) {
        if (-not $EventProcess.HasExited) { Stop-Process -InputObject $EventProcess -Force -ErrorAction SilentlyContinue }
        $EventProcess.Dispose()
    }
    Save-Text "$Name/operation.txt" @($Phase.Instruction, $Note, "MarkerExitCode=$($Begin.ExitCode)")
    if ($Name -eq "16-carplay-ready" -and $Note -match '无法连接|连不上|跳过|skip') { $SkipCarPlay = $true }
    $End = Invoke-Adb -Arguments @("shell", "log -p i -t LynkPlayClusterProbe ${Name}_AFTER")
    if ($Begin.ExitCode -ne 0 -or $End.ExitCode -ne 0) {
        Save-Text "$Name/connection-error.txt" @("当前阶段日志标记不完整，需要重采。", $Begin.Lines, $End.Lines)
        Write-Host "ADB 连接或日志标记失败，停止在第 $PhaseNumber 阶段。恢复连接后使用 -StartPhase $PhaseNumber 重新采集。"
        break
    }
    $Log = Save-Shell "$Name/logcat.txt" "logcat -b all -d -v threadtime"
    Save-Text "$Name/logcat-filtered.txt" @($Log.Lines | Where-Object {
        $_ -match '(?i)(LynkPlayClusterProbe|DimInteraction|NaviInteraction|NaviStatus|NaviMode|Presentation|GestureService|XCNaviService|AdaptBinder|ecarx_interactive|flyscreen|KeyInput|CarInput|onHk|KeyPressed|MediaSession|AudioFocus|Voice|Speech|ConnectService|InputService)'
    })
    $null = Save-Shell "$Name/display.txt" "dumpsys display"
    $null = Save-Shell "$Name/window.txt" "dumpsys window windows"
    $null = Save-Shell "$Name/activities.txt" "dumpsys activity activities"
    $null = Save-Shell "$Name/navigation-services.txt" "dumpsys activity services ecarx.naviservice"
    $null = Save-Shell "$Name/gesture-services.txt" "dumpsys activity services ecarx.xsf.gestureservice"
    $null = Save-Shell "$Name/adapt-services.txt" "dumpsys activity services com.ecarx.eas.carservice"
    $null = Save-Shell "$Name/input-state.txt" "dumpsys input"
    $null = Save-Shell "$Name/input-services.txt" "dumpsys activity services ecarx.xsf.inputservice"
    $null = Save-Shell "$Name/car-input-services.txt" "dumpsys activity services android.car.input.service"
    $null = Save-Shell "$Name/media-sessions.txt" "dumpsys media_session"
    $null = Save-Shell "$Name/audio-state.txt" "dumpsys audio"
    $null = Save-Shell "$Name/voice-services.txt" "dumpsys activity services com.tencent.wecarspeech"
    if ($script:RootAvailable -and $Name -match '^((1[6-9])|(2[0-2]))-carplay') {
        $null = Save-Shell "$Name/lynkplay-session.txt" "su -c 'cat /data/user/0/com.leth0x01.lynkplay/files/logs/diplay.log'"
    }
    Save-Screen $Name
    if ($script:ConnectionLost) {
        Save-Text "$Name/connection-error.txt" "当前阶段未采集完整，请使用 -StartPhase $PhaseNumber 重采。"
        break
    }
}

$script:PulledFiles | Export-Csv -LiteralPath (Join-Path $script:OutputDir "pulled-files.csv") -NoTypeInformation -Encoding UTF8
Save-Text "README.txt" @(
    "领克 06 仪表、方向盘与媒体语音专项采集",
    "每个场景包含操作备注、前后日志标记、完整日志、显示与窗口状态和中控截图。",
    "采集区分未导航、导航简洁模式、三指左划地图、菜单简洁模式、菜单地图模式及导航结束。",
    "方向盘场景记录原车音乐和 CarPlay 两组切歌、暂停、播放及语音键短按/长按，并记录原车音量键。",
    "其他电话或自定义按键请通过方向盘照片和备注说明原有功能。",
    "input-events.txt 最长记录 120 秒；CAN 按键需结合厂商服务日志判断。",
    "components 与 framework 包含接口核对文件；pulled-files.csv 记录来源路径与 SHA256。",
    "开始阶段=$StartPhase；跳过组件采集=$SkipComponents。",
    "connection-error.txt 表示采集中断，需从对应阶段重新采集。",
    "仪表画面请另用手机拍摄，screen.png 是车机中控截图。"
)
$Zip = "$($script:OutputDir).zip"
try {
    Compress-Archive -Path (Join-Path $script:OutputDir "*") -DestinationPath $Zip -Force -ErrorAction Stop
    Write-Host "采集完成：$Zip"
} catch {
    Write-Host "压缩失败：$($_.Exception.Message)"
    Write-Host "采集文件仍保存在：$($script:OutputDir)"
}
