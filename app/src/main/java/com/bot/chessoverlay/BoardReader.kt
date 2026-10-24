package com.bot.chessoverlay

object BoardReader {

    class Result(val present: Boolean, val highlights: List<String>)

    // Geometri papan game di layar 1080 x 2400 (diukur dari screenshot).
    private const val REF_W = 1080f
    private const val REF_H = 2400f
    private const val LEFT = 30f
    private const val TOP = 660f
    private const val CELL = 127.5f

    private val DX = intArrayOf(12, 115, 12, 115, 12, 115)
    private val DY = intArrayOf(115, 115, 64, 64, 38, 38)

    private fun red(c: Int): Int = (c shr 16) and 255
    private fun green(c: Int): Int = (c shr 8) and 255
    private fun blue(c: Int): Int = c and 255

    private fun isGold(c: Int): Boolean {
        val r = red(c)
        val b = blue(c)
        return r > 100 && b < 60 && (r - b) > 80
    }

    private fun isCream(c: Int): Boolean {
        val r = red(c)
        val g = green(c)
        val b = blue(c)
        return r > 200 && g > 175 && b > 140 && (r - b) >= 20 && (r - b) < 75
    }

    private fun isDark(c: Int): Boolean {
        return red(c) < 50 && green(c) < 50 && blue(c) < 50
    }

    private fun sqName(col: Int, row: Int, flipped: Boolean): String {
        val f = if (flipped) 7 - col else col
        val r = if (flipped) row else 7 - row
        return "${'a' + f}${r + 1}"
    }

    fun analyze(w: Int, h: Int, flipped: Boolean, px: (Int, Int) -> Int): Result {
        val sx = w / REF_W
        val sy = h / REF_H
        val hl = ArrayList<String>()
        var valid = 0
        for (row in 0 until 8) {
            for (col in 0 until 8) {
                var gold = 0
                var ok = 0
                for (i in 0 until 6) {
                    val x = ((LEFT + col * CELL + DX[i]) * sx).toInt()
                    val y = ((TOP + row * CELL + DY[i]) * sy).toInt()
                    if (x < 0 || y < 0 || x >= w || y >= h) continue
                    val c = px(x, y)
                    if (isGold(c)) {
                        gold += 1
                        ok += 1
                    } else if ((col + row) % 2 == 0) {
                        if (isCream(c)) ok += 1
                    } else {
                        if (isDark(c)) ok += 1
                    }
                }
                if (gold >= 4) hl.add(sqName(col, row, flipped))
                if (ok >= 4) valid += 1
            }
        }
        return Result(valid >= 52, hl)
    }
}
