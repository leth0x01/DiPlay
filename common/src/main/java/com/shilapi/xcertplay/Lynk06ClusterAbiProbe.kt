package com.shilapi.xcertplay

/** 只读取运行时类签名；实车日志才是固件 ABI 证据，测试桩仅验证采集流程。 */
internal object Lynk06ClusterAbiProbe {
    fun snapshot(): List<String> = buildList {
        add("领克仪表 ABI：开始")
        for (name in classNames) {
            try {
                val type = Class.forName(name, false, Lynk06ClusterAbiProbe::class.java.classLoader)
                add("领克仪表 ABI：class ${type.name}")
                type.declaredMethods.map { it.toGenericString() }.sorted().forEach {
                    add("领克仪表 ABI：$it")
                }
            } catch (error: ReflectiveOperationException) {
                add("领克仪表 ABI：$name 读取失败 ${error.javaClass.simpleName}")
            } catch (error: LinkageError) {
                add("领克仪表 ABI：$name 读取失败 ${error.javaClass.simpleName}")
            }
        }
        add("领克仪表 ABI：结束")
    }

    private val classNames = listOf(
        "com.ecarx.xui.adaptapi.diminteraction.DimInteraction",
        "com.ecarx.xui.adaptapi.binder.IConnectable",
        "com.ecarx.xui.adaptapi.binder.IConnectable\$IConnectWatcher",
        "com.ecarx.xui.adaptapi.diminteraction.IDimMenuInteraction",
        "com.ecarx.xui.adaptapi.diminteraction.IDimMenuInteraction\$IDimMenuInteractionCallback",
        "com.ecarx.xui.adaptapi.diminteraction.INaviInteraction",
    )
}
