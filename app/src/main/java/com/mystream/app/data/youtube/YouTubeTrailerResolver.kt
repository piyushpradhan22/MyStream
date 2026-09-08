package com.mystream.app.data.youtube

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import java.util.concurrent.atomic.AtomicBoolean

/** A YouTube trailer resolved to direct, ExoPlayer-playable stream URLs. */
data class ResolvedTrailer(
    val videoUrl: String,
    /** Non-null only when the video track has no muxed audio; caller must merge the two. */
    val audioUrl: String? = null
)

/**
 * Resolves a YouTube video id to direct stream URLs via NewPipeExtractor so trailers can play
 * natively in ExoPlayer instead of a WebView. Results are cached until the URLs expire.
 */
object YouTubeTrailerResolver {

    private const val TAG = "YtTrailerResolver"
    private const val CACHE_TTL_MS = 4 * 60 * 60 * 1000L // YouTube URLs expire ~6h; refresh earlier.

    private val initialized = AtomicBoolean(false)

    private data class CacheEntry(val trailer: ResolvedTrailer, val expiresAt: Long)

    private val cache = object : java.util.LinkedHashMap<String, CacheEntry>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?): Boolean = size > 48
    }

    /** Must be called once (e.g. from Application) with an OkHttp-backed downloader. */
    fun init(downloader: NewPipeDownloader) {
        if (initialized.compareAndSet(false, true)) {
            NewPipe.init(downloader)
        }
    }

    suspend fun resolve(ytId: String): ResolvedTrailer? {
        if (ytId.isBlank()) return null
        synchronized(cache) {
            cache[ytId]?.let { if (it.expiresAt > System.currentTimeMillis()) return it.trailer }
        }
        return withContext(Dispatchers.IO) {
            try {
                val extractor = ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v=$ytId")
                extractor.fetchPage()

                // Prefer a muxed progressive stream (single URL, lowest decode cost on weak TV).
                val muxed = extractor.videoStreams
                    .filter { !it.url.isNullOrBlank() }
                    .minByOrNull { resolutionRank(it.resolution) }

                val resolved = if (muxed != null) {
                    ResolvedTrailer(videoUrl = muxed.url!!)
                } else {
                    // Fall back to separate adaptive video + audio tracks to be merged by the player.
                    val video = extractor.videoOnlyStreams
                        .filter { !it.url.isNullOrBlank() }
                        .minByOrNull { resolutionRank(it.resolution) }
                    val audio = extractor.audioStreams
                        .filter { !it.url.isNullOrBlank() }
                        .maxByOrNull { it.averageBitrate }
                    if (video != null && audio != null) {
                        ResolvedTrailer(videoUrl = video.url!!, audioUrl = audio.url)
                    } else null
                }

                if (resolved != null) {
                    synchronized(cache) {
                        cache[ytId] = CacheEntry(resolved, System.currentTimeMillis() + CACHE_TTL_MS)
                    }
                }
                resolved
            } catch (e: Exception) {
                Log.w(TAG, "Failed to resolve trailer $ytId: ${e.message}")
                null
            }
        }
    }

    // Lower rank = preferred. Targets ~360-480p: smooth on weak TV, low bandwidth for a background trailer.
    private fun resolutionRank(resolution: String?): Int {
        val height = resolution?.substringBefore('p')?.trim()?.toIntOrNull() ?: return Int.MAX_VALUE
        return kotlin.math.abs(height - 420)
    }
}
