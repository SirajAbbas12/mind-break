package com.mindbreak.app

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Store {
    private fun p(c: Context) = c.getSharedPreferences("mb", Context.MODE_PRIVATE)
    private fun day() = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    fun addReel(c: Context, app: String) {
        val k = "${day()}_${app}_n"
        p(c).edit().putInt(k, p(c).getInt(k, 0) + 1).apply()
    }

    fun addMs(c: Context, app: String, ms: Long) {
        val k = "${day()}_${app}_ms"
        p(c).edit().putLong(k, p(c).getLong(k, 0L) + ms).apply()
    }

    fun reels(c: Context, app: String) = p(c).getInt("${day()}_${app}_n", 0)
    fun ms(c: Context, app: String) = p(c).getLong("${day()}_${app}_ms", 0L)
    fun total(c: Context) = reels(c, "instagram") + reels(c, "youtube")
    fun limit(c: Context) = p(c).getInt("limit", 100)
    fun setLimit(c: Context, v: Int) = p(c).edit().putInt("limit", v).apply()
    fun setDebug(c: Context, s: String) = p(c).edit().putString("debug", s).apply()
    fun debug(c: Context) = p(c).getString("debug", "") ?: ""
}

class ReelService : AccessibilityService() {
    private var lastTick = 0L
    private var lastCount = 0L
    private var bubble: TextView? = null
    private var popup: View? = null

    private val pkgs = mapOf(
        "com.instagram.android" to "instagram",
        "com.google.android.youtube" to "youtube"
    )

    // Ye IDs app update ke saath badal sakti hain
    private val reelIds = mapOf(
        "instagram" to listOf("com.instagram.android:id/clips_viewer_view_pager"),
        "youtube" to listOf(
            "com.google.android.youtube:id/reel_recycler",
            "com.google.android.youtube:id/reel_player_page_container"
        )
    )

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun wm() = getSystemService(Context.WINDOW_SERVICE) as WindowManager

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        val ev = e ?: return
        val pkg = ev.packageName?.toString() ?: return
        if (pkg == packageName) return
        val app = pkgs[pkg]
        if (app == null) {
            lastTick = 0L
            hideBubble()
            return
        }
        val root = rootInActiveWindow ?: return

        val inReels = reelIds[app].orEmpty().any {
            root.findAccessibilityNodeInfosByViewId(it).isNotEmpty()
        }

        if (!inReels) {
            lastTick = 0L
            hideBubble()
            if (ev.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) saveIds(app, root)
            return
        }

        val now = SystemClock.elapsedRealtime()
        if (lastTick != 0L && now - lastTick < 5000) Store.addMs(this, app, now - lastTick)
        lastTick = now

        if (ev.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED && now - lastCount > 800) {
            lastCount = now
            Store.addReel(this, app)
            val total = Store.total(this)
            val limit = Store.limit(this)
            if (total >= limit && (total - limit) % 50 == 0) showPopup(total)
        }
        showBubble()
    }

    private fun mood(total: Int, limit: Int): Pair<String, Int> {
        val r = total.toDouble() / limit
        return when {
            r < 0.5 -> "😀" to 0xFF2E7D32.toInt()
            r < 1.0 -> "🙂" to 0xFFB8860B.toInt()
            r < 1.1 -> "😟" to 0xFFE65100.toInt()
            r < 1.5 -> "😫" to 0xFFC62828.toInt()
            else -> "🥴" to 0xFF7F0000.toInt()
        }
    }

    private fun showBubble() {
        try {
            val (emoji, color) = mood(Store.total(this), Store.limit(this))
            var b = bubble
            if (b == null) {
                b = TextView(this).apply {
                    textSize = 20f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    setPadding(dp(16), dp(8), dp(16), dp(8))
                    background = GradientDrawable().apply { cornerRadius = dp(30).toFloat() }
                }
                val lp = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    y = dp(48)
                }
                wm().addView(b, lp)
                bubble = b
            }
            b.text = "$emoji  ${Store.total(this)}"
            (b.background as GradientDrawable).setColor(color)
        } catch (t: Throwable) {
            Store.setDebug(this, "bubble error: " + t.message)
        }
    }

    private fun hideBubble() {
        try {
            bubble?.let { wm().removeView(it) }
        } catch (t: Throwable) {
        }
        bubble = null
    }

    private fun showPopup(total: Int) {
        try {
            if (popup != null) return
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(24), dp(24), dp(24), dp(24))
                background = GradientDrawable().apply {
                    cornerRadius = dp(24).toFloat()
                    setColor(0xFF1C1C1C.toInt())
                }
            }
            card.addView(TextView(this).apply {
                text = "Aaj $total reels ho gayi 😟\nDimaag ko 2 minute ka break do?"
                textSize = 18f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            })
            card.addView(Button(this).apply {
                text = "Theek hai"
                setOnClickListener {
                    try { wm().removeView(card) } catch (t: Throwable) {}
                    popup = null
                }
            })
            val lp = WindowManager.LayoutParams(
                (resources.displayMetrics.widthPixels * 0.85).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.CENTER }
            wm().addView(card, lp)
            popup = card
        } catch (t: Throwable) {
            Store.setDebug(this, "popup error: " + t.message)
        }
    }

    private fun saveIds(app: String, root: AccessibilityNodeInfo) {
        val ids = linkedSetOf<String>()
        collect(root, ids, 0)
        Store.setDebug(this, app + ": " + ids.joinToString(", "))
    }

    private fun collect(n: AccessibilityNodeInfo?, out: MutableSet<String>, depth: Int) {
        if (n == null || depth > 12 || out.size > 40) return
        n.viewIdResourceName?.let { out.add(it.substringAfter(":id/")) }
        for (i in 0 until n.childCount) collect(n.getChild(i), out, depth + 1)
    }

    override fun onDestroy() {
        hideBubble()
        try { popup?.let { wm().removeView(it) } } catch (t: Throwable) {}
        super.onDestroy()
    }

    override fun onInterrupt() {}
}
