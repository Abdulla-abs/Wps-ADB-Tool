package `fun`.abbas.wps_adb.data

object AppLogcatMessages {
    fun systemId(tabId: String, kind: String): String =
        "logcat_${tabId}_${kind}_${System.nanoTime()}"

    fun waitingForProcess(packageName: String): String =
        "正在等待 $packageName，请在设备上打开应用…"

    fun attachedToProcess(pid: Int, packageName: String): String =
        "已连接进程 $pid（$packageName），开始监听日志"
}
