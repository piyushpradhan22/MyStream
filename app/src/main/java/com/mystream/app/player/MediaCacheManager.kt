package com.mystream.app.player

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(UnstableApi::class)
object MediaCacheManager {
    private const val TAG = "MediaCacheManager"
    // Right-sized disk cache for TV devices (200 MB max)
    private const val MAX_CACHE_BYTES = 200L * 1024L * 1024L

    @Volatile
    private var simpleCache: SimpleCache? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    @Synchronized
    fun getCache(context: Context): SimpleCache {
        return simpleCache ?: run {
            val cacheFolder = File(context.applicationContext.cacheDir, "media_stream_cache")
            if (!cacheFolder.exists()) {
                cacheFolder.mkdirs()
            }
            val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES)
            val databaseProvider = StandaloneDatabaseProvider(context.applicationContext)
            SimpleCache(cacheFolder, evictor, databaseProvider).also {
                simpleCache = it
            }
        }
    }

    fun clearCacheAsync() {
        scope.launch {
            try {
                simpleCache?.let { cache ->
                    val keys = cache.keys.toList()
                    for (key in keys) {
                        try {
                            cache.removeResource(key)
                        } catch (e: Exception) {
                            Log.w(TAG, "Error removing cache key $key: ${e.message}")
                        }
                    }
                    Log.i(TAG, "Media cache cleared (${keys.size} resources)")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error clearing media cache: ${e.message}")
            }
        }
    }

    /**
     * Calculates the total size of all cache files currently stored by the app.
     */
    fun getTotalCacheSizeBytes(context: Context): Long {
        return try {
            val cacheDir = context.applicationContext.cacheDir
            calculateFolderSize(cacheDir)
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Gets available free storage on the device filesystem.
     */
    fun getDeviceFreeSpaceBytes(context: Context): Long {
        return try {
            context.applicationContext.cacheDir.freeSpace
        } catch (e: Exception) {
            0L
        }
    }

    private fun calculateFolderSize(file: File?): Long {
        if (file == null || !file.exists()) return 0L
        if (file.isFile) return file.length()
        var size = 0L
        val children = file.listFiles() ?: return 0L
        for (child in children) {
            size += if (child.isDirectory) calculateFolderSize(child) else child.length()
        }
        return size
    }

    /**
     * Comprehensive cache purge: clears media cache, torrent cache, image cache, and old APKs.
     */
    fun clearAllAppCaches(context: Context, onComplete: (() -> Unit)? = null) {
        scope.launch {
            try {
                val appContext = context.applicationContext
                val cacheDir = appContext.cacheDir

                // 1. Evict ExoPlayer media stream cache
                try {
                    simpleCache?.let { cache ->
                        val keys = cache.keys.toList()
                        for (key in keys) {
                            try { cache.removeResource(key) } catch (_: Exception) {}
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error purging SimpleCache: ${e.message}")
                }

                // 2. Clear torrent_stream
                try {
                    File(cacheDir, "torrent_stream").listFiles()?.forEach { it.deleteRecursively() }
                } catch (_: Exception) {}

                // 3. Clear updates
                try {
                    File(cacheDir, "updates").listFiles()?.forEach { it.delete() }
                } catch (_: Exception) {}

                // 4. Clear image cache
                try {
                    File(cacheDir, "image_cache").listFiles()?.forEach { it.delete() }
                } catch (_: Exception) {}

                // 5. Clear stremio http cache
                try {
                    File(cacheDir, "stremio_http_cache").listFiles()?.forEach { it.deleteRecursively() }
                } catch (_: Exception) {}

                Log.i(TAG, "All application caches successfully purged")
            } catch (e: Exception) {
                Log.e(TAG, "Error purging application caches", e)
            } finally {
                withContext(Dispatchers.Main) {
                    onComplete?.invoke()
                }
            }
        }
    }

    /**
     * Automatic low storage guard: if remaining disk space is critically low (< 500 MB),
     * automatically evicts temporary caches to prevent system out-of-storage warnings.
     */
    fun checkAndTrimIfLowStorage(context: Context) {
        val freeBytes = getDeviceFreeSpaceBytes(context)
        val criticalThreshold = 500L * 1024L * 1024L // 500 MB
        if (freeBytes > 0 && freeBytes < criticalThreshold) {
            Log.w(TAG, "Critically low disk space (${freeBytes / (1024 * 1024)} MB free). Triggering cache purge.")
            clearAllAppCaches(context)
        }
    }
}
