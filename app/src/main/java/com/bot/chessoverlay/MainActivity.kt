package com.bot.chessoverlay

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var moveInput: EditText
    private lateinit var sideBtn: Button
    private lateinit var flipBtn: Button
    private lateinit var overlayBtn: Button

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun makeButton(label: String, action: () -> Unit): Button {
        return Button(this).apply {
            text = label
            setOnClickListener { action() }
        }
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
            setPadding(0, 0, 0, pad)
        }
        moveInput = EditText(this).apply {
            hint = "Langkah lawan, contoh: g2g4"
            isSingleLine = true
        }
        val applyBtn = makeButton("Terapkan langkah lawan") { applyTypedMove() }
        val playBtn = makeButton("Saya main sesuai saran") { playSuggestion() }
        val undoBtn = makeButton("Batalkan langkah terakhir") { undo() }
        val resetBtn = makeButton("Partai baru (reset)") { resetGame() }
        sideBtn = makeButton("") { toggleSide() }
        flipBtn = makeButton("") { toggleFlip() }
        overlayBtn = makeButton("") { toggleOverlay() }

        root.addView(status)
        root.addView(moveInput)
        root.addView(applyBtn)
        root.addView(playBtn)
        root.addView(undoBtn)
        root.addView(resetBtn)
        root.addView(sideBtn)
        root.addView(flipBtn)
        root.addView(overlayBtn)

        setContentView(ScrollView(this).apply { addView(root) })
        refresh()
    }

    override fun onResume() {
        super.onResume()
        overlayBtn.text = if (OverlayService.instance != null) "Matikan Overlay Panah"
        else "Nyalakan Overlay Panah"
        pushOverlay()
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun isMyTurn(): Boolean {
        val parts = GameState.fen.split(" ")
        val whiteToMove = parts.size > 1 && parts[1] == "w"
        return whiteToMove == GameState.myIsWhite
    }

    private fun commit(newFen: String) {
        GameState.history.add(GameState.fen)
        GameState.fen = newFen
        GameState.suggestion = ""
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
        commit(newFen)
        moveInput.setText("")
        refresh()
    }

    private fun playSuggestion() {
        val s = GameState.suggestion
        if (s.length < 4) {
            toast("Belum ada saran")
            return
        }
        val newFen = ChessEngine.applyMove(GameState.fen, s)
        if (newFen == null) {
            toast("Saran tidak bisa dijalankan")
            return
        }
        commit(newFen)
        refresh()
    }

    private fun undo() {
        if (GameState.history.isEmpty()) {
            toast("Tidak ada langkah untuk dibatalkan")
            return
        }
        GameState.fen = GameState.history.removeAt(GameState.history.size - 1)
        GameState.suggestion = ""
        refresh()
    }

    private fun resetGame() {
        GameState.fen = GameState.START_FEN
        GameState.history.clear()
        GameState.suggestion = ""
        refresh()
    }

    private fun toggleSide() {
        GameState.myIsWhite = !GameState.myIsWhite
        GameState.flipped = !GameState.myIsWhite
        GameState.suggestion = ""
        refresh()
    }

    private fun toggleFlip() {
        GameState.flipped = !GameState.flipped
        pushOverlay()
        refresh()
    }

    private fun toggleOverlay() {
        if (OverlayService.instance != null) {
            stopService(Intent(this, OverlayService::class.java))
            overlayBtn.text = "Nyalakan Overlay Panah"
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
        startService(Intent(this, OverlayService::class.java))
        overlayBtn.text = "Matikan Overlay Panah"
    }

    private fun pushOverlay() {
        OverlayService.instance?.sync()
    }

    private fun refresh() {
        sideBtn.text = if (GameState.myIsWhite) "Saya main: PUTIH (ketuk untuk ganti)"
        else "Saya main: HITAM (ketuk untuk ganti)"
        flipBtn.text = if (GameState.flipped) "Papan di game: TERBALIK (putih di atas)"
        else "Papan di game: NORMAL (putih di bawah)"

        val fen = GameState.fen
        val end = try {
            ChessEngine.statusText(fen)
        } catch (e: Exception) {
            "FEN rusak, tekan Partai baru"
        }
        if (end.isNotEmpty()) {
            status.text = end
            GameState.suggestion = ""
            pushOverlay()
            return
        }
        if (!isMyTurn()) {
            status.text = "Giliran lawan. Ketik langkah lawan di bawah."
            GameState.suggestion = ""
            pushOverlay()
            return
        }

        status.text = "Menghitung..."
        pushOverlay()
        val fenNow = fen
        Thread {
            val mv = try {
                ChessEngine.bestMoveRaw(fenNow)
            } catch (e: Exception) {
                ""
            }
            runOnUiThread {
                if (GameState.fen == fenNow) {
                    GameState.suggestion = mv
                    status.text = "Saran langkahmu: $mv"
                    pushOverlay()
                }
            }
        }.start()
    }
}
