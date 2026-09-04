package com.mystream.app.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.ui.AspectRatioFrameLayout
import com.mystream.app.data.model.MediaPlaybackItem
import com.mystream.app.data.model.PlayerTrackInfo
import com.mystream.app.data.model.VideoAspectRatio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
class MyStreamPlayerManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job()),
    val sourcesRepository: com.mystream.app.data.repository.SourcesRepository? = null
) {
    companion object {
        private const val TAG = "MyStream_Player"
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    private val bandwidthMeter = DefaultBandwidthMeter.Builder(context).build()

    private val trackSelector = DefaultTrackSelector(context).apply {
        setParameters(
            buildUponParameters()
                .setPreferredAudioLanguages("hin", "hi", "hindi", "eng", "en", "english")
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .setSelectUndeterminedTextLanguage(false)
                .setExceedVideoConstraintsIfNecessary(true)
                .setExceedRendererCapabilitiesIfNecessary(false) // Never force-pick unsupported audio tracks (e.g. TrueHD 7.1)
                .setExceedAudioConstraintsIfNecessary(true)
                .setAllowVideoMixedMimeTypeAdaptiveness(true)
                .setAllowVideoNonSeamlessAdaptiveness(true)
                .setMaxVideoBitrate(Int.MAX_VALUE)
                .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
                .setMaxVideoFrameRate(Int.MAX_VALUE)
        )
    }

    private val renderersFactory = DefaultRenderersFactory(context).apply {
        setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        setEnableDecoderFallback(true)
        setMediaCodecSelector(MediaCodecSelector.DEFAULT)
    }

    private var isCurrentStreamArchive = false
    private var hasTriggeredThaw = false
    private val _isArchiveActivating = MutableStateFlow(false)
    val isArchiveActivating: StateFlow<Boolean> = _isArchiveActivating.asStateFlow()
    private val _archiveRetryCount = MutableStateFlow(0)
    val archiveRetryCount: StateFlow<Int> = _archiveRetryCount.asStateFlow()

    private val playerOkHttpClient = OkHttpClient.Builder()
        .dns(com.mystream.app.data.api.SystemFallbackDns)
        .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
        .connectionPool(okhttp3.ConnectionPool(8, 5, TimeUnit.MINUTES))
        .addNetworkInterceptor { chain ->
            val req = chain.request()
            Log.d(TAG, "Stream HTTP -> ${req.method} ${req.url} (range=${req.header("Range")})")
            try {
                val res = chain.proceed(req)
                val xosErr = res.header("x-xos-err-desc") ?: res.header("X-Xos-Err-Desc")
                val ossStorage = res.header("x-oss-storage-class") ?: res.header("X-Oss-Storage-Class")
                val cosStorage = res.header("x-cos-storage-class") ?: res.header("X-Cos-Storage-Class")
                val isStorageArchive = (ossStorage?.contains("Archive", ignoreCase = true) == true) ||
                        (cosStorage?.contains("ARCHIVE", ignoreCase = true) == true)
                if (isStorageArchive) {
                    isCurrentStreamArchive = true
                    if (!hasTriggeredThaw) {
                        hasTriggeredThaw = true
                        sourcesRepository?.let { repo ->
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    Log.i(TAG, "Triggering PikPak control plane thaw for archive stream: ${req.url}")
                                    repo.pikpakResolver.triggerArchiveThawForUrl(req.url.toString())
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed to trigger control plane thaw: ${e.message}")
                                }
                            }
                        }
                    }
                }
                val isFrozen = (xosErr == "5") || (isStorageArchive && xosErr != "0")

                Log.i(TAG, "Stream HTTP <- ${res.code} (len=${res.header("Content-Length")}, range=${res.header("Content-Range")}, xosErr=${xosErr}, storage=${ossStorage ?: cosStorage}, type=${res.header("Content-Type")})")
                if (isFrozen) {
                    _isArchiveActivating.value = true
                    _isBuffering.value = true
                } else if (res.isSuccessful && !isCurrentStreamArchive && (xosErr == "0" || (!isStorageArchive && xosErr == null))) {
                    _isArchiveActivating.value = false
                }
                res
            } catch (e: Exception) {
                val msg = e.message.orEmpty().lowercase()
                if (msg.contains("unexpected end of stream") || msg.contains("protocol") || msg.contains("broken pipe")) {
                    _isArchiveActivating.value = true
                    _isBuffering.value = true
                }
                Log.e(TAG, "Stream HTTP FAIL -> ${e.message} for ${req.url}", e)
                throw e
            }
        }
        .build()

    private val liveBytesCounter = java.util.concurrent.atomic.AtomicLong(0L)

    private val speedTransferListener = object : androidx.media3.datasource.TransferListener {
        override fun onTransferInitializing(source: androidx.media3.datasource.DataSource, dataSpec: androidx.media3.datasource.DataSpec, isNetwork: Boolean) {}
        override fun onTransferStart(source: androidx.media3.datasource.DataSource, dataSpec: androidx.media3.datasource.DataSpec, isNetwork: Boolean) {}
        override fun onBytesTransferred(source: androidx.media3.datasource.DataSource, dataSpec: androidx.media3.datasource.DataSpec, isNetwork: Boolean, bytesTransferred: Int) {
            if (isNetwork) {
                liveBytesCounter.addAndGet(bytesTransferred.toLong())
            }
        }
        override fun onTransferEnd(source: androidx.media3.datasource.DataSource, dataSpec: androidx.media3.datasource.DataSpec, isNetwork: Boolean) {}
    }

    private val httpDataSourceFactory = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(playerOkHttpClient)
        .setUserAgent("ANDROID-com.pikcloud.pikpak/1.47.1")
        .setTransferListener(speedTransferListener)

    private val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory().apply {
        setConstantBitrateSeekingEnabled(true)
        setTsExtractorFlags(
            androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
            androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
        )
        setTsExtractorTimestampSearchBytes(188 * 1024)
    }

    private val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(context, httpDataSourceFactory)

    private val mediaSourceFactory = DefaultMediaSourceFactory(context, extractorsFactory)
        .setDataSourceFactory(dataSourceFactory)
        .setLoadErrorHandlingPolicy(object : DefaultLoadErrorHandlingPolicy() {
            override fun getMinimumLoadableRetryCount(dataType: Int): Int {
                if (isCurrentStreamArchive || _isArchiveActivating.value) return 180
                return 8
            }

            override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
                val ex = loadErrorInfo.exception
                val isNetworkDrop = ex is java.net.ProtocolException || 
                                    ex is java.net.SocketException ||
                                    ex is java.net.SocketTimeoutException ||
                                    ex is androidx.media3.datasource.HttpDataSource.HttpDataSourceException
                
                if (isCurrentStreamArchive || _isArchiveActivating.value) {
                    _isArchiveActivating.value = true
                    _isBuffering.value = true
                    val count = loadErrorInfo.errorCount
                    _archiveRetryCount.value = count
                    if (count <= 180) {
                        val delayMs = when {
                            count <= 20 -> 2500L
                            count <= 60 -> 3000L
                            else -> 4000L
                        }
                        Log.w(TAG, "Archive stream thawing retry #$count/180 (delay=${delayMs}ms): ${ex.message}")
                        return delayMs
                    }
                    return C.TIME_UNSET
                }
                
                if (isNetworkDrop && loadErrorInfo.errorCount <= 8) {
                    Log.w(TAG, "Network stream retry #${loadErrorInfo.errorCount}/8 (delay=1500ms): ${ex.message}")
                    return 1500L
                }
                
                val base = super.getRetryDelayMsFor(loadErrorInfo)
                return when {
                    base == C.TIME_UNSET -> 1000L
                    else -> base.coerceIn(500L, 3000L)
                }
            }
        })

    private val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            /* minBufferMs = */ 15_000,
            /* maxBufferMs = */ 30_000,
            /* bufferForPlaybackMs = */ 2_000,
            /* bufferForPlaybackAfterRebufferMs = */ 3_000
        )
        .setTargetBufferBytes(25 * 1024 * 1024) // 25MB max buffer memory footprint
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()

    val player: ExoPlayer = ExoPlayer.Builder(context, renderersFactory)
        .setTrackSelector(trackSelector)
        .setMediaSourceFactory(mediaSourceFactory)
        .setBandwidthMeter(bandwidthMeter)
        .setLoadControl(loadControl)
        .setAudioAttributes(
            androidx.media3.common.AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            true // Handle audio focus automatically
        )
        .setHandleAudioBecomingNoisy(true) // Auto pause on headphones disconnect
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build()

    // --- State Flows ---
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(true)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _bufferedPosition = MutableStateFlow(0L)
    val bufferedPosition: StateFlow<Long> = _bufferedPosition.asStateFlow()

    private val _downloadSpeed = MutableStateFlow("0 KB/s")
    val downloadSpeed: StateFlow<String> = _downloadSpeed.asStateFlow()

    private val _bufferPercentage = MutableStateFlow(0)
    val bufferPercentage: StateFlow<Int> = _bufferPercentage.asStateFlow()

    private val _aspectRatio = MutableStateFlow(VideoAspectRatio.FIT)
    val aspectRatio: StateFlow<VideoAspectRatio> = _aspectRatio.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _audioTracks = MutableStateFlow<List<PlayerTrackInfo>>(emptyList())
    val audioTracks: StateFlow<List<PlayerTrackInfo>> = _audioTracks.asStateFlow()

    private val _subtitleTracks = MutableStateFlow<List<PlayerTrackInfo>>(emptyList())
    val subtitleTracks: StateFlow<List<PlayerTrackInfo>> = _subtitleTracks.asStateFlow()

    private val _isSubtitleEnabled = MutableStateFlow(false)
    val isSubtitleEnabled: StateFlow<Boolean> = _isSubtitleEnabled.asStateFlow()

    private val _currentItem = MutableStateFlow<MediaPlaybackItem?>(null)
    val currentItem: StateFlow<MediaPlaybackItem?> = _currentItem.asStateFlow()

    private val _videoResolutions = MutableStateFlow<List<String>>(emptyList())
    val videoResolutions: StateFlow<List<String>> = _videoResolutions.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var tickerJob: Job? = null
    private var streamDurationFallbackMs = 0L

    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                pause()
            }
        }
    }

    init {
        setupPlayerListeners()
        try {
            val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            context.registerReceiver(becomingNoisyReceiver, filter)
        } catch (_: Exception) {
            // ignore if register fails
        }
        startProgressTicker()
    }

    private fun setupPlayerListeners() {
        player.addListener(object : Player.Listener {
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                val dur = player.duration
                if (dur > 0L && dur != C.TIME_UNSET) {
                    _duration.value = dur
                } else if (streamDurationFallbackMs > 0L) {
                    _duration.value = streamDurationFallbackMs
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                Log.d(TAG, "onIsPlayingChanged: $isPlaying")
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _isBuffering.value = (playbackState == Player.STATE_BUFFERING)
                Log.d(TAG, "onPlaybackStateChanged: $playbackState (Buffering=${_isBuffering.value})")
                if (playbackState == Player.STATE_BUFFERING && isCurrentStreamArchive) {
                    _isArchiveActivating.value = true
                }
                if (playbackState == Player.STATE_READY) {
                    val dur = player.duration
                    if (dur > 0L && dur != C.TIME_UNSET) {
                        _duration.value = dur
                    } else if (streamDurationFallbackMs > 0L) {
                        _duration.value = streamDurationFallbackMs
                    }
                    _duration.value = _duration.value.coerceAtLeast(0L)
                    _isBuffering.value = false
                    _errorMessage.value = null
                    _isArchiveActivating.value = false
                    _archiveRetryCount.value = 0
                    Log.i(TAG, "Player is READY, exoDuration=${player.duration}ms, effectiveDuration=${_duration.value}ms")
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "Player error occurred: ${error.message}", error)
                _isPlaying.value = false
                _isBuffering.value = false
                val isArchiveFail = isCurrentStreamArchive || _isArchiveActivating.value
                _isArchiveActivating.value = false
                val msg = if (isArchiveFail || error.message?.contains("unexpected end of stream", ignoreCase = true) == true) {
                    "Cold storage archive is thawing on cloud servers (~1-2 mins). Please wait or select another stream."
                } else {
                    error.message ?: "Playback error. Please try another stream."
                }
                _errorMessage.value = msg
            }

            override fun onTracksChanged(tracks: Tracks) {
                updateTracks(tracks)
            }
        })
    }

    fun saveCurrentProgress() {
        val current = _currentItem.value ?: return
        val pos = player.currentPosition.coerceAtLeast(0L)
        val dur = player.duration.coerceAtLeast(0L)
        if (pos > 5000L && dur > 0L) {
            val mediaId = if (current.isSeries) "${current.id}:${current.seasonNumber}:${current.episodeNumber}" else current.id
            scope.launch(Dispatchers.IO) {
                sourcesRepository?.savePlaybackProgress(
                    mediaId = mediaId,
                    imdbId = current.id,
                    title = current.title,
                    subtitle = current.subtitle,
                    posterUrl = current.posterUrl,
                    backdropUrl = current.backdropUrl,
                    type = if (current.isSeries) "series" else "movie",
                    seasonNumber = current.seasonNumber,
                    episodeNumber = current.episodeNumber,
                    positionMs = pos,
                    durationMs = dur
                )
            }
        }
    }

    private fun startProgressTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            var lastSaveTick = 0L
            var lastSpeedCalcTime = System.currentTimeMillis()
            while (isActive) {
                if (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING) {
                    _currentPosition.value = player.currentPosition.coerceAtLeast(0L)
                    val exoDur = player.duration
                    val effectiveDur = if (exoDur > 0L && exoDur != C.TIME_UNSET) {
                        exoDur
                    } else if (streamDurationFallbackMs > 0L) {
                        streamDurationFallbackMs
                    } else {
                        0L
                    }
                    _duration.value = effectiveDur
                    _bufferedPosition.value = player.bufferedPosition.coerceAtLeast(0L)
                    _bufferPercentage.value = player.bufferedPercentage

                    val now = System.currentTimeMillis()
                    val elapsedMs = (now - lastSpeedCalcTime).coerceAtLeast(1L)
                    val bytesReadInWindow = liveBytesCounter.getAndSet(0L)
                    val liveBytesPerSec = (bytesReadInWindow * 1000.0) / elapsedMs
                    lastSpeedCalcTime = now

                    val fallbackBytesPerSec = bandwidthMeter.bitrateEstimate / 8.0
                    val currentBps = if (liveBytesPerSec > 0) liveBytesPerSec else (if (player.isPlaying) fallbackBytesPerSec else 0.0)

                    val speedStr = when {
                        currentBps > 1024 * 1024 -> "%.1f MB/s".format(currentBps / (1024 * 1024))
                        currentBps > 1024 -> "%.0f KB/s".format(currentBps / 1024)
                        currentBps > 0 -> "%.0f KB/s".format(currentBps.coerceAtLeast(1.0))
                        else -> if (player.isPlaying) "Active" else "Idle"
                    }
                    _downloadSpeed.value = speedStr

                    if (now - lastSaveTick > 4000L && player.isPlaying) {
                        lastSaveTick = now
                        saveCurrentProgress()
                    }
                }
                delay(350)
            }
        }
    }

    fun playMedia(item: MediaPlaybackItem, startPositionMs: Long? = null) {
        saveCurrentProgress()
        _currentItem.value = item
        _isBuffering.value = true
        hasTriggeredThaw = false
        val isItemArchive = item.subtitle?.contains("ARC", ignoreCase = true) == true ||
                item.title.contains("ARC", ignoreCase = true) ||
                item.mediaUrl.contains("storage=Archive", ignoreCase = true)
        isCurrentStreamArchive = isItemArchive
        _isArchiveActivating.value = isItemArchive
        _archiveRetryCount.value = 0
        _errorMessage.value = null
        if (isItemArchive) {
            hasTriggeredThaw = true
            sourcesRepository?.let { repo ->
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        Log.i(TAG, "Pre-triggering PikPak control plane thaw for ${item.title}")
                        repo.pikpakResolver.triggerArchiveThawForUrl(item.mediaUrl)
                    } catch (e: Exception) {
                        Log.w(TAG, "Pre-trigger thaw error: ${e.message}")
                    }
                }
            }
        }
        val actualStartPos = startPositionMs ?: item.startPositionMs

        val urlDuration = Regex("""[?&](?:ms|th)=(\d+)""").find(item.mediaUrl)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        streamDurationFallbackMs = if (urlDuration > 0L) urlDuration else 0L
        if (streamDurationFallbackMs > 0L) {
            _duration.value = streamDurationFallbackMs
        }
        Log.i(TAG, "Playing media (start at ${actualStartPos}ms, urlDuration=${streamDurationFallbackMs}ms): ${item.title} -> ${item.mediaUrl}")

        val reqProps = mutableMapOf<String, String>()
        reqProps["User-Agent"] = "ANDROID-com.pikcloud.pikpak/1.47.1"
        if (item.mediaUrl.contains("mypikpak.com/download/", ignoreCase = true)) {
            reqProps["Accept-Encoding"] = "identity"
        }
        item.headers?.forEach { (k, v) -> 
            if (!k.equals("Range", ignoreCase = true)) {
                reqProps[k] = v
            }
        }
        httpDataSourceFactory.setDefaultRequestProperties(reqProps)
        Log.d(TAG, "Stream request props: acceptEncoding=${reqProps["Accept-Encoding"] ?: "default"}")

        val uri = Uri.parse(item.mediaUrl)

        val metadata = MediaMetadata.Builder()
            .setTitle(item.title)
            .setSubtitle(item.subtitle)
            .setDisplayTitle(item.title)
            .setArtworkUri(item.posterUrl?.let { Uri.parse(it) } ?: item.backdropUrl?.let { Uri.parse(it) })
            .build()

        val mediaItemBuilder = MediaItem.Builder()
            .setUri(uri)
            .setMediaId(item.id)
            .setMediaMetadata(metadata)

        val titleLower = item.title.lowercase()
        val urlLower = item.mediaUrl.lowercase()
        if (titleLower.contains(".mp4") || urlLower.contains(".mp4")) {
            mediaItemBuilder.setMimeType(androidx.media3.common.MimeTypes.VIDEO_MP4)
        } else if (titleLower.contains(".mkv") || urlLower.contains(".mkv")) {
            mediaItemBuilder.setMimeType(androidx.media3.common.MimeTypes.VIDEO_MATROSKA)
        }

        val mediaItem = mediaItemBuilder.build()

        player.setMediaItem(mediaItem, actualStartPos)
        player.prepare()
        player.playWhenReady = true
    }

    fun play() {
        player.play()
    }

    fun pause() {
        saveCurrentProgress()
        player.pause()
    }

    fun togglePlayPause() {
        if (player.isPlaying) pause() else play()
    }

    fun seekTo(positionMs: Long) {
        val requested = positionMs.coerceAtLeast(0L)
        val durationCap = if (_duration.value > 0L) _duration.value else (player.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: Long.MAX_VALUE)
        val target = if (durationCap > 0L) requested.coerceIn(0L, durationCap) else requested
        Log.i(TAG, "Seeking to ${target}ms (duration=${_duration.value}ms)")
        player.seekTo(target)
    }

    fun seekForward(seconds: Long = 10) {
        val current = if (_currentPosition.value > 0L) _currentPosition.value else player.currentPosition
        val newPos = current + (seconds * 1000)
        seekTo(newPos)
    }

    fun seekBack(seconds: Long = 10) {
        val current = if (_currentPosition.value > 0L) _currentPosition.value else player.currentPosition
        val newPos = (current - (seconds * 1000)).coerceAtLeast(0L)
        seekTo(newPos)
    }

    fun setAspectRatio(ratio: VideoAspectRatio) {
        _aspectRatio.value = ratio
    }

    fun cycleAspectRatio() {
        val next = when (_aspectRatio.value) {
            VideoAspectRatio.FIT -> VideoAspectRatio.ZOOM
            VideoAspectRatio.ZOOM -> VideoAspectRatio.STRETCH
            VideoAspectRatio.STRETCH -> VideoAspectRatio.ORIGINAL
            VideoAspectRatio.ORIGINAL -> VideoAspectRatio.FIT
        }
        _aspectRatio.value = next
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        player.playbackParameters = PlaybackParameters(speed)
    }

    fun cyclePlaybackSpeed() {
        val next = when (_playbackSpeed.value) {
            1.0f -> 1.25f
            1.25f -> 1.5f
            1.5f -> 2.0f
            2.0f -> 0.75f
            else -> 1.0f
        }
        setPlaybackSpeed(next)
    }

    private fun updateTracks(tracks: Tracks) {
        val audioList = mutableListOf<PlayerTrackInfo>()
        val subList = mutableListOf<PlayerTrackInfo>()
        val resolutions = mutableListOf<String>()

        for (trackGroup in tracks.groups) {
            val trackType = trackGroup.type
            val mediaTrackGroup = trackGroup.mediaTrackGroup

            for (i in 0 until mediaTrackGroup.length) {
                val format = mediaTrackGroup.getFormat(i)
                val isSelected = trackGroup.isTrackSelected(i)

                when (trackType) {
                    C.TRACK_TYPE_AUDIO -> {
                        val language = format.language?.let { Locale(it).displayLanguage } ?: "Audio ${audioList.size + 1}"
                        val channels = when (format.channelCount) {
                            6 -> "5.1"
                            8 -> "7.1 Atmos"
                            2 -> "Stereo"
                            else -> "${format.channelCount}ch"
                        }
                        val isSupported = trackGroup.isTrackSupported(i)
                        val title = format.label ?: "$language ($channels)"
                        audioList.add(
                            PlayerTrackInfo(
                                id = format.id ?: i.toString(),
                                index = i,
                                groupIndex = tracks.groups.indexOf(trackGroup),
                                label = title,
                                language = format.language,
                                isSelected = isSelected,
                                mimeType = format.sampleMimeType,
                                channels = format.channelCount,
                                bitrate = format.bitrate,
                                isSupported = isSupported
                            )
                        )
                    }
                    C.TRACK_TYPE_TEXT -> {
                        val language = format.language?.let { Locale(it).displayLanguage } ?: "Subtitle ${subList.size + 1}"
                        val title = format.label ?: language
                        subList.add(
                            PlayerTrackInfo(
                                id = format.id ?: i.toString(),
                                index = i,
                                groupIndex = tracks.groups.indexOf(trackGroup),
                                label = title,
                                language = format.language,
                                isSelected = isSelected,
                                mimeType = format.sampleMimeType,
                                isSupported = true
                            )
                        )
                    }
                    C.TRACK_TYPE_VIDEO -> {
                        if (format.width > 0 && format.height > 0) {
                            val resLabel = "${format.height}p"
                            if (!resolutions.contains(resLabel)) {
                                resolutions.add(resLabel)
                            }
                        }
                    }
                }
            }
        }

        _audioTracks.value = audioList
        _subtitleTracks.value = subList
        _videoResolutions.value = resolutions
        _isSubtitleEnabled.value = subList.any { it.isSelected }

        // Make sure we go with the default/1st supported audio track if no track was selected by language preferences
        if (audioList.isNotEmpty()) {
            val selectedAudio = audioList.firstOrNull { it.isSelected }
            if (selectedAudio == null) {
                val fallbackTrack = audioList.firstOrNull { it.isSupported } ?: audioList.first()
                Log.i(TAG, "No audio track selected by language preferences. Auto-selecting default/1st audio track: ${fallbackTrack.label}")
                selectAudioTrack(fallbackTrack)
            }
        }
    }

    fun selectAudioTrack(trackInfo: PlayerTrackInfo) {
        if (!trackInfo.isSupported) {
            Log.w(TAG, "Audio track '${trackInfo.label}' (${trackInfo.mimeType}) is unsupported by device decoder hardware.")
            return
        }
        val tracks = player.currentTracks
        val group = tracks.groups.getOrNull(trackInfo.groupIndex)?.mediaTrackGroup ?: return
        trackSelector.parameters = trackSelector.parameters
            .buildUpon()
            .setOverrideForType(
                TrackSelectionOverride(group, trackInfo.index)
            )
            .build()
    }

    fun selectSubtitleTrack(trackInfo: PlayerTrackInfo?) {
        if (trackInfo == null) {
            disableSubtitles()
            return
        }
        val tracks = player.currentTracks
        val group = tracks.groups.getOrNull(trackInfo.groupIndex)?.mediaTrackGroup ?: return
        trackSelector.parameters = trackSelector.parameters
            .buildUpon()
            .setOverrideForType(
                TrackSelectionOverride(group, trackInfo.index)
            )
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .build()
        _isSubtitleEnabled.value = true
    }

    fun disableSubtitles() {
        trackSelector.parameters = trackSelector.parameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
        _isSubtitleEnabled.value = false
    }

    fun applyTrackPreferences(
        preferredAudio: String = "Hindi",
        subtitlesEnabled: Boolean = true,
        preferredSubtitle: String = "English"
    ) {
        val audioLangs = when (preferredAudio.lowercase()) {
            "hindi" -> arrayOf("hin", "hi", "hindi", "eng", "en", "english")
            "english" -> arrayOf("eng", "en", "english", "hin", "hi", "hindi")
            "original" -> emptyArray()
            else -> arrayOf("hin", "hi", "hindi", "eng", "en", "english")
        }
        val subLangs = when (preferredSubtitle.lowercase()) {
            "english" -> arrayOf("eng", "en", "english")
            "hindi" -> arrayOf("hin", "hi", "hindi")
            else -> arrayOf("eng", "en", "english")
        }

        val builder = trackSelector.buildUponParameters()
            .setPreferredAudioLanguages(*audioLangs)
            .setExceedAudioConstraintsIfNecessary(true)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subtitlesEnabled)

        if (subtitlesEnabled) {
            builder.setPreferredTextLanguages(*subLangs)
                .setPreferredTextRoleFlags(C.ROLE_FLAG_SUBTITLE)
        } else {
            builder.clearOverridesOfType(C.TRACK_TYPE_TEXT)
        }

        trackSelector.parameters = builder.build()
        _isSubtitleEnabled.value = subtitlesEnabled
    }

    fun getResizeModeForAspectRatio(aspectRatio: VideoAspectRatio): Int {
        return when (aspectRatio) {
            VideoAspectRatio.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            VideoAspectRatio.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            VideoAspectRatio.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            VideoAspectRatio.ORIGINAL -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
    }

    fun release() {
        tickerJob?.cancel()
        try {
            context.unregisterReceiver(becomingNoisyReceiver)
        } catch (e: Exception) {
            // ignore
        }
        player.release()
    }
}
