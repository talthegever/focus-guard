package com.focusguard.app

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.util.Xml
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File

/**
 * Debug builds only. On the broadcast [ACTION_DUMP] the service writes the view tree of
 * the active window to files/dump.xml, which tools/dump_screen.py pulls over adb.
 *
 * The dump holds view ids, classes, and state flags. It never holds text or content
 * descriptions, so nothing the user can read on screen leaves the phone.
 */
object DebugHooks {
    const val ACTION_DUMP = "com.focusguard.app.DUMP"
    private const val MAX_NODES = 20_000
    private var receiver: BroadcastReceiver? = null

    fun attach(service: AccessibilityService) {
        if (receiver != null) return
        val created = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = dump(service)
        }
        val filter = IntentFilter(ACTION_DUMP)
        // The broadcast comes from the adb shell user, so the receiver must be exported.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            service.registerReceiver(created, filter, Context.RECEIVER_EXPORTED)
        } else {
            service.registerReceiver(created, filter)
        }
        receiver = created
    }

    fun detach(service: AccessibilityService) {
        receiver?.let(service::unregisterReceiver)
        receiver = null
    }

    private fun dump(service: AccessibilityService) {
        val root = service.rootInActiveWindow
        if (root == null) {
            Log.w(BlockerService.TAG, "dump: no active window")
            return
        }
        val appPackage = root.packageName?.toString().orEmpty()
        val appVersion = runCatching {
            service.packageManager.getPackageInfo(appPackage, 0).versionName
        }.getOrNull().orEmpty()

        // Write to a temporary file and rename, so the puller never reads a partial dump.
        val target = File(service.filesDir, "dump.xml")
        val temporary = File(service.filesDir, "dump.xml.tmp")
        var written = 0
        temporary.outputStream().use { out ->
            val xml = Xml.newSerializer()
            xml.setOutput(out, "UTF-8")
            xml.startDocument("UTF-8", true)
            xml.startTag(null, "screen")
            xml.attribute(null, "package", appPackage)
            xml.attribute(null, "appVersion", appVersion)
            xml.attribute(null, "source", "device")

            fun write(node: AccessibilityNodeInfo) {
                if (written >= MAX_NODES) return
                written++
                val bounds = Rect().also(node::getBoundsInScreen)
                xml.startTag(null, "node")
                xml.attribute(null, "id", node.viewIdResourceName.orEmpty())
                xml.attribute(null, "class", node.className?.toString().orEmpty())
                xml.attribute(null, "selected", node.isSelected.toString())
                xml.attribute(null, "visible", node.isVisibleToUser.toString())
                xml.attribute(null, "clickable", node.isClickable.toString())
                xml.attribute(null, "bounds", bounds.toShortString())
                for (i in 0 until node.childCount) node.getChild(i)?.let(::write)
                xml.endTag(null, "node")
            }
            write(root)

            xml.endTag(null, "screen")
            xml.endDocument()
        }
        temporary.renameTo(target)
        Log.i(BlockerService.TAG, "dump: package=$appPackage version=$appVersion nodes=$written")
    }
}
