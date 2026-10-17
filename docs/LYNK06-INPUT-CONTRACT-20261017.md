# 领克 06 原厂按键输入契约（XSFInputService）

> 调查日期：2026-10-17。记录领克 06（BX11 / IHU514G）原厂按键输入服务（`ecarx.xsf.inputservice`）的
> 分发链路与广播契约，解开实车 V3/V4/V5 未决项（语音键 / 电话键 / 媒体键来源）。
> 逆向方式：REA `inspect-android-package` / `inspect-android-class` / `inspect-android-method`。

## 1. 组件概览（已证实）

| 项 | 值 |
| --- | --- |
| 包名 | `ecarx.xsf.inputservice` |
| 版本 | `SY-NEU3.4-20221229-BX11-Volume0Up1` |
| 类数 | 1133 |
| 核心 Service | `ecarx.xsf.inputservice.InputService` |
| 按键策略 | `ecarx.xsf.inputservice.key.KeyPolicyImpl` |

## 2. 输入链路（已证实）

```
车辆 HAL 按键
  → android.car.input.service.DefaultInputService.onKeyEvent()
    （translateKeyCode 键码翻译，如 298→349 微信键）
  → InputManager.reportKeyToAdaptApi()（经系统 input 服务上报 AdaptAPI）
  → ECARX AdaptAPI Input.create() 连接
  → InputService.requestKeysInterception()（拦截 KEYS 数组，按车型选 e02/kx11/ex11 键码表）
  → IKeyCallback（InputService$1）→ KeyPolicyImpl.onKeyEvent()
  → mActions.get(keyCode) 取动作 → 动作类 onShortClick()
  → 按动作类型分发（见 §3）
```

`android.car.input.service` 是 AOSP 车载输入服务，`DefaultInputService.sendKeyToKeyService()` 通过
`IInputManager.reportKeyToAdaptApi()` 把车辆按键上报给 AdaptAPI（`shouldReportKeyToAdaptapi()` 为 true 时）。

`InputService.onCreate()` 按 `PcodeManager` 的车型前缀（`NL_3B_EO2` / `KX11` / `EX11`）选择键码表
`R.array.{e02,kx11,ex11}_key_action`，默认 `e02_key_action`。

## 3. 按键动作 → 分发方式（已证实）

`KeyPolicyImpl.registerActions()` 注册了 38 个按键动作，其中与 CarPlay 交互相关的：

### 3.1 媒体键：注入标准 KeyEvent（→ 媒体中心）

`ECarXRMediaNextAction.onShortClick()` 注入 `KeyEvent(87 = KEYCODE_MEDIA_NEXT)` 按下+抬起：

```java
injectInputEvent(new KeyEvent(uptime, uptime, 0, 87, 0)); // DOWN
injectInputEvent(new KeyEvent(uptime, uptime, 1, 87, 0)); // UP
```

同理：上一曲注入 88、播放/暂停注入 85。注入后由 `XSFMediaCenter.MediaKeyReceiver` 接收
（见 `LYNK06-MEDIACENTER-CONTRACT-20261017.md` §3.1 键码映射），最终经 `IMusicClient.onNext/onPrevious/onPause`
→ LynkPlay → CarPlay。

**播放/暂停键的双重行为**（`ECarXRMediaPlayPauseAction.onShortClick()`）：

`isCallActived()`（`ECarXKeyActionBase`，响铃 RINGING 或撋机 OFFHOOK）为 true 时，播放/暂停键不再注入
媒体键 85，而是改发 `ecarx.intent.action.ECARX_KEY_BLEPLAYPAUSE_EVENT` 广播 →
`XCBTPhone3.EcarxKeyReceiver.handleConfirmClick()` → 来电（1003/1004/1005）时 `setRingtoneMute()`（静音铃声）。
这印证实车观察「播放暂停键在通话中承担电话键职责」；接听/挂断本身由电话键 `KEYCODE_R_CALL = 200005`
承担（见 §3.3）。

### 3.2 语音键：有序广播

`ECarXRVoiceAssistAction.onShortClick()` 发**有序广播**：

```java
Intent intent = new Intent("ecarx.intent.action.ECARX_KEY_RVOICEASSIST_EVENT");
intent.putExtra("ecarx.extra.ECARX_KEY_EVENT_TYPE", KeyCode.KEYCODE_R_VOICE_ASSIST);
AppApplication.getInstance().sendOrderedBroadcast(intent, null);
```

对应实车日志 `Lynk06VoiceKey: 动作=0；处理=true`（V3 语音键来源 = 该有序广播）。

### 3.3 电话键：广播 → XCBTPhone3 接听/挂断

`ECarXCallAction` / `ECarXRCallAction.onShortClick()` 发广播：

```java
Intent intent = new Intent("ecarx.intent.action.ECARX_KEY_RCALL_EVENT");
intent.putExtra("ecarx.extra.ECARX_KEY_EVENT_TYPE", KeyCode.KEYCODE_R_CALL); // RCallAction；CallAction 用 5
AppApplication.getInstance().sendBroadcast(intent);
```

接收方为 `com.ecarx.btphone`（XCBTPhone3）的 `ControlKeyBroadcastReceiver`，按通话状态分派：

键码 `KEYCODE_R_CALL = 200005`（即实车 V4「200005」的语义）。

| 通话状态码 | 短按（ACTION_TYPE=0） | 长按（ACTION_TYPE=1） |
| --- | --- | --- |
| 1000 空闲 | 打开电话应用 | 拨最近号码 |
| 1003/1004/1005/1010 来电 | `answerCall()` 接听 | `rejectCall()` 拒接 |
| 10011/10012 拨出/通话中 | `disconnectCall()` 挂断 | `disconnectCall()` 挂断 |

## 4. 关键广播 action（已证实，`IntentHelper` 常量）

| 常量 | 字符串值 | 含义 |
| --- | --- | --- |
| `ECARX_ACTION_CARPLAY_SERVICE_NAME` | `ecarx.intent.action.START_CARPLAY_SERVICE` | 启动 CarPlay 服务 |
| `ECARX_ACTION_ECARX_KEY_RCARPLAY_EVENT` | `ecarx.intent.action.ECARX_KEY_EVENT_RCARPLAY_EVENT` | R CarPlay 键 |
| `ECARX_ACTION_ECARX_KEY_SIRI_EVENT` | `ecarx.intent.action.ECARX_KEY_EVENT_SIRI_EVENT` | Siri 键 |
| `ECARX_ACTION_ECARX_KEY_RVOICE_EVENT` | `ecarx.intent.action.ECARX_KEY_EVENT_RVOICE_EVENT` | R 语音键 |
| `ECARX_ACTION_ECARX_KEY_CALL_EVENT` | `ecarx.intent.action.ECARX_KEY_CALL_EVENT` | 电话键 |
| `ECARX_ACTION_ECARX_KEY_RCALL_EVENT` | `ecarx.intent.action.ECARX_KEY_RCALL_EVENT` | R 电话键 |
| （硬编码） | `ecarx.intent.action.ECARX_KEY_RVOICEASSIST_EVENT` | 语音助手键（有序广播） |

另有 `ECARX_ACTION_IAP2_SERVICE_NAME`、`ECARX_CATEGORY_CARPLAY_SERVICE` 等 iAP2/CarPlay 服务启动常量。

## 5. 结论（解开 V3/V4/V5）

| 项 | 结论 | 等级 |
| --- | --- | --- |
| V5 媒体键来源 | 方控媒体键 → XSFInputService 注入 KeyEvent 87/88/85 → XSFMediaCenter.MediaKeyReceiver → IMusicClient → CarPlay | 已证实（两端代码对齐） |
| V3 语音键来源 | 语音键短按 → `ECARX_KEY_RVOICEASSIST_EVENT` 有序广播 → LynkPlay 监听 | 已证实 |
| V4 电话键来源 | 接听/挂断 → `ECARX_KEY_RCALL_EVENT` 广播 → XCBTPhone3 按通话状态分派 `answerCall`/`rejectCall`/`disconnectCall` | 已证实 |

## 6. 键码常量（已证实，`KeyPolicyImpl` / `KeyCode`）

| 常量 | 值 | 含义 |
| --- | --- | --- |
| `KEYCODE_R_CALL` | 200005 | 方向盘电话键（V4「200005」语义） |
| `KEYCODE_R_VOICE_ASSIST` | 200231 | 方向盘语音键 |
| `KEYCODE_R_MEDIA_PLAY_PAUSE` | 200085 | 方向盘播放/暂停 |
| `KEYCODE_R_MEDIA_NEXT` | 200087 | 方向盘下一曲 |
| `KEYCODE_R_MEDIA_PREVIOUS` | 200088 | 方向盘上一曲 |
| `KEYCODE_R_VOLUME_UP` / `_DOWN` / `_MUTE` | 200024 / 200025 / 200164 | 方向盘音量加 / 减 / 静音 |
| `KEYCODE_VOICE_ASSIST` | 231 | 面板语音键 |
| `KEYCODE_R_HOME` / `_BACK` | 200003 / 200004 | 方向盘 Home / 返回 |
| `KEYCODE_NAVI` / `_RADIO` / `_MEDIA` | 110001 / 110002 / 110003 | 面板导航 / 电台 / 媒体 |
| `KEYCODE_DMSM_LEFT/RIGHT/CENTER` | 300001 / 300002 / 300003 | 方向盘 DMSM 按键 |

`KEYCODE_R_CALL = 200005` 解开实车 V4「200005 语义」：电话键由 XCBTPhone3 按通话状态分派接听/拒接/挂断，
LynkPlay 不监听电话键（电话走原车蓝牙 HFP）。

**实车证据（已证实，采集 logcat）**：领克 06 电话走 Android 系统 HFP Client 服务
（`com.android.bluetooth.hfpclient`，`HfpClientConnService` / `HfpClientConnection`），采集时序：

```
21:36:53.369 HfpClientDeviceBlock: Got call ... mState: INCOMING   ← 来电
21:36:53.378 HfpClientConnection: Got call state change to 4       ← HFP 状态 4=来电
21:36:58.657 HfpClientDeviceBlock: Got call ... mState: ACTIVE     ← 接听
21:36:58.658 HfpClientConnection: Got call state change to 0       ← HFP 状态 0=活跃通话
（21:38:04 call state change to 7 = 空闲）
```

即电话控制走蓝牙 HFP（车机为 HFP Client / Hands-Free 单元），方控电话键由原车 XCBTPhone3 处理，
CarPlay（LynkPlay）只负责媒体/地图/UI，不参与电话接听挂断。
