# 领克仪表保留原车封面的现场修复

实车：BX11 / IHU514G，安装 LynkPlay 0.2.2（53）。用户确认所有 CarPlay 歌曲均保留原车音乐封面。

2026-10-15 09:19:46，原车 Glide 已从回环 HTTP 解码 CarPlay 封面，DimImageProcess 保存了 43023 字节、300×300 的有效 JPEG，并将 file URI 交给 MediaInteractionImpl。私有采集同时显示 `SupportIPK_MEDIA_COVER_INFO=2`、`IPKMode=2`；`updateAlbumInfo` 和 `send show broadcast` 却仍使用酷我旧列表的 `683929_万海东_山风山风等等我` 与旧网络封面。09:28 的采集仍重复旧列表。

原因：此固件的多封面仪表路径从 `updatePlaylist` 缓存取当前及相邻封面，不直接使用播放信息的 artwork。之前修复的封面保留、Provider 和 HTTP 链路已经工作，未同步列表使原车旧列表持续生效。歌词随组合文字字段变化是此前实车已有行为，不在这次改动中修改。

实现：核对本车 XSFMediaCenter VDEX 中 transaction 9 的 token/source/typed-list 格式及 IMedia Parcelable 字段顺序。持有媒体控制权时，在播放信息之前同步仅含当前已知歌曲的列表；歌曲文字、时长或封面改变时更新，进度和暂停变化不重复更新列表。重新取得控制权或重连后重新发布。列表项 position=0，UUID 与 playback 相同；不编造前后歌曲，切歌控制继续走原有 IMusicClient 回调。

验证：Binder 回归覆盖完整字段消费、单项列表、封面 Uri、先列表后播放信息、重复播放信息不重复列表，以及既有控制权和封面异步回归。完整 Android CI 与车测结果见本次交接记录；编译及 Binder 测试不能代替物理仪表显示验收。

私有日志与原车程序保存在 `.private/artwork-adb-20261015/`，不提交原车程序、用户媒体图片或完整现场日志。

验证结果：封面及媒体中心专项回归通过；完整 CI `BUILD SUCCESSFUL in 13m 52s`，shared 1484、common 1480、home 6，共 2970 项测试，0 失败及错误；三模块 Lint 和 Debug 构建通过。首次全量检查因旧 JRE 缺少 jlink 失败，显式指定完整 JDK 后通过。

已在用户授权后覆盖安装并打开 `0.2.2-artwork-playlist-test`（54），保留安装前 APK 备份。安装包为 `build/artifacts/LynkPlay-0.2.2-artwork-playlist-test-v54.apk`，SHA256 `0e1822767323b325794fb7728009224b021873bbf6e4ef8ae833a70387f7ebe9`；安装前已核验本车平台签名、包名、版本、系统 UID 和认证素材。

09:52:15 与 09:52:26 的新日志确认 DIM 列表已替换为当前 CarPlay 歌曲；仪表广播的 albumPath 为当前回环 HTTP 封面，prePath/nextPath 均为 noAlbumPath，不再发送酷我旧图。CarPlay 已恢复连接。用户随后确认“已显示正确封面”，本次当前歌曲封面实车验收通过；该反馈不代表已验证长时间播放、多次切歌或所有音乐 App。
