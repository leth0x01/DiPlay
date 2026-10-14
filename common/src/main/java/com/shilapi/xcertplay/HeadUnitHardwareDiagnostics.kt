package com.shilapi.xcertplay

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import android.view.InputDevice
import android.view.KeyEvent

/**
 * 适配新车的硬件清单：输入设备（方控）与编解码器（CarPlay 视频/音频）。
 *
 * 全是只读系统接口，不依赖无障碍服务或 ADB，未适配的车上也能采到——
 * 方控日志只在服务绑定后才有按键记录，这份清单是兜底的采集渠道。
 * 设备名与编解码器名不含个人信息；报告整体仍由 DiagnosticRedactor 兜底。
 */
internal object HeadUnitHardwareDiagnostics {

    /**
     * 方控适配关心的按键：媒体、音量、方向/确认、通话、语音，以及领克等
     * 车机常用的 F1–F12。设备声明支持哪些键，比事后抓按键日志更可靠。
     */
    val KEY_CANDIDATES: List<Int> = listOf(
        KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN,
        KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_MUTE,
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE,
        KeyEvent.KEYCODE_MEDIA_STOP, KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_PREVIOUS,
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_REWIND,
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SEARCH,
        KeyEvent.KEYCODE_CALL, KeyEvent.KEYCODE_ENDCALL,
        KeyEvent.KEYCODE_VOICE_ASSIST, KeyEvent.KEYCODE_ASSIST,
        KeyEvent.KEYCODE_NAVIGATE_IN, KeyEvent.KEYCODE_NAVIGATE_OUT,
        KeyEvent.KEYCODE_NAVIGATE_NEXT, KeyEvent.KEYCODE_NAVIGATE_PREVIOUS,
    ) + (KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12).toList()

    fun inputDevicesReport(devices: List<InputDevice> = systemInputDevices()): String {
        if (devices.isEmpty()) return "no input devices visible"
        return devices.joinToString("\n") { device ->
            formatInputDevice(
                id = device.id,
                name = device.name,
                descriptor = device.descriptor,
                vendorId = device.vendorId,
                productId = device.productId,
                sources = device.sources,
                isVirtual = device.isVirtual,
                supportedKeys = supportedKeys(device),
            )
        }
    }

    /** 一行一个编解码器；解码在前、编码在后，各自按名字排序。 */
    fun codecReport(codecs: List<MediaCodecInfo> = systemCodecs()): String {
        if (codecs.isEmpty()) return "no codec information available"
        return codecs.sortedWith(compareBy({ it.isEncoder }, { it.name }))
            .joinToString("\n") { info ->
                formatCodec(
                    name = info.name,
                    types = info.supportedTypes.sorted(),
                    encoder = info.isEncoder,
                    hardwareClass = hardwareClass(info),
                    maxVideoSize = maxVideoSize(info),
                )
            }
    }

    internal fun formatInputDevice(
        id: Int,
        name: String?,
        descriptor: String?,
        vendorId: Int,
        productId: Int,
        sources: Int,
        isVirtual: Boolean,
        supportedKeys: List<Int>,
    ): String = buildString {
        append("id=").append(id).append(" \"").append(name ?: "?").append('"')
        append(" desc=").append(descriptor ?: "?")
        append(" vendor=0x%04x product=0x%04x".format(vendorId, productId))
        append(" sources=[").append(sourcesSummary(sources)).append(']')
        if (isVirtual) append(" virtual")
        if (supportedKeys.isNotEmpty()) {
            append(" keys=[").append(supportedKeys.joinToString(", ") { keyName(it) }).append(']')
        }
    }

    internal fun formatCodec(
        name: String,
        types: List<String>,
        encoder: Boolean,
        hardwareClass: String,
        maxVideoSize: String?,
    ): String = buildString {
        append(if (encoder) "encoder " else "decoder ").append(name)
        append(" [").append(types.joinToString()).append(']')
        append(' ').append(hardwareClass)
        if (maxVideoSize != null) append(" (max ").append(maxVideoSize).append(')')
    }

    internal fun keyName(code: Int): String = KEY_NAMES[code] ?: code.toString()

    // KeyEvent.keyCodeToString 依赖运行时填充的表；直接反射常量在任何环境都可读。
    private val KEY_NAMES: Map<Int, String> by lazy {
        KeyEvent::class.java.fields
            .filter { it.name.startsWith("KEYCODE_") }
            .mapNotNull { field ->
                runCatching { field.getInt(null) to field.name.removePrefix("KEYCODE_") }.getOrNull()
            }
            .toMap()
    }

    internal fun sourcesSummary(sources: Int): String {
        val flags = linkedMapOf(
            "keyboard" to 0x00000101, // SOURCE_KEYBOARD
            "dpad" to 0x00000201, // SOURCE_DPAD
            "gamepad" to 0x00000401, // SOURCE_GAMEPAD
            "touchscreen" to 0x00001002, // SOURCE_TOUCHSCREEN
            "mouse" to 0x00002002, // SOURCE_MOUSE
            "stylus" to 0x00004002, // SOURCE_STYLUS
            "trackball" to 0x00010004, // SOURCE_TRACKBALL
            "touchpad" to 0x00100008, // SOURCE_TOUCHPAD
            "joystick" to 0x01000010, // SOURCE_JOYSTICK
            "rotary" to 0x00400000, // SOURCE_ROTARY_ENCODER
        )
        val matched = flags.filterValues { sources and it == it }.keys
        return if (matched.isEmpty()) "0x${sources.toString(16)}" else matched.joinToString(", ")
    }

    private fun supportedKeys(device: InputDevice): List<Int> {
        val supported = device.hasKeys(*KEY_CANDIDATES.toIntArray())
        return KEY_CANDIDATES.filterIndexed { index, _ -> supported[index] }
    }

    /** API 29 起系统自己区分软硬件编解码器；更早的版本只能从名字猜，不如实标未分类。 */
    private fun hardwareClass(info: MediaCodecInfo): String =
        if (Build.VERSION.SDK_INT >= 29) when {
            info.isHardwareAccelerated -> "hardware"
            info.isSoftwareOnly -> "software"
            else -> "unclassified"
        } else "unclassified"

    /** 视频能力给出宽高的各自上限；上限未必能同时达到，作为适配参考足够。 */
    private fun maxVideoSize(info: MediaCodecInfo): String? {
        val video = info.supportedTypes.firstOrNull { it.startsWith("video/") } ?: return null
        val capabilities = runCatching { info.getCapabilitiesForType(video).videoCapabilities }
            .getOrNull() ?: return null
        return "${capabilities.supportedWidths.upper}x${capabilities.supportedHeights.upper}"
    }

    private fun systemInputDevices(): List<InputDevice> = runCatching {
        InputDevice.getDeviceIds().toList().mapNotNull { InputDevice.getDevice(it) }
    }.getOrDefault(emptyList())

    private fun systemCodecs(): List<MediaCodecInfo> = runCatching {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.toList()
    }.getOrDefault(emptyList())
}
