package com.mystream.app.data.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

@Serializable
data class PikPakAuthResponse(
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("refresh_token") val refreshToken: String = "",
    @SerialName("sub") val userId: String = "",
    @SerialName("expires_in") val expiresIn: Long = 0L,
    @SerialName("error") val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null
)

data class PikPakResolvedFile(
    val fileId: String,
    val baseFileId: String,
    val streamUrl: String,
    val username: String,
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val deviceId: String,
    val loginTime: Double
)

class PikPakApiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .dns(SystemFallbackDns)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    companion object {
        private const val TAG = "MyStream_PikPak"
        const val CLIENT_ID = "YNxT9w7GMdWvEOKa"
        const val CLIENT_SECRET = "dbw2OtmVEeuUvIptb1Coyg"
        const val CLIENT_VERSION = "1.47.1"
        const val PACKAGE_NAME = "com.pikcloud.pikpak"
        const val PIKPAK_USER_HOST = "user.mypikpak.com"
        const val PIKPAK_API_HOST = "api-drive.mypikpak.com"

        private val SALTS = listOf(
            "Gez0T9ijiI9WCeTsKSg3SMlx",
            "zQdbalsolyb1R/",
            "ftOjr52zt51JD68C3s",
            "yeOBMH0JkbQdEFNNwQ0RI9T3wU/v",
            "BRJrQZiTQ65WtMvwO",
            "je8fqxKPdQVJiy1DM6Bc9Nb1",
            "niV",
            "9hFCW2R1",
            "sHKHpe2i96",
            "p7c5E6AcXQ/IJUuAEC9W6",
            "",
            "aRv9hjc9P+Pbn+u3krN6",
            "BzStcgE8qVdqjEH16l4",
            "SqgeZvL5j9zoHP95xWHt",
            "zVof5yaJkPe3VFpadPof"
        )

        fun md5Hex(str: String): String {
            val md = MessageDigest.getInstance("MD5")
            return md.digest(str.toByteArray()).joinToString("") { "%02x".format(it) }
        }

        fun captchaSign(deviceId: String, timestamp: String): String {
            var sign = CLIENT_ID + CLIENT_VERSION + PACKAGE_NAME + deviceId + timestamp
            for (salt in SALTS) {
                sign = md5Hex(sign + salt)
            }
            return "1.$sign"
        }
    }

    var cachedAccessToken: String? = null
        private set
    var cachedRefreshToken: String? = null
        private set
    var userId: String? = null
        private set
    var currentUsername: String? = null
        private set
    var loginTime: Double = 0.0
        private set
    var deviceId: String = UUID.randomUUID().toString().replace("-", "")

    private suspend fun captchaInit(
        action: String,
        userId: String = this.userId ?: "",
        username: String = ""
    ): String? = withContext(Dispatchers.IO) {
        val url = "https://$PIKPAK_USER_HOST/v1/shield/captcha/init"
        val timestamp = System.currentTimeMillis().toString()
        val sign = captchaSign(deviceId, timestamp)

        val meta = buildString {
            append("{")
            append("\"captcha_sign\":\"$sign\",")
            append("\"client_version\":\"$CLIENT_VERSION\",")
            append("\"package_name\":\"$PACKAGE_NAME\",")
            if (userId.isNotBlank()) {
                append("\"user_id\":\"$userId\",")
            }
            if (username.isNotBlank()) {
                append("\"username\":\"$username\",")
            }
            append("\"timestamp\":\"$timestamp\"")
            append("}")
        }

        val payload = buildString {
            append("{")
            append("\"client_id\":\"$CLIENT_ID\",")
            append("\"action\":\"$action\",")
            append("\"device_id\":\"$deviceId\",")
            append("\"meta\":$meta")
            append("}")
        }

        val request = Request.Builder()
            .url(url)
            .post(payload.toRequestBody(jsonMediaType))
            .header("User-Agent", "ANDROID-$PACKAGE_NAME/$CLIENT_VERSION")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@withContext null
                val root = json.parseToJsonElement(body).jsonObject
                val token = root["captcha_token"]?.jsonPrimitive?.content
                token
            }
        } catch (e: Exception) {
            Log.w(TAG, "captchaInit error", e)
            null
        }
    }

    suspend fun login(
        username: String,
        pass: String
    ): Result<PikPakAuthResponse> = withContext(Dispatchers.IO) {
        if (username.isBlank() || pass.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Username and password cannot be blank"))
        }

        Log.d(TAG, "Signing into PikPak with account: $username")
        val loginUrl = "https://$PIKPAK_USER_HOST/v1/auth/signin"
        val captchaToken = captchaInit("POST:$loginUrl", username = username.trim()) ?: ""

        val formBodyBuilder = FormBody.Builder()
            .add("client_id", CLIENT_ID)
            .add("client_secret", CLIENT_SECRET)
            .add("username", username.trim())
            .add("password", pass.trim())

        if (captchaToken.isNotBlank()) {
            formBodyBuilder.add("captcha_token", captchaToken)
        }

        val request = Request.Builder()
            .url(loginUrl)
            .post(formBodyBuilder.build())
            .header("User-Agent", "ANDROID-$PACKAGE_NAME/$CLIENT_VERSION")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: throw IOException("Empty response from PikPak Auth")
                val authRes = json.decodeFromString<PikPakAuthResponse>(body)
                if (authRes.accessToken.isNotBlank()) {
                    cachedAccessToken = authRes.accessToken
                    cachedRefreshToken = authRes.refreshToken
                    userId = authRes.userId
                    currentUsername = username
                    loginTime = System.currentTimeMillis() / 1000.0
                    Log.i(TAG, "PikPak Login SUCCESSFUL! User ID: ${authRes.userId}")
                    Result.success(authRes)
                } else {
                    val err = authRes.errorDescription ?: authRes.error ?: "PikPak Auth Failed"
                    Log.w(TAG, "PikPak Login FAILED: $err")
                    Result.failure(IOException(err))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "PikPak login network error", e)
            Result.failure(e)
        }
    }

    suspend fun addMagnetAndGetStreamUrl(
        magnetUrl: String,
        token: String = cachedAccessToken ?: ""
    ): Result<String> = withContext(Dispatchers.IO) {
        val res = addMagnetAndGetResolvedFile(magnetUrl, token)
        res.map { it.streamUrl }
    }

    suspend fun addMagnetAndGetResolvedFile(
        magnetUrl: String,
        token: String = cachedAccessToken ?: ""
    ): Result<PikPakResolvedFile> = withContext(Dispatchers.IO) {
        if (token.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Not authenticated with PikPak"))
        }

        Log.d(TAG, "Adding magnet to PikPak drive: ${magnetUrl.take(60)}...")
        val url = "https://$PIKPAK_API_HOST/drive/v1/files"
        val captchaToken = captchaInit("POST:$url", userId = userId.orEmpty()) ?: ""

        val payload = """
            {
                "kind": "drive#file",
                "name": "stream_download.mkv",
                "upload_type": "UPLOAD_TYPE_URL",
                "url": { "url": "$magnetUrl" },
                "folder_type": "DOWNLOAD"
            }
        """.trimIndent()

        val reqBuilder = Request.Builder()
            .url(url)
            .post(payload.toRequestBody(jsonMediaType))
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .header("X-Device-Id", deviceId)
            .header("User-Agent", "ANDROID-$PACKAGE_NAME/$CLIENT_VERSION")

        if (captchaToken.isNotBlank()) {
            reqBuilder.header("X-Captcha-Token", captchaToken)
        }

        val request = reqBuilder.build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: throw IOException("Empty response from PikPak")
                Log.w(TAG, "PikPak Add File HTTP ${response.code}: $body")
                val rootJson = json.parseToJsonElement(body).jsonObject

                if (!response.isSuccessful) {
                    val err = rootJson["error"]?.jsonPrimitive?.content ?: "HTTP ${response.code}"
                    val desc = rootJson["error_description"]?.jsonPrimitive?.content ?: ""
                    Log.w(TAG, "PikPak Add File failed HTTP ${response.code}: $err - $desc")
                    return@withContext Result.failure(IOException(if (desc.isNotBlank()) "$err: $desc" else err))
                }

                val fileObj = rootJson["file"]?.jsonObject
                val taskObj = rootJson["task"]?.jsonObject
                var fileId = fileObj?.get("id")?.jsonPrimitive?.content
                    ?: taskObj?.get("file_id")?.jsonPrimitive?.content
                val baseFileId = taskObj?.get("file_id")?.jsonPrimitive?.content ?: fileId ?: ""
                val taskId = taskObj?.get("id")?.jsonPrimitive?.content
                var phase = taskObj?.get("phase")?.jsonPrimitive?.content

                Log.d(TAG, "Offline Task Created - ID: $taskId, Phase: $phase, FileID: $fileId")

                // If task is pending/running, poll for fast cloud cache completion (at most 3 attempts = 3.5s)
                if ((phase == "PHASE_TYPE_RUNNING" || phase == "PHASE_TYPE_PENDING" || fileId.isNullOrBlank()) && !taskId.isNullOrBlank()) {
                    var pollAttempt = 0
                    while (pollAttempt < 3) {
                        kotlinx.coroutines.delay(1000)
                        val taskListReq = Request.Builder()
                            .url("https://$PIKPAK_API_HOST/drive/v1/tasks?type=offline&limit=20&filters=%7B%22phase%22%3A%7B%22in%22%3A%22PHASE_TYPE_RUNNING%2CPHASE_TYPE_COMPLETE%2CPHASE_TYPE_PENDING%22%7D%7D")
                            .get()
                            .header("Authorization", "Bearer $token")
                            .build()
                        try {
                            client.newCall(taskListReq).execute().use { tRes ->
                                val tBody = tRes.body?.string()
                                if (!tBody.isNullOrBlank()) {
                                    val tRoot = json.parseToJsonElement(tBody).jsonObject
                                    val tasks = tRoot["tasks"]?.jsonArray
                                    if (tasks != null) {
                                        for (item in tasks) {
                                            val obj = item.jsonObject
                                            if (obj["id"]?.jsonPrimitive?.content == taskId) {
                                                phase = obj["phase"]?.jsonPrimitive?.content
                                                val fid = obj["file_id"]?.jsonPrimitive?.content
                                                if (!fid.isNullOrBlank()) fileId = fid
                                                Log.d(TAG, "Task Poll [$pollAttempt]: phase=$phase, fileId=$fileId")
                                                break
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (ignored: Exception) {}
                        if (phase == "PHASE_TYPE_COMPLETE" && !fileId.isNullOrBlank()) {
                            break
                        }
                        pollAttempt++
                    }
                }

                val finalFileId = fileId
                if (!finalFileId.isNullOrBlank()) {
                    val streamResult = getDirectStreamUrlForFile(finalFileId, token)
                    return@withContext streamResult.map { streamUrl ->
                        PikPakResolvedFile(
                            fileId = finalFileId,
                            baseFileId = baseFileId,
                            streamUrl = streamUrl,
                            username = currentUsername.orEmpty(),
                            accessToken = cachedAccessToken.orEmpty(),
                            refreshToken = cachedRefreshToken.orEmpty(),
                            userId = userId.orEmpty(),
                            deviceId = deviceId,
                            loginTime = loginTime
                        )
                    }
                }

                Result.failure(IOException("Could not extract stream URL for magnet in PikPak"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "addMagnetAndGetResolvedFile error", e)
            Result.failure(e)
        }
    }

    suspend fun getDirectStreamUrlForFile(
        fileId: String,
        token: String = cachedAccessToken ?: ""
    ): Result<String> = withContext(Dispatchers.IO) {
        if (fileId.isBlank()) return@withContext Result.failure(IllegalArgumentException("File ID is blank"))

        try {
            val req = Request.Builder()
                .url("https://$PIKPAK_API_HOST/drive/v1/files/$fileId")
                .get()
                .header("Authorization", "Bearer $token")
                .header("User-Agent", "ANDROID-$PACKAGE_NAME/$CLIENT_VERSION")
                .header("X-Device-Id", deviceId)
                .build()

            client.newCall(req).execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful || body.isNullOrBlank()) {
                    return@withContext Result.failure(IOException("PikPak getDirectStreamUrl HTTP ${response.code}"))
                }

                val rootJson = json.parseToJsonElement(body).jsonObject
                val kind = rootJson["kind"]?.jsonPrimitive?.content

                // If this is a folder (multi-file torrent), list children and find the primary video file
                if (kind == "drive#folder") {
                    try {
                        val listReq = Request.Builder()
                            .url("https://$PIKPAK_API_HOST/drive/v1/files?parent_id=$fileId&limit=50")
                            .get()
                            .header("Authorization", "Bearer $token")
                            .header("User-Agent", "ANDROID-$PACKAGE_NAME/$CLIENT_VERSION")
                            .header("X-Device-Id", deviceId)
                            .build()

                        client.newCall(listReq).execute().use { listResp ->
                            val listBody = listResp.body?.string()
                            if (!listBody.isNullOrBlank()) {
                                val listJson = json.parseToJsonElement(listBody).jsonObject
                                val files = listJson["files"]?.jsonArray
                                val videoFiles = files?.mapNotNull { it.jsonObject }?.filter { f ->
                                    val name = f["name"]?.jsonPrimitive?.content.orEmpty().lowercase()
                                    val fKind = f["kind"]?.jsonPrimitive?.content.orEmpty()
                                    fKind == "drive#file" && (name.endsWith(".mkv") || name.endsWith(".mp4") || name.endsWith(".avi") || name.endsWith(".mov"))
                                } ?: emptyList()
                                val target = videoFiles.maxByOrNull {
                                    it["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                                } ?: files?.mapNotNull { it.jsonObject }?.firstOrNull {
                                    it["kind"]?.jsonPrimitive?.content == "drive#folder"
                                } ?: files?.firstOrNull()?.jsonObject

                                val targetId = target?.get("id")?.jsonPrimitive?.content
                                if (!targetId.isNullOrBlank() && targetId != fileId) {
                                    return@withContext getDirectStreamUrlForFile(targetId, token)
                                }
                            }
                        }
                    } catch (ignored: Exception) {}
                }

                fun extractMediaUrl(obj: kotlinx.serialization.json.JsonObject): String? {
                    val medias = obj["medias"]?.jsonArray?.mapNotNull { it.jsonObject } ?: return null
                    // Find the best quality stream that has a non-empty, valid URL
                    // Prioritize 1080P/Original if present with URL, then 720P, then 480P
                    val priorityCategories = listOf("category_origin", "original", "1080p", "1080", "720p", "720", "480p", "480")
                    for (catKey in priorityCategories) {
                        val match = medias.firstOrNull { m ->
                            val cat = m["category"]?.jsonPrimitive?.content.orEmpty().lowercase()
                            val name = m["media_name"]?.jsonPrimitive?.content.orEmpty().lowercase()
                            (cat.contains(catKey) || name.contains(catKey)) && !m["link"]?.jsonObject?.get("url")?.jsonPrimitive?.content.isNullOrBlank()
                        }
                        val u = match?.get("link")?.jsonObject?.get("url")?.jsonPrimitive?.content
                        if (!u.isNullOrBlank() && !u.contains("x_limited=1") && !u.contains("ms=102400")) {
                            Log.i(TAG, "Selected streaming media: ${match["media_name"]?.jsonPrimitive?.content ?: match["category"]?.jsonPrimitive?.content}")
                            return u
                        }
                    }

                    // Fallback to any media object with a non-empty valid URL
                    for (m in medias) {
                        val u = m["link"]?.jsonObject?.get("url")?.jsonPrimitive?.content
                        if (!u.isNullOrBlank() && !u.contains("x_limited=1") && !u.contains("ms=102400")) {
                            return u
                        }
                    }
                    return null
                }

                val initialMediaUrl = extractMediaUrl(rootJson)
                if (!initialMediaUrl.isNullOrBlank()) {
                    Log.i(TAG, "🎉 EXTRACTED HIGH-SPEED DIRECT STREAM URL: ${initialMediaUrl.take(70)}...")
                    return@withContext Result.success(initialMediaUrl)
                }

                // If high-speed media stream is still generating in cloud storage, poll file details
                var attempts = 0
                while (attempts < 8) {
                    kotlinx.coroutines.delay(1500)
                    val refetchReq = Request.Builder()
                        .url("https://$PIKPAK_API_HOST/drive/v1/files/$fileId")
                        .get()
                        .header("Authorization", "Bearer $token")
                        .header("User-Agent", "ANDROID-$PACKAGE_NAME/$CLIENT_VERSION")
                        .header("X-Device-Id", deviceId)
                        .build()
                    try {
                        client.newCall(refetchReq).execute().use { r ->
                            val b = r.body?.string()
                            if (!b.isNullOrBlank()) {
                                val pollRoot = json.parseToJsonElement(b).jsonObject
                                val m = extractMediaUrl(pollRoot)
                                if (!m.isNullOrBlank()) {
                                    Log.i(TAG, "🎉 Recovered High-Speed URL on poll [$attempts]: ${m.take(70)}...")
                                    return@withContext Result.success(m)
                                }
                            }
                        }
                    } catch (_: Exception) {}
                    attempts++
                }

                // Do NOT fallback to web_content_link as it is a throttled zip/browser download link
                Result.failure(IOException("No unthrottled medias stream available for file $fileId"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getDirectStreamUrlForFile error", e)
            Result.failure(e)
        }
    }
}
