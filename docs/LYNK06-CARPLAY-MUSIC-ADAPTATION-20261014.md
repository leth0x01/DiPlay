# 领克 06 CarPlay 仪表音乐适配（2026-10-14）

## 范围与实车基线

目标车为 2023 款领克 06 燃油版，BX11 / IHU514G / Android 9。适配沿用该车 XSFMediaCenter 的 Binder 与 DIM 链路。

此前用户确认的仪表封面、歌词、歌手来自原车音乐。CarPlay 上一首、下一首可用，播放键能开始播放但不能暂停。本次源码改动需要安装新构建后实车验收。

> **2026-10-16 后续实车采集**：同一次会话里下一曲 / 上一曲 / 暂停都由原车媒体中心回调送达应用并转发
> （`领克媒体中心：回调指令=4/5/2 control=true handled=true`，暂停后 `playing=false`），上面
> “播放键能开始播放但不能暂停”的问题已不再出现；原车 `MusicPlaybackInfoHelper` 也把播放记为
> `appName:LynkPlay`，`artwork` 为本应用发布的 URL——即 CarPlay 的标题/歌手确实已进入原车仪表媒体卡片。
> 但仪表媒体中心的专辑封面仍不显示（原车对 null 封面 URL 抛 `MalformedURLException` 后回退默认），
> 原因与待办见[验证记录](LYNKPLAY-VALIDATION.md) 2026-10-16 条目。

## 已实现

- 使用 `registerInMusic`（19）携带实际安装包名注册，保持令牌、控制权回调与播放信息中的包名一致。
- 将来源列表、当前来源和播放信息中的来源统一为 `SOURCE_TYPE_ONLINE=6`，接入仪表“音乐”项目。
- 处理 `IMusicClient.onSourceSelected`（8）及 `onMediaSelectedPlay`（21）。仅接受音乐来源和当前歌曲 UUID 或空 UUID；在 worker 上请求控制权、选择音乐来源、发布当前信息后发送 CarPlay PLAY。
- `onSourceChanged`（17）是向全部客户端广播的来源/所属包通知，只更新控制门控，不触发播放或重新取得控制权。
- 继续分别转发 PLAY、PAUSE、NEXT、PREVIOUS，保留明确播放和暂停的语义。会话关闭或失去媒体控制权后禁止转发。
- 发布 iPhone NowPlaying 的标题、歌手、专辑、时长、进度和播放状态。原车的 `updateMusicPlaybackState` 返回未更新时保留连接，并继续同步进度。
- 将已解码 CarPlay 封面压缩为最长边 512 像素的 JPEG，通过 `getArtwork`（16）的可选 Uri 编码返回。受限 FileProvider 只暴露封面缓存目录，逐文件授予 `ecarx.xsf.mediacenter` 读取权限。
- 封面缓存最多保留三张；会话关闭时清理本会话文件与授权。排队切换封面期间，getter 只返回与当前封面相符的 Uri，合并未处理的信息更新。
- 增加控制指令、音乐来源、播放信息更新结果及封面状态的中文诊断日志。

## 协议证据

原始资料位于 `.private/lynk06-media-handoff-20261014/`，派生分析位于 `.private/lynk06-media-analysis-20261014/`。

1. `XSFMediaCenter.vdex` 中 `PlaybackState.SOURCE_TYPE_ONLINE=6`、`SOURCE_TYPE_LOCAL=0`。同车 `MediaInteractionImpl.updateMediaSourceTypeList` 将来源 6 转为 DIM 的音乐项目位 `0x10`，来源 0 无对应项目位。
2. `MediaMainApiImpl.updateMusicPlaybackStateInner` 比较当前来源与信息来源；不相同且信息来源不为 -1 时，返回 false，并跳过后续 DIM 发布。现场日志反复记录当前来源 6、信息来源 0，与旧实现固定来源 0 相符。
3. 同函数对 `MusicPlaybackInfoHolder.isEqual` 返回 true 的重复信息也返回 false。旧实现将所有 false 当成拒绝并注销，现已修正。
4. `DimPlaybackInfo` 读取标题、歌手、专辑、时长、来源、状态和封面 Uri；`DimInteractionHelper` 经 `DimImageLoader` 的 Glide 图片加载器将封面缓存到原车 DIM 图片目录，再更新仪表。
5. 原车 Stub 的回调编号及封面 Uri 编码已对照核对。Compact DEX 的派生宽度修正文件仅供分支阅读；quickened 方法及字段索引未恢复，不能将其中占位符当成真实符号。
6. `MediaMainApiImpl.registerMusic`（1）传入空包名，`registerInMusic`（19）将传入包名直接写入 `ClientToken`。旧客户端的控制权门控按实际包名比较，新接口避免空包名身份与回调不匹配。

## 验证

专项测试覆盖来源一致性、选择后取得控制权再播放、其他来源/UUID 拒绝、明确四种媒体指令、控制权丢失和关闭门控、重复信息不注销、封面 Parcel 编码、封面读取、缩放、缓存上限、目录隔离和关闭清理。

最终完整 CI 通过（`BUILD SUCCESSFUL in 3m 14s`）：shared 1433 项（跳过 1 项）、common 1337 项、home 6 项，均无失败/错误。媒体专项 18 项通过；mobile、home、maphost 的 Lint 与 Debug 构建通过。公开源码检查通过，打包脚本测试 8 项通过。

首次 common 全量检查中 `SettingsBackupServerTest.aLargeReportArrivesWhole` 失败，后续全量通过；中间一轮 Lint 分析器在媒体测试文件上出现 `Unexpected owner function: null`，最终完整检查通过，未禁用 Lint。日志和专项 XML 保存在私有分析目录，最终结果见 `validation.txt` 与 `verified-ci.txt`。

本机自动选择的 VS Code JRE 缺少 `jlink`，检查时临时绕过 Gradle daemon 自动选择并使用完整 Adoptium JDK 25；原 JVM 配置已恢复。

## 实车验收

1. 使用包含配件认证的车测构建，核对实际安装 APK 哈希，记录本次 CarPlay 音频模式。
2. 连接 CarPlay，在仪表选择“音乐”。检查歌名、歌手、专辑与封面是否对应 iPhone 当前歌曲。
3. 检查播放、暂停、暂停后恢复、上一首、下一首；同步记录物理结果、按键时间、媒体中心回调和 iPhone 状态。
4. 检查前后台控制、快速切歌及封面更新，切到原车其他媒体后再回到音乐。
5. 检查通话、CarPlay 断开及原车音乐恢复后的仪表信息和方控。

源码测试和编译结果用于验证本地实现，仪表实际显示与控制效果以新构建的实车结果为准。
