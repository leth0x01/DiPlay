# DiPlay 参考项目核对与移植记录

## 核对范围

参考仓库：[749001858/DiPlay](https://github.com/749001858/DiPlay)。核对时固定以下提交与发布包：

| 材料 | 标识 |
| --- | --- |
| main | `4ed5514daf5c205175621fd03884f0de68375b13` |
| android-4.3 | `4bd0732773655d9b2a70b3e4f9f483b80d7c099c` |
| v0.23.10 APK | SHA256 `cef4497fc5466f73b0100f1f79d9dbfcef423ad69821987bcb0f7e557f557005` |
| LynkPlay 比较基线 | `73c44050` |

main 的应用源码基线为 `18429e7`（DiPlay 0.2.9），该提交已是 LynkPlay 比较基线的祖先。其后的独有差异共 3 个文件：README、CS11 发布说明、发布工作流。android-4.3 在 `f2d0695`（同样已是 LynkPlay 祖先）之后新增独立 adaptation 接收器和探针。按这些差异核对了独有源码、测试、构建脚本和发布资料，避免重复移植上游代码。

公开发布附件提供 APK 与校验文件。APK 已核对 SHA256，并用 Android SDK dexdump 做静态检查；原始 APK 与派生 dump 保存在本机临时目录。CS11 最终源码与发布包的对应限制见[作者发布说明](https://github.com/749001858/DiPlay/blob/4ed5514daf5c205175621fd03884f0de68375b13/docs/RELEASE-CS11-v0.23.10.md#源码状态)。

## 已移植

### NMEA 缺失测量值与高度基准

来源：[LegacyLocationEncoder.kt](https://github.com/749001858/DiPlay/blob/4bd0732773655d9b2a70b3e4f9f483b80d7c099c/adaptation/receiver/src/local/airuize/receiver/LegacyLocationEncoder.kt) 和旧接收器 GPS provider。

在现有 `NmeaLocationEncoder` 内直接实现缺失值处理，复用坐标、时间和校验和编码：

- GGA 的未知卫星数、HDOP、大地水准面差留空。米制水平定位精度不再换算成 HDOP。
- 未知高度及其单位留空；有效 MSL 高度保留，包括零值和负值。
- 未知、非有限或负速度留空，真实零速仍输出 `0.00`。
- 航向只接受有限的 `[0, 360)` 值，正北 `0` 保留。
- Android API 34 及以上使用明确提供的 MSL 高度；旧系统不把 `Location.altitude` 的 WGS84 椭球高直接填为 GGA 海拔。

字段含义依据：[Android Location](https://developer.android.com/reference/android/location/Location#getAltitude())、[Trimble GGA](https://receiverhelp.trimble.com/oem-gnss/nmea0183-messages-gga.html)、[NMEA 空字段规则](https://receiverhelp.trimble.com/alloy-gnss/en-us/nmea0183-messages-overview.html)。这些定义不能代替 iPhone 对实际输出的车测。

### AirPlay 模式与资源状态诊断

来源：[AirPlayDiagnostics.kt](https://github.com/749001858/DiPlay/blob/4bd0732773655d9b2a70b3e4f9f483b80d7c099c/adaptation/receiver/src/local/airuize/receiver/AirPlayDiagnostics.kt)。

收到 `modesChanged` 时新增已知屏幕、音频、导航状态及资源归属的摘要日志，供仪表显示权与音频争用排查。仅输出固定字段中的布尔值和有限整数；每个列表最多 8 项，支持固定的一层 `modes` 容器，摘要最多 600 字符。

## 其余核对结果

| 参考内容 | 与 LynkPlay 的比较 |
| --- | --- |
| 无线认证交接、45 秒 watchdog、保持已建立的视频会话 | 当前 `CarPlayController` 已覆盖，并增加连接 generation 和旧回调隔离 |
| TCP keepalive、IPv4 热点监听和服务发现 | 当前已有 TCP 健康检测、多种热点模式、地址恢复与端口选择 |
| H.264 worker、IDR 恢复、重复配置过滤 | 当前解码器生命周期、Surface 管理和恢复逻辑已覆盖 |
| PCM/AAC、麦克风选路、音频统计 | 当前媒体、电话、导航选路与麦克风统计已有对应实现 |
| 关闭资源与连接重试 | 当前已有异步 teardown、关闭完成等待与会话隔离；旧阻塞 close 不直接移植 |
| Android 4.3 独立构建、JmDNS 与标准库兼容替换 | 针对 API 18 的独立目标；当前 LynkPlay 最低 API 为 25 |
| Skypine MCU 语音监听 | 属于另一套车型输入协议；当前使用已核对的领克输入与媒体接口 |
| CS11 转向提示 | 发布包静态可见 DimInteraction、导航状态、路名、距离、ETA、箭头更新；当前 BX11 已有相应链路与转向映射 |
| CS11 蓝牙释放 | 发布包静态可见 `Bt.getA2dp/getHfp`、`reqA2dpDisconnect/reqHfpDisconnect`；该车机接口及强制断开策略需要结合 BX11 实车行为验证 |
| CS11 独立音量与压低音乐 | 发布包静态可见 LegacyAudioMixer、0.28 音乐增益和 900 ms 导航尾延迟；直接移植会改变当前音频混音策略，需单独验证电话、Siri 与导航并发 |
| 仪表地图、相邻歌曲封面 | 已公开源码中的地图输出为已有 DiLink 路径；当前材料仍不足以解决 BX11 地图显示权和网易云真实队列数据来源 |
| PairSetup/PairVerify 错误诊断 | 旧目标通过源码生成脚本增加异常回调；可作后续认证专项排查线索，本轮只引入模式状态摘要 |
| GPS 新鲜度 | 旧目标使用单调时钟与 GPS-only 筛选；当前保留 GPS/fused/network 来源选择，定位来源和时间策略需独立评估 |

## 验证

定位编码专项先复现了缺失值与航向边界回归失败；修复后，定位编码和 Android 高度映射专项通过。独立代码复核检查了 GGA 字段顺序、RMC 零值、MSL API 保护、诊断字段白名单与边界，未发现阻断问题。

Python 公共目录检查通过，打包脚本 11 项测试通过。

AGENTS.md 要求的完整 Android CI 通过（`BUILD SUCCESSFUL in 4m 38s`）：

| 模块 | 测试数 | 失败 | 错误 | 跳过 |
| --- | ---: | ---: | ---: | ---: |
| shared | 1536 | 0 | 0 | 1 |
| common | 1518 | 0 | 0 | 0 |
| home | 6 | 0 | 0 | 0 |
| 合计 | 3060 | 0 | 0 | 1 |

mobile、home、maphost 的 Lint 与 Debug 构建均通过。完整本机日志：`/private/tmp/diplay-reference-ci.log`。

首次 Gradle 检查因自动选择的 VS Code JRE 缺少 jlink 失败；显式指定完整 JDK 25 后专项通过。

iPhone 车载定位采用情况、仪表显示权状态日志与实际画面的对应关系，待车机可连接时验证。
