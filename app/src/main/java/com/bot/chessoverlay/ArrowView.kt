package com.bot.chessoverlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.sqrt

class ArrowView(context: Context) : View(context) {

    companion object {
        // Posisi papan game di layar 1080 x 2400 (diukur dari screenshot).
        const val BOARD_LEFT = 30f
        const val BOARD_TOP = 660f
        const val CELL = 127.5f
        const val REF_W = 1080f
        const val REF_H = 2400f
    }

    var fromSq = -1
    var toSq = -1
    var flipped = true
    var show = true

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(230, 0, 200, 80)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(230, 0, 200, 80)
        style = Paint.Style.FILL
    }

    private fun colOf(sq: Int): Int = if (flipped) 7 - (sq % 8) else sq % 8
    private fun rowOf(sq: Int): Int = if (flipped) sq / 8 else 7 - (sq / 8)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!show || fromSq < 0 || toSq < 0) return

        val sx = width / REF_W
        val sy = height / REF_H
        val left = BOARD_LEFT * sx
        val top = BOARD_TOP * sy
        val cw = CELL * sx
        val ch = CELL * sy

        val fx = left + colOf(fromSq) * cw + cw / 2
        val fy = top + rowOf(fromSq) * ch + ch / 2
        val tx = left + colOf(toSq) * cw + cw / 2
        val ty = top + rowOf(toSq) * ch + ch / 2

        val dx = tx - fx
        val dy = ty - fy
        val len = sqrt(dx * dx + dy * dy)
        if (len < 1f) return
        val ux = dx / len
        val uy = dy / len

        val headLen = cw * 0.5f
        val bx = tx - ux * headLen
        val by = ty - uy * headLen

        linePaint.strokeWidth = cw * 0.16f
        canvas.drawLine(fx, fy, bx, by, linePaint)

        val px = -uy
        val py = ux
        val hw = cw * 0.28f
        val path = Path()
        path.moveTo(tx, ty)
        path.lineTo(bx + px * hw, by + py * hw)
        path.lineTo(bx - px * hw, by - py * hw)
        path.close()
        canvas.drawPath(path, headPaint)
    }
}
