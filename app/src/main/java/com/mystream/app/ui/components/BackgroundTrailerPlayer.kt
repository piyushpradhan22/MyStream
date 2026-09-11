package com.mystream.app.ui.components

import android.view.LayoutInflater
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.mystream.app.R
import com.mystream.app.data.youtube.YouTubeTrailerResolver

private const val TRAILER_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

/**
 * Ambient background trailer, played natively in ExoPlayer. The YouTube id is resolved to a
 * direct stream via NewPipeExtractor (no WebView). Same signature as before so callers are unchanged.
 */
@OptIn(UnstableApi::class)
@Composable
fun BackgroundTrailerPlayer(
    ytId: String,
    isAudioMuted: Boolean = false,
    isStopped: Boolean = false,
    isHomeScreen: Boolean = false,
    preferredResolution: String = "480p",
    modifier: Modifier = Modifier,
    onPlaybackStarted: (() -> Unit)? = null,
    onVideoEnded: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var isVideoReady by remember { mutableStateOf(false) }
    // Survives pauses so a resume can re-show the video without waiting for another first frame.
    var hasRenderedFrame by remember { mutableStateOf(false) }

    val dataSourceFactory = remember {
        DefaultDataSource.Factory(
            context,
            DefaultHttpDataSource.Factory()
                .setUserAgent(TRAILER_UA)
                .setAllowCrossProtocolRedirects(true)
        )
    }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
            volume = if (isAudioMuted) 0f else 1f
        }
    }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                hasRenderedFrame = true
                isVideoReady = true
                onPlaybackStarted?.invoke()
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    hasRenderedFrame = false
                    isVideoReady = false
                    onVideoEnded?.invoke()
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Resolve the YouTube id to a direct stream and load it (graceful no-op on failure).
    LaunchedEffect(ytId) {
        isVideoReady = false
        hasRenderedFrame = false
        // Drop the previous trailer immediately so a stale video never shows under a newly focused item.
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        if (ytId.isBlank()) {
            return@LaunchedEffect
        }
        val resolved = YouTubeTrailerResolver.resolve(ytId, preferredResolution) ?: return@LaunchedEffect
        val videoSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(resolved.videoUrl))
        val mediaSource = if (resolved.audioUrl != null) {
            val audioSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(resolved.audioUrl))
            MergingMediaSource(videoSource, audioSource)
        } else {
            videoSource
        }
        exoPlayer.setMediaSource(mediaSource)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = !isStopped
    }

    LaunchedEffect(isAudioMuted) { exoPlayer.volume = if (isAudioMuted) 0f else 1f }
    LaunchedEffect(isStopped) {
        exoPlayer.playWhenReady = !isStopped
        if (isStopped) {
            isVideoReady = false
        } else if (hasRenderedFrame) {
            isVideoReady = true
            onPlaybackStarted?.invoke()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Transparent)) {
        val alpha by animateFloatAsState(
            targetValue = if (isVideoReady && !isStopped) 1f else 0f,
            animationSpec = tween(400),
            label = "TrailerAlpha"
        )
        AndroidView(
            modifier = Modifier.fillMaxSize().alpha(alpha),
            factory = { ctx ->
                (LayoutInflater.from(ctx).inflate(R.layout.view_background_trailer, null) as PlayerView).apply {
                    isFocusable = false
                    isFocusableInTouchMode = false
                    setBackgroundColor(0x00000000)
                    player = exoPlayer
                }
            }
        )
    }
}
