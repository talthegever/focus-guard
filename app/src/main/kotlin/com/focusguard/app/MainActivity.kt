package com.focusguard.app

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/**
 * Setup checklist plus one on/off switch per rule.
 *
 * Android does not let an app grant itself accessibility access or exempt itself from
 * battery limits, so each step opens the exact settings page where the user does it.
 */
class MainActivity : Activity() {
    /** A setup step whose state can be read back; unknown-state steps are not tracked. */
    private class Step(val title: TextView, val label: String, val isDone: () -> Boolean)

    private val steps = mutableListOf<Step>()
    private var pad = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pad = (16 * resources.displayMetrics.density).toInt()
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        page.addView(heading(R.string.setup_heading))
        addStep(
            page,
            label = getString(R.string.step_accessibility),
            hint = getString(R.string.step_accessibility_hint),
            isDone = ::isServiceEnabled,
            getString(R.string.action_turn_on) to ::openAccessibilitySettings,
            getString(R.string.action_allow_restricted) to ::openAppInfo,
        )
        addStep(
            page,
            label = getString(R.string.step_battery),
            hint = getString(R.string.step_battery_hint),
            isDone = ::isBatteryUnrestricted,
            getString(R.string.action_allow) to ::requestBatteryExemption,
        )
        if (isXiaomiFamily()) {
            // HyperOS/MIUI adds two limits of its own, and neither can be read back.
            addStep(
                page,
                label = getString(R.string.step_autostart),
                hint = getString(R.string.step_autostart_hint),
                isDone = null,
                getString(R.string.action_open) to ::openXiaomiAutostart,
            )
            addStep(
                page,
                label = getString(R.string.step_xiaomi_battery),
                hint = getString(R.string.step_xiaomi_battery_hint),
                isDone = null,
                getString(R.string.action_open) to ::openXiaomiBatterySaver,
            )
        }

        page.addView(heading(R.string.rules_heading))
        for (rule in Rules.all) {
            page.addView(Switch(this).apply {
                text = rule.title
                textSize = 16f
                setPadding(0, pad / 2, 0, pad / 2)
                isChecked = BlockerService.isRuleEnabled(this@MainActivity, rule.key)
                setOnCheckedChangeListener { _, checked ->
                    BlockerService.setRuleEnabled(this@MainActivity, rule.key, checked)
                }
            })
        }

        setContentView(ScrollView(this).apply { addView(page) })
    }

    override fun onResume() {
        super.onResume()
        for (step in steps) {
            step.title.text = (if (step.isDone()) DONE else TODO) + step.label
        }
    }

    private fun heading(textRes: Int) = TextView(this).apply {
        text = getString(textRes)
        textSize = 20f
        setPadding(0, pad, 0, pad / 2)
    }

    private fun addStep(
        page: LinearLayout,
        label: String,
        hint: String,
        isDone: (() -> Boolean)?,
        vararg actions: Pair<String, () -> Unit>,
    ) {
        val title = TextView(this).apply {
            textSize = 16f
            text = UNKNOWN + label
            setPadding(0, pad / 2, 0, 0)
        }
        page.addView(title)
        page.addView(TextView(this).apply { text = hint })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for ((caption, onClick) in actions) {
            row.addView(Button(this).apply {
                text = caption
                setOnClickListener { onClick() }
            })
        }
        page.addView(row)
        if (isDone != null) steps += Step(title, label, isDone)
    }

    // --- state checks ---

    private fun isServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == serviceComponent() }
    }

    private fun isBatteryUnrestricted(): Boolean =
        getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

    private fun isXiaomiFamily(): Boolean =
        Build.MANUFACTURER.lowercase() in setOf("xiaomi", "poco", "redmi")

    private fun serviceComponent() = ComponentName(this, BlockerService::class.java)

    // --- settings pages ---

    /** Starts the first intent the device can handle, so vendor pages can fall back. */
    private fun openFirstAvailable(vararg intents: Intent) {
        for (intent in intents) {
            try {
                startActivity(intent)
                return
            } catch (_: Exception) {
                // Not present on this device or not allowed; try the next one.
            }
        }
        Toast.makeText(this, R.string.settings_page_missing, Toast.LENGTH_LONG).show()
    }

    private fun appInfoIntent() =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))

    private fun openAppInfo() = openFirstAvailable(appInfoIntent())

    private fun openAccessibilitySettings() {
        val component = serviceComponent().flattenToString()
        openFirstAvailable(
            // Focus Guard's own page inside Accessibility settings.
            Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
                .putExtra(Intent.EXTRA_COMPONENT_NAME, component),
            // The full list, scrolled to and highlighting Focus Guard where supported.
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .putExtra(":settings:fragment_args_key", component)
                .putExtra(
                    ":settings:show_fragment_args",
                    Bundle().apply { putString(":settings:fragment_args_key", component) },
                ),
        )
    }

    private fun requestBatteryExemption() = openFirstAvailable(
        // System dialog with Allow / Deny; needs REQUEST_IGNORE_BATTERY_OPTIMIZATIONS.
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")),
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        appInfoIntent(),
    )

    private fun openXiaomiAutostart() = openFirstAvailable(
        Intent().setComponent(
            ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity",
            ),
        ),
        Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT),
        appInfoIntent(),
    )

    private fun openXiaomiBatterySaver() = openFirstAvailable(
        Intent().setComponent(
            ComponentName(
                "com.miui.powerkeeper",
                "com.miui.powerkeeper.ui.HiddenAppsConfigActivity",
            ),
        ).putExtra("package_name", packageName)
            .putExtra("package_label", getString(R.string.app_name)),
        appInfoIntent(),
    )

    private companion object {
        const val DONE = "✓  "
        const val TODO = "✗  "
        const val UNKNOWN = "•  "
    }
}
