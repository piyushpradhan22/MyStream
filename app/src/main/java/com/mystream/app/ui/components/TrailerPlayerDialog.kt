package com.mystream.app.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mystream.app.ui.theme.FocusRingOrange
import com.mystream.app.ui.theme.PrimaryNeon
import com.mystream.app.ui.theme.SurfaceDark
import com.mystream.app.ui.theme.TextPrimary
import com.mystream.app.ui.theme.TextSecondary
import com.mystream.app.ui.utils.safeRequestFocus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private class YouTubeJsBridge(private val onError: (Int) -> Unit) {
    @JavascriptInterface
    fun onVideoError(code: Int) {
        onError(code)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TrailerPlayerDialog(
    ytId: String,
    title: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isPlaying by remember { mutableStateOf(true) }
    var areSubtitlesEnabled by remember { mutableStateOf(false) }
    var seekActionFeedback by remember { mutableStateOf<String?>(null) }
    var isControlsVisible by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var hasPlaybackError by remember { mutableStateOf(false) }

    val playPauseFocusRequester = remember { FocusRequester() }
    val rewindFocusRequester = remember { FocusRequester() }
    val forwardFocusRequester = remember { FocusRequester() }
    val subtitleFocusRequester = remember { FocusRequester() }
    val openAppFocusRequester = remember { FocusRequester() }
    val closeFocusRequester = remember { FocusRequester() }
    val errorAppFocusRequester = remember { FocusRequester() }
    val errorCloseFocusRequester = remember { FocusRequester() }

    fun launchExternalYouTube() {
        val videoUrl = "https://www.youtube.com/watch?v=$ytId"
        val uri = Uri.parse(videoUrl)

        val candidatePackages = listOf(
            "org.smarttube.stable",
            "com.google.android.youtube.tv",
            "com.google.android.youtube",
            "org.smarttube.beta",
            "com.teamsmart.videomanager.tv",
            "org.schabi.newpipe"
        )

        var launched = false

        // 1. Try known YouTube / SmartTube apps directly
        for (pkg in candidatePackages) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage(pkg)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                launched = true
                break
            } catch (_: Exception) {
                // package not installed or couldn't handle, try next
            }
        }

        // 2. Generic HTTPS ACTION_VIEW intent (picks default web browser or system handler)
        if (!launched) {
            try {
                val genericIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(genericIntent)
                launched = true
            } catch (_: Exception) {
                // continue to fallback
            }
        }

        // 3. Fallback to vnd.youtube schema
        if (!launched) {
            try {
                val vndIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$ytId")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(vndIntent)
                launched = true
            } catch (e: Exception) {
                android.util.Log.e("TrailerPlayerDialog", "Failed to launch external app for trailer", e)
                android.widget.Toast.makeText(context, "No app available to play YouTube trailer", android.widget.Toast.LENGTH_LONG).show()
            }
        }

        onDismiss()
    }

    fun registerInteraction() {
        isControlsVisible = true
        lastInteractionTime = System.currentTimeMillis()
    }

    LaunchedEffect(lastInteractionTime, hasPlaybackError) {
        if (!hasPlaybackError) {
            delay(5000)
            isControlsVisible = false
        }
    }

    LaunchedEffect(hasPlaybackError) {
        if (hasPlaybackError) {
            delay(300)
            errorAppFocusRequester.safeRequestFocus()
        }
    }

    fun seekRelative(seconds: Int) {
        registerInteraction()
        val label = if (seconds > 0) "+${seconds}s" else "${seconds}s"
        seekActionFeedback = label
        webViewInstance?.evaluateJavascript("ytSeekRelative($seconds);", null)
        scope.launch {
            delay(1200)
            if (seekActionFeedback == label) {
                seekActionFeedback = null
            }
        }
    }

    fun togglePlayPause() {
        registerInteraction()
        webViewInstance?.evaluateJavascript("ytTogglePlayPause();") { res ->
            if (res == "1") {
                isPlaying = true
                seekActionFeedback = "Play"
            } else if (res == "2") {
                isPlaying = false
                seekActionFeedback = "Pause"
            } else {
                isPlaying = !isPlaying
                seekActionFeedback = if (isPlaying) "Play" else "Pause"
            }
            scope.launch {
                delay(1200)
                seekActionFeedback = null
            }
        }
    }

    fun toggleSubtitles() {
        registerInteraction()
        webViewInstance?.evaluateJavascript("ytToggleSubtitles();") { res ->
            val enabled = res == "1"
            areSubtitlesEnabled = enabled
            val label = if (enabled) "Subtitles: ON" else "Subtitles: OFF"
            seekActionFeedback = label
            scope.launch {
                delay(1500)
                if (seekActionFeedback == label) {
                    seekActionFeedback = null
                }
            }
        }
    }

    BackHandler(onBack = onDismiss)

    LaunchedEffect(Unit) {
        delay(400)
        playPauseFocusRequester.safeRequestFocus()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown && !hasPlaybackError) {
                        registerInteraction()
                        when (keyEvent.key) {
                            Key.MediaFastForward -> {
                                seekRelative(10)
                                true
                            }
                            Key.MediaRewind -> {
                                seekRelative(-10)
                                true
                            }
                            Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                                togglePlayPause()
                                true
                            }
                            else -> false
                        }
                    } else false
                }
        ) {
            DisposableEffect(Unit) {
                onDispose {
                    webViewInstance?.apply {
                        stopLoading()
                        loadUrl("about:blank")
                        clearHistory()
                        removeAllViews()
                        destroy()
                    }
                }
            }

            // 1. YouTube IFrame API Embedded Player (Focus disabled so Compose retains TV focus)
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        isFocusable = false
                        isFocusableInTouchMode = false
                        setBackgroundColor(0xFF000000.toInt())
                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {}
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            mediaPlaybackRequiresUserGesture = false
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
                        }
                        addJavascriptInterface(
                            YouTubeJsBridge { _ ->
                                scope.launch {
                                    hasPlaybackError = true
                                }
                            },
                            "AndroidBridge"
                        )
                        val htmlContent = """
                            <!DOCTYPE html>
                            <html>
                            <head>
                                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                                <style>
                                    * { box-sizing: border-box; margin: 0; padding: 0; }
                                    body, html {
                                        width: 100vw;
                                        height: 100vh;
                                        background-color: #000000;
                                        overflow: hidden;
                                        pointer-events: none;
                                    }
                                    #player {
                                        width: 100vw;
                                        height: 100vh;
                                        border: 0;
                                        position: absolute;
                                        top: 0;
                                        left: 0;
                                    }
                                </style>
                                <script src="https://www.youtube.com/iframe_api"></script>
                            </head>
                            <body>
                                <div id="player"></div>
                                <script>
                                    var player = null;
                                    function onYouTubeIframeAPIReady() {
                                        player = new YT.Player('player', {
                                            videoId: '$ytId',
                                            playerVars: {
                                                'autoplay': 1,
                                                'controls': 0,
                                                'modestbranding': 1,
                                                'playsinline': 1,
                                                'rel': 0,
                                                'enablejsapi': 1,
                                                'iv_load_policy': 3,
                                                'fs': 0
                                            },
                                            events: {
                                                'onReady': function(event) {
                                                    event.target.playVideo();
                                                },
                                                'onError': function(event) {
                                                    try {
                                                        if (window.AndroidBridge) {
                                                            window.AndroidBridge.onVideoError(event.data);
                                                        }
                                                    } catch(e){}
                                                }
                                            }
                                        });
                                    }
                                    function ytSeekRelative(seconds) {
                                        try {
                                            if (player && typeof player.getCurrentTime === 'function' && typeof player.seekTo === 'function') {
                                                var cur = player.getCurrentTime();
                                                player.seekTo(Math.max(0, cur + seconds), true);
                                            }
                                        } catch(e) {}
                                    }
                                    function ytTogglePlayPause() {
                                        try {
                                            if (player && typeof player.getPlayerState === 'function') {
                                                var state = player.getPlayerState();
                                                if (state === 1) {
                                                    player.pauseVideo();
                                                    return 2;
                                                } else {
                                                    player.playVideo();
                                                    return 1;
                                                }
                                            }
                                        } catch(e) {}
                                        return 0;
                                    }
                                    function ytToggleSubtitles() {
                                        try {
                                            if (player) {
                                                if (typeof player.loadModule === 'function') {
                                                    player.loadModule("captions");
                                                }
                                                if (typeof player.getOption === 'function' && typeof player.setOption === 'function') {
                                                    var track = player.getOption("captions", "track");
                                                    if (track && (track.languageCode || track.displayName)) {
                                                        player.setOption("captions", "track", {});
                                                        return 0;
                                                    } else {
                                                        player.setOption("captions", "track", {"languageCode": "en"});
                                                        return 1;
                                                    }
                                                }
                                            }
                                        } catch(e) {}
                                        return 0;
                                    }
                                </script>
                            </body>
                            </html>
                        """.trimIndent()

                        loadDataWithBaseURL("https://www.youtube-nocookie.com", htmlContent, "text/html", "UTF-8", null)
                        webViewInstance = this
                    }
                }
            )

            // 2. Floating Top Overlay HUD (Title & Close)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .align(Alignment.TopCenter)
                    .alpha(if (isControlsVisible && !hasPlaybackError) 1f else 0f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Title pill on top left
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xBB05070B))
                        .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(16.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF0000)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    Text(
                        text = if (title.contains("Trailer", ignoreCase = true)) title else "$title • Official Trailer",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Close button on top right
                val closeInteraction = remember { MutableInteractionSource() }
                val isCloseFocused by closeInteraction.collectIsFocusedAsState()

                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(if (isCloseFocused) FocusRingOrange else Color(0xBB05070B))
                        .border(
                            width = if (isCloseFocused) 1.5.dp else 1.dp,
                            color = if (isCloseFocused) FocusRingOrange else Color(0x22FFFFFF),
                            shape = CircleShape
                        )
                        .focusRequester(closeFocusRequester)
                        .focusable(interactionSource = closeInteraction)
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                registerInteraction()
                                when (keyEvent.key) {
                                    Key.DirectionDown -> {
                                        playPauseFocusRequester.safeRequestFocus()
                                        true
                                    }
                                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                        onDismiss()
                                        true
                                    }
                                    else -> false
                                }
                            } else false
                        }
                        .clickable(interactionSource = closeInteraction, indication = null, onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Trailer",
                        tint = if (isCloseFocused) Color.Black else TextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // 3. Center Screen Action Feedback Toast (Seek / Play / Pause / CC)
            AnimatedVisibility(
                visible = seekActionFeedback != null && !hasPlaybackError,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xDD0F172A))
                        .border(1.5.dp, PrimaryNeon, RoundedCornerShape(12.dp))
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = seekActionFeedback.orEmpty(),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 4. Compact Floating Bottom TV Control Bar with D-Pad Nav (Kept composed to retain focus)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp)
                    .align(Alignment.BottomCenter)
                    .alpha(if (isControlsVisible && !hasPlaybackError) 1f else 0f),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xDD07090E))
                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(18.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    // Rewind 10s Button
                    val rewInteraction = remember { MutableInteractionSource() }
                    val isRewFocused by rewInteraction.collectIsFocusedAsState()

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isRewFocused) FocusRingOrange else Color(0x22FFFFFF))
                            .focusRequester(rewindFocusRequester)
                            .focusable(interactionSource = rewInteraction)
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    registerInteraction()
                                    when (keyEvent.key) {
                                        Key.DirectionUp -> {
                                            closeFocusRequester.safeRequestFocus()
                                            true
                                        }
                                        Key.DirectionRight -> {
                                            playPauseFocusRequester.safeRequestFocus()
                                            true
                                        }
                                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                            seekRelative(-10)
                                            true
                                        }
                                        else -> false
                                    }
                                } else false
                            }
                            .clickable(interactionSource = rewInteraction, indication = null) {
                                seekRelative(-10)
                            }
                            .padding(horizontal = 7.dp, vertical = 4.dp)
                        ) {
                        Icon(
                            imageVector = Icons.Default.FastRewind,
                            contentDescription = "Rewind 10s",
                            tint = if (isRewFocused) Color.Black else TextPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "-10s",
                            color = if (isRewFocused) Color.Black else TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Play / Pause Button
                    val playInteraction = remember { MutableInteractionSource() }
                    val isPlayFocused by playInteraction.collectIsFocusedAsState()

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isPlayFocused) FocusRingOrange else PrimaryNeon)
                            .focusRequester(playPauseFocusRequester)
                            .focusable(interactionSource = playInteraction)
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    registerInteraction()
                                    when (keyEvent.key) {
                                        Key.DirectionUp -> {
                                            closeFocusRequester.safeRequestFocus()
                                            true
                                        }
                                        Key.DirectionLeft -> {
                                            rewindFocusRequester.safeRequestFocus()
                                            true
                                        }
                                        Key.DirectionRight -> {
                                            forwardFocusRequester.safeRequestFocus()
                                            true
                                        }
                                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                            togglePlayPause()
                                            true
                                        }
                                        else -> false
                                    }
                                } else false
                            }
                            .clickable(interactionSource = playInteraction, indication = null) {
                                togglePlayPause()
                            }
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.Black,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (isPlaying) "Pause" else "Play",
                            color = Color.Black,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Forward 10s Button
                    val fwdInteraction = remember { MutableInteractionSource() }
                    val isFwdFocused by fwdInteraction.collectIsFocusedAsState()

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isFwdFocused) FocusRingOrange else Color(0x22FFFFFF))
                            .focusRequester(forwardFocusRequester)
                            .focusable(interactionSource = fwdInteraction)
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    registerInteraction()
                                    when (keyEvent.key) {
                                        Key.DirectionUp -> {
                                            closeFocusRequester.safeRequestFocus()
                                            true
                                        }
                                        Key.DirectionLeft -> {
                                            playPauseFocusRequester.safeRequestFocus()
                                            true
                                        }
                                        Key.DirectionRight -> {
                                            subtitleFocusRequester.safeRequestFocus()
                                            true
                                        }
                                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                            seekRelative(10)
                                            true
                                        }
                                        else -> false
                                    }
                                } else false
                            }
                            .clickable(interactionSource = fwdInteraction, indication = null) {
                                seekRelative(10)
                            }
                            .padding(horizontal = 7.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "+10s",
                            color = if (isFwdFocused) Color.Black else TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.Default.FastForward,
                            contentDescription = "Forward 10s",
                            tint = if (isFwdFocused) Color.Black else TextPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    // Subtitles / CC Button
                    val subInteraction = remember { MutableInteractionSource() }
                    val isSubFocused by subInteraction.collectIsFocusedAsState()

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isSubFocused) FocusRingOrange 
                                else if (areSubtitlesEnabled) Color(0x4400E676)
                                else Color(0x22FFFFFF)
                            )
                            .border(
                                width = if (isSubFocused) 1.5.dp else 1.dp,
                                color = if (isSubFocused) FocusRingOrange else if (areSubtitlesEnabled) Color(0xFF00E676) else Color.Transparent,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .focusRequester(subtitleFocusRequester)
                            .focusable(interactionSource = subInteraction)
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    registerInteraction()
                                    when (keyEvent.key) {
                                        Key.DirectionUp -> {
                                            closeFocusRequester.safeRequestFocus()
                                            true
                                        }
                                        Key.DirectionLeft -> {
                                            forwardFocusRequester.safeRequestFocus()
                                            true
                                        }
                                        Key.DirectionRight -> {
                                            openAppFocusRequester.safeRequestFocus()
                                            true
                                        }
                                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                            toggleSubtitles()
                                            true
                                        }
                                        else -> false
                                    }
                                } else false
                            }
                            .clickable(interactionSource = subInteraction, indication = null) {
                                toggleSubtitles()
                            }
                            .padding(horizontal = 7.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ClosedCaption,
                            contentDescription = "Subtitles CC",
                            tint = if (isSubFocused) Color.Black else if (areSubtitlesEnabled) Color(0xFF00E676) else TextPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "CC",
                            color = if (isSubFocused) Color.Black else if (areSubtitlesEnabled) Color(0xFF00E676) else TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Open in YouTube App Button
                    val appInteraction = remember { MutableInteractionSource() }
                    val isAppFocused by appInteraction.collectIsFocusedAsState()

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isAppFocused) FocusRingOrange else Color(0x22FFFFFF))
                            .border(
                                width = if (isAppFocused) 1.5.dp else 1.dp,
                                color = if (isAppFocused) FocusRingOrange else Color.Transparent,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .focusRequester(openAppFocusRequester)
                            .focusable(interactionSource = appInteraction)
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    registerInteraction()
                                    when (keyEvent.key) {
                                        Key.DirectionUp -> {
                                            closeFocusRequester.safeRequestFocus()
                                            true
                                        }
                                        Key.DirectionLeft -> {
                                            subtitleFocusRequester.safeRequestFocus()
                                            true
                                        }
                                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                            launchExternalYouTube()
                                            true
                                        }
                                        else -> false
                                    }
                                } else false
                            }
                            .clickable(interactionSource = appInteraction, indication = null) {
                                launchExternalYouTube()
                            }
                            .padding(horizontal = 7.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "YouTube App",
                            tint = if (isAppFocused) Color.Black else TextPrimary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "App",
                            color = if (isAppFocused) Color.Black else TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // 5. Age-Restricted / Embed-Restricted External App Prompt Modal
            if (hasPlaybackError) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xE605070B)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .padding(horizontal = 32.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(SurfaceDark)
                            .border(1.5.dp, Color(0x33FFFFFF), RoundedCornerShape(18.dp))
                            .padding(horizontal = 24.dp, vertical = 20.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0x33FF453A)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFFF453A),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Text(
                            text = "Playback Restricted by YouTube",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "This trailer is age-restricted or disabled for embedded playback.\nWould you like to play it directly in the YouTube app?",
                            color = TextSecondary,
                            fontSize = 12.5.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 17.sp
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Open in App Button (Default Focused)
                            val errAppInteraction = remember { MutableInteractionSource() }
                            val isErrAppFocused by errAppInteraction.collectIsFocusedAsState()

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isErrAppFocused) FocusRingOrange else PrimaryNeon)
                                    .focusRequester(errorAppFocusRequester)
                                    .focusable(interactionSource = errAppInteraction)
                                    .onPreviewKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyDown) {
                                            when (keyEvent.key) {
                                                Key.DirectionRight -> {
                                                    errorCloseFocusRequester.safeRequestFocus()
                                                    true
                                                }
                                                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                                    launchExternalYouTube()
                                                    true
                                                }
                                                else -> false
                                            }
                                        } else false
                                    }
                                    .clickable(interactionSource = errAppInteraction, indication = null) {
                                        launchExternalYouTube()
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Open in YouTube App",
                                    color = Color.Black,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Close Button
                            val errCloseInteraction = remember { MutableInteractionSource() }
                            val isErrCloseFocused by errCloseInteraction.collectIsFocusedAsState()

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isErrCloseFocused) FocusRingOrange else Color(0x22FFFFFF))
                                    .focusRequester(errorCloseFocusRequester)
                                    .focusable(interactionSource = errCloseInteraction)
                                    .onPreviewKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyDown) {
                                            when (keyEvent.key) {
                                                Key.DirectionLeft -> {
                                                    errorAppFocusRequester.safeRequestFocus()
                                                    true
                                                }
                                                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                                    onDismiss()
                                                    true
                                                }
                                                else -> false
                                            }
                                        } else false
                                    }
                                    .clickable(interactionSource = errCloseInteraction, indication = null, onClick = onDismiss)
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Close",
                                    color = if (isErrCloseFocused) Color.Black else TextPrimary,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
