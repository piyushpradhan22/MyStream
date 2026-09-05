package com.mystream.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.mystream.app.data.api.SystemFallbackDns
import com.mystream.app.data.repository.SourcesRepository
import com.mystream.app.player.MyStreamPlayerManager
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

import okio.Path.Companion.toOkioPath

class MyStreamApplication : Application(), SingletonImageLoader.Factory {

    lateinit var sourcesRepository: SourcesRepository
        private set

    lateinit var playerManager: MyStreamPlayerManager
        private set

    override fun onCreate() {
        super.onCreate()
        sourcesRepository = SourcesRepository(this)
        playerManager = MyStreamPlayerManager(this, sourcesRepository = sourcesRepository)
    }

    private var imageLoaderInstance: ImageLoader? = null

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val isTv = com.mystream.app.ui.utils.DeviceUtils.isTvDevice(context)
        val isLowRam = com.mystream.app.ui.utils.DeviceUtils.isLowRamDevice(context)
        val cachePercent = if (isTv || isLowRam) 0.08 else 0.15
        val diskCacheBytes = if (isTv || isLowRam) 50L * 1024 * 1024 else 80L * 1024 * 1024

        val okHttpClient = OkHttpClient.Builder()
            .dns(SystemFallbackDns)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .dispatcher(okhttp3.Dispatcher().apply {
                if (isTv || isLowRam) {
                    maxRequests = 4
                    maxRequestsPerHost = 4
                } else {
                    maxRequests = 16
                    maxRequestsPerHost = 8
                }
            })
            .build()

        val loader = ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, cachePercent)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(diskCacheBytes)
                    .build()
            }
            .crossfade(!isTv) // Disable heavy alpha-crossfade on TV to eliminate 2-bitmap memory overlap
            .build()

        imageLoaderInstance = loader
        return loader
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_BACKGROUND || level >= TRIM_MEMORY_COMPLETE) {
            imageLoaderInstance?.memoryCache?.clear()
        } else if (level >= TRIM_MEMORY_UI_HIDDEN || level >= TRIM_MEMORY_MODERATE) {
            val currentMax = imageLoaderInstance?.memoryCache?.maxSize ?: 0
            imageLoaderInstance?.memoryCache?.trimToSize(currentMax / 2)
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        imageLoaderInstance?.memoryCache?.clear()
    }

    override fun onTerminate() {
        playerManager.release()
        super.onTerminate()
    }
}
