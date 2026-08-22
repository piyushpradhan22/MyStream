package com.mystream.app.torrent

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile

class TorrentStreamHttpServer(port: Int = 8888) : NanoHTTPD("127.0.0.1", port) {

    companion object {
        private const val TAG = "TorrentHttpServer"
        private const val PIECE_WAIT_TIMEOUT_MS = 60_000L
        private const val POLL_INTERVAL_MS = 30L
    }

    var activeFile: File? = null
        private set

    // Reference to engine for piece tracking — NO JNI calls made from HTTP threads
    private var engine: TorrentStreamEngine? = null
    private var torrentPieceLength: Long = 0L
    private var torrentTotalSize: Long = 0L

    fun setActiveFile(file: File?, engine: TorrentStreamEngine?, pieceLength: Long, totalSize: Long) {
        this.activeFile = file
        this.engine = engine
        this.torrentPieceLength = pieceLength
        this.torrentTotalSize = totalSize
    }

    override fun serve(session: IHTTPSession): Response {
        if (session.uri != "/stream") {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")
        }

        val file = activeFile
        val fileLen = torrentTotalSize

        if (file == null || !file.exists() || fileLen == 0L || engine == null) {
            return newFixedLengthResponse(Response.Status.SERVICE_UNAVAILABLE, MIME_PLAINTEXT, "Buffering...")
        }

        val rangeHeader = session.headers["range"] ?: session.headers["Range"]
        val mimeType = getMimeType(file.name)

        return try {
            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                var rangeFrom = 0L
                var rangeTo = fileLen - 1

                val parts = rangeHeader.substring(6).split("-")
                if (parts[0].isNotEmpty()) rangeFrom = parts[0].toLongOrNull() ?: 0L
                if (parts.size > 1 && parts[1].isNotEmpty()) rangeTo = parts[1].toLongOrNull() ?: (fileLen - 1)

                if (rangeFrom > fileLen) {
                    val res = newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, MIME_PLAINTEXT, "")
                    res.addHeader("Content-Range", "bytes */$fileLen")
                    return res
                }
                if (rangeTo >= fileLen) rangeTo = fileLen - 1

                Log.d(TAG, "Range: bytes=$rangeFrom-$rangeTo / $fileLen")
                val sendLength = rangeTo - rangeFrom + 1

                // Wait for the requested start piece before sending partial content
                if (torrentPieceLength > 0) {
                    val startPiece = (rangeFrom / torrentPieceLength).toInt()
                    waitForPiece(startPiece, PIECE_WAIT_TIMEOUT_MS)
                }

                val stream = createStream(file, rangeFrom, sendLength)
                val res = newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, mimeType, stream, sendLength)
                res.addHeader("Content-Range", "bytes $rangeFrom-$rangeTo/$fileLen")
                res.addHeader("Accept-Ranges", "bytes")
                res.addHeader("Content-Length", "$sendLength")
                res.addHeader("Connection", "keep-alive")
                res
            } else {
                Log.d(TAG, "Full request: $fileLen bytes")
                if (torrentPieceLength > 0) {
                    waitForPiece(0, PIECE_WAIT_TIMEOUT_MS)
                }
                val stream = createStream(file, 0L, fileLen)
                val res = newFixedLengthResponse(Response.Status.OK, mimeType, stream, fileLen)
                res.addHeader("Accept-Ranges", "bytes")
                res.addHeader("Content-Length", "$fileLen")
                res
            }
        } catch (e: Exception) {
            Log.e(TAG, "Serve error", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error: ${e.message}")
        }
    }

    /**
     * Waits until the engine has confirmed the given piece is downloaded.
     * Reads from the engine's BitSet — no JNI calls.
     */
    private fun waitForPiece(pieceIndex: Int, timeoutMs: Long): Boolean {
        val eng = engine ?: return false
        eng.onPieceRequested(pieceIndex)
        val deadline = System.currentTimeMillis() + timeoutMs

        while (System.currentTimeMillis() < deadline) {
            val available = synchronized(eng.downloadedPieces) { eng.downloadedPieces[pieceIndex] }
            if (available) {
                return true
            }
            try {
                Thread.sleep(POLL_INTERVAL_MS)
            } catch (_: InterruptedException) {
                break
            }
        }
        Log.w(TAG, "Piece $pieceIndex wait completed (may still be buffering)")
        return false
    }

    private fun createStream(file: File, offset: Long, length: Long): InputStream {
        val raf = RandomAccessFile(file, "r")
        raf.seek(offset)
        return object : InputStream() {
            private var pos = offset
            private var remaining = length
            private var lastConfirmedPiece = -1

            override fun read(): Int {
                if (remaining <= 0) return -1
                if (torrentPieceLength > 0) {
                    val piece = (pos / torrentPieceLength).toInt()
                    if (piece != lastConfirmedPiece) {
                        waitForPiece(piece, PIECE_WAIT_TIMEOUT_MS)
                        lastConfirmedPiece = piece
                    }
                }
                val b = raf.read()
                if (b != -1) { pos++; remaining-- }
                return b
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (remaining <= 0) return -1
                var toRead = minOf(len.toLong(), remaining)
                if (torrentPieceLength > 0) {
                    val piece = (pos / torrentPieceLength).toInt()
                    if (piece != lastConfirmedPiece) {
                        waitForPiece(piece, PIECE_WAIT_TIMEOUT_MS)
                        lastConfirmedPiece = piece
                    }
                    // Prevent reading into next unconfirmed piece
                    val nextPieceBoundary = (piece + 1L) * torrentPieceLength
                    val safeInPiece = nextPieceBoundary - pos
                    if (safeInPiece > 0) {
                        toRead = minOf(toRead, safeInPiece)
                    }
                }
                val n = raf.read(b, off, toRead.toInt())
                if (n > 0) { pos += n; remaining -= n }
                return n
            }

            override fun close() {
                try { raf.close() } catch (_: Exception) {}
            }
        }
    }

    private fun getMimeType(fileName: String): String {
        return when {
            fileName.endsWith(".mp4", true) -> "video/mp4"
            fileName.endsWith(".mkv", true) -> "video/x-matroska"
            fileName.endsWith(".webm", true) -> "video/webm"
            fileName.endsWith(".ts", true) -> "video/mp2t"
            fileName.endsWith(".avi", true) -> "video/x-msvideo"
            else -> "video/mp4"
        }
    }
}
