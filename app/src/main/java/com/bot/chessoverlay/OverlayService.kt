package com.bot.chessoverlay

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.WindowManager

class OverlayService : Service() {

    companion object {
        @Volatile
        var instance: OverlayService? = null
    }

    private lateinit var windowManager: WindowManager
    private var arrow: ArrowView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    @Suppress("DEPRECATION")
    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val dm = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(dm)

        val view = ArrowView(this)
        arrow = view

        val params = WindowManager.LayoutParams(
            dm.widthPixels,
            dm.heightPixels,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

        try {
            windowManager.addView(view, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        sync()
    }

    // Membaca GameState lalu menggambar ulang panahnya.
    fun sync() {
        val v = arrow ?: return
        v.flipped = GameState.flipped
        val s = GameState.suggestion
        if (s.length >= 4) {
            v.fromSq = (s[1] - '1') * 8 + (s[0] - 'a')
            v.toSq = (s[3] - '1') * 8 + (s[2] - 'a')
        } else {
            v.fromSq = -1
            v.toSq = -1
        }
        v.invalidate()
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        val v = arrow
        if (v != null) {
            try {
                windowManager.removeView(v)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        arrow = null
    }
}
