package com.mystream.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mystream.app.data.api.StremioApiClient
import com.mystream.app.data.model.AppJsonConfig
import com.mystream.app.data.model.AppSettingsConfig
import com.mystream.app.data.model.MediaPlaybackItem
import com.mystream.app.data.model.MediaSourceEntity
import com.mystream.app.data.model.PikPakAccount
import com.mystream.app.data.model.StremioCatalogResponse
import com.mystream.app.data.model.StremioMetaDetail
import com.mystream.app.data.model.StremioStreamSource
import com.mystream.app.data.resolver.PikPakStreamResolver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import com.mystream.app.data.model.ImdbIndianItem
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "mystream_settings")

class SourcesRepository(
    private val context: Context,
    private val apiClient: StremioApiClient = StremioApiClient(),
    val pikpakResolver: PikPakStreamResolver = PikPakStreamResolver(context)
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    companion object {
        val DEFAULT_CATALOG_SOURCES = listOf(
            MediaSourceEntity(
                id = "cinemeta-official",
                name = "Cinemeta (Official Catalog)",
                baseUrl = "https://v3-cinemeta.strem.io/manifest.json",
                isDefault = true,
                isEnabled = true,
                type = "catalog"
            )
        )

        val SAMPLE_DEMO_SOURCES = listOf(
            MediaPlaybackItem(
                id = "demo_sintel_hls",
                title = "Sintel (Multi-Audio HLS)",
                subtitle = "Open Movie Project • 4K/1080p HLS with Multi-Language Audio",
                mediaUrl = "https://bitdash-a.akamaihd.net/content/sintel/hls/playlist.m3u8",
                posterUrl = "https://images.metahub.space/poster/small/tt1727587/img",
                backdropUrl = "https://images.metahub.space/background/medium/tt1727587/img"
            ),
            MediaPlaybackItem(
                id = "demo_tears_of_steel",
                title = "Tears of Steel (Dolby Vision / 4K)",
                subtitle = "Blender Foundation • 4K Widescreen Test Clip",
                mediaUrl = "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8",
                posterUrl = "https://images.metahub.space/poster/small/tt2426866/img",
                backdropUrl = "https://images.metahub.space/background/medium/tt2426866/img"
            ),
            MediaPlaybackItem(
                id = "demo_big_buck_bunny",
                title = "Big Buck Bunny (Surround 5.1 & Full HD)",
                subtitle = "Peach Open Movie • High Bitrate MP4",
                mediaUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                posterUrl = "https://images.metahub.space/poster/small/tt1254207/img",
                backdropUrl = "https://images.metahub.space/background/medium/tt1254207/img"
            )
        )

        private val CATALOG_SOURCES_KEY = stringPreferencesKey("installed_catalog_sources")
        private val APP_SETTINGS_KEY = stringPreferencesKey("app_settings_config")
        private val PLAYBACK_PROGRESS_KEY = stringPreferencesKey("playback_progress_records")
        private val WATCHLIST_KEY = stringPreferencesKey("user_watchlist_items")
    }

    val catalogSourcesFlow: Flow<List<MediaSourceEntity>> = context.dataStore.data.map { prefs ->
        val raw = prefs[CATALOG_SOURCES_KEY]
        if (raw.isNullOrBlank()) {
            DEFAULT_CATALOG_SOURCES
        } else {
            try {
                json.decodeFromString<List<MediaSourceEntity>>(raw)
            } catch (e: Exception) {
                DEFAULT_CATALOG_SOURCES
            }
        }
    }

    val appSettingsFlow: Flow<AppSettingsConfig> = context.dataStore.data.map { prefs ->
        val raw = prefs[APP_SETTINGS_KEY]
        if (raw.isNullOrBlank()) {
            AppSettingsConfig()
        } else {
            try {
                json.decodeFromString<AppSettingsConfig>(raw)
            } catch (e: Exception) {
                AppSettingsConfig()
            }
        }
    }

    val continueWatchingFlow: Flow<List<com.mystream.app.data.model.PlaybackProgressRecord>> = context.dataStore.data.map { prefs ->
        val raw = prefs[PLAYBACK_PROGRESS_KEY]
        if (raw.isNullOrBlank()) emptyList()
        else {
            try {
                json.decodeFromString<List<com.mystream.app.data.model.PlaybackProgressRecord>>(raw)
                    .filter { it.positionMs > 10_000L && (it.durationMs == 0L || it.positionMs < (it.durationMs * 0.95)) }
                    .sortedByDescending { it.lastUpdatedMs }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    val watchlistFlow: Flow<List<com.mystream.app.data.model.WatchlistItem>> = context.dataStore.data.map { prefs ->
        val raw = prefs[WATCHLIST_KEY]
        if (raw.isNullOrBlank()) emptyList()
        else {
            try {
                json.decodeFromString<List<com.mystream.app.data.model.WatchlistItem>>(raw)
                    .sortedByDescending { it.dateAddedMs }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    suspend fun addToWatchlist(item: com.mystream.app.data.model.WatchlistItem) {
        context.dataStore.edit { prefs ->
            val current = try {
                val raw = prefs[WATCHLIST_KEY]
                if (raw.isNullOrBlank()) emptyList()
                else json.decodeFromString<List<com.mystream.app.data.model.WatchlistItem>>(raw)
            } catch (e: Exception) {
                emptyList()
            }
            val updated = (listOf(item.copy(dateAddedMs = System.currentTimeMillis())) + current.filter { it.id != item.id }).take(100)
            prefs[WATCHLIST_KEY] = json.encodeToString(updated)
        }
    }

    suspend fun removeFromWatchlist(id: String) {
        context.dataStore.edit { prefs ->
            val current = try {
                val raw = prefs[WATCHLIST_KEY]
                if (raw.isNullOrBlank()) emptyList()
                else json.decodeFromString<List<com.mystream.app.data.model.WatchlistItem>>(raw)
            } catch (e: Exception) {
                emptyList()
            }
            val updated = current.filter { it.id != id }
            prefs[WATCHLIST_KEY] = json.encodeToString(updated)
        }
    }

    suspend fun clearAllWatchlist() {
        context.dataStore.edit { prefs ->
            prefs.remove(WATCHLIST_KEY)
        }
    }

    suspend fun savePlaybackProgress(
        mediaId: String,
        imdbId: String,
        title: String,
        subtitle: String?,
        posterUrl: String?,
        backdropUrl: String?,
        type: String,
        seasonNumber: Int = 0,
        episodeNumber: Int = 0,
        positionMs: Long,
        durationMs: Long
    ) {
        if (positionMs < 5_000L && durationMs <= 0L) return
        context.dataStore.edit { prefs ->
            val current = try {
                val raw = prefs[PLAYBACK_PROGRESS_KEY]
                if (raw.isNullOrBlank()) emptyList()
                else json.decodeFromString<List<com.mystream.app.data.model.PlaybackProgressRecord>>(raw)
            } catch (e: Exception) {
                emptyList()
            }

            val record = com.mystream.app.data.model.PlaybackProgressRecord(
                mediaId = mediaId,
                imdbId = imdbId,
                title = title,
                subtitle = subtitle,
                posterUrl = posterUrl,
                backdropUrl = backdropUrl,
                type = type,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                positionMs = positionMs,
                durationMs = durationMs,
                lastUpdatedMs = System.currentTimeMillis()
            )
            val updated = (listOf(record) + current.filter { it.mediaId != mediaId }).take(50)
            prefs[PLAYBACK_PROGRESS_KEY] = json.encodeToString(updated)
        }
    }

    suspend fun getSavedPosition(mediaId: String): Long {
        return try {
            val prefs = context.dataStore.data.first()
            val raw = prefs[PLAYBACK_PROGRESS_KEY] ?: return 0L
            val list = json.decodeFromString<List<com.mystream.app.data.model.PlaybackProgressRecord>>(raw)
            val record = list.firstOrNull { it.mediaId == mediaId }
            if (record != null && (record.durationMs == 0L || record.positionMs < (record.durationMs * 0.95))) {
                record.positionMs
            } else {
                0L
            }
        } catch (e: Exception) {
            0L
        }
    }

    suspend fun removePlaybackProgress(mediaId: String) {
        context.dataStore.edit { prefs ->
            val current = try {
                val raw = prefs[PLAYBACK_PROGRESS_KEY]
                if (raw.isNullOrBlank()) emptyList()
                else json.decodeFromString<List<com.mystream.app.data.model.PlaybackProgressRecord>>(raw)
            } catch (e: Exception) {
                emptyList()
            }
            val updated = current.filter { it.mediaId != mediaId }
            prefs[PLAYBACK_PROGRESS_KEY] = json.encodeToString(updated)
        }
    }

    suspend fun clearAllPlaybackProgress() {
        context.dataStore.edit { prefs ->
            prefs.remove(PLAYBACK_PROGRESS_KEY)
        }
    }

    fun getJsonConfig(): AppJsonConfig {
        return pikpakResolver.loadConfig()
    }

    fun saveJsonConfig(config: AppJsonConfig) {
        pikpakResolver.saveConfig(config)
    }

    fun importConfigFromDownloads(): Result<AppJsonConfig> {
        val downloadCandidates = listOf(
            java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "mystream_config.json"),
            java.io.File("/sdcard/Download/mystream_config.json"),
            java.io.File("/storage/emulated/0/Download/mystream_config.json")
        )
        val target = downloadCandidates.firstOrNull { it.exists() && it.canRead() }
            ?: return Result.failure(java.io.FileNotFoundException("Could not find mystream_config.json in Downloads folder"))

        return try {
            val text = target.readText()
            val parsed = json.decodeFromString<AppJsonConfig>(text)
            saveJsonConfig(parsed)
            Result.success(parsed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun testPikPakConnection(account: PikPakAccount): Result<String> {
        return pikpakResolver.testPikPakLogin(account)
    }

    suspend fun testPostgresConnection(postgresUrl: String): Result<String> {
        return pikpakResolver.testPostgresConnection(postgresUrl)
    }

    suspend fun updateAppSettings(config: AppSettingsConfig) {
        context.dataStore.edit { prefs ->
            prefs[APP_SETTINGS_KEY] = json.encodeToString(config)
        }
    }

    suspend fun addCatalogSource(name: String, manifestUrl: String): Result<MediaSourceEntity> {
        return try {
            val manifest = apiClient.getManifest(manifestUrl)
            val newSource = MediaSourceEntity(
                id = manifest.id.ifBlank { UUID.randomUUID().toString() },
                name = manifest.name.ifBlank { name },
                baseUrl = manifestUrl,
                isEnabled = true,
                type = "catalog"
            )
            context.dataStore.edit { prefs ->
                val current = try {
                    val raw = prefs[CATALOG_SOURCES_KEY]
                    if (raw.isNullOrBlank()) DEFAULT_CATALOG_SOURCES
                    else json.decodeFromString<List<MediaSourceEntity>>(raw)
                } catch (e: Exception) {
                    DEFAULT_CATALOG_SOURCES
                }
                prefs[CATALOG_SOURCES_KEY] = json.encodeToString(current.filter { it.baseUrl != manifestUrl } + newSource)
            }
            Result.success(newSource)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private var cachedIndianCatalog: Map<String, List<ImdbIndianItem>>? = null
    private val indianCatalogDiskCacheFile by lazy { java.io.File(context.cacheDir, "imdb_indian_data.json") }
    private val INDIAN_CATALOG_URL = "https://raw.githubusercontent.com/piyushpradhan22/imdb-indian/refs/heads/master/data.json"

    suspend fun loadIndianCatalogData(forceRefresh: Boolean = false): Map<String, List<ImdbIndianItem>> = withContext(Dispatchers.IO) {
        cachedIndianCatalog?.let { if (!forceRefresh) return@withContext it }

        // Try reading disk cache if recent (< 24 hours) and not forceRefresh
        if (!forceRefresh && indianCatalogDiskCacheFile.exists() && System.currentTimeMillis() - indianCatalogDiskCacheFile.lastModified() < 24 * 3600 * 1000L) {
            try {
                val text = indianCatalogDiskCacheFile.readText()
                if (text.isNotBlank()) {
                    val parsed = json.decodeFromString<Map<String, List<ImdbIndianItem>>>(text)
                    cachedIndianCatalog = parsed
                    return@withContext parsed
                }
            } catch (e: Exception) {
                android.util.Log.w("SourcesRepository", "Failed to parse local Indian catalog cache", e)
            }
        }

        // Fetch from GitHub raw
        try {
            val req = Request.Builder()
                .url(INDIAN_CATALOG_URL)
                .header("Cache-Control", "no-cache")
                .build()
            val client = OkHttpClient.Builder()
                .dns(com.mystream.app.data.api.SystemFallbackDns)
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val response = client.newCall(req).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                if (body.isNotBlank()) {
                    try {
                        indianCatalogDiskCacheFile.writeText(body)
                    } catch (_: Exception) {}
                    val parsed = json.decodeFromString<Map<String, List<ImdbIndianItem>>>(body)
                    cachedIndianCatalog = parsed
                    return@withContext parsed
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SourcesRepository", "Failed to fetch Indian catalog from network", e)
        }

        // Fallback to disk cache if network failed
        if (indianCatalogDiskCacheFile.exists()) {
            try {
                val text = indianCatalogDiskCacheFile.readText()
                val parsed = json.decodeFromString<Map<String, List<ImdbIndianItem>>>(text)
                cachedIndianCatalog = parsed
                return@withContext parsed
            } catch (_: Exception) {}
        }

        emptyMap()
    }

    suspend fun fetchIndianCatalog(
        category: String = "Top Rated",
        skip: Int = 0,
        limit: Int = 20
    ): StremioCatalogResponse {
        val data = loadIndianCatalogData()
        val key = data.keys.firstOrNull { it.equals(category, ignoreCase = true) } ?: "Top Rated"
        val items = data[key] ?: emptyList()
        val paged = items.drop(skip).take(limit).map { it.toStremioMetaPreview() }
        return StremioCatalogResponse(metas = paged)
    }

    suspend fun fetchCatalog(
        type: String,
        catalogId: String = "top",
        genre: String? = null,
        search: String? = null,
        skip: Int = 0,
        sourceUrl: String = DEFAULT_CATALOG_SOURCES.first().baseUrl
    ): StremioCatalogResponse {
        if (catalogId == "imdb-indian" || type == "indian") {
            val category = genre?.takeIf { it.isNotBlank() } ?: "Top Rated"
            return fetchIndianCatalog(category = category, skip = skip, limit = 20)
        }
        return apiClient.getCatalog(
            baseUrl = sourceUrl,
            type = type,
            id = catalogId,
            genre = genre,
            searchQuery = search,
            skip = skip
        )
    }

    suspend fun fetchMetaDetail(
        type: String,
        id: String,
        sourceUrl: String = DEFAULT_CATALOG_SOURCES.first().baseUrl
    ): StremioMetaDetail {
        val response = apiClient.getMetaDetail(sourceUrl, type, id)
        return response.meta
    }

    // Bounded in-memory streams cache (max 50 titles)
    private val memoryStreamsCache = object : java.util.LinkedHashMap<String, List<StremioStreamSource>>(50, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<StremioStreamSource>>?): Boolean {
            return size > 50
        }
    }

    private val streamDiskCacheFile = java.io.File(context.filesDir, "stream_links_cache.json")

    private fun loadDiskStreamCache(): MutableMap<String, com.mystream.app.data.model.CachedStreamLinksRecord> {
        if (!streamDiskCacheFile.exists()) return mutableMapOf()
        return try {
            val text = streamDiskCacheFile.readText()
            if (text.isBlank()) mutableMapOf()
            else json.decodeFromString<Map<String, com.mystream.app.data.model.CachedStreamLinksRecord>>(text).toMutableMap()
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    private fun saveDiskStreamCache(map: Map<String, com.mystream.app.data.model.CachedStreamLinksRecord>) {
        try {
            val bounded = map.entries.sortedByDescending { it.value.timestampMs }.take(100).associate { it.key to it.value }
            val text = json.encodeToString(bounded)
            streamDiskCacheFile.writeText(text)
        } catch (e: Exception) {
            // ignore
        }
    }

    fun clearStreamsCache(type: String, imdbId: String) {
        val cacheKey = "$type:$imdbId"
        synchronized(memoryStreamsCache) {
            memoryStreamsCache.remove(cacheKey)
        }
        try {
            val current = loadDiskStreamCache()
            if (current.remove(cacheKey) != null) {
                saveDiskStreamCache(current)
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    fun getPersistedStreams(cacheKey: String, ttlHours: Int): List<StremioStreamSource>? {
        // 1. Check in-memory
        val mem = synchronized(memoryStreamsCache) { memoryStreamsCache[cacheKey] }
        if (!mem.isNullOrEmpty()) return mem

        // 2. Check disk cache
        val diskMap = loadDiskStreamCache()
        val record = diskMap[cacheKey] ?: return null
        if (record.isExpired(ttlHours) || record.streams.isEmpty()) {
            return null
        }
        // Restore into in-memory cache
        synchronized(memoryStreamsCache) {
            memoryStreamsCache[cacheKey] = record.streams
        }
        return record.streams
    }

    fun persistStreams(cacheKey: String, streams: List<StremioStreamSource>, ttlHours: Int = 6) {
        if (streams.isEmpty()) return
        synchronized(memoryStreamsCache) {
            memoryStreamsCache[cacheKey] = streams
        }
        try {
            val current = loadDiskStreamCache()
            current[cacheKey] = com.mystream.app.data.model.CachedStreamLinksRecord(
                key = cacheKey,
                timestampMs = System.currentTimeMillis(),
                streams = streams,
                ttlHours = ttlHours
            )
            saveDiskStreamCache(current)
        } catch (e: Exception) {
            // ignore
        }
    }

    // Streams flow emitting results as each stream resolves and preserving local persistent cache
    fun streamStreamsForMedia(
        type: String,
        imdbId: String,
        forceRefresh: Boolean = false
    ): kotlinx.coroutines.flow.Flow<List<StremioStreamSource>> = kotlinx.coroutines.flow.flow {
        val cacheKey = "$type:$imdbId"
        val settings = appSettingsFlow.first()
        val configuredTtl = settings.linkCacheTtlHours

        if (!forceRefresh) {
            val cached = getPersistedStreams(cacheKey, configuredTtl)
            if (!cached.isNullOrEmpty()) {
                emit(cached)
                return@flow
            }
        } else {
            clearStreamsCache(type, imdbId)
        }

        pikpakResolver.streamPikPakStreams(type, imdbId).collect { list ->
            if (list.isNotEmpty()) {
                persistStreams(cacheKey, list, configuredTtl)
                emit(list)
            }
        }
    }

    // Resolves streams using PikPak API & Torrentio directly with persistent cache
    suspend fun fetchStreamsForMedia(
        type: String,
        imdbId: String,
        forceRefresh: Boolean = false
    ): List<StremioStreamSource> {
        val cacheKey = "$type:$imdbId"
        val settings = appSettingsFlow.first()
        val configuredTtl = settings.linkCacheTtlHours

        if (!forceRefresh) {
            val cached = getPersistedStreams(cacheKey, configuredTtl)
            if (cached != null) return cached
        } else {
            clearStreamsCache(type, imdbId)
        }

        val streams = pikpakResolver.resolvePikPakStreams(type, imdbId)
        if (streams.isNotEmpty()) {
            persistStreams(cacheKey, streams, configuredTtl)
        }
        return streams
    }

    suspend fun resolveSpecificStream(
        stream: StremioStreamSource,
        imdbId: String
    ): Result<String> {
        return pikpakResolver.resolveDirectStreamUrlOnDemand(stream, imdbId)
    }

    suspend fun fetchAllTorrentsForMedia(type: String, id: String): List<StremioStreamSource> {
        return pikpakResolver.fetchAllTorrentioTorrents(type, id)
    }

    suspend fun resolveAndSaveSingleTorrent(
        stream: StremioStreamSource,
        type: String,
        queryId: String
    ): Result<String> {
        val res = pikpakResolver.resolveDirectStreamUrlOnDemand(stream, queryId)
        val url = res.getOrNull()
        if (!url.isNullOrBlank()) {
            val resolvedStream = stream.copy(
                url = url,
                providerName = "PP"
            )
            // Add to persistent disk cache & memory cache
            val cacheKey = "$type:$queryId"
            val settings = appSettingsFlow.first()
            val existingStreams = getPersistedStreams(cacheKey, settings.linkCacheTtlHours)?.toMutableList() ?: mutableListOf()
            val updated = (listOf(resolvedStream) + existingStreams.filter { it.infoHash != stream.infoHash }).take(10)
            persistStreams(cacheKey, updated, settings.linkCacheTtlHours)
        }
        return res
    }
}
