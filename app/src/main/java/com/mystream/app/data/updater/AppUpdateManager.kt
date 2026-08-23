package com.mystream.app.data.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.mystream.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

@Serializable
data class GitHubReleaseAsset(
    val name: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
    val size: Long = 0L
)

@Serializable
data class GitHubReleaseResponse(
    @SerialName("tag_name") val tagName: String = "",
    val name: String? = null,
    val body: String? = null,
    val assets: List<GitHubReleaseAsset> = emptyList(),
    val prerelease: Boolean = false
)

@Serializable
data class RawVersionConfig(
    val versionCode: Int = 1,
    val versionName: String = "1.0.0",
    val downloadUrl: String = "",
    val changelog: String? = null,
    val mandatory: Boolean = false
)

data class AppUpdateCheckResult(
    val isUpdateAvailable: Boolean,
    val currentVersionName: String,
    val currentVersionCode: Int,
    val latestVersionName: String,
    val latestVersionCode: Int,
    val downloadUrl: String,
    val changelog: String
)

class AppUpdateManager(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    companion object {
        const val GITHUB_OWNER = "piyushpradhan22"
        const val GITHUB_REPO = "MyStream"

        // Primary: GitHub Releases API
        const val GITHUB_RELEASES_API = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

        // Fallback: Raw version.json on main branch
        const val RAW_VERSION_URL = "https://raw.githubusercontent.com/$GITHUB_OWNER/$GITHUB_REPO/main/version.json"
    }

    suspend fun checkForUpdates(): Result<AppUpdateCheckResult> = withContext(Dispatchers.IO) {
        try {
            val currentCode = BuildConfig.VERSION_CODE
            val currentName = BuildConfig.VERSION_NAME

            // 1. Try fetching from GitHub Releases API
            val githubResult = tryFetchGitHubRelease(currentCode, currentName)
            if (githubResult != null) {
                return@withContext Result.success(githubResult)
            }

            // 2. Fallback to raw version.json
            val rawResult = tryFetchRawVersionJson(currentCode, currentName)
            if (rawResult != null) {
                return@withContext Result.success(rawResult)
            }

            // If no releases are published yet on GitHub repository, app is on latest build
            Result.success(
                AppUpdateCheckResult(
                    isUpdateAvailable = false,
                    currentVersionName = currentName,
                    currentVersionCode = currentCode,
                    latestVersionName = currentName,
                    latestVersionCode = currentCode,
                    downloadUrl = "",
                    changelog = "You are using the latest version."
                )
            )
        } catch (e: Exception) {
            Result.success(
                AppUpdateCheckResult(
                    isUpdateAvailable = false,
                    currentVersionName = BuildConfig.VERSION_NAME,
                    currentVersionCode = BuildConfig.VERSION_CODE,
                    latestVersionName = BuildConfig.VERSION_NAME,
                    latestVersionCode = BuildConfig.VERSION_CODE,
                    downloadUrl = "",
                    changelog = "You are on the latest version."
                )
            )
        }
    }

    private fun tryFetchGitHubRelease(currentCode: Int, currentName: String): AppUpdateCheckResult? {
        try {
            val request = Request.Builder()
                .url(GITHUB_RELEASES_API)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "MyStream-Android/${BuildConfig.VERSION_NAME}")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null

            val bodyString = response.body?.string() ?: return null
            val release = json.decodeFromString<GitHubReleaseResponse>(bodyString)

            // Find APK asset
            val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                ?: release.assets.firstOrNull()

            val rawTag = release.tagName.trim()
            val cleanVersion = rawTag.removePrefix("v").removePrefix("V").trim()
            val downloadUrl = apkAsset?.browserDownloadUrl
                ?: "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/download/$rawTag/app-release.apk"

            // Parse version code or compare semantic versions
            val latestCode = parseVersionCodeFromTagOrName(cleanVersion)
            val isAvailable = isRemoteVersionNewer(currentName, currentCode, cleanVersion, latestCode)

            return AppUpdateCheckResult(
                isUpdateAvailable = isAvailable,
                currentVersionName = currentName,
                currentVersionCode = currentCode,
                latestVersionName = cleanVersion.ifBlank { rawTag },
                latestVersionCode = latestCode,
                downloadUrl = downloadUrl,
                changelog = release.body?.takeIf { it.isNotBlank() } ?: release.name ?: "Performance improvements & bug fixes."
            )
        } catch (e: Exception) {
            return null
        }
    }

    private fun tryFetchRawVersionJson(currentCode: Int, currentName: String): AppUpdateCheckResult? {
        try {
            val request = Request.Builder()
                .url(RAW_VERSION_URL)
                .header("Cache-Control", "no-cache")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null

            val bodyString = response.body?.string() ?: return null
            val raw = json.decodeFromString<RawVersionConfig>(bodyString)

            val isAvailable = raw.versionCode > currentCode || isVersionStringGreater(raw.versionName, currentName)

            return AppUpdateCheckResult(
                isUpdateAvailable = isAvailable,
                currentVersionName = currentName,
                currentVersionCode = currentCode,
                latestVersionName = raw.versionName,
                latestVersionCode = raw.versionCode,
                downloadUrl = raw.downloadUrl,
                changelog = raw.changelog ?: "New update available."
            )
        } catch (e: Exception) {
            return null
        }
    }

    private fun parseVersionCodeFromTagOrName(version: String): Int {
        val digits = version.filter { it.isDigit() }
        return digits.toIntOrNull() ?: 1
    }

    private fun isRemoteVersionNewer(currentName: String, currentCode: Int, remoteName: String, remoteCode: Int): Boolean {
        if (remoteCode > currentCode && remoteCode > 1) return true
        return isVersionStringGreater(remoteName, currentName)
    }

    private fun isVersionStringGreater(remote: String, current: String): Boolean {
        val rParts = remote.split(".").mapNotNull { it.filter { c -> c.isDigit() }.toIntOrNull() }
        val cParts = current.split(".").mapNotNull { it.filter { c -> c.isDigit() }.toIntOrNull() }
        val maxLen = maxOf(rParts.size, cParts.size)
        for (i in 0 until maxLen) {
            val r = rParts.getOrElse(i) { 0 }
            val c = cParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    suspend fun downloadApk(
        downloadUrl: String,
        onProgress: (percent: Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(downloadUrl)
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to download APK: HTTP ${response.code}"))
            }

            val body = response.body ?: return@withContext Result.failure(Exception("Empty download response body"))
            val totalBytes = body.contentLength()

            val destinationDir = File(context.cacheDir, "updates")
            if (!destinationDir.exists()) destinationDir.mkdirs()

            val apkFile = File(destinationDir, "MyStream_update.apk")
            if (apkFile.exists()) apkFile.delete()

            body.byteStream().use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    var totalRead: Long = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (totalBytes > 0) {
                            val percent = ((totalRead * 100) / totalBytes).toInt().coerceIn(0, 100)
                            onProgress(percent)
                        }
                    }
                    output.flush()
                }
            }

            onProgress(100)
            Result.success(apkFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun installApk(apkFile: File): Result<Unit> {
        return try {
            if (!apkFile.exists()) {
                return Result.failure(Exception("APK file not found: ${apkFile.absolutePath}"))
            }

            val authority = "${context.packageName}.fileprovider"
            val fileUri: Uri = FileProvider.getUriForFile(context, authority, apkFile)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(fileUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
