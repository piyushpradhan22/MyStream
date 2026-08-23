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
                .setExceedRendererCapabilitiesIfNecessary(true)
                .setAllowVideoMixedMimeTypeAdaptiveness(true)
                .setAllowVideoNonSeamlessAdaptiveness(true)
                .setMaxVideoBitrate(Int.MAX_VALUE)
                .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
                .setMaxVideoFrameRate(Int.MAX_VALUE)
        )
    }

    private val renderersFactory = DefaultRenderersFactory(context).apply {
        // PREFER mode: use extension decoders (e.g. libvpx, ffmpeg) before software, hardware last resort
        setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        // Allow fallback to next decoder if primary can't handle the stream
        setEnableDecoderFallback(true)
        // Use DEFAULT selector — lets hardware (c2.qcom.hevc etc.) + software all compete
        setMediaCodecSelector(MediaCodecSelector.DEFAULT)
    }

    private val okHttpClient = OkHttpClient.Builder()
        .dns(com.mystream.app.data.api.SystemFallbackDns)
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
        .setUserAgent("ANDROID-com.pikcloud.pikpak/1.47.1")
        .setTransferListener(bandwidthMeter)

    private val mediaSourceFactory = DefaultMediaSourceFactory(context)
        .setDataSourceFactory(dataSourceFactory)
        .setLoadErrorHandlingPolicy(object : DefaultLoadErrorHandlingPolicy() {
            override fun getMinimumLoadableRetryCount(dataType: Int): Int {
                // Localhost torrent streaming can legitimately return transient 5xx/timeouts while buffering pieces.
                return 10
            }

            override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
                val base = super.getRetryDelayMsFor(loadErrorInfo)
                return when {
                    base == C.TIME_UNSET -> 750L
                    else -> base.coerceIn(500L, 4000L)
                }
            }
        })

    val player: ExoPlayer = ExoPlayer.Builder(context, renderersFactory)
        .setTrackSelector(trackSelector)
        .setMediaSourceFactory(mediaSourceFactory)
        .setBandwidthMeter(bandwidthMeter)
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

    private val _downloadSpeed = MutableStateFlow("0.0 MB/s")
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(becomingNoisyReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(becomingNoisyReceiver, filter)
            }
        } catch (e: Exception) {
            // ignore if register fails
        }
        startProgressTicker()
    }

    private fun setupPlayerListeners() {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                Log.d(TAG, "onIsPlayingChanged: $isPlaying")
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _isBuffering.value = (playbackState == Player.STATE_BUFFERING)
                Log.d(TAG, "onPlaybackStateChanged: $playbackState (Buffering=${_isBuffering.value})")
                if (playbackState == Player.STATE_READY) {
                    _duration.value = player.duration.coerceAtLeast(0L)
                    _isBuffering.value = false
                    _errorMessage.value = null
                    Log.i(TAG, "Player is READY, duration=${player.duration}ms")
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "Player error occurred: ${error.message}", error)
                _isBuffering.value = false
                _errorMessage.value = error.message ?: "Playback error"
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
            while (isActive) {
                if (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING) {
                    _currentPosition.value = player.currentPosition.coerceAtLeast(0L)
                    _duration.value = player.duration.coerceAtLeast(0L)
                    _bufferedPosition.value = player.bufferedPosition.coerceAtLeast(0L)
                    _bufferPercentage.value = player.bufferedPercentage

                    val bitrateBps = bandwidthMeter.bitrateEstimate
                    val bytesPerSec = bitrateBps / 8.0
                    val speedStr = if (bytesPerSec > 1024 * 1024) {
                        "%.1f MB/s".format(bytesPerSec / (1024 * 1024))
                    } else if (bytesPerSec > 1024) {
                        "%.0f KB/s".format(bytesPerSec / 1024)
                    } else {
                        "%.0f KB/s".format((bytesPerSec / 1024).coerceAtLeast(1.0))
                    }
                    _downloadSpeed.value = speedStr

                    val now = System.currentTimeMillis()
                    if (now - lastSaveTick > 4000L && player.isPlaying) {
                        lastSaveTick = now
                        saveCurrentProgress()
                    }
                }
                delay(300)
            }
        }
    }

    fun playMedia(item: MediaPlaybackItem, startPositionMs: Long? = null) {
        saveCurrentProgress()
        _currentItem.value = item
        _isBuffering.value = true
        _errorMessage.value = null
        val actualStartPos = startPositionMs ?: item.startPositionMs
        Log.i(TAG, "Playing media (start at ${actualStartPos}ms): ${item.title} -> ${item.mediaUrl}")
        val uri = Uri.parse(item.mediaUrl)

        val metadata = MediaMetadata.Builder()
            .setTitle(item.title)
            .setSubtitle(item.subtitle)
            .setDisplayTitle(item.title)
            .setArtworkUri(item.posterUrl?.let { Uri.parse(it) } ?: item.backdropUrl?.let { Uri.parse(it) })
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setMediaId(item.id)
            .setMediaMetadata(metadata)
            .build()

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
        val durationCap = player.duration.coerceAtLeast(0L)
        val target = if (durationCap > 0L) requested.coerceIn(0L, durationCap) else requested
        Log.i(TAG, "Seeking to ${target}ms (duration=${durationCap}ms)")
        player.seekTo(target)
    }

    fun seekForward(seconds: Long = 10) {
        val newPos = player.currentPosition + (seconds * 1000)
        seekTo(newPos)
    }

    fun seekBack(seconds: Long = 10) {
        val newPos = player.currentPosition - (seconds * 1000)
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
                                bitrate = format.bitrate
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
                                mimeType = format.sampleMimeType
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
    }

    fun selectAudioTrack(trackInfo: PlayerTrackInfo) {
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
            else -> arrayOf("hin", "hi", "hindi", "eng", "en", "english")
        }
        val subLangs = when (preferredSubtitle.lowercase()) {
            "english" -> arrayOf("eng", "en", "english")
            "hindi" -> arrayOf("hin", "hi", "hindi")
            else -> arrayOf("eng", "en", "english")
        }

        val builder = trackSelector.buildUponParameters()
            .setPreferredAudioLanguages(*audioLangs)
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
