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
import java.io.File

@OptIn(UnstableApi::class)
object MediaCacheManager {
    private const val TAG = "MediaCacheManager"
    // High-speed disk cache (up to 750 MB)
    private const val MAX_CACHE_BYTES = 750L * 1024L * 1024L

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
}
