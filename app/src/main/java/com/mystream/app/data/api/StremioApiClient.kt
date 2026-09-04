package com.mystream.app.data.api

import com.mystream.app.data.model.StremioCatalogResponse
import com.mystream.app.data.model.StremioManifest
import com.mystream.app.data.model.StremioMetaDetailResponse
import com.mystream.app.data.model.StremioStreamResponse
import com.mystream.app.data.model.StremioStreamSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.InetAddress
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import okhttp3.Dns

object SystemFallbackDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        return try {
            Dns.SYSTEM.lookup(hostname)
        } catch (e: Exception) {
            try {
                val req = Request.Builder()
                    .url("https://1.1.1.1/dns-query?name=$hostname&type=A")
                    .header("Accept", "application/dns-json")
                    .build()
                val rawClient = OkHttpClient.Builder()
                    .connectTimeout(3, TimeUnit.SECONDS)
                    .readTimeout(3, TimeUnit.SECONDS)
                    .build()
                rawClient.newCall(req).execute().use { res ->
                    if (res.isSuccessful) {
                        val body = res.body?.string() ?: ""
                        val regex = """"data"\s*:\s*"([0-9]+\.[0-9]+\.[0-9]+\.[0-9]+)"""".toRegex()
                        val ips = regex.findAll(body).map { it.groupValues[1] }.toList()
                        if (ips.isNotEmpty()) {
                            return ips.map { InetAddress.getByAddress(hostname, InetAddress.getByName(it).address) }
                        }
                    }
                }
            } catch (_: Exception) {}
            throw e
        }
    }
}

class StremioApiClient(
    cacheDir: java.io.File? = null
) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .dns(SystemFallbackDns)
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .apply {
            if (cacheDir != null) {
                try {
                    val httpCache = okhttp3.Cache(java.io.File(cacheDir, "stremio_http_cache"), 60L * 1024 * 1024)
                    cache(httpCache)
                } catch (_: Exception) {}
            }
        }
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    private val inMemoryResponseCache = object : java.util.LinkedHashMap<String, String>(100, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > 100
    }

    private suspend fun getRaw(url: String, customHeaders: Map<String, String>? = null): String = withContext(Dispatchers.IO) {
        synchronized(inMemoryResponseCache) {
            inMemoryResponseCache[url]?.let { return@withContext it }
        }

        android.util.Log.d("StremioApiClient", "Fetching URL: $url")
        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", "MyStream/1.0 (Android; ExoPlayer)")
            .header("Accept", "application/json")

        customHeaders?.forEach { (k, v) ->
            requestBuilder.header(k, v)
        }

        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    android.util.Log.e("StremioApiClient", "HTTP ${response.code} for $url")
                    throw IOException("HTTP error code: ${response.code} for URL: $url")
                }
                val body = response.body?.string() ?: throw IOException("Empty response body for URL: $url")
                android.util.Log.d("StremioApiClient", "Successfully fetched ${body.length} chars from $url")
                synchronized(inMemoryResponseCache) {
                    inMemoryResponseCache[url] = body
                }
                body
            }
        } catch (e: Exception) {
            android.util.Log.e("StremioApiClient", "Network exception fetching $url", e)
            throw e
        }
    }

    suspend fun getManifest(baseUrl: String, customHeaders: Map<String, String>? = null): StremioManifest = withContext(Dispatchers.IO) {
        val url = if (baseUrl.endsWith("/manifest.json")) baseUrl else "${baseUrl.removeSuffix("/")}/manifest.json"
        val body = getRaw(url, customHeaders)
        json.decodeFromString<StremioManifest>(body)
    }

    suspend fun getCatalog(
        baseUrl: String,
        type: String,
        id: String = "top",
        genre: String? = null,
        searchQuery: String? = null,
        skip: Int = 0,
        customHeaders: Map<String, String>? = null
    ): StremioCatalogResponse = withContext(Dispatchers.IO) {
        val root = baseUrl.removeSuffix("/manifest.json").removeSuffix("/")

        val extraParts = mutableListOf<String>()
        if (!genre.isNullOrBlank()) {
            extraParts.add("genre=${URLEncoder.encode(genre, "UTF-8")}")
        }
        if (!searchQuery.isNullOrBlank()) {
            extraParts.add("search=${URLEncoder.encode(searchQuery, "UTF-8")}")
        }
        if (skip > 0) {
            extraParts.add("skip=$skip")
        }

        val extraPath = if (extraParts.isNotEmpty()) "/${extraParts.joinToString("&")}" else ""
        val url = "$root/catalog/$type/$id$extraPath.json"

        try {
            val body = getRaw(url, customHeaders)
            json.decodeFromString<StremioCatalogResponse>(body)
        } catch (e: Exception) {
            StremioCatalogResponse(emptyList())
        }
    }

    suspend fun getMetaDetail(
        baseUrl: String,
        type: String,
        id: String,
        customHeaders: Map<String, String>? = null
    ): StremioMetaDetailResponse = withContext(Dispatchers.IO) {
        val root = baseUrl.removeSuffix("/manifest.json").removeSuffix("/")
        val url = "$root/meta/$type/$id.json"
        val body = getRaw(url, customHeaders)
        json.decodeFromString<StremioMetaDetailResponse>(body)
    }

    suspend fun getStreams(
        baseUrl: String,
        type: String,
        id: String,
        customHeaders: Map<String, String>? = null
    ): List<StremioStreamSource> = withContext(Dispatchers.IO) {
        val root = baseUrl.removeSuffix("/manifest.json").removeSuffix("/")
        val url = "$root/stream/$type/$id.json"
        try {
            val body = getRaw(url, customHeaders)
            val res = json.decodeFromString<StremioStreamResponse>(body)
            res.streams
        } catch (e: Exception) {
            emptyList()
        }
    }
}
