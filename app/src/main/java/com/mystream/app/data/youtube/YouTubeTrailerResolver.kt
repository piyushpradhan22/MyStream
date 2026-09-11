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

    suspend fun resolve(ytId: String, preferredResolution: String = "480p"): ResolvedTrailer? {
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

                val selected = candidates.minWithOrNull { a, b ->
                    val targetHeight = parseResolutionHeight(preferredResolution) ?: 480
                    val aHeight = parseResolutionHeight(a.resolution)
                    val bHeight = parseResolutionHeight(b.resolution)

                    val aMatchesTarget = if (aHeight == targetHeight) 0 else 1
                    val bMatchesTarget = if (bHeight == targetHeight) 0 else 1
                    if (aMatchesTarget != bMatchesTarget) {
                        return@minWithOrNull aMatchesTarget.compareTo(bMatchesTarget)
                    }

                    val aDistance = if (aHeight != null) kotlin.math.abs(aHeight - targetHeight) else Int.MAX_VALUE
                    val bDistance = if (bHeight != null) kotlin.math.abs(bHeight - targetHeight) else Int.MAX_VALUE
                    if (aDistance != bDistance) {
                        return@minWithOrNull aDistance.compareTo(bDistance)
                    }

                    if (a.isMuxed != b.isMuxed) {
                        return@minWithOrNull if (a.isMuxed) -1 else 1
                    }

                    0
                }

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

    private fun parseResolutionHeight(resolution: String?): Int? {
        return resolution?.substringBefore('p')?.trim()?.toIntOrNull()
    }
}
