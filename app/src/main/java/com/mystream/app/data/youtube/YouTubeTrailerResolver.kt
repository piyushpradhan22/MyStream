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

    private data class TrailerVideoCandidate(
        val videoUrl: String,
        val audioUrl: String?,
        val resolution: String?,
        val isMuxed: Boolean
    )

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

                val bestAudio = extractor.audioStreams
                    .filter { !it.url.isNullOrBlank() }
                    .maxByOrNull { it.averageBitrate }

                val candidates = buildList {
                    extractor.videoStreams
                        .filter { !it.url.isNullOrBlank() }
                        .forEach { stream ->
                            add(TrailerVideoCandidate(stream.url!!, null, stream.resolution, isMuxed = true))
                        }
                    if (bestAudio != null) {
                        extractor.videoOnlyStreams
                            .filter { !it.url.isNullOrBlank() }
                            .forEach { stream ->
                                add(TrailerVideoCandidate(stream.url!!, bestAudio.url, stream.resolution, isMuxed = false))
                            }
                    }
                }

                val selected = candidates.minWithOrNull(
                    compareBy<TrailerVideoCandidate> { resolutionRank(it.resolution) }
                        .thenBy { if (it.isMuxed) 0 else 1 }
                )

                val resolved = selected?.let { ResolvedTrailer(videoUrl = it.videoUrl, audioUrl = it.audioUrl) }

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

    // Lower rank = preferred. Target 720p for sharper TV backgrounds, then fall back to 480p/360p.
    private fun resolutionRank(resolution: String?): Int {
        val height = resolution?.substringBefore('p')?.trim()?.toIntOrNull() ?: return Int.MAX_VALUE
        val preferredHeights = listOf(720, 480, 360, 1080, 240)
        val nearestPreferredIndex = preferredHeights.indices.minBy { kotlin.math.abs(preferredHeights[it] - height) }
        return nearestPreferredIndex * 10_000 + kotlin.math.abs(preferredHeights[nearestPreferredIndex] - height)
    }
}
