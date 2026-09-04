package com.mystream.app.ui.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import com.mystream.app.data.model.MediaPlaybackItem

object ExternalPlayerHelper {

    fun launchExternalPlayer(
        context: Context,
        item: MediaPlaybackItem,
        currentPositionMs: Long = 0L
    ) {
        val streamUrl = item.mediaUrl.trim()
        if (streamUrl.isBlank()) {
            Toast.makeText(context, "Stream URL is not available", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val uri = Uri.parse(streamUrl)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/*")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                // Standard title extras
                putExtra("title", item.title)
                putExtra("displayName", item.title)
                putExtra("android.media.intent.extra.TITLE", item.title)

                // Seek / resume position (MX Player, Just Player, VLC, Nova)
                val pos = if (currentPositionMs > 0L) currentPositionMs else item.startPositionMs
                if (pos > 0L) {
                    putExtra("position", pos)
                    putExtra("return_result", true)
                }

                // Headers (PikPak User-Agent, Accept-Encoding)
                val headers = mutableListOf("User-Agent", "ANDROID-com.pikcloud.pikpak/1.47.1")
                item.headers?.forEach { (k, v) ->
                    headers.add(k)
                    headers.add(v)
                }
                putExtra("headers", headers.toTypedArray())

                val bundle = Bundle().apply {
                    putString("User-Agent", "ANDROID-com.pikcloud.pikpak/1.47.1")
                    item.headers?.forEach { (k, v) -> putString(k, v) }
                }
                putExtra("android.media.intent.extra.HTTP_HEADERS", bundle)
            }

            val chooser = Intent.createChooser(intent, "Play with external player...")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to launch external player: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
