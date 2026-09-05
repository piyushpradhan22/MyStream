package com.mystream.app.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Singleton manager to coordinate seamless background trailer playback across HomeScreen and DetailScreen.
 * Prevents trailer re-downloads and video reloads during screen transitions.
 */
object TrailerPlaybackManager {
    var activeTrailerYtId by mutableStateOf<String?>(null)
        private set

    var isAudioMuted by mutableStateOf(false)
        private set

    var isStopped by mutableStateOf(false)
        private set

    var isTrailerLayerVisible by mutableStateOf(true)
        private set

    var isPlaybackEnabled by mutableStateOf(true)
        private set

    var isVideoPlaying by mutableStateOf(false)

    // Direct listener to dispatch commands to the persistent WebView player without destruction
    var playerCommandListener: ((command: String, arg: String?) -> Unit)? = null

    fun play(ytId: String, muted: Boolean = isAudioMuted, forceReplay: Boolean = false) {
        if (ytId.isBlank()) return
        isStopped = false
        isAudioMuted = muted
        isPlaybackEnabled = true
        if (activeTrailerYtId == ytId && !forceReplay) {
            playerCommandListener?.invoke("RESUME", null)
            return
        }
        activeTrailerYtId = ytId
        if (forceReplay) {
            playerCommandListener?.invoke("LOAD_OR_REPLAY", ytId)
        } else {
            playerCommandListener?.invoke("LOAD_VIDEO", ytId)
        }
    }

    fun restartOrLoad(ytId: String, muted: Boolean = isAudioMuted) {
        play(ytId, muted = muted, forceReplay = true)
    }

    fun stop() {
        isStopped = true
        isVideoPlaying = false
        playerCommandListener?.invoke("PAUSE", null)
    }

    fun pause() {
        isStopped = true
        playerCommandListener?.invoke("PAUSE", null)
    }

    fun resume() {
        if (!activeTrailerYtId.isNullOrBlank()) {
            isStopped = false
            playerCommandListener?.invoke("RESUME", null)
        }
    }

    fun setMuted(muted: Boolean) {
        isAudioMuted = muted
        playerCommandListener?.invoke(if (muted) "MUTE" else "UNMUTE", null)
    }

    fun togglePlayPause() {
        if (isStopped) resume() else pause()
    }

    @JvmName("applyPlaybackEnabled")
    fun setPlaybackEnabled(enabled: Boolean) {
        isPlaybackEnabled = enabled
        if (!enabled) {
            stop()
        }
    }

    fun setVisibility(visible: Boolean) {
        isTrailerLayerVisible = visible
        if (!visible) {
            stop()
        }
    }

    fun release() {
        activeTrailerYtId = null
        stop()
    }
}
