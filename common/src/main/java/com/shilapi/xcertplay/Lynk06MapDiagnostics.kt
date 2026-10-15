package com.shilapi.xcertplay

/** 分别记录应用地图窗口与原车投影指令，不把其中任一项冒充物理仪表像素反馈。 */
internal object Lynk06MapDiagnostics {
    internal data class Snapshot(
        val revision: Long = 0,
        val navigationRequested: Boolean? = null,
        val adapterAvailable: Boolean? = null,
        val policyShowMap: Boolean? = null,
        val menuMode: Int? = null,
        val menuCallbackMode: Int? = null,
        val presentationOption: Int? = null,
        val presentationCallbackOption: Int? = null,
        val presentationShowing: Boolean = false,
        val windowMapVisible: Boolean? = null,
        val windowVisibility: Int? = null,
        val windowAlpha: Float? = null,
        val displayId: Int? = null,
        val surfaceValid: Boolean = false,
        val streamActive: Boolean = false,
        val factoryReset: Boolean? = null,
        val naviNotStarted: Boolean? = null,
        val ihuState: Int? = null,
        val lcdState: Int? = null,
        val nativeStartRequested: Boolean? = null,
        val signalConnected: Boolean? = null,
        val nativeRejected: Boolean? = null,
        val lastMcuProjectionRequest: Boolean? = null,
        val nativeCapture: String = "not-started",
        val nativeLogGap: Boolean = false,
        val lastNativeEvent: String? = null,
    ) {
        val appMapReady: Boolean get() = presentationShowing && windowMapVisible == true &&
            windowVisibility == 0 && windowAlpha == 1f && surfaceValid && streamActive

        fun summary(): String = "mapRevision=$revision nav=$navigationRequested adapter=$adapterAvailable policyMap=$policyShowMap " +
            "menu=$menuMode menuCallback=$menuCallbackMode option=$presentationOption optionCallback=$presentationCallbackOption " +
            "presentation=$presentationShowing mapWindow=$windowMapVisible visibility=$windowVisibility alpha=$windowAlpha " +
            "display=$displayId surface=$surfaceValid stream=$streamActive appMapReady=$appMapReady " +
            "factoryReset=$factoryReset naviNotStarted=$naviNotStarted ihu=$ihuState lcd=$lcdState " +
            "nativeStart=$nativeStartRequested signal=$signalConnected rejected=$nativeRejected " +
            "mcuRequest=$lastMcuProjectionRequest capture=$nativeCapture gap=$nativeLogGap"
    }

    private var state = Snapshot()
    private var linkOwner: Any? = null
    private var presentationOwner: java.lang.ref.WeakReference<Any>? = null
    private var nativeOwner: Any? = null
    private var nativeObservedNanos = 0L

    @Synchronized fun snapshot(): Snapshot = state
    @Synchronized fun context(): String = "mapRevision=${state.revision} appMapReady=${state.appMapReady} " +
        "mcuRequest=${state.lastMcuProjectionRequest}"

    private fun change(reason: String, log: (String) -> Unit, next: Snapshot) {
        if (next == state) return
        state = next.copy(revision = state.revision + 1)
        log("仪表显示诊断：reason=$reason ${state.summary()}")
    }

    @Synchronized fun beginLink(owner: Any, log: (String) -> Unit) {
        linkOwner = owner
        change("link-start", log, state.copy(navigationRequested = false, adapterAvailable = false,
            policyShowMap = false, menuMode = null, menuCallbackMode = null,
            presentationOption = null, presentationCallbackOption = null))
    }

    @Synchronized fun navigation(owner: Any, active: Boolean, log: (String) -> Unit) {
        if (linkOwner === owner) change("navigation-request", log, state.copy(navigationRequested = active))
    }

    @Synchronized fun link(owner: Any, available: Boolean, showMap: Boolean, mode: Int?, option: Int?, log: (String) -> Unit) {
        if (linkOwner === owner) change("adapter-state", log, state.copy(adapterAvailable = available,
            policyShowMap = showMap, menuMode = mode, presentationOption = option))
    }

    @Synchronized fun callback(owner: Any, name: String, value: Int?, log: (String) -> Unit) {
        if (linkOwner !== owner) return
        val next = when (name) {
            "onChangeNaviMode" -> state.copy(menuCallbackMode = value)
            "onShowPresentationOptionChanged" -> state.copy(presentationCallbackOption = value)
            else -> state
        }
        // 回调即使值相同也保留，便于与后续 getter 对比；不根据未经核对的值切换地图。
        log("仪表显示回调：callback=$name value=$value ${context()}")
        change("callback-$name", log, next)
    }

    @Synchronized fun endLink(owner: Any, log: (String) -> Unit) {
        if (linkOwner !== owner) return
        change("link-close", log, state.copy(navigationRequested = false, adapterAvailable = false, policyShowMap = false))
        linkOwner = null
    }

    @Synchronized fun presentation(owner: Any, showing: Boolean, visible: Boolean, visibility: Int?, alpha: Float?,
        display: Int, surface: Boolean, stream: Boolean, reason: String, log: (String) -> Unit) {
        if (reason == "create" || reason == "start") presentationOwner = java.lang.ref.WeakReference(owner)
        if (presentationOwner?.get() !== owner) return
        change(reason, log, state.copy(presentationShowing = showing, windowMapVisible = visible,
            windowVisibility = visibility, windowAlpha = alpha, displayId = display, surfaceValid = surface, streamActive = stream))
    }

    @Synchronized fun capture(owner: Any, status: String, log: (String) -> Unit) {
        if (status == "starting") {
            nativeOwner = owner
            nativeObservedNanos = 0
            change("native-capture-start", log, state.copy(factoryReset = null, naviNotStarted = null,
                ihuState = null, lcdState = null, nativeStartRequested = null, signalConnected = null,
                nativeRejected = null, lastMcuProjectionRequest = null, lastNativeEvent = null,
                nativeCapture = status, nativeLogGap = false))
        } else if (nativeOwner === owner) {
            change("native-capture-$status", log, state.copy(nativeCapture = status,
                nativeLogGap = state.nativeLogGap || status != "running"))
        }
    }

    /** 只接受已核对的 Power 服务日志，不把应用转述或其他标签当作原车反馈。 */
    @Synchronized fun nativeLine(owner: Any, line: String, log: (String) -> Unit) {
        if (nativeOwner !== owner) return
        val match = POWER.matchEntire(line) ?: return
        val body = match.groupValues[2]
        var next = state
        FLAGS.find(body)?.let { next = next.copy(factoryReset = it.groupValues[1].toBoolean(), naviNotStarted = it.groupValues[2].toBoolean()) }
        POWER_STATE.find(body)?.let { next = next.copy(ihuState = it.groupValues[1].toIntOrNull(), lcdState = it.groupValues[2].toIntOrNull()) }
        START.find(body)?.let { next = next.copy(nativeStartRequested = it.groupValues[1].toBoolean(),
            signalConnected = it.groupValues[2].toBoolean(), nativeRejected = body.endsWith("not enable Screen Projection.")) }
        MCU.matchEntire(body)?.let { next = next.copy(lastMcuProjectionRequest = it.groupValues[1] == "open") }
        if (next == state) {
            // 相同有效事件仍更新时间，其他 Power 日志不能刷新投影证据时间。
            if (!FLAGS.containsMatchIn(body) && !POWER_STATE.containsMatchIn(body) &&
                !START.containsMatchIn(body) && !MCU.matches(body)) return
        }
        nativeObservedNanos = System.nanoTime()
        if (next != state) change("native-projection", log, next.copy(lastNativeEvent = match.groupValues[1]))
        else state = state.copy(lastNativeEvent = match.groupValues[1])
    }

    @Synchronized fun report(): String = state.summary() + "\nlastNativeEvent=${state.lastNativeEvent} " +
        "nativeObservedAgoMs=${if (nativeObservedNanos == 0L) null else (System.nanoTime() - nativeObservedNanos) / 1_000_000} " +
        "physicalVisibility=unknown（记录应用窗口与原车投影指令，未取得仪表像素回执）"

    @Synchronized internal fun resetForTest() {
        state = Snapshot(); linkOwner = null; presentationOwner = null; nativeOwner = null; nativeObservedNanos = 0
    }

    private val POWER = Regex("^(\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s+\\d+\\s+\\d+\\s+[VDIWEF]\\s+(?:ECarXPowerManagerService:\\s+<ECarXScreenProjectionService>|ECarXScreenProjectionService:\\s*)(.*)$")
    private val FLAGS = Regex("^isEnableScreenProjection isFactoryReset=(true|false) isNaviNotStarted=(true|false)$")
    private val POWER_STATE = Regex("^isEnableScreenProjection ihuState=(-?\\d+) lcdState=(-?\\d+)$")
    private val START = Regex("^startScreenProjection start=(true|false) mIsECarXSingalConnect=(true|false)(?: not enable Screen Projection\\.)?$")
    private val MCU = Regex("^sendBytesToMcuByPropID INFO_ID_MMI_NAVSYNCDISPLAY (open|close)$")
}
