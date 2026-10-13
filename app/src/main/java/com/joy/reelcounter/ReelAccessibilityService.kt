package com.joy.reelcounter

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import java.time.LocalDate
import java.util.Locale

/**
 * Best-effort Reel counter. Instagram and Facebook Lite do not expose a public
 * API for Reel transitions, so detection depends on accessibility UI labels.
 */
class ReelAccessibilityService : AccessibilityService() {
    private var windowManager: WindowManager? = null
    private var bubble: TextView? = null
    private var currentPlatform: String? = null
    private var lastCountedScrollAt = 0L
    private var lastRenderedLabel = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString()
        val platform = platformForPackage(pkg)

        // Only show the floating counter while Instagram or Facebook Lite is foreground.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (platform != null) {
                currentPlatform = platform
                ensureBubble()
                renderCount(platform)
            } else if (pkg != null && pkg != packageName && pkg != "android") {
                currentPlatform = null
                removeBubble()
            }
            return
        }

        // Touch interaction boundary events are not reliable across all app versions.
        // Count on a qualifying vertical scroll instead.
        if (platform == null) return
        currentPlatform = platform
        ensureBubble()
        renderCount(platform)

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED && isVerticalScroll(event)) {
            if (isLikelyReelsScreen()) {
                val now = android.os.SystemClock.elapsedRealtime()
                // Avoid duplicate accessibility scroll events for one swipe.
                if (now - lastCountedScrollAt >= 650L) {
                    increment(platform)
                    lastCountedScrollAt = now
                }
            }
        }
    }

    private fun isVerticalScroll(event: AccessibilityEvent): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= 28) {
            event.scrollDeltaY != 0 || event.scrollY != 0
        } else {
            event.scrollY > 0 || event.fromIndex >= 0 || event.toIndex >= 0
        }
    }

    /**
     * Accessibility labels vary by app version/localization. Accept common
     * Reel/video indicators plus the usual action controls, instead of requiring
     * the exact word "Reels" which is often absent from the accessibility tree.
     */
    private fun isLikelyReelsScreen(): Boolean {
        val root = rootInActiveWindow ?: return false
        val labels = ArrayList<String>(80)
        collectLabels(root, labels, 0)
        val normalized = labels.map { it.lowercase(Locale.ROOT).trim() }.filter { it.isNotBlank() }

        val hasLike = normalized.any { it == "like" || it.contains("like button") || it.contains("like reel") }
        val hasComment = normalized.any { it == "comment" || it.contains("comment button") || it.contains("comment on") }
        val hasShare = normalized.any { it == "share" || it.contains("share button") || it.contains("share reel") }
        val hasVideoContext = normalized.any {
            it.contains("reel") || it.contains("original audio") || it.contains("use audio") ||
                it.contains("audio") || it.contains("remix") || it.contains("short video") ||
                it.contains("video player") || it.contains("music")
        }
        // Like/Comment/Share also appear on normal feed posts, so they are never
        // sufficient by themselves. Require explicit Reel/video context to avoid
        // incrementing while the user scrolls Facebook Lite Home.
        val hasExplicitVideoContext = normalized.any {
            it.contains("reel") || it.contains("video") || it.contains("original audio") ||
                it.contains("use audio") || it.contains("remix") || it.contains("full-screen") ||
                it.contains("fullscreen") || it.contains("watching") || it.contains("audio")
        }
        return hasLike && hasComment && hasShare && hasExplicitVideoContext
    }

    private fun collectLabels(node: AccessibilityNodeInfo, out: MutableList<String>, depth: Int) {
        if (depth > 14 || out.size > 350) return
        node.text?.toString()?.let { out.add(it) }
        node.contentDescription?.toString()?.let { out.add(it) }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectLabels(child, out, depth + 1)
            child.recycle()
        }
    }

    private fun increment(platform: String) {
        val key = countKey(LocalDate.now(), platform)
        val prefs = getSharedPreferences("reel_counter", MODE_PRIVATE)
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
        renderCount(platform)
    }

    private fun platformForPackage(pkg: String?): String? = when (pkg) {
        "com.instagram.android" -> "Instagram"
        "com.facebook.lite" -> "Facebook Lite"
        else -> null
    }

    private fun ensureBubble() {
        if (!Settings.canDrawOverlays(this) || bubble != null) return
        val wm = windowManager ?: return
        val view = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(13), dp(9), dp(13), dp(9))
            background = GradientDrawable().apply {
                setColor(0xE6171717.toInt())
                cornerRadius = dp(28).toFloat()
                setStroke(dp(1), 0xFFD7FF3F.toInt())
            }
            elevation = dp(8).toFloat()
            contentDescription = "Reel Counter floating count"
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(12)
            y = dp(105)
        }
        try {
            wm.addView(view, params)
            bubble = view
        } catch (_: Exception) {
            bubble = null
        }
    }

    private fun renderCount(platform: String) {
        val view = bubble ?: return
        val count = getSharedPreferences("reel_counter", MODE_PRIVATE)
            .getInt(countKey(LocalDate.now(), platform), 0)
        val label = if (platform == "Instagram") "IG • $count" else "FB • $count"
        if (label != lastRenderedLabel) {
            view.text = label
            lastRenderedLabel = label
        }
    }

    private fun removeBubble() {
        val view = bubble ?: return
        try {
            if (view.parent != null) windowManager?.removeView(view)
        } catch (_: Exception) { }
        bubble = null
        lastRenderedLabel = ""
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        removeBubble()
        super.onDestroy()
    }
}
