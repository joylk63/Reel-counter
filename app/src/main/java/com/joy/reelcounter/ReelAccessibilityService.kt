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
 * Best-effort Reel counter.
 * A platform does not expose a stable public Reel-transition API, so UI checks
 * are heuristic. The bubble is persistent after first creation to avoid flicker.
 */
class ReelAccessibilityService : AccessibilityService() {
    private var windowManager: WindowManager? = null
    private var bubble: TextView? = null
    private var currentPlatform: String? = null
    private var bubblePlatform: String? = null
    private var gestureSawQualifiedVerticalScroll = false
    private var lastCountedGestureAt = 0L
    private var lastRenderedLabel = ""
    private var lastRootSignature = ""
    private var lastRootSignatureAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString()
        val platform = platformForPackage(pkg)

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (platform != null) {
                currentPlatform = platform
                ensureBubble()
                renderCount(platform)
            }
            // Do not remove the overlay for transient/null/system window events.
            // This was the main source of the visible disappear/reappear flicker.
            return
        }

        if (platform == null) return
        currentPlatform = platform
        ensureBubble()
        renderCount(platform)

        when (event.eventType) {
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_START -> {
                gestureSawQualifiedVerticalScroll = false
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                val vertical = if (android.os.Build.VERSION.SDK_INT >= 28) {
                    event.scrollDeltaY != 0 || event.scrollY != 0
                } else {
                    event.scrollY >= 0
                }
                if (vertical && isLikelyReelsScreen()) {
                    gestureSawQualifiedVerticalScroll = true
                }
            }
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_END -> {
                if (gestureSawQualifiedVerticalScroll && isLikelyReelsScreen()) {
                    val now = android.os.SystemClock.elapsedRealtime()
                    // Ignore only near-simultaneous duplicate end events, not fast swipes.
                    if (now - lastCountedGestureAt > 180L) {
                        increment(platform)
                        lastCountedGestureAt = now
                    }
                }
                gestureSawQualifiedVerticalScroll = false
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // Keep bubble persistent; content changes only refresh the displayed count.
                renderCount(platform)
            }
        }
    }

    /**
     * Conservative UI heuristic: require a Reels-labelled surface and common
     * video action controls. Ordinary Home-feed scrolling should not qualify
     * merely because it emitted TYPE_VIEW_SCROLLED.
     */
    private fun isLikelyReelsScreen(): Boolean {
        val root = rootInActiveWindow ?: return false
        val labels = ArrayList<String>(80)
        collectLabels(root, labels, 0)
        val normalized = labels.map { it.lowercase(Locale.ROOT).trim() }.filter { it.isNotBlank() }
        val hasReelsLabel = normalized.any {
            it == "reels" || it == "reel" || it.contains("reels video") || it.contains("reel video")
        }
        val hasVideoActions = normalized.any { it == "like" || it.contains("like button") } &&
            normalized.any { it == "comment" || it.contains("comment button") } &&
            normalized.any { it == "share" || it.contains("share button") }
        val signature = normalized.filter {
            it == "reels" || it == "reel" || it == "like" || it == "comment" || it == "share"
        }.joinToString("|")
        val now = android.os.SystemClock.elapsedRealtime()
        if (signature != lastRootSignature || now - lastRootSignatureAt > 1000L) {
            lastRootSignature = signature
            lastRootSignatureAt = now
        }
        return hasReelsLabel && hasVideoActions
    }

    private fun collectLabels(node: AccessibilityNodeInfo, out: MutableList<String>, depth: Int) {
        if (depth > 12 || out.size > 250) return
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
            bubblePlatform = platform
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

    override fun onInterrupt() {
        // Keep the overlay if possible; interruption should not cause flicker.
    }

    override fun onDestroy() {
        removeBubble()
        super.onDestroy()
    }
}
