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

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val okHttpClient = OkHttpClient.Builder()
            .dns(SystemFallbackDns)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(100L * 1024 * 1024) // 100MB disk cache
                    .build()
            }
            .crossfade(true)
            .build()
    }

    override fun onTerminate() {
        playerManager.release()
        super.onTerminate()
    }
}
