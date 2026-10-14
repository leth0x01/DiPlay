# 领克 06 CarPlay 仪表音乐适配实施计划

**目标：** CarPlay 连接时将歌曲信息、封面与播放状态发布到原车仪表音乐入口，并通过原车回调控制播放、暂停和切歌。

**架构：** 沿用 XSFMediaCenter Binder 客户端，来源统一为本车 ONLINE=6。来源选择回调在媒体 worker 上取得控制权再发送播放，明确播放与暂停保留原意。封面通过受限 FileProvider 交给媒体中心，由原车既有 DIM 链路传到仪表。

**技术：** Kotlin、Android 9 Binder/Parcel、FileProvider、Robolectric、Gradle。

1. 核对原车 VDEX 的来源枚举、DIM 来源列表和信息发布分支，保存私有分析资料。
2. 在 `Lynk06MediaCenterTest.kt` 增加来源一致性、选择入口、封面 Uri 编码与控制权回归测试，先验证现有实现失败。
3. 修改 `Lynk06MediaCenterProtocol.kt`，统一来源 6，补齐来源/媒体选择回调和封面 getter。
4. 修改 `Lynk06MediaCenter.kt` 与 `CarPlayMediaKeys.kt`，在原车音乐选择后取得控制权并发布最新状态；接入已解码封面，限制缓存文件数量并清理授权。
5. 在 common manifest 和 XML 中声明仅封面缓存目录的 FileProvider，验证 Uri 读权限和清理。
6. 运行媒体专项测试，再运行 `.github/workflows/android.yml` 的完整 Gradle 检查，记录真实结果。
7. 更新交接文档，记录协议证据、实现内容和实车验收步骤。实车确认音乐来源、歌名/歌手/封面、四种控制、媒体切换与断开行为。
