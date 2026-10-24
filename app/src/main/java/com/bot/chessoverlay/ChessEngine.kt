package com.bot.chessoverlay

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move

object ChessEngine {

    private const val MATE = 100000

    private val values = mapOf(
        PieceType.PAWN to 100,
        PieceType.KNIGHT to 320,
        PieceType.BISHOP to 330,
        PieceType.ROOK to 500,
        PieceType.QUEEN to 900,
        PieceType.KING to 0
    )

    fun bestMove(fen: String, depth: Int = 3): String {
        val board = Board()
        board.loadFromFen(fen)

        if (board.isMated()) return "Skakmat, tidak ada langkah"
        val moves = ordered(board)
        if (moves.isEmpty()) return "Remis (stalemate), tidak ada langkah"

        var best: Move = moves[0]
        var alpha = -MATE * 2
        val beta = MATE * 2

        for (m in moves) {
            board.doMove(m)
            val score = -search(board, depth - 1, -beta, -alpha, 1)
            board.undoMove()
            if (score > alpha) {
                alpha = score
                best = m
            }
        }
        return "${best}  (nilai: $alpha)"
    }

    private fun search(board: Board, depth: Int, alphaIn: Int, beta: Int, ply: Int): Int {
        if (board.isMated()) return -MATE + ply
        if (board.isDraw()) return 0
        if (depth == 0) return evaluate(board)

        var alpha = alphaIn
        for (m in ordered(board)) {
            board.doMove(m)
            val score = -search(board, depth - 1, -beta, -alpha, ply + 1)
            board.undoMove()
            if (score >= beta) return beta
            if (score > alpha) alpha = score
        }
        return alpha
    }

    private fun ordered(board: Board): List<Move> {
        return board.legalMoves().sortedByDescending {
            if (board.getPiece(it.to) != Piece.NONE) 1 else 0
        }
    }

    private fun evaluate(board: Board): Int {
        var sum = 0
        for (sq in Square.values()) {
            if (sq == Square.NONE) continue
            val p = board.getPiece(sq)
            if (p == Piece.NONE) continue
            val v = values[p.pieceType] ?: 0
            sum += if (p.pieceSide == Side.WHITE) v else -v
        }
        return if (board.sideToMove == Side.WHITE) sum else -sum
    }
}
