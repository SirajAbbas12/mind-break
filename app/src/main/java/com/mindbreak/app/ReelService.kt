package com.mindbreak.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var info: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (20 * resources.displayMetrics.density).toInt()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        val btn = Button(this).apply {
            text = "Accessibility ON karo"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        info = TextView(this).apply { textSize = 18f; setPadding(0, pad, 0, 0) }
        box.addView(btn)
        box.addView(info)
        setContentView(ScrollView(this).apply { addView(box) })
    }

    override fun onResume() {
        super.onResume()
        refresh()
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
        info.text = "Mind score: $score / 100\n$status\n\n" +
            "Aaj ki reels: ${igN + ytN}\n" +
            "Instagram: $igN\nYouTube Shorts: $ytN\n" +
            "Reels time: ${"%.1f".format(mins)} min\n\n" +
            "(Ye wellbeing estimate hai, medical salah nahi.)\n\n" +
            "Debug: ${Store.debug(this)}"
    }
}
