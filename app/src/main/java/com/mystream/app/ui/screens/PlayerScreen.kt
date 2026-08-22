package com.mystream.app.ui.screens

import android.app.Activity
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
    val seekbarFocusRequester = remember { FocusRequester() }
    val audioFocusRequester = remember { FocusRequester() }
    val subtitleFocusRequester = remember { FocusRequester() }

    val rewindInteraction = remember { MutableInteractionSource() }
    val playPauseInteraction = remember { MutableInteractionSource() }
    val forwardInteraction = remember { MutableInteractionSource() }
    val seekbarInteraction = remember { MutableInteractionSource() }
    val audioInteraction = remember { MutableInteractionSource() }
    val subtitleInteraction = remember { MutableInteractionSource() }

    val rewindFocused by rewindInteraction.collectIsFocusedAsState()
    val playPauseFocused by playPauseInteraction.collectIsFocusedAsState()
    val forwardFocused by forwardInteraction.collectIsFocusedAsState()
    val seekbarFocused by seekbarInteraction.collectIsFocusedAsState()
    val audioFocused by audioInteraction.collectIsFocusedAsState()
    val subtitleFocused by subtitleInteraction.collectIsFocusedAsState()
    var audioChipFocused by remember { mutableStateOf(false) }
    var subtitleChipFocused by remember { mutableStateOf(false) }

    val changeVolumeByStep: (Int) -> Unit = { delta ->
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val newVol = (currentVol + delta).coerceIn(0, maxVol)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
        gestureFeedbackText = "Volume: ${(newVol * 100 / maxVol)}%"
        gestureFeedbackIcon = Icons.AutoMirrored.Filled.VolumeUp
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
        showControls = true

        val sign = if (accumulatedSeekDeltaMs >= 0) "+" else "-"
        val absSec = kotlin.math.abs(accumulatedSeekDeltaMs) / 1000L
        val deltaFormatted = if (absSec >= 60) "${sign}${absSec / 60}m ${absSec % 60}s" else "${sign}${absSec}s"
        gestureFeedbackText = "$deltaFormatted (${formatDuration(newTarget)})"
        gestureFeedbackIcon = if (deltaSeconds > 0) Icons.Default.Forward10 else Icons.Default.Replay10

        pendingSeekJob?.cancel()
        pendingSeekJob = coroutineScope.launch {
            delay(750) // Wait for user to stop pressing D-Pad navigation buttons
            playerManager.seekTo(newTarget)
            isUserSeeking = false
            pendingSeekTargetMs = null
            accumulatedSeekDeltaMs = 0L
        }
    }

    fun handleCenterControlNavigation(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        return when (event.key) {
            Key.DirectionDown -> {
                seekbarFocusRequester.requestFocus()
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
                playPauseFocusRequester.requestFocus()
                true
            }
            Key.DirectionDown -> {
                commitPendingSeekNow()
                audioFocusRequester.requestFocus()
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
                subtitleFocusRequester.requestFocus()
                true
            }
            Key.DirectionUp -> {
                seekbarFocusRequester.requestFocus()
                true
            }
            else -> false
        }
    }

    fun handleSubtitleNav(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        return when (event.key) {
            Key.DirectionLeft -> {
                audioFocusRequester.requestFocus()
                true
            }
            Key.DirectionUp -> {
                seekbarFocusRequester.requestFocus()
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
                        triggerDebouncedNavSeek(-10L)
                        true
                    }

                    Key.DirectionRight -> {
                        triggerDebouncedNavSeek(10L)
                        true
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
                    resizeMode = playerManager.getResizeModeForAspectRatio(aspectRatio)
                    keepScreenOn = true
                    subtitleView?.visibility = if (isSubtitleEnabled) android.view.View.VISIBLE else android.view.View.GONE
                }
            },
            update = { playerView ->
                if (playerView.player != playerManager.player) {
                    playerView.player = playerManager.player
                }
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
                        text = "The selected stream format exceeds the emulator/device hardware decoder capabilities.\nPlease select a 1080p or 720p stream.",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    androidx.compose.material3.Button(
                        onClick = onBack,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = PrimaryNeon)
                    ) {
                        Text(text = "Choose Another Stream", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Gesture Feedback Badge (Brightness / Volume / 10s Seek)
        gestureFeedbackText?.let { text ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xCC000000))
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    gestureFeedbackIcon?.let { icon ->
                        Icon(imageVector = icon, contentDescription = null, tint = PrimaryNeon)
                    }
                    Text(
                        text = text,
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
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
                // Top Header Bar
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

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Aspect Ratio switcher
                        IconButton(onClick = { showAspectDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.AspectRatio,
                                contentDescription = "Aspect Ratio",
                                tint = AccentAmber
                            )
                        }

                        // Playback Speed
                        IconButton(onClick = { showSpeedDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = "Speed",
                                tint = TextSecondary
                            )
                        }

                        // Fullscreen / Landscape Toggle Button
                        IconButton(
                            onClick = {
                                isLandscape = !isLandscape
                                val activity = context as? Activity
                                if (isLandscape) {
                                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                } else {
                                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isLandscape) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = "Toggle Landscape / Fullscreen",
                                tint = PrimaryNeon
                            )
                        }

                        // PiP button
                        if (onEnterPiP != null) {
                            IconButton(onClick = onEnterPiP) {
                                Icon(
                                    imageVector = Icons.Default.PictureInPicture,
                                    contentDescription = "Picture-in-Picture",
                                    tint = SecondaryCyan
                                )
                            }
                        }

                        // Lock Controls button
                        IconButton(onClick = { isControlsLocked = true; showControls = false }) {
                            Icon(
                                imageVector = Icons.Default.LockOpen,
                                contentDescription = "Lock Controls",
                                tint = TextSecondary
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
                            .clip(CircleShape)
                            .then(if (rewindFocused) Modifier.border(2.dp, PrimaryNeon, CircleShape) else Modifier)
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
                            .onPreviewKeyEvent(::handleCenterControlNavigation)
                            .clip(CircleShape)
                            .then(if (playPauseFocused) Modifier.border(3.dp, Color.White, CircleShape) else Modifier)
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
                            .clip(CircleShape)
                            .then(if (forwardFocused) Modifier.border(2.dp, PrimaryNeon, CircleShape) else Modifier)
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

                // Bottom HUD Bar (Seekbar, timestamps, audio and subtitle selectors)
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
                            thumbColor = PrimaryNeon,
                            activeTrackColor = PrimaryNeon,
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
                                    color = PrimaryNeon,
                                    shape = RoundedCornerShape(8.dp)
                                ) else Modifier
                            )
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Bottom Action Row (Audio Tracks, Subtitles, Aspect Ratio, Speed Indicator)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Audio Track Selector Button
                            Row(
                                modifier = Modifier
                                    .focusRequester(audioFocusRequester)
                                    .focusable(interactionSource = audioInteraction)
                                    .onFocusChanged { audioChipFocused = it.isFocused }
                                    .onPreviewKeyEvent(::handleAudioNav)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x33FFFFFF))
                                    .then(
                                        if (audioChipFocused || audioFocused) Modifier.border(
                                            width = 2.dp,
                                            color = PrimaryNeon,
                                            shape = RoundedCornerShape(8.dp)
                                        ) else Modifier
                                    )
                                    .clickable(interactionSource = audioInteraction, indication = null) { showAudioDialog = true }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Audiotrack,
                                    contentDescription = null,
                                    tint = PrimaryNeon,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Audio",
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            // Subtitle Selector Button
                            Row(
                                modifier = Modifier
                                    .focusRequester(subtitleFocusRequester)
                                    .focusable(interactionSource = subtitleInteraction)
                                    .onFocusChanged { subtitleChipFocused = it.isFocused }
                                    .onPreviewKeyEvent(::handleSubtitleNav)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSubtitleEnabled) SecondaryCyan.copy(alpha = 0.25f) else Color(0x33FFFFFF))
                                    .then(
                                        if (subtitleChipFocused || subtitleFocused) Modifier.border(
                                            width = 2.dp,
                                            color = SecondaryCyan,
                                            shape = RoundedCornerShape(8.dp)
                                        ) else Modifier
                                    )
                                    .clickable(interactionSource = subtitleInteraction, indication = null) { showSubtitleDialog = true }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Subtitles,
                                    contentDescription = null,
                                    tint = if (isSubtitleEnabled) SecondaryCyan else TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (isSubtitleEnabled) "CC On" else "Subtitles",
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Video Mode / Speed Info
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = aspectRatio.label.substringBefore(" ("),
                                color = AccentAmber,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0x33FFAA00))
                                    .clickable { playerManager.cycleAspectRatio() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )

                            if (playbackSpeed != 1.0f) {
                                Text(
                                    text = "${playbackSpeed}x",
                                    color = PrimaryNeon,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0x336C5CE7))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
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
