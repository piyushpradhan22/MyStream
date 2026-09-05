package com.mystream.app.data.updater

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import com.mystream.app.data.api.SystemFallbackDns

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
    val versionCode: Int = 3,
    val versionName: String = "1.0.2",
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
        .dns(SystemFallbackDns)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    companion object {
        const val GITHUB_OWNER = "piyushpradhan22"
        const val GITHUB_REPO = "MyStream"

        // Real-time un-cached version.json directly from GitHub API
        const val GITHUB_CONTENTS_VERSION_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/contents/version.json?ref=main"

        // Primary: GitHub Releases API
        const val GITHUB_RELEASES_API = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

        // Fallback: Raw version.json
        const val RAW_VERSION_URL = "https://raw.githubusercontent.com/$GITHUB_OWNER/$GITHUB_REPO/main/version.json"
    }

    suspend fun checkForUpdates(): Result<AppUpdateCheckResult> = withContext(Dispatchers.IO) {
        try {
            val currentCode = BuildConfig.VERSION_CODE
            val currentName = BuildConfig.VERSION_NAME
            android.util.Log.d("AppUpdateManager", "Checking updates... Current app: v$currentName (code $currentCode)")

            // 1. Primary: Check GitHub Releases API (directly from git tags & releases)
            val githubResult = tryFetchGitHubRelease(currentCode, currentName)
            if (githubResult != null && githubResult.isUpdateAvailable) {
                android.util.Log.d("AppUpdateManager", "GitHub release update available: v${githubResult.latestVersionName}")
                return@withContext Result.success(githubResult)
            }

            // 2. Secondary: Check GitHub Contents API for version.json (instant live file)
            val contentsResult = tryFetchGitHubContentsVersion(currentCode, currentName)
            if (contentsResult != null && contentsResult.isUpdateAvailable) {
                android.util.Log.d("AppUpdateManager", "GitHub Contents version.json update available: v${contentsResult.latestVersionName}")
                return@withContext Result.success(contentsResult)
            }

            // 3. Tertiary: Check Raw version.json
            val rawResult = tryFetchRawVersionJson(currentCode, currentName)
            if (rawResult != null && rawResult.isUpdateAvailable) {
                android.util.Log.d("AppUpdateManager", "Raw version.json update available: v${rawResult.latestVersionName}")
                return@withContext Result.success(rawResult)
            }

            // If no updates available across all sources, report latest version info
            val fallback = githubResult ?: contentsResult ?: rawResult ?: AppUpdateCheckResult(
                isUpdateAvailable = false,
                currentVersionName = currentName,
                currentVersionCode = currentCode,
                latestVersionName = currentName,
                latestVersionCode = currentCode,
                downloadUrl = "",
                changelog = "You are using the latest version."
            )

            Result.success(fallback.copy(isUpdateAvailable = false))
        } catch (e: Exception) {
            android.util.Log.e("AppUpdateManager", "Error checking updates", e)
            Result.failure(e)
        }
    }

    private fun tryFetchGitHubContentsVersion(currentCode: Int, currentName: String): AppUpdateCheckResult? {
        try {
            val request = Request.Builder()
                .url(GITHUB_CONTENTS_VERSION_URL)
                .header("Accept", "application/vnd.github.v3.raw")
                .header("User-Agent", "MyStream-Android/${BuildConfig.VERSION_NAME}")
                .header("Cache-Control", "no-cache, no-store")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                android.util.Log.w("AppUpdateManager", "GitHub contents returned HTTP ${response.code}")
                return null
            }

            val bodyString = response.body?.string() ?: return null
            val raw = json.decodeFromString<RawVersionConfig>(bodyString)

            val isAvailable = raw.versionCode > currentCode || isVersionStringGreater(raw.versionName, currentName)

            return AppUpdateCheckResult(
                isUpdateAvailable = isAvailable,
                currentVersionName = currentName,
                currentVersionCode = currentCode,
                latestVersionName = raw.versionName,
                latestVersionCode = raw.versionCode,
                downloadUrl = resolveVersionDownloadUrl(raw.versionName, raw.downloadUrl),
                changelog = raw.changelog ?: "New update available."
            )
        } catch (e: Exception) {
            android.util.Log.e("AppUpdateManager", "GitHub contents version fetch error", e)
            return null
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

            // Find APK asset matching device ABI or universal release
            val deviceAbis = android.os.Build.SUPPORTED_ABIS.toList()
            val apkAsset = release.assets.firstOrNull { asset ->
                val name = asset.name.lowercase()
                name.endsWith(".apk") && deviceAbis.any { abi -> name.contains(abi.lowercase()) }
            } ?: release.assets.firstOrNull { it.name.endsWith("universal-release.apk", ignoreCase = true) }
              ?: release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
              ?: release.assets.firstOrNull()

            val rawTag = release.tagName.trim()
            val cleanVersion = rawTag.removePrefix("v").removePrefix("V").trim()
            val downloadUrl = apkAsset?.browserDownloadUrl
                ?: resolveVersionDownloadUrl(cleanVersion, "")

            val latestCode = parseVersionCodeFromTagOrName(cleanVersion)
            val isAvailable = isRemoteVersionNewer(currentName, currentCode, cleanVersion, latestCode)

            val validChangelog = release.body?.takeIf { body ->
                body.isNotBlank() && !body.trim().startsWith("**Full Changelog**")
            } ?: tryFetchRawVersionJson(currentCode, currentName)?.changelog
            ?: release.body?.takeIf { it.isNotBlank() }
            ?: "Performance improvements & bug fixes."

            return AppUpdateCheckResult(
                isUpdateAvailable = isAvailable,
                currentVersionName = currentName,
                currentVersionCode = currentCode,
                latestVersionName = cleanVersion.ifBlank { rawTag },
                latestVersionCode = latestCode,
                downloadUrl = downloadUrl,
                changelog = validChangelog
            )
        } catch (e: Exception) {
            android.util.Log.e("AppUpdateManager", "GitHub release fetch error", e)
            return null
        }
    }

    private fun tryFetchRawVersionJson(currentCode: Int, currentName: String): AppUpdateCheckResult? {
        try {
            val cacheBustUrl = "$RAW_VERSION_URL?t=${System.currentTimeMillis()}"
            val request = Request.Builder()
                .url(cacheBustUrl)
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .header("Pragma", "no-cache")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                android.util.Log.w("AppUpdateManager", "raw version.json returned HTTP ${response.code}")
                return null
            }

            val bodyString = response.body?.string() ?: return null
            val raw = json.decodeFromString<RawVersionConfig>(bodyString)

            val isAvailable = raw.versionCode > currentCode || isVersionStringGreater(raw.versionName, currentName)

            return AppUpdateCheckResult(
                isUpdateAvailable = isAvailable,
                currentVersionName = currentName,
                currentVersionCode = currentCode,
                latestVersionName = raw.versionName,
                latestVersionCode = raw.versionCode,
                downloadUrl = resolveVersionDownloadUrl(raw.versionName, raw.downloadUrl),
                changelog = raw.changelog ?: "New update available."
            )
        } catch (e: Exception) {
            android.util.Log.e("AppUpdateManager", "raw version.json fetch error", e)
            return null
        }
    }

    private fun parseVersionCodeFromTagOrName(version: String): Int {
        val digits = version.filter { it.isDigit() }
        return digits.toIntOrNull() ?: 1
    }

    private fun resolveVersionDownloadUrl(versionName: String, configuredUrl: String): String {
        val cleanVersion = versionName.trim().removePrefix("v").removePrefix("V")
        val expectedTag = "v$cleanVersion"
        val expectedPath = "/releases/download/$expectedTag/"
        if (configuredUrl.isNotBlank() && configuredUrl.contains(expectedPath) && !configuredUrl.endsWith("app-debug.apk")) {
            return configuredUrl
        }
        val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull()?.lowercase().orEmpty()
        val apkName = when {
            abi.contains("arm64") -> "app-arm64-v8a-release.apk"
            abi.contains("v7a") || abi.contains("arm") -> "app-armeabi-v7a-release.apk"
            abi.contains("x86_64") -> "app-x86_64-release.apk"
            else -> "app-universal-release.apk"
        }
        return "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/download/$expectedTag/$apkName"
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

    fun canInstallApk(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                try {
                    val secIntent = Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(secIntent)
                } catch (_: Exception) {}
            }
        }
    }

    fun installApk(apkFile: File): Result<Unit> {
        return try {
            if (!apkFile.exists()) {
                return Result.failure(Exception("APK file not found: ${apkFile.absolutePath}"))
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                openInstallPermissionSettings()
                return Result.failure(Exception("Permission required: Please toggle 'Allow from this source' for MyStream in the Settings screen, then tap Update again."))
            }

            val authority = "${context.packageName}.fileprovider"
            val fileUri: Uri = FileProvider.getUriForFile(context, authority, apkFile)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(fileUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            }

            val resolveLists = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            for (res in resolveLists) {
                try {
                    context.grantUriPermission(res.activityInfo.packageName, fileUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) {}
            }

            context.startActivity(intent)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
