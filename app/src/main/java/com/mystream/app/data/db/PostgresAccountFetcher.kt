package com.mystream.app.data.db

import android.util.Log
import com.mystream.app.data.api.PikPakApiClient
import com.mystream.app.data.model.StremioStreamSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Arrays
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

data class PikPakV2Record(
    val imdbId: String,
    val quality: String,
    val title: String,
    val filename: String,
    val fileId: String,
    val size: String,
    val fileExtension: String,
    val infoHash: String,
    val type: String,
    val username: String,
    val encodedToken: String,
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val deviceId: String,
    val loginTime: Double,
    val baseFileId: String
)

object PostgresAccountFetcher {

    private const val TAG = "MyStream_DB"

    private data class PgConnectionConfig(
        val host: String,
        val port: Int,
        val user: String,
        val pass: String,
        val db: String
    )

    private fun md5Hex(data: ByteArray): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(data)
        val sb = StringBuilder()
        for (b in digest) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    private fun parseUrl(rawUrl: String): PgConnectionConfig {
        val cleaned = rawUrl.trim()
        val pattern = Regex("""^(?:postgres(?:ql)?://)(?:([^:]+)(?::([^@]+))?@)?([^:/]+)(?::([0-9]+))?(?:/([^?]+))?(?:\?(.*))?$""")
        val match = pattern.find(cleaned)

        if (match != null) {
            val user = match.groupValues[1].ifBlank { "postgres" }
            val pass = match.groupValues[2]
            val host = match.groupValues[3].ifBlank { "localhost" }
            val port = match.groupValues[4].toIntOrNull() ?: 5432
            val dbName = match.groupValues[5].ifBlank { "defaultdb" }
            return PgConnectionConfig(host, port, user, pass, dbName)
        }

        return PgConnectionConfig("localhost", 5432, "postgres", "", "defaultdb")
    }

    private fun executePgQuery(config: PgConnectionConfig, query: String): List<List<String>> {
        Log.d(TAG, "Connecting to PostgreSQL ${config.host}:${config.port}/${config.db}...")
        val socket = Socket(config.host, config.port)
        socket.soTimeout = 12000
        var inStream = DataInputStream(socket.getInputStream())
        var outStream = DataOutputStream(socket.getOutputStream())

        // 1. SSLRequest
        outStream.writeInt(8)
        outStream.writeInt(80877103)
        outStream.flush()

        val sslRes = inStream.readByte().toInt().toChar()
        if (sslRes == 'S') {
            val trustAll = arrayOf<javax.net.ssl.TrustManager>(object : X509TrustManager {
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {}
            })
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAll, SecureRandom())
            val factory = sslContext.socketFactory

            val sslSocket = factory.createSocket(socket, config.host, config.port, true) as SSLSocket
            sslSocket.startHandshake()
            inStream = DataInputStream(sslSocket.getInputStream())
            outStream = DataOutputStream(sslSocket.getOutputStream())
            Log.d(TAG, "SSL Handshake successful")
        }

        // 2. StartupMessage
        val paramBaos = ByteArrayOutputStream()
        paramBaos.write("user\u0000".toByteArray())
        paramBaos.write("${config.user}\u0000".toByteArray())
        paramBaos.write("database\u0000".toByteArray())
        paramBaos.write("${config.db}\u0000".toByteArray())
        paramBaos.write("client_encoding\u0000UTF8\u0000".toByteArray())
        paramBaos.write(0)

        val params = paramBaos.toByteArray()
        outStream.writeInt(8 + params.size)
        outStream.writeInt(196608) // Protocol 3.0
        outStream.write(params)
        outStream.flush()

        // 3. Auth loop
        while (true) {
            val type = inStream.readByte().toInt().toChar()
            val len = inStream.readInt() - 4
            val payload = ByteArray(len)
            inStream.readFully(payload)

            if (type == 'R') {
                val authType = ((payload[0].toInt() and 0xFF) shl 24) or
                        ((payload[1].toInt() and 0xFF) shl 16) or
                        ((payload[2].toInt() and 0xFF) shl 8) or
                        (payload[3].toInt() and 0xFF)
                if (authType == 0) {
                    Log.d(TAG, "Authentication OK")
                } else if (authType == 3) {
                    val pb = "${config.pass}\u0000".toByteArray()
                    outStream.writeByte('p'.code)
                    outStream.writeInt(4 + pb.size)
                    outStream.write(pb)
                    outStream.flush()
                } else if (authType == 5) {
                    val salt = Arrays.copyOfRange(payload, 4, 8)
                    val step1 = md5Hex((config.pass + config.user).toByteArray())
                    val step1Bytes = step1.toByteArray()
                    val concat = ByteArray(step1Bytes.size + salt.size)
                    System.arraycopy(step1Bytes, 0, concat, 0, step1Bytes.size)
                    System.arraycopy(salt, 0, concat, step1Bytes.size, salt.size)
                    val step2 = "md5" + md5Hex(concat)
                    val pb = "$step2\u0000".toByteArray()
                    outStream.writeByte('p'.code)
                    outStream.writeInt(4 + pb.size)
                    outStream.write(pb)
                    outStream.flush()
                    Log.d(TAG, "Sent MD5 credentials")
                }
            } else if (type == 'E') {
                val errorMsg = String(payload)
                socket.close()
                Log.e(TAG, "PostgreSQL Server Error: $errorMsg")
                throw Exception("Postgres Error: $errorMsg")
            } else if (type == 'Z') {
                break
            }
        }

        // 4. Send Query
        Log.d(TAG, "Executing Query: $query")
        val qb = "$query\u0000".toByteArray()
        outStream.writeByte('Q'.code)
        outStream.writeInt(4 + qb.size)
        outStream.write(qb)
        outStream.flush()

        val rows = mutableListOf<List<String>>()
        while (true) {
            val type = inStream.readByte().toInt().toChar()
            val len = inStream.readInt() - 4
            val payload = ByteArray(len)
            inStream.readFully(payload)

            if (type == 'D') {
                val dis = DataInputStream(ByteArrayInputStream(payload))
                val numCols = dis.readShort().toInt()
                val row = mutableListOf<String>()
                for (i in 0 until numCols) {
                    val colLen = dis.readInt()
                    if (colLen >= 0) {
                        val col = ByteArray(colLen)
                        dis.readFully(col)
                        row.add(String(col))
                    } else {
                        row.add("")
                    }
                }
                rows.add(row)
            } else if (type == 'Z') {
                break
            }
        }

        socket.close()
        Log.d(TAG, "Query returned ${rows.size} rows")
        return rows
    }

    suspend fun fetchUsernames(postgresUrl: String): Result<List<String>> = withContext(Dispatchers.IO) {
        if (postgresUrl.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("PostgreSQL URL is empty"))
        }

        try {
            val config = parseUrl(postgresUrl)
            val emails = mutableListOf<String>()

            val candidateQueries = listOf(
                "SELECT email FROM email WHERE used IS NOT TRUE AND email LIKE '%@gmail.com' ORDER BY RANDOM() LIMIT 20",
                "SELECT email FROM email WHERE email LIKE '%@gmail.com' ORDER BY RANDOM() LIMIT 20",
                "SELECT email FROM email WHERE email IS NOT NULL AND email != '' ORDER BY RANDOM() LIMIT 20",
                "SELECT email FROM selected_email WHERE email IS NOT NULL AND email != '' ORDER BY RANDOM() LIMIT 20"
            )

            for (q in candidateQueries) {
                try {
                    val rows = executePgQuery(config, q)
                    for (row in rows) {
                        if (row.isNotEmpty() && row[0].isNotBlank()) {
                            emails.add(row[0].trim())
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Candidate query failed: $q -> ${e.message}")
                }
                if (emails.isNotEmpty()) break
            }

            if (emails.isNotEmpty()) {
                Log.i(TAG, "Successfully loaded ${emails.size} dynamic accounts from database: ${emails.take(3)}...")
                Result.success(emails.distinct())
            } else {
                Result.failure(Exception("Connected to database, but no emails found in email/selected_email tables."))
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchUsernames error", e)
            Result.failure(e)
        }
    }

    suspend fun markEmailAsUsed(postgresUrl: String, email: String) = withContext(Dispatchers.IO) {
        if (postgresUrl.isBlank() || email.isBlank()) return@withContext
        try {
            val config = parseUrl(postgresUrl)
            val safeEmail = email.replace("'", "''")
            Log.d(TAG, "Marking email as used in DB: $safeEmail")
            executePgQuery(config, "UPDATE email SET used = true WHERE email = '$safeEmail'")
        } catch (e: Exception) {
            Log.w(TAG, "markEmailAsUsed error for $email", e)
        }
    }

    suspend fun getPikpakV2Records(postgresUrl: String, imdbId: String): List<PikPakV2Record> = withContext(Dispatchers.IO) {
        if (postgresUrl.isBlank() || imdbId.isBlank()) return@withContext emptyList()

        val list = mutableListOf<PikPakV2Record>()
        try {
            val config = parseUrl(postgresUrl)
            val safeId = imdbId.replace("'", "''")
            val query = "SELECT imdb_id, quality, title, filename, file_id, size, file_extension, \"infoHash\", type, username, encoded_token, access_token, refresh_token, user_id, device_id, login_time, base_file_id FROM pikpak_v2 WHERE imdb_id = '$safeId'"
            val rows = executePgQuery(config, query)
            for (r in rows) {
                if (r.size >= 17 && r[4].isNotBlank()) {
                    list.add(
                        PikPakV2Record(
                            imdbId = r[0],
                            quality = r[1],
                            title = r[2],
                            filename = r[3],
                            fileId = r[4],
                            size = r[5],
                            fileExtension = r[6],
                            infoHash = r[7],
                            type = r[8],
                            username = r[9],
                            encodedToken = r[10],
                            accessToken = r[11],
                            refreshToken = r[12],
                            userId = r[13],
                            deviceId = r[14],
                            loginTime = r[15].toDoubleOrNull() ?: 0.0,
                            baseFileId = r[16]
                        )
                    )
                }
            }
            Log.i(TAG, "Found ${list.size} records in pikpak_v2 table for $imdbId")
        } catch (e: Exception) {
            Log.w(TAG, "getPikpakV2Records error for $imdbId", e)
        }
        list
    }

    suspend fun savePikpakV2Record(postgresUrl: String, record: PikPakV2Record) = withContext(Dispatchers.IO) {
        if (postgresUrl.isBlank() || record.fileId.isBlank()) return@withContext
        try {
            val config = parseUrl(postgresUrl)
            val currentTime = System.currentTimeMillis() / 1000.0
            val safeImdb = record.imdbId.replace("'", "''")
            val safeQuality = record.quality.replace("'", "''")
            val safeTitle = record.title.replace("'", "''")
            val safeFilename = record.filename.replace("'", "''")
            val safeFileId = record.fileId.replace("'", "''")
            val safeSize = record.size.replace("'", "''")
            val safeExt = record.fileExtension.replace("'", "''")
            val safeHash = record.infoHash.replace("'", "''")
            val safeType = record.type.replace("'", "''")
            val safeUser = record.username.replace("'", "''")
            val safeEnc = record.encodedToken.replace("'", "''")
            val safeAcc = record.accessToken.replace("'", "''")
            val safeRef = record.refreshToken.replace("'", "''")
            val safeUid = record.userId.replace("'", "''")
            val safeDev = record.deviceId.replace("'", "''")
            val safeBase = record.baseFileId.replace("'", "''")

            val query = """
                INSERT INTO pikpak_v2 (time, imdb_id, quality, title, filename, file_id, size, file_extension, "infoHash", type, username, encoded_token, access_token, refresh_token, user_id, device_id, login_time, base_file_id)
                VALUES ($currentTime, '$safeImdb', '$safeQuality', '$safeTitle', '$safeFilename', '$safeFileId', '$safeSize', '$safeExt', '$safeHash', '$safeType', '$safeUser', '$safeEnc', '$safeAcc', '$safeRef', '$safeUid', '$safeDev', '${record.loginTime}', '$safeBase')
            """.trimIndent()
            executePgQuery(config, query)
            Log.i(TAG, "Successfully inserted record into pikpak_v2 table for [${record.imdbId}] ${record.quality}")
        } catch (e: Exception) {
            Log.w(TAG, "savePikpakV2Record error", e)
        }
    }

    suspend fun getPikpakTorrents(postgresUrl: String, imdbId: String): List<PikPakTorrentRecord> = withContext(Dispatchers.IO) {
        val list = mutableListOf<PikPakTorrentRecord>()
        if (postgresUrl.isBlank() || imdbId.isBlank()) return@withContext list
        try {
            val config = parseUrl(postgresUrl)
            val safeId = imdbId.replace("'", "''")
            val query = """
                SELECT imdb_id, type, quality, title, "infoHash", size, filename
                FROM pikpak_torrents
                WHERE imdb_id = '$safeId'
            """.trimIndent()
            val rows = executePgQuery(config, query)
            for (r in rows) {
                if (r.size >= 7 && r[4].isNotBlank()) {
                    list.add(
                        PikPakTorrentRecord(
                            imdbId = r[0],
                            type = r[1],
                            quality = r[2],
                            title = r[3],
                            infoHash = r[4],
                            size = r[5].toDoubleOrNull() ?: 0.0,
                            filename = r[6]
                        )
                    )
                }
            }
            Log.i(TAG, "Found ${list.size} records in pikpak_torrents for $imdbId")
        } catch (e: Exception) {
            Log.w(TAG, "getPikpakTorrents error for $imdbId", e)
        }
        list
    }

    suspend fun savePikpakTorrents(postgresUrl: String, torrents: List<PikPakTorrentRecord>) = withContext(Dispatchers.IO) {
        if (postgresUrl.isBlank() || torrents.isEmpty()) return@withContext
        try {
            val config = parseUrl(postgresUrl)
            val valueRows = torrents.map { t ->
                val safeImdb = t.imdbId.replace("'", "''")
                val safeType = t.type.replace("'", "''")
                val safeQuality = t.quality.replace("'", "''")
                val safeTitle = t.title.replace("'", "''")
                val safeHash = t.infoHash.replace("'", "''")
                val safeFilename = t.filename.replace("'", "''")
                "('$safeImdb', '$safeType', '$safeQuality', '$safeTitle', '$safeHash', ${t.size}, '$safeFilename')"
            }
            // Batch insert up to 40 per statement for high performance
            valueRows.chunked(40).forEach { chunk ->
                val query = """
                    INSERT INTO pikpak_torrents (imdb_id, type, quality, title, "infoHash", size, filename)
                    VALUES ${chunk.joinToString(", ")}
                """.trimIndent()
                executePgQuery(config, query)
            }
            Log.i(TAG, "Batch saved ${torrents.size} torrents to pikpak_torrents table")
        } catch (e: Exception) {
            Log.w(TAG, "savePikpakTorrents error", e)
        }
    }

    suspend fun clearPikpakV2Record(postgresUrl: String, imdbId: String) = withContext(Dispatchers.IO) {
        if (postgresUrl.isBlank() || imdbId.isBlank()) return@withContext
        try {
            val config = parseUrl(postgresUrl)
            val safeId = imdbId.replace("'", "''")
            executePgQuery(config, "DELETE FROM pikpak_v2 WHERE imdb_id = '$safeId'")
            executePgQuery(config, "DELETE FROM pikpak_torrents WHERE imdb_id = '$safeId'")
        } catch (e: Exception) {
            Log.w(TAG, "clearPikpakV2Record error", e)
        }
    }
}

data class PikPakTorrentRecord(
    val imdbId: String,
    val type: String,
    val quality: String,
    val title: String,
    val infoHash: String,
    val size: Double,
    val filename: String
)
