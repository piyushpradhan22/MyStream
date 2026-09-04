package com.mystream.app.data.resolver

import android.content.Context
import android.util.Log
import com.mystream.app.data.api.PikPakApiClient
import com.mystream.app.data.api.PikPakAuthSession
import com.mystream.app.data.api.PikPakPythonBridge
import com.mystream.app.data.api.PikPakResolvedFile
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
    val pikpakClient: PikPakApiClient = PikPakApiClient(),
    private val stremioClient: StremioApiClient = StremioApiClient()
) {
    suspend fun checkStreamArchiveStatus(url: String): Boolean = pikpakClient.checkStreamArchiveStatus(url)
    val pythonBridge by lazy { PikPakPythonBridge(context) }
    private val lastThawTrigger = java.util.concurrent.ConcurrentHashMap<String, Long>()
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
        val torrentioHindiBase = "https://torrentio.strem.fun/language=hindi|qualityfilter=480p,other,scr,cam,unknown"
        val torrentioGeneralBase = "https://torrentio.strem.fun/qualityfilter=480p,other,scr,cam,unknown"
        try {
            var rawTorrents = stremioClient.getStreams(torrentioHindiBase, type, id)
            if (rawTorrents.isEmpty()) {
                rawTorrents = stremioClient.getStreams(torrentioGeneralBase, type, id)
            }
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
        id: String,
        forceRefresh: Boolean = false
    ): Flow<List<StremioStreamSource>> = channelFlow {
        Log.i(TAG, "==================================================")
        Log.i(TAG, "Starting non-blocking 1-by-1 stream emission for $type -> $id (forceRefresh=$forceRefresh)")
        val config = loadConfig()
        val postgresUrl = config.postgresUrl.orEmpty()
        val sharedPass = if (config.pikpakPassword.isNotBlank()) config.pikpakPassword else config.primaryAccount?.password.orEmpty()

        if (forceRefresh && postgresUrl.isNotBlank()) {
            launch(Dispatchers.IO) {
                PostgresAccountFetcher.clearPikpakV2Record(postgresUrl, id)
            }
        }

        // 1. Fetch Torrentio + pikpak_v2 + pikpak_torrents all concurrently in PARALLEL upfront!
        val torrentioHindiBase = "https://torrentio.strem.fun/language=hindi|qualityfilter=480p,other,scr,cam,unknown|sizefilter=6GB"
        val torrentioGeneralBase = "https://torrentio.strem.fun/qualityfilter=480p,other,scr,cam,unknown|sizefilter=6GB"

        val torrentsDeferred = async(Dispatchers.IO) {
            try {
                val hindiTorrents = stremioClient.getStreams(torrentioHindiBase, type, id)
                if (hindiTorrents.isNotEmpty()) {
                    hindiTorrents
                } else {
                    stremioClient.getStreams(torrentioGeneralBase, type, id)
                }
            } catch (e: Exception) {
                try {
                    stremioClient.getStreams(torrentioGeneralBase, type, id)
                } catch (e2: Exception) {
                    Log.e(TAG, "Error fetching from Torrentio", e2)
                    emptyList()
                }
            }
        }

        val dataV2Deferred = async(Dispatchers.IO) {
            if (!forceRefresh && postgresUrl.isNotBlank()) PostgresAccountFetcher.getPikpakV2Records(postgresUrl, id) else emptyList()
        }

        val dataTorrentsDeferred = async(Dispatchers.IO) {
            if (!forceRefresh && postgresUrl.isNotBlank()) PostgresAccountFetcher.getPikpakTorrents(postgresUrl, id) else emptyList()
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

        val currentInfoHashes = currentTorrents.mapNotNull { it.infoHash }.filter { it.isNotBlank() }.distinct()
        val accumulatedStreams = mutableListOf<StremioStreamSource>()
        val mutex = Mutex()
        val resolvedQualities = mutableSetOf<String>()

        // Check if any candidate torrent infoHash already exists in DB pikpak_v2 (e.g. from previous episode or quality)
        val infoHashRecordsDeferred = async(Dispatchers.IO) {
            if (postgresUrl.isNotBlank() && currentInfoHashes.isNotEmpty()) {
                PostgresAccountFetcher.getPikpakV2RecordsForInfoHashes(postgresUrl, currentInfoHashes)
            } else {
                emptyList()
            }
        }
        val infoHashRecords = infoHashRecordsDeferred.await()
        val accountByInfoHash = infoHashRecords.filter { it.infoHash.isNotBlank() && it.username.isNotBlank() }
            .associateBy { it.infoHash.lowercase() }

        if (accountByInfoHash.isNotEmpty()) {
            Log.i(TAG, "♻️ Found ${accountByInfoHash.size} existing torrent infoHashes in DB cache for account reuse!")
        }

        // 2. Check and reuse cached records in pikpak_v2 for this exact ID
        if (!forceRefresh && postgresUrl.isNotBlank() && dataV2.isNotEmpty()) {
            val validCachedRecords = dataV2.filter { rec ->
                val notSample = !rec.title.contains("sample", ignoreCase = true) &&
                        !rec.filename.contains("sample", ignoreCase = true) &&
                        !(rec.size.contains("MB", ignoreCase = true) && (rec.size.replace("MB", "", ignoreCase = true).trim().toDoubleOrNull() ?: 0.0) < 100.0)
                notSample && (rec.infoHash.isBlank() || currentInfoHashes.contains(rec.infoHash))
            }

            if (validCachedRecords.isNotEmpty()) {
                Log.i(TAG, "⚡ Checking ${validCachedRecords.size} cached DB records for exact ID in parallel...")
                val cachedJobs = validCachedRecords.map { record ->
                    launch(Dispatchers.IO) {
                        val urlRes = pythonBridge.resolveStreamWithPython(
                            username = record.username,
                            password = sharedPass,
                            magnet = if (record.infoHash.isNotBlank()) "magnet:?xt=urn:btih:${record.infoHash}" else "",
                            fileId = record.fileId,
                            fileName = record.title.ifBlank { record.filename }
                        )
                        val freshUrl = urlRes.getOrNull()
                        val isThrottled = freshUrl?.contains("x_limited=1") == true || freshUrl?.contains("ms=102400") == true
                        if (freshUrl != null && !isThrottled) {
                            val isArchive = pikpakClient.checkStreamArchiveStatus(freshUrl)
                            val isDownscaled = (record.quality.contains("1080", ignoreCase = true) || record.quality.contains("4k", ignoreCase = true) || record.quality.contains("2160", ignoreCase = true)) &&
                                    (freshUrl.contains("category=transcoded", ignoreCase = true) || freshUrl.contains("category=transcode", ignoreCase = true))
                            val downTag = if (isDownscaled) " ⬇" else ""
                            val arcTag = if (isArchive) " ARC" else ""
                            val qName = "📽️ ${record.quality}$downTag$arcTag"
                            val s = StremioStreamSource(
                                name = qName,
                                title = record.title,
                                url = freshUrl,
                                infoHash = record.infoHash,
                                providerName = "PP"
                            )
                            mutex.withLock {
                                accumulatedStreams.add(s)
                                resolvedQualities.add(record.quality)
                                send(accumulatedStreams.toList())
                                Log.i(TAG, "⚡ Emitted cached stream: ${s.name} | Account: ${record.username} | UserID: ${record.userId} | FileID: ${record.fileId}")
                            }
                        } else {
                            Log.w(TAG, "⚠️ Cached DB record for ${record.quality} was throttled or invalid, re-resolving...")
                        }
                    }
                }
                cachedJobs.joinAll()
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

        // 4. Group remaining missing quality tiers (4K, 1080p HDR, 1080p 3D, 1080p, 720p, etc.)
        val torrentsByQuality = currentTorrents.groupBy { torr ->
            torr.name?.replace("Torrentio\n", "")?.trim() ?: "HD"
        }.entries.filter { !resolvedQualities.contains(it.key) }

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
        val workers = torrentsByQuality.mapIndexed { workerIndex, (quality, candidates) ->
            launch(Dispatchers.IO) {
                val accountSlice = if (accounts.size > workerIndex * 3) {
                    accounts.drop(workerIndex * 3) + accounts.take(workerIndex * 3)
                } else {
                    accounts.shuffled()
                }
                val assignedAccounts = accountSlice.ifEmpty { listOfNotNull(config.primaryAccount) }
                var qualityResolved = false

                // Prioritize Hindi audio candidates first within quality tier!
                val sortedCandidates = candidates.sortedWith(compareByDescending<StremioStreamSource> { it.hasHindiAudio })

                for (torr in sortedCandidates.take(2)) {
                    if (qualityResolved) break
                    val infoHash = torr.infoHash ?: continue
                    val magnet = "magnet:?xt=urn:btih:$infoHash"
                    val titleClean = torr.title?.lines()?.firstOrNull() ?: torr.name ?: "Stream"

                    val knownRecord = accountByInfoHash[infoHash.lowercase()]
                    val candidateAccounts = if (knownRecord != null && knownRecord.username.isNotBlank()) {
                        Log.i(TAG, "[$quality] ♻️ Torrent infoHash [$infoHash] already in account ${knownRecord.username}, reusing it directly!")
                        listOf(PikPakAccount(username = knownRecord.username, password = sharedPass))
                    } else {
                        assignedAccounts.take(1)
                    }

                    for (acc in candidateAccounts) {
                        try {
                            val isReusedAccount = knownRecord != null && acc.username == knownRecord.username
                            Log.d(TAG, "[$quality] Trying candidate [$infoHash] (Hindi=${torr.hasHindiAudio}, ReusedAccount=$isReusedAccount) with account: ${acc.username}")
                            val authRes = pikpakClient.login(acc.username, acc.password)
                            if (authRes.isFailure) {
                                if (postgresUrl.isNotBlank() && !isReusedAccount) {
                                    launch(Dispatchers.IO) { PostgresAccountFetcher.markEmailAsUsed(postgresUrl, acc.username) }
                                }
                                Log.w(TAG, "[$quality] Login failed for account ${acc.username}")
                                break
                            }

                            val session = authRes.getOrNull() ?: continue
                            val targetNameHint = torr.behaviorHints?.filename ?: torr.title

                            // Primary Method: Extract stream via Python pikpakapi directly
                            Log.i(TAG, "[$quality] 🐍 Extracting stream using Python pikpakapi for ${acc.username}...")
                            val pyDetails = pythonBridge.resolveStreamDetailsWithPython(
                                username = acc.username,
                                password = acc.password,
                                magnet = magnet,
                                fileId = knownRecord?.fileId,
                                fileName = targetNameHint
                            )
                            val pyResp = pyDetails.getOrNull()

                            val resolvedFile: PikPakResolvedFile? = if (pyDetails.isSuccess && pyResp != null && !pyResp.stream_url.isNullOrBlank()) {
                                Log.i(TAG, "[$quality] 🎉 Successfully extracted via Python pikpakapi: ${pyResp.name} (fileId=${pyResp.file_id})")
                                PikPakResolvedFile(
                                    fileId = pyResp.file_id.orEmpty(),
                                    baseFileId = pyResp.file_id.orEmpty(),
                                    streamUrl = pyResp.stream_url.orEmpty(),
                                    username = session.username,
                                    accessToken = session.accessToken,
                                    refreshToken = session.refreshToken,
                                    userId = session.userId,
                                    deviceId = session.deviceId,
                                    loginTime = session.loginTime
                                )
                            } else {
                                Log.w(TAG, "[$quality] Python pikpakapi failed: ${pyDetails.exceptionOrNull()?.message}")
                                null
                            }

                            val streamUrl = resolvedFile?.streamUrl.orEmpty()
                            val isThrottled = streamUrl.contains("x_limited=1") || streamUrl.contains("ms=102400") || streamUrl.contains("ms=1048576")

                            if (resolvedFile != null && streamUrl.isNotBlank() && !isThrottled) {
                                Log.i(TAG, "🎉 RESOLVED [$quality] -> EMITTING TO SCREEN NOW! | Account: ${resolvedFile.username} | UserID: ${resolvedFile.userId} | FileID: ${resolvedFile.fileId}")

                                if (postgresUrl.isNotBlank() && !isReusedAccount) {
                                    launch(Dispatchers.IO) {
                                        PostgresAccountFetcher.markEmailAsUsed(postgresUrl, acc.username)
                                    }
                                }

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

                                val isDownscaled = (quality.contains("1080", ignoreCase = true) || quality.contains("4k", ignoreCase = true) || quality.contains("2160", ignoreCase = true)) &&
                                        (resolvedFile.streamUrl.contains("category=transcoded", ignoreCase = true) || resolvedFile.streamUrl.contains("category=transcode", ignoreCase = true))
                                val downTag = if (isDownscaled) " ⬇" else ""
                                val isArchive = pikpakClient.checkStreamArchiveStatus(resolvedFile.streamUrl)
                                val arcTag = if (isArchive) " ARC" else ""
                                val effectiveQualityName = "📽️ $quality$downTag$arcTag"

                                val newStream = StremioStreamSource(
                                    name = effectiveQualityName,
                                    title = torr.title ?: titleClean,
                                    url = resolvedFile.streamUrl,
                                    infoHash = infoHash,
                                    providerName = "PP"
                                )

                                mutex.withLock {
                                    accumulatedStreams.add(newStream)
                                    send(accumulatedStreams.toList())
                                }
                                Log.i(TAG, "🎉 EMITTED [$quality] to screen -> ${newStream.name} | Account: ${resolvedFile.username} | UserID: ${resolvedFile.userId}")
                                qualityResolved = true
                                break
                            } else if (isThrottled) {
                                Log.w(TAG, "[$quality] Account ${acc.username} generated a throttled link")
                                if (postgresUrl.isNotBlank() && !isReusedAccount) {
                                    launch(Dispatchers.IO) { PostgresAccountFetcher.markEmailAsUsed(postgresUrl, acc.username) }
                                }
                                break
                            }
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            Log.e(TAG, "Error resolving candidate [$infoHash]", e)
                            break
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

    suspend fun triggerArchiveThawForUrl(url: String): Boolean = withContext(Dispatchers.IO) {
        val fileId = Regex("""[?&]fileid=([a-zA-Z0-9_-]+)""").find(url)?.groupValues?.get(1) ?: return@withContext false
        val userId = Regex("""[?&]userid=([a-zA-Z0-9_-]+)""").find(url)?.groupValues?.get(1)
        val config = loadConfig()
        val postgresUrl = config.postgresUrl.orEmpty()
        val sharedPass = if (config.pikpakPassword.isNotBlank()) config.pikpakPassword else config.primaryAccount?.password.orEmpty()

        val record = if (postgresUrl.isNotBlank()) {
            PostgresAccountFetcher.getPikpakV2RecordForFileId(postgresUrl, fileId, userId)
        } else null

        if (record != null && record.username.isNotBlank()) {
            var token = record.accessToken
            val now = System.currentTimeMillis() / 1000.0
            if ((now - record.loginTime) > 7000.0 || token.isBlank()) {
                val loginRes = pikpakClient.login(record.username, sharedPass)
                if (loginRes.isSuccess) {
                    token = loginRes.getOrNull()?.accessToken.orEmpty()
                }
            }
            if (token.isNotBlank()) {
                val lastTime = lastThawTrigger[fileId] ?: 0L
                val currentTime = System.currentTimeMillis()
                // Re-inject offline_download once per 45s during player retry cycles to command PikPak's cluster to pull blocks
                if (currentTime - lastTime > 45_000L && record.infoHash.isNotBlank()) {
                    lastThawTrigger[fileId] = currentTime
                    Log.i(TAG, "❄️ Waking up cold archive via cloud offline_download engine for fileId=$fileId, hash=${record.infoHash}")
                    val session = PikPakAuthSession(
                        username = record.username,
                        accessToken = token,
                        refreshToken = record.refreshToken,
                        userId = record.userId,
                        deviceId = record.deviceId.ifBlank { java.util.UUID.randomUUID().toString().replace("-", "") },
                        loginTime = record.loginTime
                    )
                    val targetName = record.title.ifBlank { record.filename }
                    pikpakClient.addMagnetAndGetResolvedFile(
                        magnetUrl = "magnet:?xt=urn:btih:${record.infoHash}",
                        session = session,
                        targetFileName = targetName
                    )
                }

                Log.i(TAG, "❄️ Waking up / refreshing stream URL on PikPak control plane for fileId=$fileId via ${record.username}")
                val direct = pikpakClient.getDirectStreamUrlForFile(
                    fileId = fileId,
                    token = token,
                    targetFileName = record.title.ifBlank { record.filename },
                    deviceId = record.deviceId.ifBlank { java.util.UUID.randomUUID().toString().replace("-", "") },
                    userId = record.userId
                )
                return@withContext direct.isSuccess
            }
        }
        false
    }

    suspend fun resolveDirectStreamUrlOnDemand(
        stream: StremioStreamSource,
        imdbId: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val existingUrl = stream.url
        val isArcStream = stream.name?.contains("ARC", ignoreCase = true) == true ||
                stream.title?.contains("ARC", ignoreCase = true) == true
        if (!isArcStream && !existingUrl.isNullOrBlank() && (existingUrl.startsWith("http://") || existingUrl.startsWith("https://"))) {
            return@withContext Result.success(stream.url)
        }

        val infoHash = stream.infoHash
        if (infoHash.isNullOrBlank()) {
            return@withContext Result.failure(IllegalArgumentException("No infoHash available for stream"))
        }

        val config = loadConfig()
        val postgresUrl = config.postgresUrl.orEmpty()
        val sharedPass = if (config.pikpakPassword.isNotBlank()) config.pikpakPassword else config.primaryAccount?.password.orEmpty()

        val knownRecord = if (postgresUrl.isNotBlank()) {
            PostgresAccountFetcher.getPikpakV2RecordsForInfoHashes(postgresUrl, listOf(infoHash))
                .firstOrNull { it.infoHash.equals(infoHash, ignoreCase = true) && it.username.isNotBlank() }
        } else {
            null
        }

        val magnet = "magnet:?xt=urn:btih:$infoHash"
        val filenameHint = stream.behaviorHints?.filename ?: stream.title ?: knownRecord?.filename?.ifBlank { knownRecord.title }

        val isKnownSample = knownRecord != null && (
            knownRecord.title.contains("sample", ignoreCase = true) ||
            knownRecord.filename.contains("sample", ignoreCase = true) ||
            (knownRecord.size.contains("MB", ignoreCase = true) && (knownRecord.size.replace("MB", "", ignoreCase = true).trim().toDoubleOrNull() ?: 0.0) < 100.0)
        )
        if (isKnownSample && postgresUrl.isNotBlank()) {
            val targetImdb = knownRecord?.imdbId ?: imdbId
            Log.w(TAG, "🗑️ Purging poisoned sample record from DB for imdbId=$targetImdb...")
            launch(Dispatchers.IO) {
                PostgresAccountFetcher.clearPikpakV2Record(postgresUrl, targetImdb)
            }
        }
        val pyFileId = if (isKnownSample) null else knownRecord?.fileId

        val candidateAccounts = mutableListOf<String>()
        if (!knownRecord?.username.isNullOrBlank()) candidateAccounts.add(knownRecord!!.username)
        if (postgresUrl.isNotBlank()) {
            val pool = PostgresAccountFetcher.fetchUsernames(postgresUrl).getOrNull() ?: emptyList()
            candidateAccounts.addAll(pool.filter { it != knownRecord?.username })
        } else if (config.primaryAccount?.username != null) {
            candidateAccounts.add(config.primaryAccount!!.username)
        }

        // Pure Python pikpakapi resolution (No native fallback)
        var lastError: Throwable? = null
        for (pyUsername in candidateAccounts.take(3)) {
            Log.i(TAG, "🐍 Attempting stream resolution via Python pikpakapi for $pyUsername...")
            val pyResult = pythonBridge.resolveStreamWithPython(
                username = pyUsername,
                password = sharedPass,
                magnet = magnet,
                fileId = pyFileId,
                fileName = filenameHint
            )
            val pyUrl = pyResult.getOrNull()
            if (pyResult.isSuccess && !pyUrl.isNullOrBlank()) {
                Log.i(TAG, "🎉 Python pikpakapi resolved stream successfully: ${pyUrl.take(70)}...")
                return@withContext Result.success(pyUrl)
            } else {
                lastError = pyResult.exceptionOrNull()
                Log.w(TAG, "⚠️ Python pikpakapi resolution for $pyUsername did not succeed: ${lastError?.message}.")
            }
        }

        Result.failure(lastError ?: IOException("Python pikpakapi failed to resolve stream for infoHash=$infoHash"))
    }
}
