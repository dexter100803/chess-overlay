package com.bot.chessoverlay

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var info: TextView
    private lateinit var moveInput: EditText
    private lateinit var sideBtn: Button
    private lateinit var flipBtn: Button
    private lateinit var captureBtn: Button

    private val captureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        val data = res.data
        if (res.resultCode == RESULT_OK && data != null) {
            val i = Intent(this, OverlayService::class.java)
            i.putExtra("code", res.resultCode)
            i.putExtra("data", data)
            ContextCompat.startForegroundService(this, i)
        } else {
            toast("Izin rekam layar ditolak")
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun makeButton(label: String, action: () -> Unit): Button {
        return Button(this).apply {
            text = label
            setOnClickListener { action() }
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = dp(16)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        status = TextView(this).apply {
            textSize = 20f
            setPadding(0, 0, 0, dp(8))
        }
        info = TextView(this).apply {
            textSize = 14f
            setPadding(0, 0, 0, pad)
        }
        captureBtn = makeButton("") { toggleCapture() }
        sideBtn = makeButton("") { toggleSide() }
        flipBtn = makeButton("") { toggleFlip() }
        val resetBtn = makeButton("Partai baru (reset)") {
            GameController.resetGame()
        }
        val undoBtn = makeButton("Batalkan langkah terakhir") {
            if (!GameController.undo()) toast("Tidak ada langkah untuk dibatalkan")
        }

        moveInput = EditText(this).apply {
            hint = "Manual (cadangan), contoh: g2g4"
            isSingleLine = true
        }
        val applyBtn = makeButton("Terapkan langkah manual") { applyTypedMove() }

        root.addView(status)
        root.addView(info)
        root.addView(captureBtn)
        root.addView(sideBtn)
        root.addView(flipBtn)
        root.addView(resetBtn)
        root.addView(undoBtn)
        root.addView(moveInput)
        root.addView(applyBtn)

        setContentView(ScrollView(this).apply { addView(root) })

        if (GameState.statusMsg.isEmpty()) GameController.recompute()
    }

    override fun onResume() {
        super.onResume()
        GameState.onChange = { refreshUi() }
        refreshUi()
    }

    override fun onPause() {
        super.onPause()
        GameState.onChange = null
    }

    private fun refreshUi() {
        status.text = GameState.statusMsg
        info.text = if (GameState.capturing) {
            if (GameState.boardVisible) "Rekam layar: aktif, papan game terdeteksi"
            else "Rekam layar: aktif, papan game belum terlihat (buka game-nya)"
        } else {
            "Rekam layar: mati"
        }
        captureBtn.text = if (OverlayService.instance != null) "Hentikan Deteksi Otomatis"
        else "Mulai Deteksi Otomatis"
        sideBtn.text = if (GameState.myIsWhite) "Saya main: PUTIH (ketuk untuk ganti)"
        else "Saya main: HITAM (ketuk untuk ganti)"
        flipBtn.text = if (GameState.flipped) "Papan di game: TERBALIK (putih di atas)"
        else "Papan di game: NORMAL (putih di bawah)"
    }

    private fun toggleCapture() {
        if (OverlayService.instance != null) {
            stopService(Intent(this, OverlayService::class.java))
            captureBtn.text = "Mulai Deteksi Otomatis"
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            toast("Izinkan aplikasi tampil di atas aplikasi lain, lalu kembali")
            return
        }
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        captureLauncher.launch(mpm.createScreenCaptureIntent())
    }

    private fun toggleSide() {
        GameState.myIsWhite = !GameState.myIsWhite
        GameState.flipped = !GameState.myIsWhite
        GameController.recompute()
    }

    private fun toggleFlip() {
        GameState.flipped = !GameState.flipped
        GameController.notifyChange()
    }

    private fun applyTypedMove() {
        val txt = moveInput.text.toString().trim().lowercase()
        if (txt.length < 4) {
            toast("Tulis langkah, contoh: g2g4")
            return
        }
        val newFen = ChessEngine.applyMove(GameState.fen, txt)
        if (newFen == null) {
            toast("Langkah tidak sah: $txt")
            return
        }
        GameController.commit(newFen)
        moveInput.setText("")
    }
}
