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
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class StremioApiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    private suspend fun getRaw(url: String, customHeaders: Map<String, String>? = null): String = withContext(Dispatchers.IO) {
        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", "MyStream/1.0 (Android; ExoPlayer)")

        customHeaders?.forEach { (k, v) ->
            requestBuilder.header(k, v)
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP error code: ${response.code} for URL: $url")
            }
            response.body?.string() ?: throw IOException("Empty response body for URL: $url")
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
