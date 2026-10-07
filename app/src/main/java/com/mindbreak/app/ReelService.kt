package com.mindbreak.app

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
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
    fun setDebug(c: Context, s: String) = p(c).edit().putString("debug", s).apply()
    fun debug(c: Context) = p(c).getString("debug", "") ?: ""
}

class ReelService : AccessibilityService() {
    private var lastTick = 0L
    private var lastCount = 0L

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

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        val ev = e ?: return
        val app = pkgs[ev.packageName?.toString()] ?: return
        val root = rootInActiveWindow ?: return

        val inReels = reelIds[app].orEmpty().any {
            root.findAccessibilityNodeInfosByViewId(it).isNotEmpty()
        }

        if (!inReels) {
            lastTick = 0L
            if (ev.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) saveIds(app, root)
            return
        }

        val now = SystemClock.elapsedRealtime()
        if (lastTick != 0L && now - lastTick < 5000) Store.addMs(this, app, now - lastTick)
        lastTick = now

        if (ev.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED && now - lastCount > 800) {
            lastCount = now
            Store.addReel(this, app)
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

    override fun onInterrupt() {}
}
