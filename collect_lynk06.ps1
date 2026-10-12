# collect_lynk06.ps1
# 23款领克06车机信息采集脚本
# 请将本文件放到 adb.exe 同目录执行

param (
    [ValidateSet("baseline", "wireless", "usb", "menu")]
    [string]$Scene = "baseline"
)

$ErrorActionPreference = "Continue"
# 原生 adb 输出采用 UTF-8，避免中文被系统代码页误解码。
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

$ADB = Join-Path $PSScriptRoot "adb.exe"
if (-not (Test-Path -LiteralPath $ADB)) {
    throw "找不到 adb.exe，请将脚本放在 adb.exe 同目录。"
}
$Timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$OutputDir = Join-Path $PSScriptRoot "lynk06_${Timestamp}_${Scene}"
$ZipFile = "$OutputDir.zip"

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

function Run-Adb {
    param (
        [string]$FileName,
        [string]$Command
    )

    Write-Host "[采集] $FileName"

    try {
        if ($Command.StartsWith("shell ")) {
            # 整段远端命令作为一个参数传给 adb；管道和 grep 在车机执行。
            $ShellCommand = $Command.Substring(6)
            $Result = & $ADB shell $ShellCommand 2>&1
        } else {
            $AdbArguments = $Command -split "\s+"
            $Result = & $ADB @AdbArguments 2>&1
        }
        $ExitCode = $LASTEXITCODE
        $Result | Out-File -FilePath "$OutputDir\$FileName" -Encoding utf8
        if ($ExitCode -ne 0) {
            "ADB_EXIT_CODE=$ExitCode" | Add-Content -Path "$OutputDir\$FileName" -Encoding utf8
        }
    }
    catch {
        "ERROR: $($_.Exception.Message)" |
            Out-File -FilePath "$OutputDir\$FileName" -Encoding utf8
    }
}

Write-Host ""
Write-Host "======================================="
Write-Host "  Lynk & Co 06 车机信息采集 [$Scene]"
Write-Host "======================================="
Write-Host ""

Write-Host "采集期间请保持 LynkPlay 在前台。"
Write-Host "wireless：无线连接并操作地图；usb：USB 连接或错误现场；menu：三指菜单。"

# --------------------------------------------------
# ADB连接
# --------------------------------------------------

Run-Adb "00-adb-devices.txt" "devices -l"

# --------------------------------------------------
# 系统信息
# --------------------------------------------------

Run-Adb "01-system-properties.txt" "shell getprop"

Run-Adb "02-cpu.txt" "shell cat /proc/cpuinfo"

Run-Adb "03-memory.txt" "shell cat /proc/meminfo"

Run-Adb "04-storage.txt" "shell df -h"

Run-Adb "05-storage-partitions.txt" "shell cat /proc/partitions"

Run-Adb "05-1-mount.txt" "shell mount"

Run-Adb "05-2-kernel.txt" "shell uname -a"

Run-Adb "05-3-android-version.txt" `
    "shell getprop ro.build.version.release"

Run-Adb "05-4-sdk-version.txt" `
    "shell getprop ro.build.version.sdk"

Run-Adb "05-5-build-fingerprint.txt" `
    "shell getprop ro.build.fingerprint"

Run-Adb "05-6-product.txt" `
    "shell getprop ro.product.name"

Run-Adb "05-7-device.txt" `
    "shell getprop ro.product.device"

Run-Adb "05-8-hardware.txt" `
    "shell getprop ro.hardware"

Run-Adb "05-9-board.txt" `
    "shell getprop ro.product.board"


# --------------------------------------------------
# 屏幕
# --------------------------------------------------

Run-Adb "06-screen-size.txt" `
    "shell wm size"

Run-Adb "07-screen-density.txt" `
    "shell wm density"

Run-Adb "08-font-scale.txt" `
    "shell settings get system font_scale"

Run-Adb "09-display-details.txt" `
    "shell dumpsys display"

Run-Adb "10-app-window.txt" `
    "shell dumpsys activity top"

Run-Adb "11-touch-input.txt" `
    "shell dumpsys input"

Run-Adb "11-1-window.txt" `
    "shell dumpsys window"

Run-Adb "11-2-window-displays.txt" `
    "shell dumpsys window displays"

Run-Adb "11-3-surfaceflinger.txt" `
    "shell dumpsys SurfaceFlinger"

Run-Adb "11-4-surfaceflinger-list.txt" `
    "shell dumpsys SurfaceFlinger --list"

Run-Adb "11-5-input-devices.txt" `
    "shell cat /proc/bus/input/devices"


# --------------------------------------------------
# GPU / 图形
# --------------------------------------------------

Run-Adb "11-6-gpu-properties.txt" `
    "shell getprop | grep -iE 'gpu|egl|gles|vulkan|adreno'"

Run-Adb "11-7-egl.txt" `
    "shell dumpsys SurfaceFlinger | grep -iE 'GLES|EGL|OpenGL|GPU'"

Run-Adb "11-8-hardware-composer.txt" `
    "shell dumpsys SurfaceFlinger | grep -iE 'HWC|Composer|Display'"


# --------------------------------------------------
# LynkPlay App
# --------------------------------------------------

Run-Adb "12-installed-app.txt" `
    "shell dumpsys package com.leth0x01.lynkplay"

Run-Adb "13-apk-path.txt" `
    "shell pm path com.leth0x01.lynkplay"

Write-Host "[采集] 14-apk-sha256.txt"

try {
    $ApkPath = & $ADB shell pm path com.leth0x01.lynkplay

    $ApkPath = $ApkPath |
        Select-String "^package:" |
        Select-Object -First 1

    if ($ApkPath) {

        $ApkPath = $ApkPath.ToString().Replace("package:", "").Trim()

        & $ADB shell sha256sum $ApkPath |
            Out-File "$OutputDir\14-apk-sha256.txt" -Encoding utf8

    } else {

        "APK NOT FOUND" |
            Out-File "$OutputDir\14-apk-sha256.txt" -Encoding utf8
    }
}
catch {

    "ERROR: $($_.Exception.Message)" |
        Out-File "$OutputDir\14-apk-sha256.txt" -Encoding utf8
}


# --------------------------------------------------
# Root / VPN
# --------------------------------------------------

Run-Adb "15-root.txt" `
    "shell su -c id"

Run-Adb "16-vpn-permission.txt" `
    "shell appops get com.leth0x01.lynkplay ACTIVATE_VPN"

Run-Adb "17-vpn-dialog.txt" `
    "shell dumpsys package com.android.vpndialogs"


# --------------------------------------------------
# 系统服务 / 软件包
# --------------------------------------------------

Run-Adb "18-system-services.txt" `
    "shell service list"

Run-Adb "19-installed-packages.txt" `
    "shell pm list packages"

Run-Adb "19-1-installed-packages-path.txt" `
    "shell pm list packages -f"

Run-Adb "19-2-system-packages.txt" `
    "shell pm list packages -s"

Run-Adb "19-3-thirdparty-packages.txt" `
    "shell pm list packages -3"


# --------------------------------------------------
# 网络
# --------------------------------------------------

Run-Adb "20-network-interfaces.txt" `
    "shell ip addr"

Run-Adb "21-network-route.txt" `
    "shell ip route"

Run-Adb "22-network-links.txt" `
    "shell ip link"

Run-Adb "23-wifi.txt" `
    "shell dumpsys wifi"

Run-Adb "24-connectivity.txt" `
    "shell dumpsys connectivity"

Run-Adb "25-netd.txt" `
    "shell dumpsys netd"


# --------------------------------------------------
# USB
# --------------------------------------------------

Run-Adb "26-usb.txt" `
    "shell dumpsys usb"

Run-Adb "27-usb-properties.txt" `
    "shell getprop | grep -i usb"

Run-Adb "28-usb-devices.txt" `
    "shell cat /sys/kernel/debug/usb/devices"


# --------------------------------------------------
# 蓝牙
# --------------------------------------------------

Run-Adb "29-bluetooth.txt" `
    "shell dumpsys bluetooth_manager"

Run-Adb "30-bluetooth-properties.txt" `
    "shell getprop | grep -i bluetooth"


# --------------------------------------------------
# 音频
# --------------------------------------------------

Run-Adb "31-audio.txt" `
    "shell dumpsys audio"

Run-Adb "32-media-audio-flinger.txt" `
    "shell dumpsys media.audio_flinger"

Run-Adb "33-media-audio-policy.txt" `
    "shell dumpsys media.audio_policy"


# --------------------------------------------------
# Camera
# --------------------------------------------------

Run-Adb "34-camera.txt" `
    "shell dumpsys media.camera"


# --------------------------------------------------
# Sensor
# --------------------------------------------------

Run-Adb "35-sensors.txt" `
    "shell dumpsys sensorservice"


# --------------------------------------------------
# 电源
# --------------------------------------------------

Run-Adb "36-power.txt" `
    "shell dumpsys power"

Run-Adb "37-battery.txt" `
    "shell dumpsys battery"


# --------------------------------------------------
# 车机 / OEM 服务查找
# --------------------------------------------------

Run-Adb "38-lynk-services.txt" `
    "shell service list | grep -iE 'lynk|ecarx|vehicle|car|geely|can|ivi|mcu'"

Run-Adb "39-lynk-packages.txt" `
    "shell pm list packages | grep -iE 'lynk|ecarx|vehicle|car|geely|mcu|ivi'"

Run-Adb "40-vehicle-properties.txt" `
    "shell getprop | grep -iE 'lynk|ecarx|vehicle|car|geely|mcu|ivi'"

Run-Adb "41-processes.txt" `
    "shell ps -A"

Run-Adb "42-vehicle-processes.txt" `
    "shell ps -A | grep -iE 'lynk|ecarx|vehicle|car|geely|mcu|ivi'"

Run-Adb "43-binder-services.txt" `
    "shell dumpsys -l"


# --------------------------------------------------
# CAN / 网络设备线索
# --------------------------------------------------

Run-Adb "44-network-can.txt" `
    "shell ip -details link"

Run-Adb "45-proc-net-dev.txt" `
    "shell cat /proc/net/dev"

Run-Adb "46-dev-nodes.txt" `
    "shell ls -la /dev"

Run-Adb "47-dev-can-mcu.txt" `
    "shell ls -la /dev | grep -iE 'can|mcu|ipc|spi|uart|tty'"


# --------------------------------------------------
# MCU / Serial
# --------------------------------------------------

Run-Adb "48-tty.txt" `
    "shell ls -la /dev/tty*"

Run-Adb "49-proc-tty.txt" `
    "shell cat /proc/tty/drivers"


# --------------------------------------------------
# 截图
# --------------------------------------------------

Write-Host "[采集] 50-screen.png"

try {
    # 先在车机生成文件，再由 adb pull 传输，避免 PowerShell 文本重定向损坏 PNG。
    $RemoteScreenshot = "/sdcard/LynkPlay-collect-$Timestamp.png"
    & $ADB shell screencap -p $RemoteScreenshot
    if ($LASTEXITCODE -ne 0) { throw "车机截图命令失败。" }
    & $ADB pull $RemoteScreenshot "$OutputDir\50-screen.png"
    if ($LASTEXITCODE -ne 0) { throw "截图传输失败。" }
    $ScreenshotBytes = [System.IO.File]::ReadAllBytes("$OutputDir\50-screen.png")
    if ($ScreenshotBytes.Length -lt 8 -or
        [System.BitConverter]::ToString($ScreenshotBytes[0..7]) -ne "89-50-4E-47-0D-0A-1A-0A") {
        throw "截图 PNG 文件头校验失败。"
    }
    & $ADB shell rm $RemoteScreenshot
}
catch {
    "ERROR: $($_.Exception.Message)" | Out-File "$OutputDir\50-screen-error.txt" -Encoding utf8
    Write-Host "截图失败：$($_.Exception.Message)"
}


# --------------------------------------------------
# Logcat
# --------------------------------------------------

Run-Adb "51-logcat.txt" `
    "shell logcat -d -v threadtime"

Run-Adb "52-kernel-log.txt" `
    "shell dmesg"


# 应用会话日志包含 VPN 授权结果、连接握手和实际视频参数。
Run-Adb "53-media-codec.txt" "shell dumpsys media.codec"
Run-Adb "54-lynkplay-session-log.txt" "shell su -c 'cat /data/user/0/com.leth0x01.lynkplay/files/logs/diplay.log'"
Run-Adb "55-lynkplay-memory.txt" "shell dumpsys meminfo com.leth0x01.lynkplay"

# --------------------------------------------------
# 打包
# --------------------------------------------------

Write-Host ""
Write-Host "[打包] $ZipFile"

Compress-Archive `
    -Path "$OutputDir\*" `
    -DestinationPath $ZipFile `
    -Force


Write-Host ""
Write-Host "======================================="
Write-Host "采集完成"
Write-Host ""
Write-Host "目录:"
Write-Host "  $OutputDir"
Write-Host ""
Write-Host "压缩包:"
Write-Host "  $ZipFile"
Write-Host "======================================="