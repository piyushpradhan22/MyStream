package com.mystream.app.torrent

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.libtorrent4j.AlertListener
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.Sha1Hash
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.alerts.MetadataReceivedAlert
import org.libtorrent4j.alerts.TorrentFinishedAlert
import org.libtorrent4j.swig.settings_pack
import java.io.File
import java.util.BitSet
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

data class TorrentStreamStatus(
    val infoHash: String = "",
    val title: String = "",
    val state: String = "Idle",
    val seeds: Int = 0,
    val peers: Int = 0,
    val downloadRateBytes: Long = 0,
    val progress: Float = 0f,
    val isReadyForStreaming: Boolean = false,
    val streamUrl: String? = null,
    val videoFilePath: String? = null,
    val totalSizeBytes: Long = 0
)

class TorrentStreamEngine private constructor(private val context: Context) {

    companion object {
        private const val TAG = "TorrentStreamEngine"
        private const val STREAM_PORT = 8888
        private const val MIN_PIECES_BEFORE_PLAY = 3
        private const val MIN_INITIAL_CONTIGUOUS_PIECES = 2
        private const val MIN_CONTIGUOUS_FALLBACK_PIECES = 1
        private const val AUTOPLAY_FALLBACK_AFTER_MS = 8_000L
        private const val START_WINDOW_PIECES = 32
        private const val REQUEST_WINDOW_FORWARD_PIECES = 32
        private const val REQUEST_WINDOW_BACK_PIECES = 2

        @Volatile
        private var INSTANCE: TorrentStreamEngine? = null

        fun getInstance(context: Context): TorrentStreamEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TorrentStreamEngine(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val sessionManager = SessionManager()
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var httpServer: TorrentStreamHttpServer? = null
    private var currentHandle: TorrentHandle? = null
    private var monitorJob: Job? = null
    private var supportsPieceDeadlineApi: Boolean? = null
    private val requestedPieceHint = AtomicInteger(-1)
    private val requestedPieceHintAtMs = AtomicLong(0L)
    private val streamStartedAtMs = AtomicLong(0L)

    private val _statusFlow = MutableStateFlow(TorrentStreamStatus())
    val statusFlow = _statusFlow.asStateFlow()

    // Piece tracking — updated via PieceFinishedAlert + monitor loop, read by HTTP server
    @Volatile var downloadedPieceCount: Int = 0
    @Volatile var totalPieceCount: Int = 0
    private val _downloadedPieces = BitSet()
    val downloadedPieces: BitSet get() = _downloadedPieces

    private val torrentStorageDir: File
        get() = File(context.cacheDir, "torrent_stream").apply { mkdirs() }

    init {
        initSession()
    }

    private fun initSession() {
        try {
            val settings = SettingsPack()
            settings.setBoolean(settings_pack.bool_types.enable_dht.swigValue(), true)
            settings.setBoolean(settings_pack.bool_types.enable_lsd.swigValue(), true)
            settings.setBoolean(settings_pack.bool_types.enable_upnp.swigValue(), true)
            settings.setBoolean(settings_pack.bool_types.enable_natpmp.swigValue(), true)
            settings.setBoolean(settings_pack.bool_types.seeding_outgoing_connections.swigValue(), true)
            settings.setBoolean(settings_pack.bool_types.enable_outgoing_utp.swigValue(), true)
            settings.setBoolean(settings_pack.bool_types.enable_incoming_utp.swigValue(), true)
            settings.setBoolean(settings_pack.bool_types.dht_aggressive_lookups.swigValue(), true)
            settings.setBoolean(settings_pack.bool_types.dht_extended_routing_table.swigValue(), true)

            // High-throughput connection settings (Stremio-like fast peer discovery & download)
            settings.setInteger(settings_pack.int_types.connections_limit.swigValue(), 300)
            settings.setInteger(settings_pack.int_types.connection_speed.swigValue(), 150)
            settings.setInteger(settings_pack.int_types.active_downloads.swigValue(), 10)
            settings.setInteger(settings_pack.int_types.max_out_request_queue.swigValue(), 1500)
            settings.setInteger(settings_pack.int_types.max_allowed_in_request_queue.swigValue(), 1500)
            settings.setInteger(settings_pack.int_types.request_queue_time.swigValue(), 3)
            settings.setInteger(settings_pack.int_types.max_peerlist_size.swigValue(), 3000)
            settings.setInteger(settings_pack.int_types.unchoke_slots_limit.swigValue(), 40)
            settings.setInteger(settings_pack.int_types.download_rate_limit.swigValue(), 0) // Unlimited
            settings.setInteger(settings_pack.int_types.upload_rate_limit.swigValue(), 0) // Unlimited

            // DHT bootstrap routers
            settings.setString(
                settings_pack.string_types.dht_bootstrap_nodes.swigValue(),
                "router.bittorrent.com:6881,dht.transmissionbt.com:6881,router.utorrent.com:6881,dht.aelitis.com:6881,dht.libtorrent.org:25401"
            )

            // Subscribe to all alerts
            settings.setInteger(settings_pack.int_types.alert_mask.swigValue(), 0x7fffffff)

            val params = SessionParams(settings)
            sessionManager.start(params)
            Log.i(TAG, "BitTorrent session started successfully with high-throughput streaming settings.")

            sessionManager.addListener(object : AlertListener {
                override fun types(): IntArray? = null
                override fun alert(alert: Alert<*>?) {
                    when (alert) {
                        is MetadataReceivedAlert -> {
                            Log.i(TAG, "Metadata received for: ${alert.torrentName()}")
                            onMetadataReceived(alert.handle())
                        }
                        is org.libtorrent4j.alerts.PieceFinishedAlert -> {
                            val pIndex = alert.pieceIndex()
                            synchronized(_downloadedPieces) {
                                _downloadedPieces.set(pIndex)
                                downloadedPieceCount = _downloadedPieces.cardinality()
                            }
                            Log.d(TAG, "PieceFinishedAlert: piece $pIndex (total=${downloadedPieceCount})")
                        }
                        is TorrentFinishedAlert ->
                            Log.i(TAG, "Torrent finished: ${alert.torrentName()}")
                        else -> {}
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize BitTorrent session", e)
        }
    }

    fun startStreaming(infoHash: String, title: String, fileIndex: Int? = null) {
        scope.launch {
            stopStreaming()

            // Reset piece tracking
            synchronized(_downloadedPieces) { _downloadedPieces.clear() }
            downloadedPieceCount = 0
            totalPieceCount = 0
            requestedPieceHint.set(-1)
            requestedPieceHintAtMs.set(0L)
            streamStartedAtMs.set(System.currentTimeMillis())

            // Delete old cache to start fresh
            torrentStorageDir.deleteRecursively()
            torrentStorageDir.mkdirs()

            _statusFlow.value = TorrentStreamStatus(
                infoHash = infoHash,
                title = title,
                state = "Connecting to peers..."
            )

            startHttpServer()

            val magnetUrl = buildMagnetUri(infoHash, title)
            Log.i(TAG, "Starting P2P stream for $title ($infoHash)")

            try {
                val flags = TorrentFlags.SEQUENTIAL_DOWNLOAD.or_(TorrentFlags.AUTO_MANAGED)
                sessionManager.download(magnetUrl, torrentStorageDir, flags)

                var handle: TorrentHandle? = null
                var attempts = 0
                while (handle == null && attempts < 40) {
                    handle = sessionManager.find(Sha1Hash.parseHex(infoHash))
                    if (handle != null) break
                    delay(300)
                    attempts++
                }

                if (handle == null) {
                    _statusFlow.value = _statusFlow.value.copy(state = "Error: Could not join swarm")
                    return@launch
                }

                currentHandle = handle
                startMonitorLoop(handle, title, infoHash)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to start torrent download", e)
                _statusFlow.value = _statusFlow.value.copy(state = "Error: ${e.localizedMessage}")
            }
        }
    }

    private fun onMetadataReceived(handle: TorrentHandle?) {
        if (handle == null || !handle.isValid) return
        val torrentInfo = handle.torrentFile() ?: return

        val files = torrentInfo.files()
        var largestIndex = 0
        var largestSize = 0L

        for (i in 0 until files.numFiles()) {
            val size = files.fileSize(i)
            val name = files.fileName(i).lowercase()
            val isVideo = name.endsWith(".mkv") || name.endsWith(".mp4") ||
                    name.endsWith(".avi") || name.endsWith(".ts")
            if (isVideo && size > largestSize) {
                largestSize = size
                largestIndex = i
            }
        }

        val filePriorities = Array(files.numFiles()) { Priority.IGNORE }
        filePriorities[largestIndex] = Priority.TOP_PRIORITY
        handle.prioritizeFiles(filePriorities)

        val pieceLength = torrentInfo.pieceLength().toLong()
        totalPieceCount = torrentInfo.numPieces()

        // Aggressively prioritize initial pieces so playback can actually start.
        for (p in 0 until minOf(START_WINDOW_PIECES, totalPieceCount)) handle.piecePriority(p, Priority.TOP_PRIORITY)
        for (p in maxOf(0, totalPieceCount - 8) until totalPieceCount) handle.piecePriority(p, Priority.TOP_PRIORITY)
        handle.setSequentialRange(0)
        forceStartWindowDeadlines(handle, 0)

        val relativePath = files.filePath(largestIndex)
        val fullVideoFile = File(torrentStorageDir, relativePath)

        httpServer?.setActiveFile(fullVideoFile, this, pieceLength, largestSize)

        _statusFlow.value = _statusFlow.value.copy(
            state = "Buffering 0 / $MIN_PIECES_BEFORE_PLAY pieces",
            videoFilePath = fullVideoFile.absolutePath,
            streamUrl = "http://127.0.0.1:$STREAM_PORT/stream",
            totalSizeBytes = largestSize
        )

        Log.i(TAG, "Video: $relativePath ($largestSize bytes, pieceLen=${pieceLength / 1024}KB, numPieces=$totalPieceCount)")
    }

    /**
     * Single polling loop — all JNI calls happen here only, never from HTTP threads.
     * Uses havePiece() + status() to track piece downloads and speed.
     */
    private fun startMonitorLoop(handle: TorrentHandle, title: String, infoHash: String) {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            var debugLogged = false
            while (isActive) {
                try {
                    if (!handle.isValid) break

                    val status = handle.status()
                    val numPieces = totalPieceCount

                    // Use havePiece() for reliable per-piece check
                    if (numPieces > 0) {
                        var count = 0
                        synchronized(_downloadedPieces) {
                            for (p in 0 until numPieces) {
                                if (!_downloadedPieces[p] && handle.havePiece(p)) {
                                    _downloadedPieces.set(p)
                                }
                            }
                            count = _downloadedPieces.cardinality()
                        }
                        downloadedPieceCount = count

                        // Log first detection
                        if (!debugLogged && count > 0) {
                            val first = synchronized(_downloadedPieces) { _downloadedPieces.nextSetBit(0) }
                            Log.i(TAG, "First piece detected: piece $first, total downloaded=$count")
                            debugLogged = true
                        }
                    }

                    val downloaded = downloadedPieceCount
                    val isReady = _statusFlow.value.isReadyForStreaming

                    val contiguousFromStart = if (numPieces > 0) {
                        synchronized(_downloadedPieces) {
                            var idx = 0
                            while (idx < numPieces && _downloadedPieces[idx]) idx++
                            idx
                        }
                    } else 0

                    val dlRateBytes = status?.downloadRate()?.toLong() ?: 0L
                    val dlRateStr = when {
                        dlRateBytes > 1_000_000 -> "%.1f MB/s".format(dlRateBytes / 1_000_000.0)
                        dlRateBytes > 1_000 -> "${dlRateBytes / 1000} KB/s"
                        else -> "$dlRateBytes B/s"
                    }

                    val elapsedMs = System.currentTimeMillis() - streamStartedAtMs.get()
                    val hasStartPiece = synchronized(_downloadedPieces) { _downloadedPieces.get(0) }
                    val contiguousReady = contiguousFromStart >= MIN_INITIAL_CONTIGUOUS_PIECES
                    val instantPieceReady = hasStartPiece && downloaded >= 2
                    val fallbackReady = elapsedMs >= AUTOPLAY_FALLBACK_AFTER_MS && (hasStartPiece || downloaded >= MIN_PIECES_BEFORE_PLAY)

                    val nowReady = isReady || contiguousReady || instantPieceReady || fallbackReady

                    val newState = when {
                        isReady -> "Streaming P2P • $dlRateStr"
                        nowReady -> "Ready to Play"
                        numPieces > 0 -> "Buffering Start $contiguousFromStart/$MIN_INITIAL_CONTIGUOUS_PIECES • Total $downloaded • $dlRateStr"
                        else -> "Connecting... $dlRateStr"
                    }

                    // Keep nudging missing start pieces (header) with urgent deadline
                    if (!hasStartPiece && numPieces > 0) {
                        boostMissingStartPieces(handle, 0, numPieces)
                    } else if (!nowReady && numPieces > 0) {
                        boostMissingStartPieces(handle, contiguousFromStart, numPieces)
                    }

                    // Prioritize currently requested playback window so startup/seek catches up faster.
                    if (numPieces > 0) {
                        prioritizeRequestedPlaybackWindow(handle, numPieces)
                    }

                    _statusFlow.value = _statusFlow.value.copy(
                        infoHash = infoHash,
                        title = title,
                        seeds = status?.numSeeds() ?: 0,
                        peers = status?.numPeers() ?: 0,
                        downloadRateBytes = dlRateBytes,
                        progress = status?.progress() ?: 0f,
                        state = newState,
                        isReadyForStreaming = nowReady
                    )

                    if (!isReady && nowReady) {
                        Log.i(TAG, "🎉 Ready: downloaded=$downloaded, contiguousFromStart=$contiguousFromStart, elapsedMs=$elapsedMs — launching player!")
                    }

                } catch (e: Exception) {
                    val message = e.message ?: "unknown error"
                    Log.w(TAG, "Monitor loop error: $message")
                    if (message.contains("invalid torrent handle", ignoreCase = true)) {
                        _statusFlow.value = _statusFlow.value.copy(
                            state = "Torrent handle invalidated, reconnecting...",
                            isReadyForStreaming = false
                        )
                        break
                    }
                }
                delay(250)
            }
        }
    }

    private fun boostMissingStartPieces(handle: TorrentHandle, contiguousFromStart: Int, totalPieces: Int) {
        val from = contiguousFromStart
        val toExclusive = minOf(from + 8, totalPieces)
        for (p in from until toExclusive) {
            try {
                handle.piecePriority(p, Priority.TOP_PRIORITY)
                handle.setPieceDeadline(p, (p - from) * 50, TorrentHandle.ALERT_WHEN_AVAILABLE)
            } catch (_: Exception) {}
        }
    }

    private fun prioritizeRequestedPlaybackWindow(handle: TorrentHandle, totalPieces: Int) {
        val requestedPiece = requestedPieceHint.get()
        if (requestedPiece < 0 || requestedPiece >= totalPieces) return

        val ageMs = System.currentTimeMillis() - requestedPieceHintAtMs.get()
        if (ageMs > 30_000L) return

        val from = (requestedPiece - REQUEST_WINDOW_BACK_PIECES).coerceAtLeast(0)
        val toExclusive = (requestedPiece + REQUEST_WINDOW_FORWARD_PIECES).coerceAtMost(totalPieces)

        // Clear stale deadlines from other parts of the torrent so all peer bandwidth converges here
        try { handle.clearPieceDeadlines() } catch (_: Exception) {}

        // Immediate urgency (deadline 0..150ms) for the playback piece and next 8 pieces
        for (p in from until minOf(from + 8, totalPieces)) {
            try {
                handle.piecePriority(p, Priority.TOP_PRIORITY)
                handle.setPieceDeadline(p, (p - from) * 20, TorrentHandle.ALERT_WHEN_AVAILABLE)
            } catch (_: Exception) {}
        }

        // High priority for the next 24 pieces in the buffer pipeline
        for (p in minOf(from + 8, totalPieces) until toExclusive) {
            try {
                handle.piecePriority(p, Priority.SIX)
            } catch (_: Exception) {}
        }

        // Sequential download range centered on playback
        try {
            handle.setSequentialRange(requestedPiece, toExclusive)
        } catch (_: Exception) {
            try { handle.setSequentialRange(requestedPiece) } catch (_: Exception) {}
        }
    }

    fun onPieceRequested(pieceIndex: Int) {
        if (pieceIndex < 0) return
        requestedPieceHint.set(pieceIndex)
        requestedPieceHintAtMs.set(System.currentTimeMillis())
        currentHandle?.let { handle ->
            if (handle.isValid && totalPieceCount > 0) {
                prioritizeRequestedPlaybackWindow(handle, totalPieceCount)
            }
        }
    }

    private fun forceStartWindowDeadlines(handle: TorrentHandle, fromPiece: Int) {
        val end = minOf(fromPiece + 12, totalPieceCount)
        for (p in fromPiece until end) {
            try {
                handle.piecePriority(p, Priority.TOP_PRIORITY)
                handle.setPieceDeadline(p, (p - fromPiece) * 50, TorrentHandle.ALERT_WHEN_AVAILABLE)
            } catch (_: Exception) {}
        }
        // Last 4 pieces (metadata/index)
        for (p in maxOf(0, totalPieceCount - 4) until totalPieceCount) {
            try {
                handle.piecePriority(p, Priority.TOP_PRIORITY)
                handle.setPieceDeadline(p, 100, TorrentHandle.ALERT_WHEN_AVAILABLE)
            } catch (_: Exception) {}
        }
    }

    private fun startHttpServer() {
        if (httpServer == null) {
            try {
                httpServer = TorrentStreamHttpServer(STREAM_PORT)
                httpServer?.start()
                Log.i(TAG, "Localhost stream server started on :$STREAM_PORT")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start HTTP server", e)
            }
        }
    }

    fun purgeTorrentCache() {
        try {
            val dir = torrentStorageDir
            if (dir.exists()) {
                dir.listFiles()?.forEach { file ->
                    try { file.deleteRecursively() } catch (_: Exception) {}
                }
                Log.i(TAG, "Purged torrent stream cache files from disk")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error purging torrent cache: ${e.message}")
        }
    }

    fun stopStreaming() {
        monitorJob?.cancel()
        monitorJob = null
        currentHandle?.let { handle ->
            try {
                if (handle.isValid) {
                    try {
                        sessionManager.remove(handle, org.libtorrent4j.SessionHandle.DELETE_FILES)
                    } catch (_: Exception) {
                        sessionManager.remove(handle)
                    }
                }
            } catch (_: Exception) {}
        }
        currentHandle = null
        httpServer?.setActiveFile(null, null, 0L, 0L)
        _statusFlow.value = TorrentStreamStatus(state = "Idle")

        scope.launch(Dispatchers.IO) {
            delay(500)
            purgeTorrentCache()
        }
    }

    private fun buildMagnetUri(infoHash: String, name: String): String {
        val trackers = listOf(
            "udp://tracker.opentrackr.org:1337/announce",
            "udp://open.tracker.cl:1337/announce",
            "udp://open.demonii.com:1337/announce",
            "udp://tracker.openbittorrent.com:6969/announce",
            "udp://tracker.torrent.eu.org:451/announce",
            "udp://explodie.org:6969/announce",
            "udp://tracker.coppersurfer.tk:6969/announce",
            "udp://tracker.leechers-paradise.org:6969/announce",
            "udp://p4p.arenabg.com:1337/announce",
            "udp://movies.zsw.ca:6969/announce",
            "udp://tracker.dler.org:6969/announce",
            "udp://9.rarbg.to:2920/announce",
            "udp://9.rarbg.me:2970/announce",
            "http://tracker.openbittorrent.com:80/announce",
            "http://tracker.opentrackr.org:1337/announce"
        )
        val trParams = trackers.joinToString("") { "&tr=${android.net.Uri.encode(it)}" }
        val dnParam = if (name.isNotBlank()) "&dn=${android.net.Uri.encode(name)}" else ""
        return "magnet:?xt=urn:btih:$infoHash$dnParam$trParams"
    }
}
