package com.bot.chessoverlay

import kotlin.math.abs

object ChessEngine {

    private const val MATE = 100000
    private val VAL = intArrayOf(0, 100, 320, 330, 500, 900, 0)

    private val PST = arrayOf(
        // Pion
        intArrayOf(
            0, 0, 0, 0, 0, 0, 0, 0,
            50, 50, 50, 50, 50, 50, 50, 50,
            10, 10, 20, 30, 30, 20, 10, 10,
            5, 5, 10, 25, 25, 10, 5, 5,
            0, 0, 0, 20, 20, 0, 0, 0,
            5, -5, -10, 0, 0, -10, -5, 5,
            5, 10, 10, -20, -20, 10, 10, 5,
            0, 0, 0, 0, 0, 0, 0, 0
        ),
        // Kuda
        intArrayOf(
            -50, -40, -30, -30, -30, -30, -40, -50,
            -40, -20, 0, 0, 0, 0, -20, -40,
            -30, 0, 10, 15, 15, 10, 0, -30,
            -30, 5, 15, 20, 20, 15, 5, -30,
            -30, 0, 15, 20, 20, 15, 0, -30,
            -30, 5, 10, 15, 15, 10, 5, -30,
            -40, -20, 0, 5, 5, 0, -20, -40,
            -50, -40, -30, -30, -30, -30, -40, -50
        ),
        // Gajah
        intArrayOf(
            -20, -10, -10, -10, -10, -10, -10, -20,
            -10, 0, 0, 0, 0, 0, 0, -10,
            -10, 0, 5, 10, 10, 5, 0, -10,
            -10, 5, 5, 10, 10, 5, 5, -10,
            -10, 0, 10, 10, 10, 10, 0, -10,
            -10, 10, 10, 10, 10, 10, 10, -10,
            -10, 5, 0, 0, 0, 0, 5, -10,
            -20, -10, -10, -10, -10, -10, -10, -20
        ),
        // Benteng
        intArrayOf(
            0, 0, 0, 0, 0, 0, 0, 0,
            5, 10, 10, 10, 10, 10, 10, 5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            0, 0, 0, 5, 5, 0, 0, 0
        ),
        // Menteri
        intArrayOf(
            -20, -10, -10, -5, -5, -10, -10, -20,
            -10, 0, 0, 0, 0, 0, 0, -10,
            -10, 0, 5, 5, 5, 5, 0, -10,
            -5, 0, 5, 5, 5, 5, 0, -5,
            0, 0, 5, 5, 5, 5, 0, -5,
            -10, 5, 5, 5, 5, 5, 0, -10,
            -10, 0, 5, 0, 0, 0, 0, -10,
            -20, -10, -10, -5, -5, -10, -10, -20
        ),
        // Raja
        intArrayOf(
            -30, -40, -40, -50, -50, -40, -40, -30,
            -30, -40, -40, -50, -50, -40, -40, -30,
            -30, -40, -40, -50, -50, -40, -40, -30,
            -30, -40, -40, -50, -50, -40, -40, -30,
            -20, -30, -30, -40, -40, -30, -30, -20,
            -10, -20, -20, -20, -20, -20, -20, -10,
            20, 20, 0, 0, 0, 0, 20, 20,
            20, 30, 10, 0, 0, 10, 30, 20
        )
    )

    private val KN_F = intArrayOf(1, 2, 2, 1, -1, -2, -2, -1)
    private val KN_R = intArrayOf(2, 1, -1, -2, -2, -1, 1, 2)
    private val KG_F = intArrayOf(1, 1, 0, -1, -1, -1, 0, 1)
    private val KG_R = intArrayOf(0, 1, 1, 1, 0, -1, -1, -1)
    private val DG_F = intArrayOf(1, -1, -1, 1)
    private val DG_R = intArrayOf(1, 1, -1, -1)
    private val OR_F = intArrayOf(1, -1, 0, 0)
    private val OR_R = intArrayOf(0, 0, 1, -1)

    private class Pos(val b: IntArray, val side: Int, val castle: Int, val ep: Int)

    fun bestMove(fen: String, depth: Int = 3): String {
        val pos = parse(fen)
        if (kingSq(pos.b, 1) < 0 || kingSq(pos.b, -1) < 0) {
            throw IllegalArgumentException("Raja tidak ada")
        }
        val moves = legal(pos)
        if (moves.isEmpty()) {
            return if (inCheck(pos)) "Skakmat, tidak ada langkah"
            else "Remis (stalemate), tidak ada langkah"
        }
        val sorted = moves.sortedByDescending { orderKey(pos, it) }
        var alpha = -MATE * 2
        var best = sorted[0]
        for (m in sorted) {
            val score = -negamax(apply(pos, m), depth - 1, -MATE * 2, -alpha, 1)
            if (score > alpha) {
                alpha = score
                best = m
            }
        }
        return "${moveToString(best)}  (nilai: ${fmt(alpha)})"
    }

    private fun fmt(s: Int): String {
        return if (s > 90000) "skakmat untuk giliran ini"
        else if (s < -90000) "akan terkena skakmat"
        else "${s / 100.0}"
    }

    private fun moveToString(m: Int): String {
        val from = m and 63
        val to = (m shr 6) and 63
        val promo = (m shr 12) and 15
        val suffix = when (promo) {
            5 -> "q"
            4 -> "r"
            3 -> "b"
            2 -> "n"
            else -> ""
        }
        return sqName(from) + sqName(to) + suffix
    }

    private fun sqName(s: Int): String {
        return "${'a' + (s % 8)}${(s / 8) + 1}"
    }

    private fun negamax(pos: Pos, depth: Int, alphaIn: Int, beta: Int, ply: Int): Int {
        val moves = legal(pos)
        if (moves.isEmpty()) {
            return if (inCheck(pos)) -MATE + ply else 0
        }
        if (depth == 0) return evaluate(pos)
        var alpha = alphaIn
        val sorted = moves.sortedByDescending { orderKey(pos, it) }
        for (m in sorted) {
            val score = -negamax(apply(pos, m), depth - 1, -beta, -alpha, ply + 1)
            if (score >= beta) return beta
            if (score > alpha) alpha = score
        }
        return alpha
    }

    private fun evaluate(pos: Pos): Int {
        var s = 0
        for (sq in 0 until 64) {
            val p = pos.b[sq]
            if (p == 0) continue
            val t = abs(p)
            val r = sq / 8
            val f = sq % 8
            if (p > 0) s += VAL[t] + PST[t - 1][(7 - r) * 8 + f]
            else s -= VAL[t] + PST[t - 1][r * 8 + f]
        }
        return s * pos.side
    }

    private fun orderKey(pos: Pos, m: Int): Int {
        val from = m and 63
        val to = (m shr 6) and 63
        val promo = (m shr 12) and 15
        val victim = abs(pos.b[to])
        val attacker = abs(pos.b[from])
        var k = 0
        if (victim != 0) k = VAL[victim] * 10 - VAL[attacker] + 1000
        if (promo != 0) k += VAL[promo]
        return k
    }

    private fun parse(fen: String): Pos {
        val parts = fen.trim().split(Regex("\\s+"))
        val b = IntArray(64)
        var r = 7
        var f = 0
        for (ch in parts[0]) {
            if (ch == '/') {
                r -= 1
                f = 0
            } else if (ch.isDigit()) {
                f += ch - '0'
            } else {
                val v = when (ch.lowercaseChar()) {
                    'p' -> 1
                    'n' -> 2
                    'b' -> 3
                    'r' -> 4
                    'q' -> 5
                    'k' -> 6
                    else -> throw IllegalArgumentException("Bidak tidak dikenal")
                }
                b[r * 8 + f] = if (ch.isUpperCase()) v else -v
                f += 1
            }
        }
        val side = if (parts.size > 1 && parts[1] == "b") -1 else 1
        val cs = if (parts.size > 2) parts[2] else "-"
        var c = 0
        if (cs.contains('K')) c = c or 1
        if (cs.contains('Q')) c = c or 2
        if (cs.contains('k')) c = c or 4
        if (cs.contains('q')) c = c or 8
        var ep = -1
        if (parts.size > 3 && parts[3].length == 2 && parts[3] != "-") {
            ep = (parts[3][1] - '1') * 8 + (parts[3][0] - 'a')
        }
        return Pos(b, side, c, ep)
    }

    private fun attacked(b: IntArray, sq: Int, by: Int): Boolean {
        val f = sq % 8
        val r = sq / 8
        val pr = r - by
        for (df in intArrayOf(-1, 1)) {
            val nf = f + df
            if (nf in 0..7 && pr in 0..7 && b[pr * 8 + nf] == by) return true
        }
        for (i in 0 until 8) {
            val nf = f + KN_F[i]
            val nr = r + KN_R[i]
            if (nf in 0..7 && nr in 0..7 && b[nr * 8 + nf] == by * 2) return true
        }
        for (i in 0 until 8) {
            val nf = f + KG_F[i]
            val nr = r + KG_R[i]
            if (nf in 0..7 && nr in 0..7 && b[nr * 8 + nf] == by * 6) return true
        }
        for (i in 0 until 4) {
            var nf = f + DG_F[i]
            var nr = r + DG_R[i]
            while (nf in 0..7 && nr in 0..7) {
                val p = b[nr * 8 + nf]
                if (p != 0) {
                    if (p == by * 3 || p == by * 5) return true
                    break
                }
                nf += DG_F[i]
                nr += DG_R[i]
            }
        }
        for (i in 0 until 4) {
            var nf = f + OR_F[i]
            var nr = r + OR_R[i]
            while (nf in 0..7 && nr in 0..7) {
                val p = b[nr * 8 + nf]
                if (p != 0) {
                    if (p == by * 4 || p == by * 5) return true
                    break
                }
                nf += OR_F[i]
                nr += OR_R[i]
            }
        }
        return false
    }

    private fun addPromos(out: ArrayList<Int>, from: Int, to: Int) {
        for (pr in intArrayOf(5, 4, 3, 2)) {
            out.add(from or (to shl 6) or (pr shl 12))
        }
    }

    private fun pseudo(pos: Pos): ArrayList<Int> {
        val b = pos.b
        val side = pos.side
        val out = ArrayList<Int>()
        for (sq in 0 until 64) {
            val p = b[sq]
            if (p == 0 || p * side <= 0) continue
            val t = abs(p)
            val f = sq % 8
            val r = sq / 8
            if (t == 1) {
                val nr = r + side
                if (nr in 0..7) {
                    val promo = (nr == 7 || nr == 0)
                    val fwd = nr * 8 + f
                    if (b[fwd] == 0) {
                        if (promo) {
                            addPromos(out, sq, fwd)
                        } else {
                            out.add(sq or (fwd shl 6))
                            val start = if (side == 1) 1 else 6
                            val two = (r + 2 * side) * 8 + f
                            if (r == start && b[two] == 0) out.add(sq or (two shl 6))
                        }
                    }
                    for (df in intArrayOf(-1, 1)) {
                        val nf = f + df
                        if (nf in 0..7) {
                            val to = nr * 8 + nf
                            if (b[to] * side < 0 || (b[to] == 0 && to == pos.ep)) {
                                if (promo) addPromos(out, sq, to)
                                else out.add(sq or (to shl 6))
                            }
                        }
                    }
                }
            } else if (t == 2 || t == 6) {
                for (i in 0 until 8) {
                    val nf = if (t == 2) f + KN_F[i] else f + KG_F[i]
                    val nr = if (t == 2) r + KN_R[i] else r + KG_R[i]
                    if (nf in 0..7 && nr in 0..7) {
                        val to = nr * 8 + nf
                        if (b[to] * side <= 0) out.add(sq or (to shl 6))
                    }
                }
                if (t == 6) {
                    val base = if (side == 1) 0 else 56
                    if (sq == base + 4 && !attacked(b, sq, -side)) {
                        val ks = if (side == 1) 1 else 4
                        val qs = if (side == 1) 2 else 8
                        if ((pos.castle and ks) != 0 &&
                            b[base + 5] == 0 && b[base + 6] == 0 &&
                            b[base + 7] == side * 4 &&
                            !attacked(b, base + 5, -side) &&
                            !attacked(b, base + 6, -side)
                        ) {
                            out.add(sq or ((base + 6) shl 6))
                        }
                        if ((pos.castle and qs) != 0 &&
                            b[base + 1] == 0 && b[base + 2] == 0 && b[base + 3] == 0 &&
                            b[base] == side * 4 &&
                            !attacked(b, base + 3, -side) &&
                            !attacked(b, base + 2, -side)
                        ) {
                            out.add(sq or ((base + 2) shl 6))
                        }
                    }
                }
            } else {
                val n = if (t == 3) 4 else if (t == 4) 4 else 8
                for (i in 0 until n) {
                    val df: Int
                    val dr: Int
                    if (t == 3) {
                        df = DG_F[i]; dr = DG_R[i]
                    } else if (t == 4) {
                        df = OR_F[i]; dr = OR_R[i]
                    } else {
                        if (i < 4) {
                            df = DG_F[i]; dr = DG_R[i]
                        } else {
                            df = OR_F[i - 4]; dr = OR_R[i - 4]
                        }
                    }
                    var nf = f + df
                    var nr = r + dr
                    while (nf in 0..7 && nr in 0..7) {
                        val to = nr * 8 + nf
                        if (b[to] == 0) {
                            out.add(sq or (to shl 6))
                        } else {
                            if (b[to] * side < 0) out.add(sq or (to shl 6))
                            break
                        }
                        nf += df
                        nr += dr
                    }
                }
            }
        }
        return out
    }

    private fun maskFor(sq: Int): Int {
        return when (sq) {
            0 -> 2
            7 -> 1
            4 -> 3
            56 -> 8
            63 -> 4
            60 -> 12
            else -> 0
        }
    }

    private fun apply(pos: Pos, m: Int): Pos {
        val from = m and 63
        val to = (m shr 6) and 63
        val promo = (m shr 12) and 15
        val b = pos.b.copyOf()
        val p = b[from]
        val side = pos.side
        val t = abs(p)
        val cap = b[to]
        if (t == 1 && to == pos.ep && cap == 0 && (from % 8) != (to % 8)) {
            b[to - side * 8] = 0
        }
        b[to] = if (promo != 0) side * promo else p
        b[from] = 0
        if (t == 6 && abs(to - from) == 2) {
            if (to > from) {
                b[to - 1] = b[to + 1]
                b[to + 1] = 0
            } else {
                b[to + 1] = b[to - 2]
                b[to - 2] = 0
            }
        }
        var ep = -1
        if (t == 1 && abs(to - from) == 16) ep = (from + to) / 2
        var c = pos.castle
        c = c and maskFor(from).inv()
        c = c and maskFor(to).inv()
        return Pos(b, -side, c, ep)
    }

    private fun kingSq(b: IntArray, side: Int): Int {
        for (i in 0 until 64) {
            if (b[i] == side * 6) return i
        }
        return -1
    }

    private fun inCheck(pos: Pos): Boolean {
        val k = kingSq(pos.b, pos.side)
        return k >= 0 && attacked(pos.b, k, -pos.side)
    }

    private fun legal(pos: Pos): ArrayList<Int> {
        val out = ArrayList<Int>()
        for (m in pseudo(pos)) {
            val n = apply(pos, m)
            val k = kingSq(n.b, pos.side)
            if (k >= 0 && !attacked(n.b, k, -pos.side)) out.add(m)
        }
        return out
    }
}
