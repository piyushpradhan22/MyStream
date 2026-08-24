package com.mystream.app.ui.screens

import android.app.Activity
import com.mystream.app.ui.utils.safeRequestFocus
import android.content.Context
import android.media.AudioManager
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import android.content.pm.ActivityInfo
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.KeyEvent
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.mystream.app.data.model.MediaPlaybackItem
import com.mystream.app.player.MyStreamPlayerManager
import com.mystream.app.ui.components.AspectRatioDialog
import com.mystream.app.ui.components.AudioTrackSelectorDialog
import com.mystream.app.ui.components.SpeedSelectorDialog
import com.mystream.app.ui.components.SubtitleTrackSelectorDialog
import com.mystream.app.ui.theme.AccentAmber
import com.mystream.app.ui.theme.FocusRingOrange
import com.mystream.app.ui.theme.PrimaryNeon
import com.mystream.app.ui.theme.SecondaryCyan
import com.mystream.app.ui.theme.TextMuted
import com.mystream.app.ui.theme.TextPrimary
import com.mystream.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    playerManager: MyStreamPlayerManager,
    item: MediaPlaybackItem,
    repository: com.mystream.app.data.repository.SourcesRepository? = null,
    onBack: () -> Unit,
    onEnterPiP: (() -> Unit)? = null,
    onNextEpisode: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val view = LocalView.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    val appSettings by repository?.appSettingsFlow?.collectAsState(initial = com.mystream.app.data.model.AppSettingsConfig())
        ?: remember { mutableStateOf(com.mystream.app.data.model.AppSettingsConfig()) }

    val isPlaying by playerManager.isPlaying.collectAsState()
    val isBuffering by playerManager.isBuffering.collectAsState()
    val currentPosition by playerManager.currentPosition.collectAsState()
    val duration by playerManager.duration.collectAsState()
    val bufferedPosition by playerManager.bufferedPosition.collectAsState()
    val bufferPercentage by playerManager.bufferPercentage.collectAsState()
    val downloadSpeed by playerManager.downloadSpeed.collectAsState()
    val errorMessage by playerManager.errorMessage.collectAsState()
    val aspectRatio by playerManager.aspectRatio.collectAsState()
    val playbackSpeed by playerManager.playbackSpeed.collectAsState()

    val audioTracks by playerManager.audioTracks.collectAsState()
    val subtitleTracks by playerManager.subtitleTracks.collectAsState()
    val isSubtitleEnabled by playerManager.isSubtitleEnabled.collectAsState()

    var showControls by remember { mutableStateOf(true) }
    var isControlsLocked by remember { mutableStateOf(false) }

    var showAudioDialog by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showAspectDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }

    // Touch gesture feedback state (volume / brightness overlay)
    var gestureFeedbackText by remember { mutableStateOf<String?>(null) }
    var gestureFeedbackIcon by remember { mutableStateOf<androidx.compose.ui.graphics.vector.ImageVector?>(null) }

    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var isUserSeeking by remember { mutableStateOf(false) }

    var isLandscape by remember { mutableStateOf(true) }
    val playerFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }
    val rewindFocusRequester = remember { FocusRequester() }
    val forwardFocusRequester = remember { FocusRequester() }
    val seekbarFocusRequester = remember { FocusRequester() }
    val audioFocusRequester = remember { FocusRequester() }
    val subtitleFocusRequester = remember { FocusRequester() }
    val aspectFocusRequester = remember { FocusRequester() }
    val speedFocusRequester = remember { FocusRequester() }
    val fullscreenFocusRequester = remember { FocusRequester() }
    val lockFocusRequester = remember { FocusRequester() }

    val rewindInteraction = remember { MutableInteractionSource() }
    val playPauseInteraction = remember { MutableInteractionSource() }
    val forwardInteraction = remember { MutableInteractionSource() }
    val seekbarInteraction = remember { MutableInteractionSource() }
    val audioInteraction = remember { MutableInteractionSource() }
    val subtitleInteraction = remember { MutableInteractionSource() }
    val aspectInteraction = remember { MutableInteractionSource() }
    val speedInteraction = remember { MutableInteractionSource() }
    val fullscreenInteraction = remember { MutableInteractionSource() }
    val lockInteraction = remember { MutableInteractionSource() }

    val rewindFocused by rewindInteraction.collectIsFocusedAsState()
    val playPauseFocused by playPauseInteraction.collectIsFocusedAsState()
    val forwardFocused by forwardInteraction.collectIsFocusedAsState()
    val seekbarFocused by seekbarInteraction.collectIsFocusedAsState()
    val audioFocused by audioInteraction.collectIsFocusedAsState()
    val subtitleFocused by subtitleInteraction.collectIsFocusedAsState()
    val aspectFocused by aspectInteraction.collectIsFocusedAsState()
    val speedFocused by speedInteraction.collectIsFocusedAsState()
    val fullscreenFocused by fullscreenInteraction.collectIsFocusedAsState()
    val lockFocused by lockInteraction.collectIsFocusedAsState()

    val changeVolumeByStep: (Int) -> Unit = { delta ->
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val newVol = (currentVol + delta).coerceIn(0, maxVol)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
        gestureFeedbackText = "Volume: ${(newVol * 100 / maxVol)}%"
        gestureFeedbackIcon = Icons.AutoMirrored.Filled.VolumeUp
    }

    // Lifecycle observer to pause playback on ON_PAUSE / ON_STOP (e.g. Netflix button / Home / App switch)
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE || event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                playerManager.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Immersive Full Screen (Hides Status Bar & Navigation Bar) + Screen On + Auto Landscape
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val window = activity?.window

        // Keep screen on
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        view.keepScreenOn = true

        // Hide Status Bar (Notification Bar) & Bottom Navigation Bar (Home pill/buttons)
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // Auto switch to landscape for cinema viewing
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        playerManager.applyTrackPreferences(
            preferredAudio = appSettings.preferredAudioLanguage,
            subtitlesEnabled = appSettings.subtitlesEnabled,
            preferredSubtitle = appSettings.preferredSubtitleLanguage
        )
        playerManager.playMedia(item)

        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            view.keepScreenOn = false
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Auto-hide controls timer
    LaunchedEffect(showControls, isPlaying, isUserSeeking) {
        if (showControls && isPlaying && !isControlsLocked && !isUserSeeking) {
            delay(4000)
            showControls = false
        }
    }

    LaunchedEffect(Unit) {
        playerFocusRequester.requestFocus()
    }

    val errorButtonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            delay(100)
            try {
                errorButtonFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(showControls) {
        if (showControls && !isControlsLocked) {
            playPauseFocusRequester.requestFocus()
        }
    }

    val coroutineScope = rememberCoroutineScope()
    var pendingSeekJob by remember { mutableStateOf<Job?>(null) }
    var pendingSeekTargetMs by remember { mutableStateOf<Long?>(null) }
    var accumulatedSeekDeltaMs by remember { mutableStateOf(0L) }

    fun commitPendingSeekNow(): Boolean {
        val target = pendingSeekTargetMs
        if (target != null) {
            pendingSeekJob?.cancel()
            pendingSeekJob = null
            playerManager.seekTo(target)
            isUserSeeking = false
            pendingSeekTargetMs = null
            accumulatedSeekDeltaMs = 0L
            return true
        }
        return false
    }

    fun triggerDebouncedNavSeek(deltaSeconds: Long) {
        val durationCap = if (duration > 0L) duration else Long.MAX_VALUE
        val basePos = pendingSeekTargetMs ?: (if (isUserSeeking) sliderPosition.toLong() else currentPosition)
        val deltaMs = deltaSeconds * 1000L
        val newTarget = (basePos + deltaMs).coerceIn(0L, durationCap)
        accumulatedSeekDeltaMs += deltaMs
        pendingSeekTargetMs = newTarget

        isUserSeeking = true
        sliderPosition = newTarget.toFloat()
        // Do NOT open overlay controls during D-Pad seek so focus is not stolen from seeking

        val sign = if (accumulatedSeekDeltaMs >= 0) "+" else "-"
        val absSec = kotlin.math.abs(accumulatedSeekDeltaMs) / 1000L
        val deltaFormatted = if (absSec >= 60) "${sign}${absSec / 60}m ${absSec % 60}s" else "${sign}${absSec}s"
        gestureFeedbackText = "$deltaFormatted (${formatDuration(newTarget)})"
        gestureFeedbackIcon = if (deltaSeconds > 0) Icons.Default.Forward10 else Icons.Default.Replay10

        pendingSeekJob?.cancel()
        pendingSeekJob = coroutineScope.launch {
            delay(650) // Wait for user to stop pressing D-Pad navigation buttons
            playerManager.seekTo(newTarget)
            isUserSeeking = false
            pendingSeekTargetMs = null
            accumulatedSeekDeltaMs = 0L
        }
    }

    fun handlePlayPauseNav(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        return when (event.key) {
            Key.DirectionLeft -> {
                rewindFocusRequester.safeRequestFocus()
                true
            }
            Key.DirectionRight -> {
                forwardFocusRequester.safeRequestFocus()
                true
            }
            Key.DirectionDown -> {
                seekbarFocusRequester.safeRequestFocus()
                true
            }
            else -> false
        }
    }

    fun handleRewindNav(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        return when (event.key) {
            Key.DirectionRight -> {
                playPauseFocusRequester.safeRequestFocus()
                true
            }
            Key.DirectionDown -> {
                seekbarFocusRequester.safeRequestFocus()
                true
            }
            else -> false
        }
    }

    fun handleForwardNav(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        return when (event.key) {
            Key.DirectionLeft -> {
                playPauseFocusRequester.safeRequestFocus()
                true
            }
            Key.DirectionDown -> {
                seekbarFocusRequester.safeRequestFocus()
                true
            }
            else -> false
        }
    }

    fun handleSeekbarNavigation(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        return when (event.key) {
            Key.DirectionUp -> {
                commitPendingSeekNow()
                playPauseFocusRequester.safeRequestFocus()
                true
            }
            Key.DirectionDown -> {
                commitPendingSeekNow()
                audioFocusRequester.safeRequestFocus()
                true
            }
            Key.DirectionLeft -> {
                triggerDebouncedNavSeek(-15L)
                true
            }
            Key.DirectionRight -> {
                triggerDebouncedNavSeek(15L)
                true
            }
            Key.DirectionCenter,
            Key.Enter,
            Key.NumPadEnter -> {
                if (commitPendingSeekNow()) {
                    true
                } else {
                    playerManager.togglePlayPause()
                    true
                }
            }
            else -> false
        }
    }

    fun handleAudioNav(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        return when (event.key) {
            Key.DirectionRight -> {
                subtitleFocusRequester.safeRequestFocus()
                true
            }
            Key.DirectionUp -> {
                seekbarFocusRequester.safeRequestFocus()
                true
            }
            else -> false
        }
    }

    fun handleSubtitleNav(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        return when (event.key) {
            Key.DirectionLeft -> {
                audioFocusRequester.safeRequestFocus()
                true
            }
            Key.DirectionRight -> {
                aspectFocusRequester.safeRequestFocus()
                true
            }
            Key.DirectionUp -> {
                seekbarFocusRequester.safeRequestFocus()
                true
            }
            else -> false
        }
    }

    fun handleAspectNav(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        return when (event.key) {
            Key.DirectionLeft -> {
                subtitleFocusRequester.safeRequestFocus()
                true
            }
            Key.DirectionRight -> {
                speedFocusRequester.safeRequestFocus()
                true
            }
            Key.DirectionUp -> {
                seekbarFocusRequester.safeRequestFocus()
                true
            }
            else -> false
        }
    }

    fun handleSpeedNav(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        return when (event.key) {
            Key.DirectionLeft -> {
                aspectFocusRequester.safeRequestFocus()
                true
            }
            Key.DirectionRight -> {
                fullscreenFocusRequester.safeRequestFocus()
                true
            }
            Key.DirectionUp -> {
                seekbarFocusRequester.safeRequestFocus()
                true
            }
            else -> false
        }
    }

    fun handleFullscreenNav(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        return when (event.key) {
            Key.DirectionLeft -> {
                speedFocusRequester.safeRequestFocus()
                true
            }
            Key.DirectionRight -> {
                lockFocusRequester.safeRequestFocus()
                true
            }
            Key.DirectionUp -> {
                seekbarFocusRequester.safeRequestFocus()
                true
            }
            else -> false
        }
    }

    fun handleLockNav(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        return when (event.key) {
            Key.DirectionLeft -> {
                fullscreenFocusRequester.safeRequestFocus()
                true
            }
            Key.DirectionUp -> {
                seekbarFocusRequester.safeRequestFocus()
                true
            }
            else -> false
        }
    }

    // Clear gesture feedback overlay after 1.5s
    LaunchedEffect(gestureFeedbackText) {
        if (gestureFeedbackText != null) {
            delay(1500)
            if (!isUserSeeking) {
                gestureFeedbackText = null
                gestureFeedbackIcon = null
            }
        }
    }

    // Dialogs
    if (showAudioDialog) {
        AudioTrackSelectorDialog(
            tracks = audioTracks,
            onSelectTrack = { playerManager.selectAudioTrack(it) },
            onDismiss = { showAudioDialog = false }
        )
    }

    if (showSubtitleDialog) {
        SubtitleTrackSelectorDialog(
            tracks = subtitleTracks,
            isSubtitleEnabled = isSubtitleEnabled,
            onSelectTrack = { playerManager.selectSubtitleTrack(it) },
            onDismiss = { showSubtitleDialog = false }
        )
    }

    if (showAspectDialog) {
        AspectRatioDialog(
            currentRatio = aspectRatio,
            onSelectRatio = { playerManager.setAspectRatio(it) },
            onDismiss = { showAspectDialog = false }
        )
    }

    if (showSpeedDialog) {
        SpeedSelectorDialog(
            currentSpeed = playbackSpeed,
            onSelectSpeed = { playerManager.setPlaybackSpeed(it) },
            onDismiss = { showSpeedDialog = false }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(playerFocusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                // Let dialogs own DPAD navigation when open.
                if (showAudioDialog || showSubtitleDialog || showAspectDialog || showSpeedDialog) {
                    return@onPreviewKeyEvent false
                }

                if (isControlsLocked) {
                    return@onPreviewKeyEvent false
                }

                when (keyEvent.key) {
                    Key.DirectionUp -> {
                        if (!showControls) {
                            showControls = true
                            true
                        } else {
                            false
                        }
                    }

                    Key.DirectionDown -> {
                        if (!showControls) {
                            showControls = true
                            true
                        } else {
                            false
                        }
                    }

                    Key.DirectionLeft -> {
                        if (!showControls) {
                            triggerDebouncedNavSeek(-10L)
                            true
                        } else {
                            false
                        }
                    }

                    Key.DirectionRight -> {
                        if (!showControls) {
                            triggerDebouncedNavSeek(10L)
                            true
                        } else {
                            false
                        }
                    }

                    Key.DirectionCenter,
                    Key.Enter,
                    Key.NumPadEnter,
                    Key.Spacebar -> {
                        if (commitPendingSeekNow()) {
                            true
                        } else if (!showControls) {
                            showControls = true
                            true
                        } else {
                            false
                        }
                    }

                    else -> false
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        if (!isControlsLocked) {
                            showControls = !showControls
                        }
                    },
                    onDoubleTap = { offset ->
                        if (!isControlsLocked) {
                            if (offset.x < size.width / 2) {
                                playerManager.seekBack(10L)
                                gestureFeedbackText = "-10s"
                                gestureFeedbackIcon = Icons.Default.Replay10
                            } else {
                                playerManager.seekForward(10L)
                                gestureFeedbackText = "+10s"
                                gestureFeedbackIcon = Icons.Default.Forward10
                            }
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, dragAmount ->
                    if (!isControlsLocked) {
                        val isLeft = change.position.x < size.width / 2
                        if (isLeft) {
                            // Adjust Brightness
                            val activity = context as? Activity
                            activity?.let {
                                val lp = it.window.attributes
                                val currentBrightness = if (lp.screenBrightness < 0) 0.5f else lp.screenBrightness
                                val delta = -dragAmount / 500f
                                val newBrightness = (currentBrightness + delta).coerceIn(0.01f, 1.0f)
                                lp.screenBrightness = newBrightness
                                it.window.attributes = lp
                                gestureFeedbackText = "Brightness: ${(newBrightness * 100).toInt()}%"
                                gestureFeedbackIcon = Icons.Default.BrightnessMedium
                            }
                        } else {
                            // Adjust Media Volume
                            val delta = if (dragAmount < 0) 1 else -1
                            changeVolumeByStep(delta)
                        }
                    }
                }
            }
    ) {
        // ExoPlayer View
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = playerManager.player
                    useController = false // We use our sleek Compose HUD controls
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    resizeMode = playerManager.getResizeModeForAspectRatio(aspectRatio)
                    keepScreenOn = true
                    subtitleView?.visibility = if (isSubtitleEnabled) android.view.View.VISIBLE else android.view.View.GONE
                }
            },
            update = { playerView ->
                if (playerView.player != playerManager.player) {
                    playerView.player = playerManager.player
                }
                playerView.setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                playerView.resizeMode = playerManager.getResizeModeForAspectRatio(aspectRatio)
                playerView.subtitleView?.visibility = if (isSubtitleEnabled) android.view.View.VISIBLE else android.view.View.GONE
            },
            modifier = Modifier.fillMaxSize()
        )

        // Rich Buffering & Loading Screen Overlay with Live Download Speed
        AnimatedVisibility(
            visible = isBuffering,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x66000000)),
                contentAlignment = Alignment.TopCenter
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .padding(top = 76.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0x55000000))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "⚡ $downloadSpeed",
                        color = PrimaryNeon,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "•",
                        color = TextMuted,
                        fontSize = 14.sp
                    )

                    Text(
                        text = if (bufferPercentage > 0) "Buffered $bufferPercentage%" else "Buffering stream...",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Playback Error Overlay
        errorMessage?.let { err ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xDD000000)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text(
                        text = "⚠️ Playback Error",
                        color = Color(0xFFFF4757),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = errorMessage ?: "Unable to stream this media. Please retry or pick another stream.",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val retryBtnInteraction = remember { MutableInteractionSource() }
                        val isRetryBtnFocused by retryBtnInteraction.collectIsFocusedAsState()

                        androidx.compose.material3.Button(
                            onClick = {
                                playerManager.playMedia(item, currentPosition)
                            },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = if (isRetryBtnFocused) FocusRingOrange else PrimaryNeon
                            ),
                            modifier = Modifier
                                .focusRequester(errorButtonFocusRequester)
                                .focusable(interactionSource = retryBtnInteraction)
                        ) {
                            Text(text = "Retry", color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        val errorBtnInteraction = remember { MutableInteractionSource() }
                        val isErrorBtnFocused by errorBtnInteraction.collectIsFocusedAsState()

                        androidx.compose.material3.OutlinedButton(
                            onClick = onBack,
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isErrorBtnFocused) FocusRingOrange.copy(alpha = 0.2f) else Color.Transparent
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                if (isErrorBtnFocused) 2.dp else 1.dp,
                                if (isErrorBtnFocused) FocusRingOrange else Color(0x66FFFFFF)
                            ),
                            modifier = Modifier
                                .focusable(interactionSource = errorBtnInteraction)
                        ) {
                            Text(text = "Choose Another Stream", color = Color.White)
                        }
                    }
                }
            }
        }

        // Screen Lock Toggle Button (always visible when tapped if locked)
        if (isControlsLocked) {
            IconButton(
                onClick = { isControlsLocked = false; showControls = true },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 24.dp)
                    .clip(CircleShape)
                    .background(Color(0x80000000))
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Unlock Controls",
                    tint = PrimaryNeon,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // Controls HUD Overlay
        AnimatedVisibility(
            visible = showControls && !isControlsLocked,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color(0xB3000000),
                            0.3f to Color.Transparent,
                            0.7f to Color.Transparent,
                            1.0f to Color(0xE6000000)
                        )
                    )
            ) {
                // Top Header Bar (Clean - only Back button & Title)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        Column {
                            Text(
                                text = item.title,
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            if (!item.subtitle.isNullOrBlank()) {
                                Text(
                                    text = item.subtitle,
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    if (onEnterPiP != null) {
                        IconButton(onClick = onEnterPiP) {
                            Icon(
                                imageVector = Icons.Default.PictureInPicture,
                                contentDescription = "Picture-in-Picture",
                                tint = SecondaryCyan
                            )
                        }
                    }
                }

                // Center Play/Pause & Seek Controls
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { playerManager.seekBack(10L) },
                        interactionSource = rewindInteraction,
                        modifier = Modifier
                            .size(50.dp)
                            .focusRequester(rewindFocusRequester)
                            .onPreviewKeyEvent(::handleRewindNav)
                            .clip(CircleShape)
                            .then(if (rewindFocused) Modifier.border(2.5.dp, FocusRingOrange, CircleShape) else Modifier)
                            .background(Color(0x55000000))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay10,
                            contentDescription = "Rewind 10s",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    IconButton(
                        onClick = { playerManager.togglePlayPause() },
                        interactionSource = playPauseInteraction,
                        modifier = Modifier
                            .size(68.dp)
                            .focusRequester(playPauseFocusRequester)
                            .onPreviewKeyEvent(::handlePlayPauseNav)
                            .clip(CircleShape)
                            .then(if (playPauseFocused) Modifier.border(3.5.dp, FocusRingOrange, CircleShape) else Modifier)
                            .background(PrimaryNeon)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                            if (isBuffering) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.5.dp,
                                    modifier = Modifier.size(54.dp)
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = { playerManager.seekForward(10L) },
                        interactionSource = forwardInteraction,
                        modifier = Modifier
                            .size(50.dp)
                            .focusRequester(forwardFocusRequester)
                            .onPreviewKeyEvent(::handleForwardNav)
                            .clip(CircleShape)
                            .then(if (forwardFocused) Modifier.border(2.5.dp, FocusRingOrange, CircleShape) else Modifier)
                            .background(Color(0x55000000))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forward10,
                            contentDescription = "Forward 10s",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                // Bottom HUD Bar (Seekbar, timestamps, and combined bottom controls)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 24.dp, vertical = 18.dp)
                ) {
                    // Seekbar with timestamps
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val currentMs = if (isUserSeeking) sliderPosition.toLong() else currentPosition
                        Text(
                            text = formatDuration(currentMs),
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Text(
                            text = formatDuration(duration),
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    val maxSliderValue = if (duration > 0) duration.toFloat() else 1f
                    val currentSliderValue = if (isUserSeeking) sliderPosition else currentPosition.toFloat().coerceIn(0f, maxSliderValue)

                    Slider(
                        value = currentSliderValue,
                        onValueChange = {
                            isUserSeeking = true
                            sliderPosition = it
                        },
                        onValueChangeFinished = {
                            playerManager.seekTo(sliderPosition.toLong())
                            isUserSeeking = false
                        },
                        valueRange = 0f..maxSliderValue,
                        colors = SliderDefaults.colors(
                            thumbColor = FocusRingOrange,
                            activeTrackColor = FocusRingOrange,
                            inactiveTrackColor = Color(0x40FFFFFF)
                        ),
                        interactionSource = seekbarInteraction,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(seekbarFocusRequester)
                            .onPreviewKeyEvent(::handleSeekbarNavigation)
                            .then(
                                if (seekbarFocused) Modifier.border(
                                    width = 2.dp,
                                    color = FocusRingOrange,
                                    shape = RoundedCornerShape(8.dp)
                                ) else Modifier
                            )
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Bottom Action Row (Audio, Subtitles, Aspect Ratio, Speed, Screen, Lock)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Audio Track Selector Button
                        Row(
                            modifier = Modifier
                                .focusRequester(audioFocusRequester)
                                .focusable(interactionSource = audioInteraction)
                                .onPreviewKeyEvent(::handleAudioNav)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (audioFocused) FocusRingOrange.copy(alpha = 0.25f) else Color(0x33FFFFFF))
                                .then(
                                    if (audioFocused) Modifier.border(
                                        width = 2.dp,
                                        color = FocusRingOrange,
                                        shape = RoundedCornerShape(8.dp)
                                    ) else Modifier
                                )
                                .clickable(interactionSource = audioInteraction, indication = null) { showAudioDialog = true }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Audiotrack,
                                contentDescription = null,
                                tint = if (audioFocused) FocusRingOrange else PrimaryNeon,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Audio",
                                color = if (audioFocused) FocusRingOrange else TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // 2. Subtitle Selector Button
                        Row(
                            modifier = Modifier
                                .focusRequester(subtitleFocusRequester)
                                .focusable(interactionSource = subtitleInteraction)
                                .onPreviewKeyEvent(::handleSubtitleNav)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (subtitleFocused) FocusRingOrange.copy(alpha = 0.25f) else if (isSubtitleEnabled) SecondaryCyan.copy(alpha = 0.25f) else Color(0x33FFFFFF))
                                .then(
                                    if (subtitleFocused) Modifier.border(
                                        width = 2.dp,
                                        color = FocusRingOrange,
                                        shape = RoundedCornerShape(8.dp)
                                    ) else Modifier
                                )
                                .clickable(interactionSource = subtitleInteraction, indication = null) { showSubtitleDialog = true }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Subtitles,
                                contentDescription = null,
                                tint = if (subtitleFocused) FocusRingOrange else if (isSubtitleEnabled) SecondaryCyan else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (isSubtitleEnabled) "CC On" else "Subtitles",
                                color = if (subtitleFocused) FocusRingOrange else TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // 3. Aspect Ratio / Fit Screen Button
                        Row(
                            modifier = Modifier
                                .focusRequester(aspectFocusRequester)
                                .focusable(interactionSource = aspectInteraction)
                                .onPreviewKeyEvent(::handleAspectNav)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (aspectFocused) FocusRingOrange.copy(alpha = 0.25f) else Color(0x33FFAA00))
                                .then(
                                    if (aspectFocused) Modifier.border(
                                        width = 2.dp,
                                        color = FocusRingOrange,
                                        shape = RoundedCornerShape(8.dp)
                                    ) else Modifier
                                )
                                .clickable(interactionSource = aspectInteraction, indication = null) { showAspectDialog = true }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AspectRatio,
                                contentDescription = null,
                                tint = if (aspectFocused) FocusRingOrange else AccentAmber,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = aspectRatio.label.substringBefore(" ("),
                                color = if (aspectFocused) FocusRingOrange else AccentAmber,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // 4. Playback Speed Button
                        Row(
                            modifier = Modifier
                                .focusRequester(speedFocusRequester)
                                .focusable(interactionSource = speedInteraction)
                                .onPreviewKeyEvent(::handleSpeedNav)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (speedFocused) FocusRingOrange.copy(alpha = 0.25f) else Color(0x336C5CE7))
                                .then(
                                    if (speedFocused) Modifier.border(
                                        width = 2.dp,
                                        color = FocusRingOrange,
                                        shape = RoundedCornerShape(8.dp)
                                    ) else Modifier
                                )
                                .clickable(interactionSource = speedInteraction, indication = null) { showSpeedDialog = true }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = if (speedFocused) FocusRingOrange else PrimaryNeon,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "${playbackSpeed}x",
                                color = if (speedFocused) FocusRingOrange else PrimaryNeon,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // 5. Fullscreen / Orientation Toggle Button
                        Row(
                            modifier = Modifier
                                .focusRequester(fullscreenFocusRequester)
                                .focusable(interactionSource = fullscreenInteraction)
                                .onPreviewKeyEvent(::handleFullscreenNav)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (fullscreenFocused) FocusRingOrange.copy(alpha = 0.25f) else Color(0x33FFFFFF))
                                .then(
                                    if (fullscreenFocused) Modifier.border(
                                        width = 2.dp,
                                        color = FocusRingOrange,
                                        shape = RoundedCornerShape(8.dp)
                                    ) else Modifier
                                )
                                .clickable(interactionSource = fullscreenInteraction, indication = null) {
                                    isLandscape = !isLandscape
                                    val activity = context as? Activity
                                    if (isLandscape) {
                                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                    } else {
                                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = if (isLandscape) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = null,
                                tint = if (fullscreenFocused) FocusRingOrange else Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (isLandscape) "Landscape" else "Portrait",
                                color = if (fullscreenFocused) FocusRingOrange else TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // 6. Lock Controls Button
                        Row(
                            modifier = Modifier
                                .focusRequester(lockFocusRequester)
                                .focusable(interactionSource = lockInteraction)
                                .onPreviewKeyEvent(::handleLockNav)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (lockFocused) FocusRingOrange.copy(alpha = 0.25f) else Color(0x33FFFFFF))
                                .then(
                                    if (lockFocused) Modifier.border(
                                        width = 2.dp,
                                        color = FocusRingOrange,
                                        shape = RoundedCornerShape(8.dp)
                                    ) else Modifier
                                )
                                .clickable(interactionSource = lockInteraction, indication = null) {
                                    isControlsLocked = true
                                    showControls = false
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LockOpen,
                                contentDescription = null,
                                tint = if (lockFocused) FocusRingOrange else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Lock",
                                color = if (lockFocused) FocusRingOrange else TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // Gesture Feedback Badge (Brightness / Volume / Seek Delta & Timestamp) - Topmost overlay positioned above seekbar
        gestureFeedbackText?.let { text ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 120.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xE6000000))
                        .border(1.5.dp, FocusRingOrange.copy(alpha = 0.9f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 22.dp, vertical = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        gestureFeedbackIcon?.let { icon ->
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = FocusRingOrange,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Text(
                            text = text,
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
