package com.bot.chessoverlay

object GameState {
    const val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

    var fen: String = START_FEN
    var suggestion: String = ""
    var myIsWhite: Boolean = false
    var flipped: Boolean = true
    val history = ArrayList<String>()

    var statusMsg: String = ""
    var capturing: Boolean = false
    var boardVisible: Boolean = false

    // Dipanggil di thread utama setiap kali ada perubahan.
    var onChange: (() -> Unit)? = null
}
