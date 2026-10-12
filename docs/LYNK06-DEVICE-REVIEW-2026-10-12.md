# 领克 06 实车采集复核（2026-10-12）

来源：`lynk06_20261012_121852`。以下结论来自用户采集的实际输出，屏幕物理尺寸 12.3 英寸沿用用户提供的信息。

## 设备与安装包

| 项目 | 采集结果 | 依据 |
| --- | --- | --- |
| 平台 | `ECARX_E02_V1.3` | `01-system-properties.txt` |
| 设备型号 | `IHU514G`；hardware 为 `xecarx` | 系统属性、设备及 board 输出 |
| 系统 | Android 9 / API 28 | `05-3-android-version.txt`、`05-4-sdk-version.txt` |
| 固件 | `SW0BX110520H8EHH.00762` | `ro.build.version.versionname` |
| CPU / ABI | 8 核 ARM；支持 `arm64-v8a` | `/proc/cpuinfo`、系统属性 |
| GPU | ARM Mali-G72 MP3 / OpenGL ES 3.2 | `11-3-surfaceflinger.txt` |
| 内存 | 系统可见 5,940,192 KiB，约 5.67 GiB，与 6 GB 配置相符；采集时可用约 1.85 GiB | `03-memory.txt` |
| 存储 | eMMC 主设备 62,496,768 KiB，约 64.0 GB；`/data` 约 41 GiB、可用约 39 GiB | `05-storage-partitions.txt`、`04-storage.txt` |
| 应用 | `com.leth0x01.lynkplay`，`0.1.3-lynk06-test` / versionCode 41 | `12-installed-app.txt` |
| APK SHA256 | `c6d6366575a71f16e58def1c65bf9a596c4ce1f792e1d1f37d761d02734739c1`，与生成的 0.1.3 车测包一致 | `14-apk-sha256.txt` |

## 显示与窗口

- 主屏原生分辨率为 1920 × 720，8:3 横屏，旋转值为 0；刷新率为 59.98 Hz。
- 主屏逻辑密度为 **160 dpi**，`wm` 查询未出现覆盖值。此前模拟布局使用的 320 dpi 不是实车密度。
- `settings get system font_scale` 返回 `null`；Activity 配置实际记录字体缩放 **1.0**，不能把 `null` 当作零。
- 普通应用配置为 `w1800dp h670dp`，系统安全区域为 `[120,50][1920,720]`，对应左侧 120 px 和顶部 50 px 的系统占用。
- CarPlay Activity 保留的 View 层次中，根视图及 TextureView 均为 1920 × 720。三指菜单面板为 `[600,16][1320,704]`，即 720 × 688；固定标题栏 84 px、滚动区域 520 px、固定操作栏 84 px。
- 系统另有 `DashboardMapDisplay`，为 1920 × 720、213 dpi、30 Hz；其参数应与主屏区分。

上述窗口依据 `09-display-details.txt`、`10-app-window.txt` 和 `11-1-window.txt`。采集时前台是 `ecarx.launcher3`；CarPlay Activity 为 `mResumed=false`、`mStopped=true`，窗口 `mHasSurface=false`。保留的 View 尺寸不能替代 App 前台显示和键盘弹出时的复核。

## USB 与性能数据

- ADB shell 的 `su -c id` 返回 `uid=0`，SELinux 上下文为 Magisk。
- LynkPlay 的 `ACTIVATE_VPN` 当前为 `allow`。
- 系统查询确认 `com.android.vpndialogs` 包缺失，与此前无法打开 VPN 授权界面的提示相符。
- USB host 状态为已连接，枚举到 Apple iPhone；日志出现有线 iAP2、NCM 邻居通告和持续的视频统计，证明此次有线连接已经进入视频传输与解码阶段。
- 视频统计中接收帧率与显示计数接近，解码 p90 多次为约 11 ms，记录的 `late=0`、`recoveries=0`。这些是有线后台会话数据，不能用来确定无线卡顿原因，也不能据低帧率直接认定丢帧。
- 完整复核实际视频握手分辨率、解码器和 App 内 root 授权路径，需要应用会话日志或诊断报告。

## 采集异常与修正

原脚本使用 `Invoke-Expression`，导致带管道的 `grep` 在 Windows 本机执行，10 项筛选输出失败。对应完整系统输出仍在原目录，已在 `derived/` 中本地筛选恢复这些结果，未覆盖原始错误文件。

`50-screen.png` 使用 PowerShell 文本重定向，实际以 UTF-16 文件头开头，不是有效 PNG。修正版脚本改为车机生成截图后通过 `adb pull` 传输，并检查 PNG 文件头。

修正版还设置 UTF-8 原生输出、记录失败退出码，并增加视频 codec、LynkPlay 会话日志和应用内存采集。支持 `baseline`、`wireless`、`usb`、`menu` 场景标记；采集期间需保持相应 App 画面在前台。

项目规定的完整 Gradle 检查通过。用户随后执行了修正版脚本，结果见下节。


## 第二轮前台复核

来源：`lynk06_20261012_131000_wireless`、`lynk06_20261012_131105_menu`。用户要求暂缓卡顿排查，本轮仅复核采集、安装包、显示和菜单。

- 两份截图均为有效的 1920 × 720 PNG，修正版截图采集已在用户环境执行成功；筛选输出未再出现上一轮的本机 `grep` 错误。
- 主屏仍为 1920 × 720、160 dpi；已安装 APK 的 SHA256 与 0.1.3 车测包一致。
- 采集时 CarPlay Activity 位于前台；无线截图显示完整 CarPlay 画面。
- 会话日志确认实际协商画布为 1920 × 720、100% 分辨率、H.264、30 fps，实际解码器为 `OMX.MTK.VIDEO.DECODER.AVC`。
- 三指菜单截图确认应用主题已生效，面板居中，宽 720 px；标题、滚动主体及底部操作栏均可见，截图内未见控件重叠。该面板宽度来自菜单布局限制，CarPlay 视频画布仍为 1920 × 720。
- `dumpsys media.codec` 返回 `Can't find service: media.codec`；实际视频参数和解码器以上述会话日志为依据。

本轮未采集新的 USB 前台会话；USB 授权及连接阶段的结论沿用第一轮证据。


## 根据采集结果生成的 0.1.4 车测包

菜单最大宽度由 720 dp 调整为宽屏模式下的 1120 dp，弹窗最大宽度由 640 dp 调整为 960 dp；宽屏控件最低高度 72 dp，正文同步放大。窄窗和低高度窗口保留紧凑模式，视频画布仍为原有的 1920 × 720。

自动匹配使用已采集的完整 IHU514G / xecarx / API 28 组合，统一为领克 06 燃油版配置。运行时匹配、部分标识误匹配、界面大字体及低高度弹窗回归通过，完整 CI 检查通过。0.1.4 安装包版本、签名和认证素材检查通过；新版本的实车显示、键盘与 USB 前台流程待覆盖安装后验证。


## 0.1.5 仪表地图接入

针对已采集的 DashboardMapDisplay 接入独立 CarPlay 仪表地图。实际设备标识匹配后，选择有效、具有 Presentation 标记的 1920 × 720 副屏；默认地图流为 1920 × 720、30 fps、100% 比例，车标居中。设置入口沿用高级分类的仪表地图开关。

9 项新增回归测试、项目完整 CI 和车测包校验通过。实际仪表显示区域、原车地图模式及关闭后的恢复情况待车机验证。
