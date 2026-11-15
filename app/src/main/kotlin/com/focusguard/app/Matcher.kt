package com.focusguard.app

/**
 * What the matcher needs to know about the screen in front of the user.
 * The service backs this with the live accessibility tree; tests back it with a saved dump.
 */
interface Screen {
    /** Package of the app that owns the screen, e.g. "com.instagram.android". */
    val appPackage: String

    /** Every view whose resource id entry name is [viewId] (the part after ":id/"). */
    fun views(viewId: String): List<ViewState>
}

data class ViewState(val selected: Boolean, val visible: Boolean)

/** One view whose presence means "the blocked screen is open". */
data class Signal(val viewId: String, val mustBeSelected: Boolean = false)

sealed interface Reaction {
    /** Press the system Back button. */
    data object Back : Reaction

    /** Tap the view with this id; falls back to Back when it cannot be tapped. */
    data class Tap(val viewId: String) : Reaction
}

data class Rule(
    /** Stable identifier, used for the on/off preference and in logs. */
    val key: String,
    /** Label shown in the app. */
    val title: String,
    val appPackage: String,
    /** The rule fires when any one of these is on screen. */
    val anyOf: List<Signal>,
    val reaction: Reaction,
)

data class Match(val rule: Rule, val signal: Signal)

object Matcher {
    /** The first rule in [rules] that recognises [screen], or null when nothing is blocked. */
    fun firstMatch(screen: Screen, rules: List<Rule>): Match? {
        for (rule in rules) {
            if (rule.appPackage != screen.appPackage) continue
            for (signal in rule.anyOf) {
                val onScreen = screen.views(signal.viewId).any { view ->
                    view.visible && (!signal.mustBeSelected || view.selected)
                }
                if (onScreen) return Match(rule, signal)
            }
        }
        return null
    }
}
