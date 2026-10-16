# 领克 06 ECARX AdaptAPI 输入调查（2026-10-16）

**结论：不采用，相关代码已移除。** 本文是决策记录，保留证据，避免以后重复调查。

目标车：2023 款领克 06 燃油版，BX11 / IHU514G / xecarx / Android 9。
实车结论汇总见 [验证记录](LYNKPLAY-VALIDATION.md) 的 2026-10-16 条目。

曾实现过一个 `EcarxAdaptApiInputBackend`（反射调用
`com.ecarx.xui.adaptapi.input.Input.requestKeysInterception`），依据是原厂 AppleCarPlay
在 **SX11-A3 / IHU715P** 上这样用。实车（**BX11 / IHU514G**）验证后确认该机制在本车**不可用**，
代码与测试已整体回退；生产代码回到引入前状态。

---

## 1. 反编译来源

车机只读取回，离线反编译（vdex019 → CompactDex → 标准 dex → jadx）：

```
/system/framework/AdapterAPI.jar              classes.dex（741 类）
/system/framework/AdapterAPIImpl.jar          仅 manifest，无代码
/system/framework/arm64/boot-AdapterAPI.vdex      cdex001 → dex
/system/framework/arm64/boot-AdapterAPIImpl.vdex  cdex001 → dex（252 类）
/system/priv-app/XSFInputService/XSFInputService.apk
```

流水线：`vdexExtractor -i <vdex> -o out -f` → 得 `cdex001` → `Cdex2Dex`
（dexlib2 HEAD `CDexBackedDexFile` + `DexFileFactory.writeDexFile`）→ `dex.039` → `jadx`。

## 2. 真实语义（`com.ecarx.xui.adaptapi.input.impl.InputImpl`）

```java
ArrayMap<IKeyCallback, int[]> mCallbacks = new ArrayMap<>();

public int[] requestKeysInterception(int[] keys, IKeyCallback callback) {
    mCallbacks.put(callback, keys);
    int[] finalKeys = filterKeys(keys);          // filterKeys(keys){ return keys; }
    mIm.requestKeysInterception(finalKeys, mKeyEventObserver);   // IInputManager
    return finalKeys;
}

private void reportKeys(boolean down, int keyCode) {
    int appCode = this.mKeyMap.getAppKey(keyCode);              // 原始码 → 应用码
    for (IKeyCallback key : this.mCallbacks.keySet()) {
        int[] value = this.mCallbacks.get(key);                 // ← ArrayMap.get
        if (isValidKey(value, appCode) != -1) {
            if (down) key.onKeyPressed(appCode); else key.onKeyReleased(appCode);
        }
    }
}

private int isValidKey(int[] keys, int keyCode) {
    for (int i = 0; i < keys.length; i++) { ... }               // keys==null → NPE
}
```

三个必须记住的点：

1. **注册的是"应用码"**（ECARX `KeyCode.*` 命名空间），不是驱动原始码。
   `reportKeys` 先用 `KeyMapAdapterApi.getAppKey()` 把原始码转成应用码，再与注册数组比对。
2. **返回值不是"授权结果"**：`filterKeys` 原样返回入参，所以
   `requestKeysInterception` 的返回值**恒等于请求数组**。用它判断"系统是否授权"是**假阳性**。
3. **回调容器是 `ArrayMap`**：需要 key 的 `equals` 自反。用 `java.lang.reflect.Proxy`
   实现 `IKeyCallback` 时，如果 `equals` 恒返回 `false`，`mCallbacks.get(proxy)` 恒为 `null`
   → `isValidKey(null, code)` → `NullPointerException: Attempt to get length of null array`。

> 首次实现的 NPE 就是这个原因（`equals -> false`），实车 logcat 中表现为
> `at com.ecarx.xui.adaptapi.input.impl.InputImpl.isValidKey(InputImpl.java:192)`
> 共 16 次。这个坑对所有"把 Proxy 放进哈希/ArrayMap 容器"的代码都成立。

## 3. 原始码 → 应用码映射（`KeyMapAdapterApi.SystemKeyToApp`，节选有效项）

| 原始码 | 应用码 | 含义 |
| --- | --- | --- |
| 298 | 200231 | `KEYCODE_R_VOICE_ASSIST` |
| 299 | 200005 | `KEYCODE_R_CALL` |
| 300 / 301 / 302 | 200164 / 200024 / 200025 | MUTE / VOLUME_UP / VOLUME_DOWN |
| 303 | 210004 | `KEYCODE_R_SRC` |
| 304 / 305 | 200088 / 200087 | MEDIA_PREVIOUS / MEDIA_NEXT |
| 286 / 287 | 24 / 25 | 音量 −/+ |
| 326 / 330 / 308 / 307 / 348 | 5 / 91 / 3 / 119 / 119 | 其它 |
| 314 / 315 / 333 / 349 / 350 | 110002 / 110001 / 110003 / 200400 / 200600 | 其它 |

**没有映射（注册了也永远不会命中）**：`200003`、`200004`、`200006`(ENDCALL)、`200085`(PLAY_PAUSE)、
`210003`、`210005`、`210006`、`300001`~`300003`、`26`、`200082`、`210020`、`300020+`。

## 4. 参照实现：`XSFInputService`（本车可正常工作）

```java
mInputApi.requestKeysInterception(this.KEYS, mCallback);   // KEYS 来自资源数组
```

`KEYS` 按 `PcodeManager` 前缀选择；BX11 落到 `R.array.e02_key_action`（30 项）：

```
200003, 3, 200004, 4, 210003, 200087, 200085, 200088,
200231, 200025, 200164, 200024, 210004, 200005, 5, 300001,
300002, 300003, 110001, 110003, 24, 25, 91, 200164,
110002, 210005, 210006, 119, 200400, 26
```

`index 8 = 200231`，与实车 logcat 的 `isValidKey keys[8]=200231=200231` 完全吻合。
它注册了 `200087/200085/200088`，但**这些键在 BX11 上并不进 `IKeyEventObserver`**（见下）。

## 5. 实车实际交付了什么（BX11）

对整段 logcat 统计 `InputImpl` 的 observer 事件，只出现两种原始码：

| 原始码 | → 应用码 | 次数 | 时间 |
| --- | --- | --- | --- |
| 298 | 200231 语音键 | 8 | 21:29:46 |
| 302 | 200025 音量− | 56 | 21:32:53–54 |

**媒体键（304/305）与接听键（299）一次都未出现。** 同一时间的媒体键由
**原车媒体中心回调**送达（`领克媒体中心：回调指令=4/5/2/1 control=true handled=true`）。

## 6. 结论

1. AdaptAPI 在 BX11 上**无法覆盖媒体键**（被原车媒体中心先消费），因此替代不了现有媒体中心路径。
2. 它能拿到的**语音键 200231** 已经由有序广播 `ecarx.intent.action.ECARX_KEY_RVOICEASSIST_EVENT`
   → `Lynk06VoiceKeyReceiver` 处理；同时注册会造成**双触发 Siri**。
3. 接听/挂断（299）在本次会话中从未经 AdaptAPI 到达；实车那次接听/挂断成功来自其它路径。
4. `200085`、`200006` 是死码。

**决定：移除该后端。** 车机上的按键继续走两条既有路径：

- **媒体键** → 原车媒体中心回调 → `Lynk06MediaCenter` → CarPlay
- **语音键** → ECARX 有序广播 → `Lynk06VoiceKeyReceiver` → Siri

## 7. 复现命令

```sh
# 采集（只读）
adb pull /system/framework/arm64/boot-AdapterAPI.vdex
adb pull /system/framework/arm64/boot-AdapterAPIImpl.vdex
adb pull /system/framework/AdapterAPI.jar
adb pull /system/priv-app/XSFInputService/XSFInputService.apk

# 反编译（VDEX 019 内为 CompactDex）
vdexExtractor -i boot-AdapterAPIImpl.vdex -o out -f
# cdex001 → 标准 dex：dexlib2 HEAD 的 CDexBackedDexFile + DexFileFactory.writeDexFile
jadx -d src --no-res impl.dex                              # InputImpl / KeyMapAdapterApi
aapt2 dump resources XSFInputService.apk | grep -A12 e02_key_action
```
