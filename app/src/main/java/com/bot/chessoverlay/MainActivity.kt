package com.bot.chessoverlay

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val startFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        val fenInput = EditText(this).apply {
            setText(startFen)
            hint = "Posisi FEN"
        }
        val result = TextView(this).apply {
            text = "Langkah terbaik akan tampil di sini"
            textSize = 18f
            setPadding(0, pad, 0, pad)
        }
        val searchBtn = Button(this).apply {
            text = "Cari Langkah Terbaik"
            setOnClickListener {
                val fen = fenInput.text.toString().trim()
                result.text = "Menghitung..."
                Thread {
                    val out = try {
                        ChessEngine.bestMove(fen)
                    } catch (e: Exception) {
                        "FEN tidak valid"
                    }
                    runOnUiThread { result.text = out }
                }.start()
            }
        }
        val overlayBtn = Button(this).apply {
            text = "Mulai Catur Overlay"
            setOnClickListener { checkOverlayPermissionAndStart() }
        }

        root.addView(fenInput)
        root.addView(searchBtn)
        root.addView(result)
        root.addView(overlayBtn)
        setContentView(root)
    }

    private fun checkOverlayPermissionAndStart() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            Toast.makeText(this, "Izinkan aplikasi tampil di atas aplikasi lain", Toast.LENGTH_LONG).show()
        } else {
            startService(Intent(this, OverlayService::class.java))
            Toast.makeText(this, "Overlay Diaktifkan!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
