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

                if (fileId.isNullOrBlank()) {
                    // Check file list in account as instant fallback
                    val fileListReq = Request.Builder()
                        .url("https://$PIKPAK_API_HOST/drive/v1/files?limit=10")
                        .get()
                        .header("Authorization", "Bearer $token")
                        .build()
                    try {
                        client.newCall(fileListReq).execute().use { fRes ->
                            val fBody = fRes.body?.string()
                            if (!fBody.isNullOrBlank()) {
                                val fRoot = json.parseToJsonElement(fBody).jsonObject
                                val files = fRoot["files"]?.jsonArray
                                val found = files?.mapNotNull { it.jsonObject }?.firstOrNull {
                                    val n = it["name"]?.jsonPrimitive?.content.orEmpty()
                                    n != "PikPak Tutorial.mkv" && n.isNotBlank()
                                }
                                val foundId = found?.get("id")?.jsonPrimitive?.content
                                if (!foundId.isNullOrBlank()) {
                                    fileId = foundId
                                    Log.d(TAG, "Recovered file ID from file list: $fileId")
                                }
                            }
                        }
                    } catch (ignored: Exception) {}
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
        Log.d(TAG, "Fetching direct streaming URL for file: $fileId")
        val captchaToken = captchaInit("GET:/drive/v1/files/$fileId")

        val url = "https://$PIKPAK_API_HOST/drive/v1/files/$fileId"
        val reqBuilder = Request.Builder()
            .url(url)
            .get()
            .header("Authorization", "Bearer $token")

        if (!captchaToken.isNullOrBlank()) {
            reqBuilder.header("X-Captcha-Token", captchaToken)
        }

        try {
            client.newCall(reqBuilder.build()).execute().use { response ->
                val body = response.body?.string() ?: throw IOException("Empty response from PikPak file detail")
                val rootJson = json.parseToJsonElement(body).jsonObject

                val kind = rootJson["kind"]?.jsonPrimitive?.content
                if (kind == "drive#folder") {
                    val folderListReq = Request.Builder()
                        .url("https://$PIKPAK_API_HOST/drive/v1/files?parent_id=$fileId&limit=50")
                        .get()
                        .header("Authorization", "Bearer $token")
                        .build()
                    try {
                        client.newCall(folderListReq).execute().use { fRes ->
                            val fBody = fRes.body?.string()
                            if (!fBody.isNullOrBlank()) {
                                val fRoot = json.parseToJsonElement(fBody).jsonObject
                                val files = fRoot["files"]?.jsonArray
                                val videoFiles = files?.mapNotNull { it.jsonObject }?.filter {
                                    val name = it["name"]?.jsonPrimitive?.content?.lowercase().orEmpty()
                                    name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi") || name.endsWith(".mov")
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

                // 1. High Speed Media Streaming URL (medias[0].link.url)
                val medias = rootJson["medias"]?.jsonArray
                val mediaUrl = medias?.firstOrNull()?.jsonObject?.get("link")?.jsonObject?.get("url")?.jsonPrimitive?.content
                if (!mediaUrl.isNullOrBlank()) {
                    Log.i(TAG, "🎉 EXTRACTED HIGH-SPEED DIRECT STREAM URL: ${mediaUrl.take(70)}...")
                    return@withContext Result.success(mediaUrl)
                }

                // 2. Web Content Direct Link
                val directLink = rootJson["web_content_link"]?.jsonPrimitive?.content
                if (!directLink.isNullOrBlank()) {
                    Log.i(TAG, "🎉 EXTRACTED WEB_CONTENT_LINK: ${directLink.take(70)}...")
                    return@withContext Result.success(directLink)
                }

                Result.failure(IOException("No stream URL in file details"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getDirectStreamUrlForFile error", e)
            Result.failure(e)
        }
    }
}
