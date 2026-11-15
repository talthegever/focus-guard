package com.focusguard.app

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/** Shows whether the blocker is running and lets each rule be switched on or off. */
class MainActivity : Activity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        status = TextView(this).apply { textSize = 18f }
        page.addView(status)

        page.addView(Button(this).apply {
            text = getString(R.string.open_accessibility_settings)
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })

        page.addView(TextView(this).apply {
            text = getString(R.string.rules_heading)
            setPadding(0, pad, 0, pad / 2)
        })
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
        status.text = getString(
            if (isServiceEnabled()) R.string.status_running else R.string.status_stopped,
        )
    }

    private fun isServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        val mine = ComponentName(this, BlockerService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == mine }
    }
}
