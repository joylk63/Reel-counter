package com.joy.reelcounter

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import java.time.LocalDate
import java.util.Locale

/**
 * Best-effort counter using accessibility scroll events.
 *
 * Instagram and Facebook Lite do not expose a supported API that identifies
 * every Reel transition. This service deliberately counts only distinct
 * vertical scroll events and debounces duplicate events. It cannot guarantee
 * an exact Reel count on every app version.
 */
class ReelAccessibilityService : AccessibilityService() {
    private var windowManager: WindowManager? = null
    private var bubble: TextView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var currentPlatform: String? = null
    private var lastCountAt = 0L
    private var lastScrollY = Int.MIN_VALUE
    private var lastFromIndex = Int.MIN_VALUE
    private var lastToIndex = Int.MIN_VALUE
    private var lastCountedSignature = ""
    private var lastCountedAt = 0L
    private var lastRenderedLabel = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString()

        // Only remove the bubble on a real window transition away from the
        // target app. Content events from system UI can otherwise cause flicker.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val platformForWindow = platformForPackage(pkg)
            if (platformForWindow == null) {
                currentPlatform = null
                removeBubble()
                return
            }
        }

        val platform = platformForPackage(pkg) ?: return
        if (!Settings.canDrawOverlays(this)) {
            removeBubble()
            return
        }

        if (currentPlatform != platform) {
            currentPlatform = platform
            lastScrollY = Int.MIN_VALUE
            lastFromIndex = Int.MIN_VALUE
            lastToIndex = Int.MIN_VALUE
            lastCountedSignature = ""
            lastCountedAt = 0L
            ensureBubble(platform)
            renderCount(platform)
        } else {
            ensureBubble(platform)
        }

        if (event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) return

        // Ignore horizontal-only and non-scroll events. On API 28+, use
        // scroll delta where available to reduce false positives.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (event.scrollDeltaY == 0 && event.scrollY == lastScrollY) return
        }
        val from = event.fromIndex
        val to = event.toIndex
        val signature = "${event.className}:${event.scrollY}:${from}:${to}"
        val now = System.currentTimeMillis()

        // Suppress repeated accessibility notifications from the same swipe.
        if (signature == lastCountedSignature && now - lastCountedAt < 1800L) return
        if (now - lastCountAt < 1200L) {
            lastScrollY = event.scrollY
            lastFromIndex = from
            lastToIndex = to
            return
        }

        // If the event exposes list indices, require the index to advance.
        val hasIndices = from >= 0 && to >= 0
        if (hasIndices && from == lastFromIndex && to == lastToIndex) {
            lastScrollY = event.scrollY
            return
        }

        lastCountAt = now
        lastCountedAt = now
        lastCountedSignature = signature
        lastScrollY = event.scrollY
        lastFromIndex = from
        lastToIndex = to

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

    private fun ensureBubble(platform: String) {
        if (!Settings.canDrawOverlays(this)) return
        val wm = windowManager ?: return
        var view = bubble
        if (view == null) {
            view = TextView(this).apply {
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
                bubbleParams = params
            } catch (_: Exception) {
                bubble = null
                bubbleParams = null
            }
        }
    }

    private fun renderCount(platform: String) {
        val view = bubble ?: return
        val count = getSharedPreferences("reel_counter", MODE_PRIVATE)
            .getInt(countKey(LocalDate.now(), platform), 0)
        val label = if (platform == "Instagram") "IG • $count" else "FB • $count"
        // Updating the text only when the value changes avoids relayout flicker.
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
        bubbleParams = null
        lastRenderedLabel = ""
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onInterrupt() { removeBubble() }

    override fun onDestroy() {
        removeBubble()
        super.onDestroy()
    }
}
