# 领克仪表封面保留修复计划

目标：修复已解码的当前封面因分批到达的歌曲信息被误清空，并验证真实换歌仍不会发布旧图。

证据：私有报告 `diplay-report 6.txt` 多次记录封面接收、解码和 URI 发布成功，约一秒后同一引用的 URI 变为空；报告未记录触发清理的具体字段，不能认定全部实车原因已查明。

设计：单独更新歌手、专辑或时长不作为换歌证据；引用变化、明确来源切换或标题变化并伴随其他换歌证据才清理缓存。字段暂时清空也不等于换歌。真实换歌继续执行已有缓存和解码队列失效流程。增加只记录字段变化布尔值和时间的清理日志。

- [x] 添加分批元数据补正、字段清空和真实换歌的回归测试。
- [x] 修改 `AlbumTrackIdentity.kt`、`CarPlayMediaKeys.kt`，保留符合当前引用的封面。
- [x] 运行封面专项测试和 AGENTS.md 的完整 Android CI。
- [x] 记录验证结果；下一首接口 17 的数据来源尚未确认，不伪造下一首封面。

验证结果：新增回归在修复前 3 项失败，修复后封面专项 37 项通过；最终完整 CI 通过，shared 1486、common 1468、home 6 项，共 2960 项，0 失败、0 错误、0 跳过，三个应用 Lint 和 Debug 构建通过。使用 Java 25 完整 JDK，关闭 JDK 自动发现以避开缺少 jlink 的 VS Code JRE；设置 `ANDROID_HOME=D:/AndroidSdk`。私有日志：`artwork-targeted-before.log`、`artwork-targeted-after.log`、`artwork-ci.log`。

实车限制：报告没有元数据变化字段和原车图片打开计数，本次修复覆盖已在测试中复现的误清理条件，尚未证明两处空白的全部原因。下一首图片接口已经核对为 17，但当前 iPhone 输入只保留当前歌曲元数据；本次没有把当前封面填充到下一首位置。

车测产物：`build/artifacts/LynkPlay-0.2.1-artwork-test.apk`，versionCode 49，包名 `com.leth0x01.lynkplay`。Release 构建及专用校验通过，平台签名、系统 UID 和两项认证素材均已核验。未安装到车机。验收时关注当前封面能否持续显示、切歌是否正确更新，以及诊断中的封面失效原因和原车读取计数。

## 报告 7：补齐原车卡片读取者

新报告出现 `attempts=15 opened=0 failures=15 lastCallerUid=10050 lastFailure=SecurityException`，直接证明 Provider 的打开请求被拒绝。原车历史 `am_pss` 记录将 UID 10050 对应到 `ecarx.xsf.widget`。因此新增该具体包作为只读封面读取者，运行时查询安装 UID，不硬编码 10050，也不扩大到全部原车包或全部调用者。原有媒体中心权限、文件路径限制、写入拒绝保持。

专项测试通过：故意把 Widget 安装 UID 设为 10150，验证其可以读取 JPEG、类型和文件信息，不能写入或越出缓存目录；此时 UID 10050 仍被拒绝。其他应用的拒绝和当前歌曲路由回归通过。

版本说明：用户确认报告来自刚打的包。报告头为 0.2.1，本地交付 APK 元数据为 0.2.1-artwork-test/49，且报告没有新失效日志；这一差异尚未现场核对，不据此否定用户的安装操作，也不宣称缓存修复实车验证通过。

读取者修复的完整 CI 再次通过：shared 1486、common 1468、home 6 项，共 2960 项，0 失败、0 错误；三个应用 Lint 和 Debug 构建通过。日志 `.private/artwork-widget-ci.log`。新的车测版本码为 50，避免与上一轮版本码 49 混淆。

新车测包 `build/artifacts/LynkPlay-0.2.1-artwork-widget-test.apk` 已完成 Release 构建及签名、包名、版本码 50、系统 UID 和认证素材校验，尚未实车安装。验收关注 `opened` 是否增加、`SecurityException` 是否消失，并单独观察当前封面显示；不把 Widget 能读图等同于仪表已经显示。
