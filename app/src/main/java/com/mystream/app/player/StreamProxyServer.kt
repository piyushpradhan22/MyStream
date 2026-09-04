package com.mystream.app.player

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * High-performance localhost streaming proxy for Android TV.
 *
 * Decouples ExoPlayer's synchronous media demuxer from remote high-latency CDN sockets.
 * Runs an unthrottled background I/O read loop over 128 KB chunks so that the remote TCP window
 * stays wide open at full line speed (10-20 MB/s), while ExoPlayer reads from 127.0.0.1 at 0ms latency.
 */
class StreamProxyServer(private val okHttpClient: OkHttpClient) {

    companion object {
        private const val TAG = "StreamProxyServer"
    }

    private var serverSocket: ServerSocket? = null
    private var port: Int = 0
    private val isRunning = AtomicBoolean(false)
    private val proxyScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Synchronized
    fun start(): Int {
        if (isRunning.get() && serverSocket != null && !serverSocket!!.isClosed) {
            return port
        }
        try {
            serverSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
            port = serverSocket!!.localPort
            isRunning.set(true)
            Log.i(TAG, "StreamProxyServer started successfully on 127.0.0.1:$port")
            proxyScope.launch {
                listenLoop()
            }
            return port
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start StreamProxyServer: ${e.message}", e)
            return 0
        }
    }

    fun getProxyUrl(targetUrl: String): String {
        val p = if (isRunning.get() && port > 0) port else start()
        if (p == 0) {
            Log.w(TAG, "Proxy port is 0, falling back to direct URL")
            return targetUrl
        }
        val encoded = URLEncoder.encode(targetUrl, "UTF-8")
        val proxyUrl = "http://127.0.0.1:$p/stream?url=$encoded"
        Log.i(TAG, "Proxy URL generated: $proxyUrl for $targetUrl")
        return proxyUrl
    }

    private suspend fun listenLoop() = withContext(Dispatchers.IO) {
        while (isRunning.get()) {
            try {
                val clientSocket = serverSocket?.accept() ?: break
                launch(Dispatchers.IO) {
                    handleClient(clientSocket)
                }
            } catch (e: Exception) {
                if (isRunning.get()) {
                    Log.w(TAG, "Accept error in listen loop: ${e.message}")
                }
            }
        }
    }

    private fun handleClient(clientSocket: Socket) {
        var upstreamStream: InputStream? = null
        var clientOut: OutputStream? = null
        var upstreamResponse: okhttp3.Response? = null
        try {
            clientSocket.tcpNoDelay = true
            clientSocket.receiveBufferSize = 1024 * 1024
            clientSocket.sendBufferSize = 1024 * 1024
            clientSocket.soTimeout = 20000

            val clientIn = clientSocket.getInputStream()
            clientOut = clientSocket.getOutputStream()

            // Read raw HTTP headers from ExoPlayer
            val reader = clientIn.bufferedReader(Charsets.ISO_8859_1)
            val requestLine = reader.readLine()
            if (requestLine.isNullOrBlank()) return

            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val path = parts[1]
            var rangeHeader: String? = null
            var userAgentHeader: String? = null
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line.isNullOrEmpty()) break
                val l = line!!
                if (l.startsWith("Range:", ignoreCase = true)) {
                    rangeHeader = l.substringAfter(":").trim()
                } else if (l.startsWith("User-Agent:", ignoreCase = true)) {
                    userAgentHeader = l.substringAfter(":").trim()
                }
            }

            val targetUrl = extractTargetUrl(path)
            if (targetUrl.isNullOrBlank()) {
                Log.w(TAG, "No target URL found in path: $path")
                return
            }

            Log.i(TAG, "Proxy request: range=$rangeHeader, target=$targetUrl")

            val reqBuilder = Request.Builder()
                .url(targetUrl)
                .header(
                    "User-Agent",
                    userAgentHeader ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
                )

            if (!rangeHeader.isNullOrBlank()) {
                reqBuilder.header("Range", rangeHeader)
            }

            val call = okHttpClient.newCall(reqBuilder.build())
            val response = call.execute()
            upstreamResponse = response

            val code = response.code
            val statusMessage = if (code == 206) "Partial Content" else if (code == 200) "OK" else "OK"

            val headerBuilder = StringBuilder()
            headerBuilder.append("HTTP/1.1 $code $statusMessage\r\n")
            response.header("Content-Type")?.let { headerBuilder.append("Content-Type: $it\r\n") }
                ?: headerBuilder.append("Content-Type: video/mp4\r\n")
            response.header("Content-Length")?.let { headerBuilder.append("Content-Length: $it\r\n") }
            response.header("Content-Range")?.let { headerBuilder.append("Content-Range: $it\r\n") }
            headerBuilder.append("Accept-Ranges: bytes\r\n")
            headerBuilder.append("Connection: close\r\n\r\n")

            clientOut.write(headerBuilder.toString().toByteArray(Charsets.ISO_8859_1))
            clientOut.flush()

            val body = response.body
            if (body != null) {
                upstreamStream = body.byteStream()
                val buffer = ByteArray(128 * 1024) // 128 KB buffer
                var bytesRead: Int
                while (upstreamStream.read(buffer).also { bytesRead = it } != -1) {
                    clientOut.write(buffer, 0, bytesRead)
                }
                clientOut.flush()
            }
        } catch (_: java.net.SocketException) {
            // Expected when ExoPlayer seeks or closes connection
        } catch (e: Exception) {
            Log.w(TAG, "Proxy streaming exception: ${e.message}")
        } finally {
            try { upstreamStream?.close() } catch (_: Exception) {}
            try { upstreamResponse?.close() } catch (_: Exception) {}
            try { clientOut?.close() } catch (_: Exception) {}
            try { clientSocket.close() } catch (_: Exception) {}
        }
    }

    private fun extractTargetUrl(path: String): String? {
        val query = path.substringAfter("?", "")
        if (query.isBlank()) return null
        val raw = if (query.startsWith("url=")) query.substring(4) else {
            query.split("&").find { it.startsWith("url=") }?.substring(4)
        } ?: return null
        return try {
            URLDecoder.decode(raw, "UTF-8")
        } catch (_: Exception) {
            raw
        }
    }

    fun stop() {
        isRunning.set(false)
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        proxyScope.cancel()
    }
}
