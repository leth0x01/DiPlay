package com.ecarx.xui.adaptapi.binder

interface IConnectable {
    interface IConnectWatcher {
        fun onConnected()
        fun onDisConnected()
    }
    fun connect()
    fun disconnect()
    fun registerConnectWatcher(watcher: IConnectWatcher)
    fun unregisterConnectWatcher()
}
