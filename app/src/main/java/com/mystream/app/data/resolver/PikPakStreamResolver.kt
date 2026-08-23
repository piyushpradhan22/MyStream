package com.mystream.app.data.resolver

import android.content.Context
import android.util.Log
import com.mystream.app.data.api.PikPakApiClient
import com.mystream.app.data.api.StremioApiClient
import com.mystream.app.data.db.PikPakTorrentRecord
import com.mystream.app.data.db.PikPakV2Record
import com.mystream.app.data.db.PostgresAccountFetcher
import com.mystream.app.data.model.AppJsonConfig
import com.mystream.app.data.model.PikPakAccount
import com.mystream.app.data.model.StremioStreamSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class PikPakStreamResolver(
    private val context: Context,
    private val pikpakClient: PikPakApiClient = PikPakApiClient(),
    private val stremioClient: StremioApiClient = StremioApiClient()
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true; prettyPrint = true }
    private val encConfigFile: File get() = File(context.filesDir, "mystream_config.enc")
    private val legacyConfigFile: File get() = File(context.filesDir, "mystream_config.json")

    private var cachedPostgresUsernames: List<String> = emptyList()

    companion object {
        private const val TAG = "MyStream_Resolver"
    }

    private object EncryptedConfigStore {
        private const val KEY_ALIAS = "MyStream_Config_Master_Key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
        private const val TAG_SIZE = 128

        @Synchronized
        private fun getSecretKey(): SecretKey {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                val spec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGenerator.init(spec)
                return keyGenerator.generateKey()
            }
            return (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
        }

        fun encrypt(plainText: String): ByteArray {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val key = getSecretKey()
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            return iv + cipherText
        }

        fun decrypt(encryptedBytes: ByteArray): String {
            if (encryptedBytes.size < IV_SIZE) return ""
            val iv = encryptedBytes.copyOfRange(0, IV_SIZE)
            val cipherText = encryptedBytes.copyOfRange(IV_SIZE, encryptedBytes.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val key = getSecretKey()
            val spec = GCMParameterSpec(TAG_SIZE, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)
            val plainTextBytes = cipher.doFinal(cipherText)
            return String(plainTextBytes, Charsets.UTF_8)
        }
    }

    fun loadConfig(): AppJsonConfig {
        return try {
            // 1. Try reading encrypted config
            if (encConfigFile.exists()) {
                val cipherBytes = encConfigFile.readBytes()
                val decryptedJson = EncryptedConfigStore.decrypt(cipherBytes)
                if (decryptedJson.isNotBlank()) {
                    return json.decodeFromString<AppJsonConfig>(decryptedJson)
                }
            }

            // 2. Try migrating legacy plaintext config
            if (legacyConfigFile.exists()) {
                val text = legacyConfigFile.readText()
                val parsed = json.decodeFromString<AppJsonConfig>(text)
                saveConfig(parsed)
                try { legacyConfigFile.delete() } catch (_: Exception) {}
                return parsed
            }

            // 3. Fallback to bundled asset
            val assetConfig = try {
                val assetJson = context.assets.open("mystream_config.json").bufferedReader().use { it.readText() }
                json.decodeFromString<AppJsonConfig>(assetJson)
            } catch (e: Exception) {
                AppJsonConfig()
            }

            saveConfig(assetConfig)
            assetConfig
        } catch (e: Exception) {
            AppJsonConfig()
        }
    }

    fun saveConfig(config: AppJsonConfig) {
        try {
            val text = json.encodeToString(config)
            val cipherBytes = EncryptedConfigStore.encrypt(text)
            encConfigFile.writeBytes(cipherBytes)
            try { if (legacyConfigFile.exists()) legacyConfigFile.delete() } catch (_: Exception) {}
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save encrypted config", e)
        }
    }

    suspend fun testPostgresConnection(postgresUrl: String): Result<String> {
        val result = PostgresAccountFetcher.fetchUsernames(postgresUrl)
        return result.map { users ->
            cachedPostgresUsernames = users
            "Found ${users.size} dynamic PikPak accounts in PostgreSQL database!"
        }
    }

    suspend fun testPikPakLogin(account: PikPakAccount): Result<String> {
        val result = pikpakClient.login(
            username = account.username,
            pass = account.password
        )
        return result.map { "Logged in successfully! User ID: ${it.userId}" }
    }

    private fun parseFileSizeMb(title: String): Double {
        val clean = title.replace("\n", " ")
        if (clean.contains("💾")) {
            val afterSave = clean.substringAfter("💾").trim()
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
        val match = regex.find(clean) ?: return 2000.0
        val num = match.groupValues[1].toDoubleOrNull() ?: 2000.0
        val unit = match.groupValues[2].uppercase()
        return if (unit.contains("GB") || unit.contains("GIB")) num * 1024.0 else num
    }

    suspend fun fetchAllTorrentioTorrents(type: String, id: String): List<StremioStreamSource> = withContext(Dispatchers.IO) {
        val torrentioBase = "https://torrentio.strem.fun/language=hindi|qualityfilter=480p,other,scr,cam,unknown"
        try {
            val rawTorrents = stremioClient.getStreams(torrentioBase, type, id)
            val collectionKeywords = listOf("complete", "collection", "pack", "moviesup")
            val filteredTorrents = mutableListOf<StremioStreamSource>()
            val lowerPriorityTorrents = mutableListOf<StremioStreamSource>()

            for (torr in rawTorrents) {
                val titleLower = torr.title.orEmpty().lowercase()
                val hasCollection = collectionKeywords.any { titleLower.contains(it) }

                if (!hasCollection) {
                    filteredTorrents.add(torr)
                } else {
                    lowerPriorityTorrents.add(torr)
                }
            }
            filteredTorrents.addAll(lowerPriorityTorrents)
            filteredTorrents
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Unblocked real-time progressive stream resolution:
     * - As soon as ANY individual quality finishes, it emits it to UI immediately 1-by-1!
     * - If any cached file in DB is dead/missing, it automatically falls back to fresh torrent resolution!
     */
    fun streamPikPakStreams(
        type: String,
        id: String
    ): Flow<List<StremioStreamSource>> = channelFlow {
        Log.i(TAG, "==================================================")
        Log.i(TAG, "Starting non-blocking 1-by-1 stream emission for $type -> $id")
        val config = loadConfig()
        val postgresUrl = config.postgresUrl.orEmpty()
        val sharedPass = if (config.pikpakPassword.isNotBlank()) config.pikpakPassword else config.primaryAccount?.password.orEmpty()

        // 1. Fetch Torrentio + pikpak_v2 + pikpak_torrents all concurrently in PARALLEL upfront!
        val torrentioFilteredBase = "https://torrentio.strem.fun/language=hindi|qualityfilter=480p,other,scr,cam,unknown|sizefilter=6GB"

        val torrentsDeferred = async(Dispatchers.IO) {
            try {
                stremioClient.getStreams(torrentioFilteredBase, type, id)
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching from Torrentio", e)
                emptyList()
            }
        }

        val dataV2Deferred = async(Dispatchers.IO) {
            if (postgresUrl.isNotBlank()) PostgresAccountFetcher.getPikpakV2Records(postgresUrl, id) else emptyList()
        }

        val dataTorrentsDeferred = async(Dispatchers.IO) {
            if (postgresUrl.isNotBlank()) PostgresAccountFetcher.getPikpakTorrents(postgresUrl, id) else emptyList()
        }

        val rawTorrents = torrentsDeferred.await()
        val dataV2 = dataV2Deferred.await()
        val dataTorrents = dataTorrentsDeferred.await()

        // Filter collection keywords and clamp size < 5800 MB (exact pradhanStream_v3 logic)
        val collectionKeywords = listOf("complete", "collection", "pack", "moviesup")
        val currentTorrents = mutableListOf<StremioStreamSource>()
        val lowerPriorityTorrents = mutableListOf<StremioStreamSource>()

        for (torr in rawTorrents) {
            val titleLower = torr.title.orEmpty().lowercase()
            val hasCollection = collectionKeywords.any { titleLower.contains(it) }
            val sizeMb = parseFileSizeMb(torr.title.orEmpty())

            if (sizeMb < 5800.0) {
                if (!hasCollection) {
                    currentTorrents.add(torr)
                } else {
                    lowerPriorityTorrents.add(torr)
                }
            }
        }
        currentTorrents.addAll(lowerPriorityTorrents)

        if (currentTorrents.isEmpty()) {
            send(emptyList())
            return@channelFlow
        }

        val currentInfoHashes = currentTorrents.mapNotNull { it.infoHash }.toSet()
        val accumulatedStreams = mutableListOf<StremioStreamSource>()
        val mutex = Mutex()
        val resolvedQualities = mutableSetOf<String>()

        // 2. Check and reuse cached records in pikpak_v2
        if (postgresUrl.isNotBlank() && dataV2.isNotEmpty()) {
            val validCachedRecords = dataV2.filter { rec ->
                rec.infoHash.isBlank() || currentInfoHashes.contains(rec.infoHash)
            }

            if (validCachedRecords.isNotEmpty()) {
                Log.i(TAG, "⚡ Checking ${validCachedRecords.size} cached DB records...")
                val cachedJobs = validCachedRecords.map { record ->
                    launch(Dispatchers.IO) {
                        var token = record.accessToken
                        val now = System.currentTimeMillis() / 1000.0
                        if ((now - record.loginTime) > 7000.0 || token.isBlank()) {
                            val loginRes = pikpakClient.login(record.username, sharedPass)
                            if (loginRes.isSuccess) {
                                token = pikpakClient.cachedAccessToken.orEmpty()
                            }
                        }
                        val urlRes = pikpakClient.getDirectStreamUrlForFile(record.fileId, token)
                        val freshUrl = urlRes.getOrNull()
                        if (!freshUrl.isNullOrBlank()) {
                            val s = StremioStreamSource(
                                name = "📽️ " + record.quality,
                                title = record.title,
                                url = freshUrl,
                                infoHash = record.infoHash,
                                providerName = "PP"
                            )
                            mutex.withLock {
                                accumulatedStreams.add(s)
                                resolvedQualities.add(record.quality)
                                send(accumulatedStreams.toList())
                                Log.i(TAG, "⚡ Emitted valid cached stream: ${s.name}")
                            }
                        }
                    }
                }
                cachedJobs.joinAll()

                // If all 4 qualities were resolved from cache, complete flow
                if (accumulatedStreams.size >= 4) {
                    return@channelFlow
                }
            }
        }

        // 3. New Torrents -> Save snapshot to pikpak_torrents in background
        if (postgresUrl.isNotBlank()) {
            val torrentRecordsToSave = currentTorrents.map { t ->
                val qClean = t.name?.replace("Torrentio\n", "")?.trim() ?: "HD"
                PikPakTorrentRecord(
                    imdbId = id,
                    type = type,
                    quality = qClean,
                    title = t.title.orEmpty(),
                    infoHash = t.infoHash.orEmpty(),
                    size = parseFileSizeMb(t.title.orEmpty()),
                    filename = t.behaviorHints?.bingeGroup.orEmpty()
                )
            }
            launch(Dispatchers.IO) {
                PostgresAccountFetcher.savePikpakTorrents(postgresUrl, torrentRecordsToSave)
            }
        }

        // 4. Group remaining missing quality tiers
        val torrentsByQuality = currentTorrents.groupBy { torr ->
            torr.name?.replace("Torrentio\n", "")?.trim() ?: "HD"
        }.entries.filter { !resolvedQualities.contains(it.key) }.take(4)

        // 5. Fetch fresh random unused accounts from DB
        var accounts = if (postgresUrl.isNotBlank()) {
            val dbRes = PostgresAccountFetcher.fetchUsernames(postgresUrl)
            dbRes.getOrNull()?.map { email -> PikPakAccount(username = email, password = sharedPass) } ?: emptyList()
        } else {
            listOfNotNull(config.primaryAccount)
        }

        if (accounts.isEmpty() && config.primaryAccount != null) {
            accounts = listOf(config.primaryAccount!!)
        }

        // 6. Launch ALL remaining quality tier workers concurrently and EMIT 1-BY-1 INSTANTLY!
        val workers = torrentsByQuality.mapIndexed { index, (quality, candidates) ->
            launch(Dispatchers.IO) {
                val assignedAccounts = accounts.drop(index * 4).take(4).ifEmpty { accounts }
                var qualityResolved = false

                // Prioritize Hindi audio candidates first within quality tier!
                val sortedCandidates = candidates.sortedWith(compareByDescending<StremioStreamSource> { it.hasHindiAudio })

                for (torr in sortedCandidates.take(5)) {
                    if (qualityResolved) break
                    val infoHash = torr.infoHash ?: continue
                    val magnet = "magnet:?xt=urn:btih:$infoHash"
                    val qualityName = "📽️ $quality"
                    val titleClean = torr.title?.lines()?.firstOrNull() ?: torr.name ?: "Stream"

                    for (acc in assignedAccounts) {
                        try {
                            Log.d(TAG, "[$quality] Trying candidate [$infoHash] (Hindi=${torr.hasHindiAudio}) with account: ${acc.username}")
                            val authRes = pikpakClient.login(acc.username, acc.password)
                            if (authRes.isFailure) {
                                if (postgresUrl.isNotBlank()) {
                                    launch(Dispatchers.IO) { PostgresAccountFetcher.markEmailAsUsed(postgresUrl, acc.username) }
                                }
                                continue // try next account if login failed
                            }

                            val token = pikpakClient.cachedAccessToken.orEmpty()
                            val fileRes = pikpakClient.addMagnetAndGetResolvedFile(magnetUrl = magnet, token = token)
                            val resolvedFile = fileRes.getOrNull()

                            if (resolvedFile != null && resolvedFile.streamUrl.isNotBlank()) {
                                Log.i(TAG, "🎉 RESOLVED [$quality] -> EMITTING TO SCREEN NOW!")

                                // Save to PostgreSQL pikpak_v2 table asynchronously
                                if (postgresUrl.isNotBlank()) {
                                    val v2Record = PikPakV2Record(
                                        imdbId = id,
                                        quality = quality,
                                        title = torr.title ?: titleClean,
                                        filename = torr.behaviorHints?.bingeGroup ?: titleClean,
                                        fileId = resolvedFile.fileId,
                                        size = torr.fileSize ?: "2GB",
                                        fileExtension = "mkv",
                                        infoHash = infoHash,
                                        type = type,
                                        username = resolvedFile.username,
                                        encodedToken = "",
                                        accessToken = resolvedFile.accessToken,
                                        refreshToken = resolvedFile.refreshToken,
                                        userId = resolvedFile.userId,
                                        deviceId = resolvedFile.deviceId,
                                        loginTime = resolvedFile.loginTime,
                                        baseFileId = resolvedFile.baseFileId
                                    )
                                    launch(Dispatchers.IO) {
                                        PostgresAccountFetcher.savePikpakV2Record(postgresUrl, v2Record)
                                    }
                                }

                                val newStream = StremioStreamSource(
                                    name = qualityName,
                                    title = torr.title ?: titleClean,
                                    url = resolvedFile.streamUrl,
                                    infoHash = infoHash,
                                    providerName = "PP"
                                )

                                mutex.withLock {
                                    accumulatedStreams.add(newStream)
                                    send(accumulatedStreams.toList())
                                }
                                qualityResolved = true
                                break // Quality resolved!
                            } else {
                                // Magnet not cached in PikPak Cloud Drive -> Break account loop to try next candidate torrent!
                                Log.d(TAG, "[$quality] Candidate [$infoHash] not instant-cached in cloud drive, trying next candidate...")
                                break
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error resolving candidate [$infoHash]", e)
                        }
                    }
                }
            }
        }
        workers.joinAll()
    }.flowOn(Dispatchers.IO)

    suspend fun resolvePikPakStreams(
        type: String,
        id: String
    ): List<StremioStreamSource> = withContext(Dispatchers.IO) {
        val result = mutableListOf<StremioStreamSource>()
        streamPikPakStreams(type, id).collect { list ->
            result.clear()
            result.addAll(list)
        }
        result
    }

    suspend fun resolveDirectStreamUrlOnDemand(
        stream: StremioStreamSource,
        imdbId: String
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!stream.url.isNullOrBlank() && (stream.url.startsWith("http://") || stream.url.startsWith("https://"))) {
            return@withContext Result.success(stream.url)
        }

        val infoHash = stream.infoHash
        if (infoHash.isNullOrBlank()) {
            return@withContext Result.failure(IllegalArgumentException("No infoHash available for stream"))
        }

        val config = loadConfig()
        val postgresUrl = config.postgresUrl.orEmpty()
        val sharedPass = if (config.pikpakPassword.isNotBlank()) config.pikpakPassword else config.primaryAccount?.password.orEmpty()

        var accounts = if (postgresUrl.isNotBlank()) {
            val dbRes = PostgresAccountFetcher.fetchUsernames(postgresUrl)
            dbRes.getOrNull()?.map { email -> PikPakAccount(username = email, password = sharedPass) } ?: emptyList()
        } else {
            listOfNotNull(config.primaryAccount)
        }

        if (accounts.isEmpty() && config.primaryAccount != null) {
            accounts = listOf(config.primaryAccount!!)
        }

        val magnet = "magnet:?xt=urn:btih:$infoHash"
        val qualityClean = stream.name?.replace("Torrentio\n", "")?.replace("📽️", "")?.trim() ?: "HD"
        val streamTitle = stream.title?.lines()?.firstOrNull() ?: stream.name ?: "Stream"

        for (acc in accounts) {
            try {
                Log.d(TAG, "Resolving stream with account: ${acc.username}")
                val authRes = pikpakClient.login(acc.username, acc.password)
                if (authRes.isFailure) {
                    if (postgresUrl.isNotBlank()) {
                        PostgresAccountFetcher.markEmailAsUsed(postgresUrl, acc.username)
                    }
                    continue
                }

                val token = pikpakClient.cachedAccessToken.orEmpty()
                val fileRes = pikpakClient.addMagnetAndGetResolvedFile(magnetUrl = magnet, token = token)
                val resolvedFile = fileRes.getOrNull()

                if (resolvedFile != null && resolvedFile.streamUrl.isNotBlank()) {
                    Log.i(TAG, "🎉 Successfully resolved stream: ${resolvedFile.streamUrl.take(70)}...")
                    if (postgresUrl.isNotBlank()) {
                        val v2Record = PikPakV2Record(
                            imdbId = imdbId,
                            quality = qualityClean,
                            title = stream.title ?: streamTitle,
                            filename = streamTitle,
                            fileId = resolvedFile.fileId,
                            size = "2GB",
                            fileExtension = "mkv",
                            infoHash = infoHash,
                            type = "movie",
                            username = resolvedFile.username,
                            encodedToken = "",
                            accessToken = resolvedFile.accessToken,
                            refreshToken = resolvedFile.refreshToken,
                            userId = resolvedFile.userId,
                            deviceId = resolvedFile.deviceId,
                            loginTime = resolvedFile.loginTime,
                            baseFileId = resolvedFile.baseFileId
                        )
                        PostgresAccountFetcher.savePikpakV2Record(postgresUrl, v2Record)
                    }
                    return@withContext Result.success(resolvedFile.streamUrl)
                } else {
                    if (postgresUrl.isNotBlank()) {
                        PostgresAccountFetcher.markEmailAsUsed(postgresUrl, acc.username)
                    }
                }
            } catch (e: Exception) {
                if (postgresUrl.isNotBlank()) {
                    PostgresAccountFetcher.markEmailAsUsed(postgresUrl, acc.username)
                }
            }
        }

        Result.failure(IOException("Failed to resolve PikPak stream link"))
    }
}
