package com.mystream.app.data.api

import android.content.Context
import android.util.Log
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PythonResolveResponse(
    val success: Boolean = false,
    val stream_url: String? = null,
    val file_id: String? = null,
    val name: String? = null,
    val error: String? = null,
    val error_type: String? = null
)

class PikPakPythonBridge(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    companion object {
        private const val TAG = "PikPak_PyBridge"
        @Volatile
        private var isInitialized = false

        fun initPython(context: Context) {
            if (!isInitialized) {
                synchronized(this) {
                    if (!isInitialized) {
                        if (!Python.isStarted()) {
                            Log.i(TAG, "Starting Chaquopy Python runtime...")
                            Python.start(AndroidPlatform(context.applicationContext))
                        }
                        isInitialized = true
                        Log.i(TAG, "Chaquopy Python runtime initialized successfully")
                    }
                }
            }
        }
    }

    init {
        try {
            initPython(context)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Chaquopy Python platform: ${e.message}", e)
        }
    }

    suspend fun resolveStreamDetailsWithPython(
        username: String,
        password: String,
        magnet: String,
        fileId: String? = null,
        fileName: String? = null
    ): Result<PythonResolveResponse> = withContext(Dispatchers.IO) {
        try {
            initPython(context)
            val py = Python.getInstance()
            val module = py.getModule("pikpak_resolver")

            Log.i(TAG, "🐍 Invoking pikpak_resolver.resolve_stream via pikpakapi for $username (fileId=$fileId)...")
            val rawJson = module.callAttr(
                "resolve_stream",
                username,
                password,
                magnet,
                fileId,
                fileName
            ).toString()

            Log.d(TAG, "🐍 Python response: $rawJson")
            val resp = json.decodeFromString<PythonResolveResponse>(rawJson)

            if (resp.success && !resp.stream_url.isNullOrBlank()) {
                Log.i(TAG, "🎉 Python pikpakapi resolved stream successfully: ${resp.stream_url.take(70)}...")
                Result.success(resp)
            } else {
                val errMsg = resp.error ?: "Python resolver returned unsuccessful response"
                Log.w(TAG, "Python pikpakapi resolution failed: $errMsg (type: ${resp.error_type})")
                Result.failure(Exception("Python pikpakapi: $errMsg"))
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Python pikpakapi invocation threw exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun resolveStreamWithPython(
        username: String,
        password: String,
        magnet: String,
        fileId: String? = null,
        fileName: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        resolveStreamDetailsWithPython(username, password, magnet, fileId, fileName).map { it.stream_url.orEmpty() }
    }
}
