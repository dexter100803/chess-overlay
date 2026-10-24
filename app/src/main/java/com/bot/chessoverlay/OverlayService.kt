package com.bot.chessoverlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    companion object {
        @Volatile
        var instance: OverlayService? = null
    }

    private lateinit var windowManager: WindowManager
    private var arrow: ArrowView? = null

    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private var thread: HandlerThread? = null
    private var bg: Handler? = null
    private val main = Handler(Looper.getMainLooper())

    private var started = false
    private var pendingKey = ""
    private var pendingCount = 0
    private var lastKey = ""
    private var zeroCount = 0

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

    @Suppress("DEPRECATION")
    private fun readData(intent: Intent?): Intent? {
        if (intent == null) return null
        return if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra("data", Intent::class.java)
        } else {
            intent.getParcelableExtra<Intent>("data")
        }
    }

    private fun startAsForeground() {
        val chId = "chess_overlay"
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(chId, "Catur Overlay", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val n = NotificationCompat.Builder(this, chId)
            .setContentTitle("Catur Overlay aktif")
            .setContentText("Membaca papan game dari layar")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(1, n)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (started) return START_NOT_STICKY
        started = true

        val code = intent?.getIntExtra("code", 0) ?: 0
        val data = readData(intent)
        if (data == null || code == 0) {
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            startAsForeground()
            val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val proj = mpm.getMediaProjection(code, data)
            projection = proj
            setupCapture(proj)
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
            return START_NOT_STICKY
        }

        GameState.capturing = true
        GameState.statusMsg = "Rekam layar aktif. Buka game-nya."
        if (GameState.fen == GameState.START_FEN) {
            GameController.recompute()
        } else {
            GameController.notifyChange()
        }
        return START_NOT_STICKY
    }

    @Suppress("DEPRECATION")
    private fun setupCapture(proj: MediaProjection) {
        val dm = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(dm)
        val cw = dm.widthPixels / 2
        val ch = dm.heightPixels / 2

        val t = HandlerThread("capture")
        t.start()
        thread = t
        val handler = Handler(t.looper)
        bg = handler

        proj.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                main.post { stopSelf() }
            }
        }, handler)

        val rd = ImageReader.newInstance(cw, ch, PixelFormat.RGBA_8888, 2)
        reader = rd
        display = proj.createVirtualDisplay(
            "chess-capture",
            cw, ch, dm.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            rd.surface, null, handler
        )
        handler.post(loop)
    }

    private val loop = object : Runnable {
        override fun run() {
            try {
                grab()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            bg?.postDelayed(this, 200)
        }
    }

    private fun grab() {
        val rd = reader ?: return
        val img = rd.acquireLatestImage() ?: return
        try {
            val plane = img.planes[0]
            val buf = plane.buffer
            val rowStride = plane.rowStride
            val pixStride = plane.pixelStride
            val w = img.width
            val h = img.height
            val res = BoardReader.analyze(w, h, GameState.flipped) { x, y ->
                val o = y * rowStride + x * pixStride
                val r = buf.get(o).toInt() and 0xFF
                val g = buf.get(o + 1).toInt() and 0xFF
                val b = buf.get(o + 2).toInt() and 0xFF
                (r shl 16) or (g shl 8) or b
            }
            main.post { onObservation(res.present, res.highlights) }
        } finally {
            img.close()
        }
    }

    // Berjalan di thread utama.
    private fun onObservation(present: Boolean, hl: List<String>) {
        if (GameState.boardVisible != present) {
            GameState.boardVisible = present
            GameController.notifyChange()
        }
        if (!present) return

        if (hl.size == 2) {
            zeroCount = 0
            val key = hl.sorted().joinToString("")
            if (key == lastKey) return
            if (key != pendingKey) {
                pendingKey = key
                pendingCount = 1
                return
            }
            pendingCount += 1
            if (pendingCount < 2) return
            lastKey = key
            handlePair(hl[0], hl[1])
        } else if (hl.isEmpty()) {
            zeroCount += 1
            if (zeroCount == 8 && GameState.fen != GameState.START_FEN) {
                // Kotak langkah hilang cukup lama: game kemungkinan di-restart.
                lastKey = ""
                pendingKey = ""
                GameController.resetGame()
            }
        }
    }

    private fun tryPair(fen: String, a: String, b: String): String? {
        return ChessEngine.applyMove(fen, a + b) ?: ChessEngine.applyMove(fen, b + a)
    }

    private fun handlePair(a: String, b: String) {
        val newFen = tryPair(GameState.fen, a, b)
        if (newFen != null) {
            GameController.commit(newFen)
            return
        }
        // Mungkin saya sudah main sesuai saran lalu komputer membalas.
        if (GameController.isMyTurn() && GameState.suggestion.length >= 4) {
            val afterMine = ChessEngine.applyMove(GameState.fen, GameState.suggestion)
            if (afterMine != null) {
                val f2 = tryPair(afterMine, a, b)
                if (f2 != null) {
                    GameController.commit(afterMine)
                    GameController.commit(f2)
                    return
                }
            }
        }
        GameState.statusMsg = "Tidak sinkron dengan game ($a-$b). Restart game lalu tekan Partai baru."
        GameState.suggestion = ""
        GameController.notifyChange()
    }

    // Menggambar ulang panah dari GameState.
    fun sync() {
        val v = arrow ?: return
        v.flipped = GameState.flipped
        v.show = GameState.boardVisible
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
        GameState.capturing = false
        GameState.boardVisible = false
        bg?.removeCallbacksAndMessages(null)
        try {
            display?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            reader?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            projection?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        thread?.quitSafely()
        val v = arrow
        if (v != null) {
            try {
                windowManager.removeView(v)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        arrow = null
        GameState.onChange?.invoke()
    }
}
