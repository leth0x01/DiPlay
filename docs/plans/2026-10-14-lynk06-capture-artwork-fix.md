# 领克现场采集后的封面修复计划

目标：修复 v51 现场复现的组合文字更新清封面，以及原车 RePlugin 图片流加载失败。

证据：采集 ZIP 的 SHA256 已匹配清单。现场 APK SHA256 与本地 v51 一致；同引用、同来源、同时长、同进度的 title/artist/album 更新触发清理。Provider 累计 16 次成功打开、0 次失败，仪表 Glide 的插件 openInputStream 仍返回 null；原车音乐 HTTP 封面成功转换并发送到 DIM。

设计：换歌依据引用、明确来源切换、有效时长变化或标题变化伴随大幅进度重启；组合文字更新不作为独立换歌证据。当前封面继续保存在私有缓存，JPEG 通过仅绑定 127.0.0.1 的临时 HTTP 服务发布，随机会话令牌和随机文件名限定访问，最多保留三张，不接受上传或目录访问，会话结束关闭服务并删除文件。服务读取、头部长度、图片大小和连接等待均有界。原车下一首依赖 updatePlaylist(9)，真实 iPhone 队列来源仍未知，本轮不伪造队列。

- [x] 添加同引用组合文字更新与真实换歌回归。
- [x] 实现回环 JPEG 服务，测试错误令牌、路径、请求方法、慢连接、关闭和有效图片读取。
- [x] 接入媒体 worker 上的图片存储与会话清理，增加不含地址令牌的服务读取诊断。
- [x] 执行专项测试和完整 Android CI。
- [x] 生成并校验新车测 APK，记录实车未验证的边界。

验证：专项 33 项通过；完整 CI 通过（4m33s），shared 1486、common 1473、home 6 项，共 2965 项，0 失败、0 错误、0 跳过；mobile/home/maphost 的 Lint 与 Debug 构建通过。私有日志 `capture-artwork-targeted.log`、`capture-artwork-ci.log`。

现场边界：Provider 白名单修复已被 v51 的 16 次成功打开记录证实；本轮回环 HTTP 的 JPEG 解码和会话清理在本地验证通过，未在车机验证原车 DimImageProcess 的保存和显示。现场相邻封面通过队列而非直接读取 getPreviousArtwork/getNextArtwork；没有真实下一首数据，本轮仍不提供相邻封面。

车测包：`build/artifacts/LynkPlay-0.2.1-artwork-http-test-v52.apk`，版本码 52。Release 构建和平台签名、包名、版本、系统 UID、两项认证素材校验通过，最终 DEX 已核对包含新回环图片服务与超时线程。SHA256：`48af887029594d73249d56514527eab8721d7f1edc48dacdfec05ad4d1352803`。验收时核对同引用连续播放不再发布 null，`领克封面 HTTP` 的 requests/served，以及原车 DimImageProcess 保存 JPEG 和 DIM 更新；HTTP 成功返回仍不能替代实物显示验收。
