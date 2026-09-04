package com.mystream.app.data.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

data class PikPakAuthSession(
    val username: String,
    val accessToken: String,
    val refreshToken: String = "",
    val userId: String = "",
    val deviceId: String = UUID.randomUUID().toString().replace("-", ""),
    val loginTime: Double = System.currentTimeMillis() / 1000.0
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

    private suspend fun captchaInit(
        action: String,
        userId: String = "",
        username: String = "",
        deviceId: String = UUID.randomUUID().toString().replace("-", "")
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
                root["captcha_token"]?.jsonPrimitive?.content
            }
        } catch (e: Exception) {
            Log.w(TAG, "captchaInit error", e)
            null
        }
    }

    suspend fun login(
        username: String,
        pass: String
    ): Result<PikPakAuthSession> = withContext(Dispatchers.IO) {
        if (username.isBlank() || pass.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Username and password cannot be blank"))
        }

        val sessionDeviceId = UUID.randomUUID().toString().replace("-", "")
        Log.d(TAG, "Signing into PikPak with account: $username")
        val loginUrl = "https://$PIKPAK_USER_HOST/v1/auth/signin"
        val captchaToken = captchaInit("POST:$loginUrl", username = username.trim(), deviceId = sessionDeviceId) ?: ""

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
                    val session = PikPakAuthSession(
                        username = username.trim(),
                        accessToken = authRes.accessToken,
                        refreshToken = authRes.refreshToken,
                        userId = authRes.userId,
                        deviceId = sessionDeviceId,
                        loginTime = System.currentTimeMillis() / 1000.0
                    )
                    Log.i(TAG, "PikPak Login SUCCESSFUL for ${session.username}! User ID: ${session.userId}")
                    Result.success(session)
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

    suspend fun addMagnetAndGetResolvedFile(
        magnetUrl: String,
        session: PikPakAuthSession,
        targetFileName: String? = null
    ): Result<PikPakResolvedFile> = withContext(Dispatchers.IO) {
        if (session.accessToken.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Not authenticated with PikPak"))
        }

        Log.d(TAG, "Adding magnet to PikPak drive for ${session.username}: ${magnetUrl.take(60)}...")
        val url = "https://$PIKPAK_API_HOST/drive/v1/files"
        val captchaToken = captchaInit("POST:$url", userId = session.userId, deviceId = session.deviceId) ?: ""

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
            .header("Authorization", "Bearer ${session.accessToken}")
            .header("Content-Type", "application/json")
            .header("X-Device-Id", session.deviceId)
            .header("User-Agent", "ANDROID-$PACKAGE_NAME/$CLIENT_VERSION")

        if (captchaToken.isNotBlank()) {
            reqBuilder.header("X-Captcha-Token", captchaToken)
        }

        val request = reqBuilder.build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: throw IOException("Empty response from PikPak")
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

                // If task is still running/saving, poll up to 12s for completion
                if ((phase == "PHASE_TYPE_RUNNING" || phase == "PHASE_TYPE_PENDING" || fileId.isNullOrBlank()) && !taskId.isNullOrBlank()) {
                    Log.d(TAG, "Waiting for PikPak task $taskId to finish saving...")
                    val maxWaitSec = 12
                    for (sec in 1..maxWaitSec) {
                        delay(1000)
                        try {
                            val taskPollReq = Request.Builder()
                                .url("https://$PIKPAK_API_HOST/drive/v1/tasks?type=offline")
                                .get()
                                .header("Authorization", "Bearer ${session.accessToken}")
                                .header("User-Agent", "ANDROID-$PACKAGE_NAME/$CLIENT_VERSION")
                                .header("X-Device-Id", session.deviceId)
                                .build()

                            client.newCall(taskPollReq).execute().use { pollResp ->
                                val pollBody = pollResp.body?.string()
                                if (!pollBody.isNullOrBlank()) {
                                    val pollJson = json.parseToJsonElement(pollBody).jsonObject
                                    val taskList = pollJson["tasks"]?.jsonArray?.mapNotNull { it.jsonObject } ?: emptyList()
                                    val currentTask = taskList.firstOrNull { it["id"]?.jsonPrimitive?.content == taskId }
                                    if (currentTask != null) {
                                        phase = currentTask["phase"]?.jsonPrimitive?.content
                                        val pollFileId = currentTask["file_id"]?.jsonPrimitive?.content
                                        if (!pollFileId.isNullOrBlank()) fileId = pollFileId
                                        if (phase == "PHASE_TYPE_COMPLETE") return@use
                                    } else {
                                        phase = "PHASE_TYPE_COMPLETE"
                                        return@use
                                    }
                                }
                            }
                        } catch (ignored: Exception) {}

                        if (phase == "PHASE_TYPE_COMPLETE" && !fileId.isNullOrBlank()) break
                    }
                }

                val finalFileId = fileId
                if (!finalFileId.isNullOrBlank()) {
                    val streamResult = getDirectStreamUrlForFile(
                        fileId = finalFileId,
                        token = session.accessToken,
                        targetFileName = targetFileName,
                        deviceId = session.deviceId,
                        userId = session.userId
                    )
                    return@withContext streamResult.map { streamUrl ->
                        val actualFileId = Regex("""[?&]fileid=([a-zA-Z0-9_-]+)""").find(streamUrl)?.groupValues?.get(1)?.takeIf { it.isNotBlank() } ?: finalFileId
                        PikPakResolvedFile(
                            fileId = actualFileId,
                            baseFileId = baseFileId.ifBlank { finalFileId },
                            streamUrl = streamUrl,
                            username = session.username,
                            accessToken = session.accessToken,
                            refreshToken = session.refreshToken,
                            userId = session.userId,
                            deviceId = session.deviceId,
                            loginTime = session.loginTime
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
        token: String,
        targetFileName: String? = null,
        deviceId: String = UUID.randomUUID().toString().replace("-", ""),
        userId: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        if (fileId.isBlank()) return@withContext Result.failure(IllegalArgumentException("File ID is blank"))

        try {
            val captchaToken = captchaInit("GET:/drive/v1/files/$fileId", userId = userId, deviceId = deviceId)
            val reqBuilder = Request.Builder()
                .url("https://$PIKPAK_API_HOST/drive/v1/files/$fileId?usage=PLAY&with_play_info=true")
                .get()
                .header("Authorization", "Bearer $token")
                .header("User-Agent", "ANDROID-$PACKAGE_NAME/$CLIENT_VERSION")
                .header("X-Device-Id", deviceId)
            if (!captchaToken.isNullOrBlank()) {
                reqBuilder.header("X-Captcha-Token", captchaToken)
            }
            val req = reqBuilder.build()

            client.newCall(req).execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful || body.isNullOrBlank()) {
                    return@withContext Result.failure(IOException("PikPak getDirectStreamUrl HTTP ${response.code}"))
                }

                val rootJson = json.parseToJsonElement(body).jsonObject
                val kind = rootJson["kind"]?.jsonPrimitive?.content
                val currentName = rootJson["name"]?.jsonPrimitive?.content.orEmpty().lowercase()
                val currentSize = rootJson["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                val parentId = rootJson["parent_id"]?.jsonPrimitive?.content.orEmpty()

                // If this file is a sample preview and sits inside a folder, redirect to parent folder to select the full movie!
                if (kind == "drive#file" && (currentName.contains("sample") || currentName.startsWith("sample") || (currentSize in 1..80_000_000L)) && parentId.isNotBlank()) {
                    Log.w(TAG, "⚠️ File $fileId is a sample preview ('$currentName', $currentSize bytes)! Redirecting to parent folder $parentId to select full movie...")
                    return@withContext getDirectStreamUrlForFile(parentId, token, targetFileName, deviceId, userId)
                }
                if (currentName.contains("sample") || currentName.startsWith("sample")) {
                    return@withContext Result.failure(IOException("Refusing to stream sample file '$currentName'"))
                }

                // If folder, list children and select target video file
                if (kind == "drive#folder") {
                    try {
                        val listReq = Request.Builder()
                            .url("https://$PIKPAK_API_HOST/drive/v1/files?parent_id=$fileId&limit=100")
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
                                val allVideos = files?.mapNotNull { it.jsonObject }?.filter { f ->
                                    val name = f["name"]?.jsonPrimitive?.content.orEmpty().lowercase()
                                    val fKind = f["kind"]?.jsonPrimitive?.content.orEmpty()
                                    fKind == "drive#file" && (name.endsWith(".mkv") || name.endsWith(".mp4") || name.endsWith(".avi") || name.endsWith(".mov") || name.endsWith(".ts"))
                                } ?: emptyList()
                                val nonSampleVideos = allVideos.filterNot { 
                                    val n = it["name"]?.jsonPrimitive?.content.orEmpty().lowercase()
                                    val s = it["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                                    n.contains("sample") || n.startsWith("sample") || (s in 1..80_000_000L && allVideos.size > 1)
                                }
                                val videoFiles = if (nonSampleVideos.isNotEmpty()) nonSampleVideos else allVideos

                                val target = if (!targetFileName.isNullOrBlank() && videoFiles.isNotEmpty()) {
                                    val cleanTarget = targetFileName.lines().firstOrNull { it.isNotBlank() } ?: targetFileName
                                    val targetTokens = cleanTarget.lowercase().replace(Regex("[^a-z0-9]"), " ").split("\\s+".toRegex()).filter { it.length > 2 }
                                    
                                    videoFiles.firstOrNull { f ->
                                        val fn = f["name"]?.jsonPrimitive?.content.orEmpty().lowercase()
                                        targetTokens.isNotEmpty() && targetTokens.all { token -> fn.contains(token) }
                                    } ?: videoFiles.firstOrNull { f ->
                                        val fn = f["name"]?.jsonPrimitive?.content.orEmpty().lowercase()
                                        val fnClean = fn.replace(Regex("[^a-z0-9]"), " ")
                                        val targetClean = cleanTarget.lowercase().replace(Regex("[^a-z0-9]"), " ")
                                        fnClean.contains(targetClean) || targetClean.contains(fnClean)
                                    } ?: run {
                                        val epPattern = Regex("""(?i)(?:s\d{1,2})?e(\d{1,3})|(\d{1,2})x(\d{1,3})|episode\s*(\d{1,3})""")
                                        val match = epPattern.find(cleanTarget)
                                        if (match != null) {
                                            val epMatchStr = match.value.lowercase()
                                            videoFiles.firstOrNull { f ->
                                                val fn = f["name"]?.jsonPrimitive?.content.orEmpty().lowercase()
                                                fn.contains(epMatchStr)
                                            }
                                        } else null
                                    } ?: videoFiles.maxByOrNull {
                                        it["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                                    }
                                } else {
                                    videoFiles.maxByOrNull {
                                        it["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                                    }
                                } ?: files?.mapNotNull { it.jsonObject }?.firstOrNull {
                                    it["kind"]?.jsonPrimitive?.content == "drive#folder"
                                } ?: files?.firstOrNull()?.jsonObject

                                val targetId = target?.get("id")?.jsonPrimitive?.content
                                if (!targetId.isNullOrBlank() && targetId != fileId) {
                                    return@withContext getDirectStreamUrlForFile(targetId, token, targetFileName, deviceId, userId)
                                }
                            }
                        }
                    } catch (ignored: Exception) {}
                }

                // Collect all candidates: original torrent file (web_content_link) and medias
                val candidates = mutableListOf<String>()

                val webLink = rootJson["web_content_link"]?.jsonPrimitive?.content
                if (!webLink.isNullOrBlank() && !webLink.contains("x_limited=1") && !webLink.contains("ms=102400")) {
                    candidates.add(webLink)
                }

                val medias = rootJson["medias"]?.jsonArray?.mapNotNull { it.jsonObject } ?: emptyList()
                val priorityCategories = listOf("1080", "720", "480", "category_origin", "original")
                for (catKey in priorityCategories) {
                    val match = medias.firstOrNull { m ->
                        val cat = m["category"]?.jsonPrimitive?.content.orEmpty().lowercase()
                        val name = m["media_name"]?.jsonPrimitive?.content.orEmpty().lowercase()
                        val url = m["link"]?.jsonObject?.get("url")?.jsonPrimitive?.content
                        (cat.contains(catKey) || name.contains(catKey)) && !url.isNullOrBlank() && !url.contains("x_limited=1") && !url.contains("ms=102400")
                    }
                    val url = match?.get("link")?.jsonObject?.get("url")?.jsonPrimitive?.content
                    if (!url.isNullOrBlank() && !candidates.contains(url)) {
                        candidates.add(url)
                    }
                }
                for (m in medias) {
                    val url = m["link"]?.jsonObject?.get("url")?.jsonPrimitive?.content
                    if (!url.isNullOrBlank() && !url.contains("x_limited=1") && !url.contains("ms=102400") && !candidates.contains(url)) {
                        candidates.add(url)
                    }
                }

                // Prioritize candidate that is NOT in cold archive (Standard storage -> plays instantly)
                var selectedUrl: String? = null
                for (cand in candidates) {
                    val isArchive = checkStreamArchiveStatus(cand)
                    if (!isArchive) {
                        selectedUrl = cand
                        Log.i(TAG, "⚡ Selected INSTANT active stream (non-archive) for ${rootJson["name"]?.jsonPrimitive?.content ?: fileId}")
                        break
                    }
                }

                // If all candidates are in Archive, fallback to highest quality to initiate cloud unfreeze
                if (selectedUrl == null && candidates.isNotEmpty()) {
                    selectedUrl = candidates.first()
                    Log.i(TAG, "❄️ All stream candidates are cold Archive for ${rootJson["name"]?.jsonPrimitive?.content ?: fileId}")
                }

                if (!selectedUrl.isNullOrBlank()) {
                    return@withContext Result.success(selectedUrl)
                }

                Result.failure(IOException("No playable stream URL found for file $fileId"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getDirectStreamUrlForFile error", e)
            Result.failure(e)
        }
    }

    suspend fun checkStreamArchiveStatus(url: String): Boolean = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext false
        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "ANDROID-$PACKAGE_NAME/$CLIENT_VERSION")
                .header("Range", "bytes=0-0")
                .get()
                .build()
            client.newCall(req).execute().use { res ->
                val ossStorage = res.header("x-oss-storage-class") ?: res.header("X-Oss-Storage-Class")
                val cosStorage = res.header("x-cos-storage-class") ?: res.header("X-Cos-Storage-Class")
                val xosErr = res.header("x-xos-err-desc") ?: res.header("X-Xos-Err-Desc")
                val hasArchiveClass = (ossStorage?.contains("Archive", ignoreCase = true) == true) ||
                        (cosStorage?.contains("ARCHIVE", ignoreCase = true) == true)
                val isRestored = xosErr == "0"
                val isArchive = (hasArchiveClass && !isRestored) || (xosErr != null && xosErr != "0" && xosErr.isNotBlank())
                if (isArchive) {
                    Log.i(TAG, "❄️ Probe detected Archive storage class (oss=$ossStorage, cos=$cosStorage, xosErr=$xosErr) for stream")
                } else if (hasArchiveClass && isRestored) {
                    Log.i(TAG, "☀️ Probe detected Archive object is RESTORED/ACTIVE (xosErr=0) for stream")
                }
                isArchive
            }
        } catch (e: Exception) {
            false
        }
    }
}

