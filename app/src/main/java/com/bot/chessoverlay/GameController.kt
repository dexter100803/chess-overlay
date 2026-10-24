package com.bot.chessoverlay

import android.os.Handler
import android.os.Looper

object GameController {

    private val main = Handler(Looper.getMainLooper())

    fun isMyTurn(): Boolean {
        val parts = GameState.fen.split(" ")
        val whiteToMove = parts.size > 1 && parts[1] == "w"
        return whiteToMove == GameState.myIsWhite
    }

    fun notifyChange() {
        OverlayService.instance?.sync()
        GameState.onChange?.invoke()
    }

    fun commit(newFen: String) {
        GameState.history.add(GameState.fen)
        GameState.fen = newFen
        GameState.suggestion = ""
        recompute()
    }

    fun undo(): Boolean {
        if (GameState.history.isEmpty()) return false
        GameState.fen = GameState.history.removeAt(GameState.history.size - 1)
        GameState.suggestion = ""
        recompute()
        return true
    }

    fun resetGame() {
        GameState.fen = GameState.START_FEN
        GameState.history.clear()
        GameState.suggestion = ""
        recompute()
    }

    // Harus dipanggil dari thread utama.
    fun recompute() {
        val fenNow = GameState.fen
        val end = try {
            ChessEngine.statusText(fenNow)
        } catch (e: Exception) {
            "Posisi rusak, tekan Partai baru"
        }
        if (end.isNotEmpty()) {
            GameState.statusMsg = end
            GameState.suggestion = ""
            notifyChange()
            return
        }
        if (!isMyTurn()) {
            GameState.statusMsg = "Giliran lawan..."
            GameState.suggestion = ""
            notifyChange()
            return
        }
        GameState.statusMsg = "Menghitung..."
        GameState.suggestion = ""
        notifyChange()
        Thread {
            val mv = try {
                ChessEngine.bestMoveRaw(fenNow)
            } catch (e: Exception) {
                ""
            }
            main.post {
                if (GameState.fen == fenNow) {
                    GameState.suggestion = mv
                    GameState.statusMsg = "Saran langkahmu: $mv"
                    notifyChange()
                }
            }
        }.start()
    }
}
