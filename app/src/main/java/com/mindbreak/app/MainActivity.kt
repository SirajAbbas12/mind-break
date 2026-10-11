package com.mindbreak.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private var info: TextView? = null
    private var limitBox: EditText? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            build()
        } catch (t: Throwable) {
            setContentView(TextView(this).apply {
                text = "Error:\n" + t.stackTraceToString().take(1500)
                setPadding(40, 40, 40, 40)
            })
        }
    }

    private fun build() {
        val pad = (20 * resources.displayMetrics.density).toInt()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        box.addView(Button(this).apply {
            text = "Accessibility ON karo"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        })
        box.addView(TextView(this).apply { text = "Roz ki reel limit:"; setPadding(0, pad, 0, 0) })
        limitBox = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(Store.limit(this@MainActivity).toString())
        }
        box.addView(limitBox)
        box.addView(Button(this).apply {
            text = "Limit save karo"
            setOnClickListener {
                val v = limitBox?.text.toString().toIntOrNull()
                if (v != null && v > 0) Store.setLimit(this@MainActivity, v)
                refresh()
            }
        })
        info = TextView(this).apply { textSize = 18f; setPadding(0, pad, 0, 0) }
        box.addView(info)
        setContentView(ScrollView(this).apply { addView(box) })
    }

    override fun onResume() {
        super.onResume()
        try { refresh() } catch (t: Throwable) { info?.text = "Error: " + t.message }
    }

    private fun refresh() {
        val igN = Store.reels(this, "instagram")
        val ytN = Store.reels(this, "youtube")
        val totalMs = Store.ms(this, "instagram") + Store.ms(this, "youtube")
        val mins = totalMs / 60000.0
        val penalty = (igN + ytN) * 0.15 + mins * 0.6
        val score = (100 - penalty).toInt().coerceIn(0, 100)
        val status = when {
            score >= 70 -> "🟢 Healthy"
            score >= 40 -> "🟡 Stress zone"
            else -> "🔴 Overload"
        }
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        val on = if (enabled.contains(packageName)) "ON ✅" else "OFF ❌"
        info?.text = "Service: $on\nLimit: ${Store.limit(this)}\n\n" +
            "Mind score: $score / 100\n$status\n\n" +
            "Aaj ki reels: ${igN + ytN}\n" +
            "Instagram: $igN\nYouTube Shorts: $ytN\n" +
            "Reels time: ${"%.1f".format(mins)} min\n\n" +
            "(Ye wellbeing estimate hai, medical salah nahi.)\n\n" +
            "Debug: ${Store.debug(this)}"
    }
}

