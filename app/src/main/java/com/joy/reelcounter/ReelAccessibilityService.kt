package com.joy.reelcounter

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import java.time.LocalDate
import java.util.Locale

/**
 * Approximate auto counter based on vertical accessibility scroll events.
 * Third-party apps do not expose a reliable public "Reel changed" event, so
 * normal vertical feed scrolling can also be counted. Users can disable this
 * service at any time in Android Accessibility settings.
 */
class ReelAccessibilityService : AccessibilityService() {
    private var windowManager: WindowManager? = null
    private var bubble: TextView? = null
    private var currentPlatform: String? = null
    private var lastCountAt = 0L
    private var lastDate = LocalDate.now().toString()

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return
        val platform = when (pkg) {
            "com.instagram.android" -> "Instagram"
            "com.facebook.lite" -> "Facebook Lite"
            else -> null
        }
        if (platform == null) {
            removeBubble()
            currentPlatform = null
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            removeBubble()
            return
        }

        if (currentPlatform != platform) {
            currentPlatform = platform
            showOrUpdateBubble(platform)
        } else {
            showOrUpdateBubble(platform)
        }

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            val now = System.currentTimeMillis()
            // Debounce multiple accessibility events produced by one swipe.
            if (now - lastCountAt >= 900L) {
                lastCountAt = now
                val today = LocalDate.now().toString()
                val prefs = getSharedPreferences("reel_counter", MODE_PRIVATE)
                if (lastDate != today) lastDate = today
                val key = countKey(LocalDate.now(), platform)
                val next = prefs.getInt(key, 0) + 1
                prefs.edit().putInt(key, next).apply()
                showOrUpdateBubble(platform)
            }
        }
    }

    private fun showOrUpdateBubble(platform: String) {
        if (!Settings.canDrawOverlays(this)) return
        val prefs = getSharedPreferences("reel_counter", MODE_PRIVATE)
        val count = prefs.getInt(countKey(LocalDate.now(), platform), 0)
        val text = bubble ?: TextView(this).apply {
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
        }.also { bubble = it }

        text.text = if (platform == "Instagram") "IG  •  $count" else "FB  •  $count"
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
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
            if (text.parent == null) windowManager?.addView(text, params)
            else windowManager?.updateViewLayout(text, params)
        } catch (_: Exception) { }
    }

    private fun removeBubble() {
        try {
            bubble?.let { if (it.parent != null) windowManager?.removeView(it) }
        } catch (_: Exception) { }
        bubble = null
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onInterrupt() { removeBubble() }

    override fun onDestroy() {
        removeBubble()
        super.onDestroy()
    }
}
