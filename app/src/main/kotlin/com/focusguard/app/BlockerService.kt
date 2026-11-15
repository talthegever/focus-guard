package com.focusguard.app

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Watches the apps listed in res/xml/blocker_service.xml and leaves any screen that a
 * rule in [Rules] recognises.
 */
class BlockerService : AccessibilityService() {
    private val lastReactionAt = mutableMapOf<String, Long>()

    override fun onServiceConnected() {
        Log.i(TAG, "service connected; rules=${Rules.all.joinToString { it.key }}")
        DebugHooks.attach(this)
    }

    override fun onDestroy() {
        DebugHooks.detach(this)
        super.onDestroy()
    }

    override fun onInterrupt() = Unit

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val type = event?.eventType ?: return
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return

        val enabledRules = Rules.all.filter { isRuleEnabled(this, it.key) }
        if (enabledRules.isEmpty()) return

        val root = rootInActiveWindow ?: return
        val screen = LiveScreen(root)
        val match = Matcher.firstMatch(screen, enabledRules) ?: return
        if (!cooldownElapsed(match.rule.key)) return

        val done = react(screen, match.rule.reaction)
        Log.i(TAG, "blocked rule=${match.rule.key} signal=${match.signal.viewId} reaction=$done")
    }

    /** Stops one screen from triggering a burst of Back presses while it animates away. */
    private fun cooldownElapsed(ruleKey: String): Boolean {
        val now = SystemClock.uptimeMillis()
        if (now - (lastReactionAt[ruleKey] ?: 0L) < COOLDOWN_MS) return false
        lastReactionAt[ruleKey] = now
        return true
    }

    private fun react(screen: LiveScreen, reaction: Reaction): String = when (reaction) {
        Reaction.Back -> back()
        is Reaction.Tap -> if (screen.tap(reaction.viewId)) "tap:${reaction.viewId}" else back()
    }

    private fun back(): String =
        if (performGlobalAction(GLOBAL_ACTION_BACK)) "back" else "back-failed"

    /** [Screen] backed by the live accessibility tree of the active window. */
    private class LiveScreen(private val root: AccessibilityNodeInfo) : Screen {
        override val appPackage: String = root.packageName?.toString().orEmpty()

        override fun views(viewId: String): List<ViewState> =
            nodes(viewId).map { ViewState(selected = it.isSelected, visible = it.isVisibleToUser) }

        fun tap(viewId: String): Boolean {
            var node: AccessibilityNodeInfo? = nodes(viewId).firstOrNull { it.isVisibleToUser }
            // The id often sits on a child of the view that actually handles clicks.
            while (node != null && !node.isClickable) node = node.parent
            return node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        }

        private fun nodes(viewId: String): List<AccessibilityNodeInfo> =
            root.findAccessibilityNodeInfosByViewId("$appPackage:id/$viewId").orEmpty()
    }

    companion object {
        const val TAG = "FocusGuard"
        private const val COOLDOWN_MS = 1500L
        private const val PREFS = "rules"

        fun isRuleEnabled(context: Context, ruleKey: String): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(ruleKey, true)

        fun setRuleEnabled(context: Context, ruleKey: String, enabled: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(ruleKey, enabled).apply()
        }
    }
}
