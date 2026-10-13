# 领克 06 媒体方控实现计划

**Goal:** CarPlay 连接时，将原车播放/暂停、上一首、下一首回调转发到 iPhone。

**Architecture:** 根据实车 XSFMediaCenter 的 Binder 描述符、transaction 编号和参数类型实现窄范围协议。注册音乐客户端并上报 iPhone 播放状态，由原车媒体中心选择该客户端；结束会话时注销。原车蓝牙音频模式继续使用 AVRCP。注册、状态上报和注销放到独立线程，回调读取保留快照，避免同步 Binder 调用互相等待。

**Tech Stack:** Kotlin、Android Binder/Parcel、HandlerThread、Robolectric、Mockito。

## 依据

- `lynk06_cluster_20261012_154433/10-wheel-pause/logcat.txt`：DIM 状态 8 → MediaCtrlByDimCallback → 蓝牙暂停。
- `lynk06_cluster_20261012_154433/11-wheel-play/logcat.txt`：DIM 状态 7 → MediaCtrlByDimCallback → 蓝牙播放。
- 同目录 XSFMediaCenter APK 清单：MediaCenterService 导出，`ecarx.xsf.MEDIA_CENTER_SERVICE`。
- 同目录 VDEX：IMediaCenterSvc 注册 1、注销 4、requestPlay 5、播放状态 6、来源列表 7、来源 8、进度 10、能力 30；IMusicClient 播放 1、暂停 2、下一首 3、上一首 4；IMusicPlaybackInfo getter 编号以该文件的 Stub 字段为准。

## 实施步骤

1. 在 `common/src/test/java/com/shilapi/xcertplay/Lynk06MediaCenterTest.kt` 添加 Binder 协议测试，覆盖四个操作、状态序列化、失去控制权、退出以及协议不匹配。
2. 在 `common/src/main/java/com/shilapi/xcertplay/Lynk06MediaCenterProtocol.kt` 实现实际使用的服务调用与音乐客户端/播放状态 Binder；用常量标注原车 transaction 编号。
3. 在 `common/src/main/java/com/shilapi/xcertplay/Lynk06MediaCenter.kt` 管理服务绑定、注册、播放状态同步、媒体控制权和注销。只在本车且 CarPlay 音频开启时启用。
4. 在 `CarPlayMediaKeys.kt` 接入 attach、元数据更新和 detach；在报告中记录注册结果与四个回调结果。
5. 运行专项测试与 AGENTS.md 的完整 CI 检查，记录实际异常；更新 `docs/LYNK06-WHEEL-MICROPHONE-BLUETOOTH.md` 的实现与验证记录。
6. 使用 `uv run scripts/build_lynkplay_car_test.py` 构建并校验签名、包名和认证素材，交付实车测试 APK。
