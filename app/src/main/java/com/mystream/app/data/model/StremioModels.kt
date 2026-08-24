package com.mystream.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// --- Stremio Manifest Models ---
@Serializable
data class StremioManifest(
    val id: String = "",
    val name: String = "",
    val description: String? = null,
    val version: String? = null,
    val types: List<String> = emptyList(),
    val resources: List<String> = emptyList(),
    val catalogs: List<StremioCatalogDesc> = emptyList(),
    val idPrefixes: List<String> = emptyList(),
    val logo: String? = null,
    val background: String? = null
)

@Serializable
data class StremioCatalogDesc(
    val type: String,
    val id: String,
    val name: String,
    val genres: List<String> = emptyList(),
    val extraSupported: List<String> = emptyList(),
    val extraRequired: List<String> = emptyList()
)

// --- Catalog & Search Responses ---
@Serializable
data class StremioCatalogResponse(
    val metas: List<StremioMetaPreview> = emptyList()
)

@Serializable
data class StremioMetaPreview(
    val id: String,
    val name: String,
    val type: String, // "movie" or "series"
    val poster: String? = null,
    val background: String? = null,
    val logo: String? = null,
    val description: String? = null,
    val year: String? = null,
    val releaseInfo: String? = null,
    val imdbRating: String? = null,
    val genres: List<String> = emptyList(),
    @SerialName("imdb_id") val imdbId: String? = null
)

// --- Meta Detail Response ---
@Serializable
data class StremioMetaDetailResponse(
    val meta: StremioMetaDetail
)

@Serializable
data class StremioMetaDetail(
    val id: String,
    val name: String,
    val type: String,
    val poster: String? = null,
    val background: String? = null,
    val logo: String? = null,
    val description: String? = null,
    val year: String? = null,
    val releaseInfo: String? = null,
    val imdbRating: String? = null,
    val genres: List<String> = emptyList(),
    val runtime: String? = null,
    val director: List<String>? = null,
    val writer: List<String>? = null,
    val cast: List<String>? = null,
    val country: String? = null,
    val awards: String? = null,
    val videos: List<StremioVideoEpisode> = emptyList(),
    val trailerStreams: List<StremioTrailerStream> = emptyList()
)

@Serializable
data class StremioVideoEpisode(
    val id: String,
    val name: String? = null,
    val season: Int = 1,
    val number: Int = 1,
    val episode: Int = 1,
    val overview: String? = null,
    val description: String? = null,
    val thumbnail: String? = null,
    val rating: String? = null,
    val released: String? = null
)

@Serializable
data class StremioTrailerStream(
    val title: String? = null,
    val ytId: String? = null
)

// --- Stream Provider Models & Parsed Stream Metadata ---
@Serializable
data class StremioStreamResponse(
    val streams: List<StremioStreamSource> = emptyList()
)

@Serializable
data class StremioStreamSource(
    val name: String? = null,
    val title: String? = null,
    val url: String? = null,
    val ytId: String? = null,
    val infoHash: String? = null,
    val fileIdx: Int? = null,
    val behaviorHints: StremioStreamBehaviorHints? = null,
    val providerName: String? = null
) {
    val quality: String
        get() {
            val text = (name.orEmpty() + " " + title.orEmpty()).uppercase()
            return when {
                text.contains("4K") || text.contains("2160P") || text.contains("UHD") -> "4K UHD"
                text.contains("1080P") || text.contains("FHD") -> "1080p FHD"
                text.contains("720P") || text.contains("HD") -> "720p HD"
                text.contains("480P") || text.contains("SD") -> "480p SD"
                text.contains("CAM") || text.contains("TS") || text.contains("TELESYNC") -> "CAM / TS"
                else -> "HD"
            }
        }

    val hdrType: String?
        get() {
            val text = (name.orEmpty() + " " + title.orEmpty()).uppercase()
            return when {
                text.contains("DV") || text.contains("DOLBY VISION") -> "Dolby Vision"
                text.contains("HDR10+") -> "HDR10+"
                text.contains("HDR") -> "HDR10"
                else -> null
            }
        }

    val audioDetails: String?
        get() {
            val text = (name.orEmpty() + " " + title.orEmpty()).uppercase()
            val audioChannels = when {
                text.contains("7.1") -> "7.1 Atmos"
                text.contains("5.1") || text.contains("DDP5.1") || text.contains("AAC5.1") -> "5.1 Surround"
                text.contains("ATMOS") -> "Dolby Atmos"
                else -> null
            }
            val langs = mutableListOf<String>()
            if (text.contains("MULTI") || text.contains("DUAL")) langs.add("Multi Audio")
            if (text.contains("ENG") || text.contains("ENGLISH")) langs.add("English")
            if (text.contains("HIN") || text.contains("HINDI")) langs.add("Hindi")
            if (text.contains("SPA") || text.contains("SPANISH") || text.contains("LATINO")) langs.add("Spanish")
            if (text.contains("FRE") || text.contains("FRENCH")) langs.add("French")
            if (text.contains("GER") || text.contains("GERMAN")) langs.add("German")
            if (text.contains("ITA") || text.contains("ITALIAN")) langs.add("Italian")
            if (text.contains("RUS") || text.contains("RUSSIAN")) langs.add("Russian")
            if (text.contains("TEL") || text.contains("TELUGU")) langs.add("Telugu")
            if (text.contains("TAM") || text.contains("TAMIL")) langs.add("Tamil")

            val langStr = if (langs.isNotEmpty()) langs.joinToString(", ") else null
            return listOfNotNull(audioChannels, langStr).joinToString(" • ").takeIf { it.isNotBlank() }
        }

    val hasHindiAudio: Boolean
        get() {
            val text = (name.orEmpty() + " " + title.orEmpty()).uppercase()
            return text.contains("🇮🇳") ||
                   text.contains("HINDI") ||
                   text.contains("HIN-") ||
                   text.contains("-HIN") ||
                   text.contains("[HIN") ||
                   text.contains("(HIN") ||
                   text.contains(" HIN ") ||
                   text.contains("[HI]") ||
                   text.contains("(HI)") ||
                   text.contains("BOLLYWOOD") ||
                   text.contains("DESI")
        }

    val fileSize: String?
        get() {
            val text = (name.orEmpty() + " " + title.orEmpty())
            val regex = Regex("""([0-9]+(?:\.[0-9]+)?\s*(?:GB|MB|GiB|MiB))""", RegexOption.IGNORE_CASE)
            return regex.find(text)?.value?.uppercase()
        }

    val fileSizeMb: Double
        get() {
            val fullText = "${name.orEmpty()} ${title.orEmpty()}".replace("\n", " ")
            if (fullText.contains("💾")) {
                val afterSave = fullText.substringAfter("💾").trim()
                val sizePart = afterSave.substringBefore("⚙️").substringBefore("👤").trim()
                val regex = Regex("""([0-9]+(?:\.[0-9]+)?)\s*(GB|MB|GiB|MiB)""", RegexOption.IGNORE_CASE)
                val match = regex.find(sizePart)
                if (match != null) {
                    val num = match.groupValues[1].toDoubleOrNull() ?: 2000.0
                    val unit = match.groupValues[2].uppercase()
                    return if (unit.contains("GB") || unit.contains("GIB")) num * 1024.0 else num
                }
            }
            val regex = Regex("""([0-9]+(?:\.[0-9]+)?)\s*(GB|MB|GiB|MiB)""", RegexOption.IGNORE_CASE)
            val match = regex.find(fullText) ?: return 2000.0
            val num = match.groupValues[1].toDoubleOrNull() ?: 2000.0
            val unit = match.groupValues[2].uppercase()
            return if (unit.contains("GB") || unit.contains("GIB")) num * 1024.0 else num
        }

    val seeders: String?
        get() {
            val text = (name.orEmpty() + " " + title.orEmpty())
            val regex = Regex("""(?:👤|👥|seeds?:\s*|s:\s*)([0-9]+)""", RegexOption.IGNORE_CASE)
            return regex.find(text)?.groupValues?.getOrNull(1)?.let { "Seeds: $it" }
        }
}

@Serializable
data class StremioStreamBehaviorHints(
    val notWebReady: Boolean = false,
    val bingeGroup: String? = null,
    val proxyHeaders: Map<String, String>? = null,
    val customHeaders: Map<String, String>? = null
)

// --- App Source / Provider Entities ---
@Serializable
data class MediaSourceEntity(
    val id: String,
    val name: String,
    val baseUrl: String,
    val isDefault: Boolean = false,
    val isEnabled: Boolean = true,
    val type: String = "catalog", // "catalog" or "stream" or "both"
    val apiKey: String? = null,
    val customHeaders: Map<String, String>? = null
)

// --- App JSON Configuration (mystream_config.json) ---
@Serializable
data class AppJsonConfig(
    @SerialName("postgres_url") val postgresUrl: String? = null,
    @SerialName("pikpak_password") val pikpakPassword: String = "",
    @SerialName("torrentio_url") val torrentioUrl: String = "https://torrentio.strem.fun",
    @SerialName("pikpak_accounts") val pikpakAccounts: List<PikPakAccount> = emptyList(),
    // Fallback single account support if user formats as {"pikpak": {"username": "...", "password": "..."}}
    val pikpak: PikPakAccount? = null
) {
    val primaryAccount: PikPakAccount?
        get() = pikpakAccounts.firstOrNull { it.username.isNotBlank() && it.password.isNotBlank() }
            ?: pikpak?.takeIf { it.username.isNotBlank() && it.password.isNotBlank() }
            ?: if (pikpakPassword.isNotBlank()) PikPakAccount(password = pikpakPassword) else null
}

@Serializable
data class PikPakAccount(
    val username: String = "",
    val password: String = ""
)

// --- App Settings Configuration ---
@Serializable
data class AppSettingsConfig(
    val preferredQuality: String = "Auto", // "Auto", "4K", "1080p", "720p"
    val preferredAudioLanguage: String = "Hindi", // "Hindi", "English", "Original"
    val secondaryAudioLanguage: String = "English",
    val subtitlesEnabled: Boolean = false, // default: OFF
    val preferredSubtitleLanguage: String = "English", // default: English
    val linkCacheTtlHours: Int = 6, // default: 6 hours (0 = never expire / permanent)
    val autoPlayBestStream: Boolean = false,
    val debridApiKey: String? = null,
    val customServerUrl: String? = null
)

@Serializable
data class CachedStreamLinksRecord(
    val key: String, // e.g. "movie:tt1234567" or "series:tt1234567:1:2"
    val timestampMs: Long,
    val streams: List<StremioStreamSource>,
    val ttlHours: Int = 6
) {
    fun isExpired(configuredTtlHours: Int): Boolean {
        val effectiveTtl = if (ttlHours == 0) 0 else configuredTtlHours
        if (effectiveTtl <= 0) return false // 0 means non-expiring
        val ageMs = System.currentTimeMillis() - timestampMs
        return ageMs > (effectiveTtl * 3600 * 1000L)
    }
}

// --- Player Video Scale / Aspect Ratio ---
enum class VideoAspectRatio(val label: String) {
    FIT("Fit (16:9 / Letterbox)"),
    ZOOM("Zoom to Fill (Crop)"),
    STRETCH("Stretch to Screen"),
    ORIGINAL("100% Original")
}

// --- Audio & Subtitle Track Representations ---
data class PlayerTrackInfo(
    val id: String,
    val index: Int,
    val groupIndex: Int,
    val label: String,
    val language: String?,
    val isSelected: Boolean,
    val mimeType: String? = null,
    val channels: Int = 0,
    val bitrate: Int = 0,
    val isSupported: Boolean = true
)

// --- Playback State & Media Item ---
data class MediaPlaybackItem(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val mediaUrl: String,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val isSeries: Boolean = false,
    val seasonNumber: Int = 0,
    val episodeNumber: Int = 0,
    val startPositionMs: Long = 0L,
    val nextEpisodeId: String? = null,
    val nextEpisodeTitle: String? = null,
    val headers: Map<String, String>? = null
)

@Serializable
data class PlaybackProgressRecord(
    val mediaId: String, // e.g. "tt1234567" or "tt1234567:1:2"
    val imdbId: String,  // e.g. "tt1234567"
    val title: String,
    val subtitle: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val type: String = "movie", // "movie" or "series"
    val seasonNumber: Int = 0,
    val episodeNumber: Int = 0,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val lastUpdatedMs: Long = 0L
) {
    val progressFraction: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
}

@Serializable
data class WatchlistItem(
    val id: String, // infoHash ?: imdbId
    val imdbId: String,
    val title: String,
    val subtitle: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val type: String = "movie",
    val seasonNumber: Int = 0,
    val episodeNumber: Int = 0,
    val infoHash: String? = null,
    val torrentTitle: String? = null,
    val torrentQuality: String? = null,
    val dateAddedMs: Long = 0L
)

