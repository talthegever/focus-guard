package com.focusguard.app

import android.accessibilityservice.AccessibilityService

/** Release builds carry no screen-dump hook. The debug source set has the real one. */
object DebugHooks {
    fun attach(service: AccessibilityService) = Unit
    fun detach(service: AccessibilityService) = Unit
}
