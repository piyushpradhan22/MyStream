package com.mystream.app.ui.components

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.viewinterop.AndroidView

private class BackgroundYouTubeJsBridge(
    private val onVideoPlaying: () -> Unit,
    private val onVideoEnded: () -> Unit,
    private val onVideoError: (Int) -> Unit
) {
    @JavascriptInterface
    fun onStateChange(state: Int) {
        // 1 = YT.PlayerState.PLAYING, 0 = YT.PlayerState.ENDED
        if (state == 1) {
            onVideoPlaying()
        } else if (state == 0) {
            onVideoEnded()
        }
    }

    @JavascriptInterface
    fun onError(code: Int) {
        onVideoError(code)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BackgroundTrailerPlayer(
    ytId: String,
    isAudioMuted: Boolean = false,
    isStopped: Boolean = false,
    isHomeScreen: Boolean = false,
    modifier: Modifier = Modifier,
    onPlaybackStarted: (() -> Unit)? = null,
    onVideoEnded: (() -> Unit)? = null
) {
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isVideoReady by remember { mutableStateOf(false) }

    // Register TrailerPlaybackManager command listener for in-place video switching and playback control
    DisposableEffect(Unit) {
        TrailerPlaybackManager.playerCommandListener = { cmd, arg ->
            when (cmd) {
                "LOAD_VIDEO" -> {
                    if (!arg.isNullOrBlank()) {
                        webViewInstance?.evaluateJavascript(
                            "if (window.player && player.loadVideoById) { player.loadVideoById('$arg'); player.playVideo(); }",
                            null
                        )
                    }
                }
                "LOAD_OR_REPLAY" -> {
                    if (!arg.isNullOrBlank()) {
                        webViewInstance?.evaluateJavascript(
                            "if (window.player) { if (player.getVideoData && player.getVideoData().video_id === '$arg') { player.seekTo(0, true); player.playVideo(); } else if (player.loadVideoById) { player.loadVideoById('$arg'); player.playVideo(); } }",
                            null
                        )
                    }
                }
                "PAUSE" -> {
                    webViewInstance?.evaluateJavascript("if (window.player && player.pauseVideo) { player.pauseVideo(); }", null)
                }
                "RESUME" -> {
                    webViewInstance?.evaluateJavascript("if (window.player && player.playVideo) { player.playVideo(); }", null)
                }
                "MUTE" -> {
                    webViewInstance?.evaluateJavascript("if (window.player && player.mute) { player.mute(); }", null)
                }
                "UNMUTE" -> {
                    webViewInstance?.evaluateJavascript("if (window.player && player.unMute) { player.unMute(); player.setVolume(85); }", null)
                }
            }
        }
        onDispose {
            TrailerPlaybackManager.playerCommandListener = null
            try {
                webViewInstance?.apply {
                    stopLoading()
                    loadUrl("about:blank")
                    clearHistory()
                    removeAllViews()
                    destroy()
                }
            } catch (_: Exception) {}
        }
    }

    // Dynamic video loading when ytId changes without recreating the WebView
    LaunchedEffect(ytId, webViewInstance) {
        if (webViewInstance != null && ytId.isNotBlank()) {
            webViewInstance?.evaluateJavascript(
                "if (window.player && player.loadVideoById) { if (!player.getVideoData || player.getVideoData().video_id !== '$ytId') { player.loadVideoById('$ytId'); player.playVideo(); } }",
                null
            )
        }
    }

    // Update mute state dynamically on the running player
    LaunchedEffect(isAudioMuted, webViewInstance) {
        val jsCmd = if (isAudioMuted) {
            "if (window.player && player.mute) { player.mute(); }"
        } else {
            "if (window.player && player.unMute) { player.unMute(); player.setVolume(85); }"
        }
        webViewInstance?.evaluateJavascript(jsCmd, null)
    }

    // Stop playback dynamically when isStopped becomes true
    LaunchedEffect(isStopped, webViewInstance) {
        if (isStopped) {
            webViewInstance?.evaluateJavascript("if (window.player && player.stopVideo) { player.stopVideo(); }", null)
            isVideoReady = false
        }
    }

    // Dynamically adjust player layout when transitioning between HomeScreen and DetailScreen
    LaunchedEffect(isHomeScreen, webViewInstance) {
        val jsCmd = if (isHomeScreen) {
            """
            var el = document.getElementById('player');
            if (el) {
                el.style.position = 'absolute';
                el.style.top = '-8%';
                el.style.left = '0';
                el.style.right = '0';
                el.style.width = '100%';
                el.style.height = '110%';
                el.style.transform = 'none';
            }
            """.trimIndent()
        } else {
            """
            var el = document.getElementById('player');
            if (el) {
                el.style.position = 'absolute';
                el.style.top = '0';
                el.style.left = '0';
                el.style.right = '0';
                el.style.width = '100%';
                el.style.height = '100%';
                el.style.transform = 'none';
            }
            """.trimIndent()
        }
        webViewInstance?.evaluateJavascript(jsCmd, null)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        val alpha by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (isVideoReady && !isStopped) 1f else 0f,
            animationSpec = androidx.compose.animation.core.tween(400),
            label = "TrailerAlpha"
        )

        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .alpha(alpha),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    isFocusable = false
                    isFocusableInTouchMode = false
                    setBackgroundColor(0x00000000)
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
                        BackgroundYouTubeJsBridge(
                            onVideoPlaying = {
                                post {
                                    isVideoReady = true
                                    onPlaybackStarted?.invoke()
                                }
                            },
                            onVideoEnded = {
                                post {
                                    isVideoReady = false
                                    onVideoEnded?.invoke()
                                }
                            },
                            onVideoError = { _ ->
                                post {
                                    isVideoReady = false
                                }
                            }
                        ),
                        "AndroidBridge"
                    )

                    val playerStyle = if (isHomeScreen) {
                        """
                        #player {
                            position: absolute;
                            top: -8%;
                            left: 0;
                            right: 0;
                            width: 100%;
                            height: 110%;
                            border: none;
                            transition: all 0.4s ease-in-out;
                        }
                        """.trimIndent()
                    } else {
                        """
                        #player {
                            position: absolute;
                            top: 0;
                            left: 0;
                            right: 0;
                            width: 100%;
                            height: 100%;
                            border: none;
                            transition: all 0.4s ease-in-out;
                        }
                        """.trimIndent()
                    }
                        val muteParam = if (isAudioMuted) "1" else "0"
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
                                        background-color: transparent;
                                        overflow: hidden;
                                        pointer-events: none;
                                    }
                                    $playerStyle
                                    .ytp-chrome-top, .ytp-title, .ytp-title-channel, .ytp-watermark, .ytp-pause-overlay, .ytp-gradient-top, .ytp-show-cards-title, .ytp-ce-element,
                                    .caption-window, .ytp-caption-window-bottom, .ytp-caption-segment, .ytp-subtitles-player, .ytp-caption-window-rollup, div[class*="caption-window"], div[class*="ytp-caption"], span[class*="caption"] {
                                        display: none !important;
                                        opacity: 0 !important;
                                        visibility: hidden !important;
                                    }
                                </style>
                            </head>
                            <body>
                                <div id="player"></div>
                                <script>
                                    var tag = document.createElement('script');
                                    tag.src = "https://www.youtube.com/iframe_api";
                                    var firstScriptTag = document.getElementsByTagName('script')[0];
                                    firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);

                                    var player;
                                    function onYouTubeIframeAPIReady() {
                                        player = new YT.Player('player', {
                                            videoId: '$ytId',
                                            playerVars: {
                                                'autoplay': 1,
                                                'controls': 0,
                                                'rel': 0,
                                                'showinfo': 0,
                                                'iv_load_policy': 3,
                                                'modestbranding': 1,
                                                'playsinline': 1,
                                                'enablejsapi': 1,
                                                'fs': 0,
                                                'disablekb': 1,
                                                'loop': 0,
                                                'cc_load_policy': 0,
                                                'cc_lang_pref': '',
                                                'origin': 'https://www.youtube-nocookie.com',
                                                'mute': $muteParam
                                            },
                                            events: {
                                                'onReady': onPlayerReady,
                                                'onStateChange': onPlayerStateChange,
                                                'onError': onPlayerError
                                            }
                                        });
                                    }

                                    function disableCaptions(p) {
                                        try {
                                            if (p && p.unloadModule) {
                                                p.unloadModule("captions");
                                                p.unloadModule("cc");
                                            }
                                            if (p && p.setOption) {
                                                p.setOption("captions", "track", {});
                                                p.setOption("cc", "track", {});
                                            }
                                        } catch (e) {}
                                    }

                                    function onPlayerReady(event) {
                                        disableCaptions(event.target);
                                        if ($muteParam === 0) {
                                            event.target.unMute();
                                            event.target.setVolume(85);
                                        }
                                        event.target.playVideo();
                                    }

                                    function onPlayerStateChange(event) {
                                        disableCaptions(event.target);
                                        if (window.AndroidBridge && window.AndroidBridge.onStateChange) {
                                            window.AndroidBridge.onStateChange(event.data);
                                        }
                                    }

                                    function onPlayerError(event) {
                                        if (window.AndroidBridge && window.AndroidBridge.onError) {
                                            window.AndroidBridge.onError(event.data);
                                        }
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
    }
}
