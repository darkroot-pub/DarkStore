package com.darkstore.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Client for the Dark Store notifier's HTTP status API (Wispbyte). */
object NotifierServer {
    /** Built-in fallback address. No trailing slash. */
    const val DEFAULT_BASE_URL = "https://darkstore.bot-keep.xyz"

    /**
     * The address currently in use. Changeable WITHOUT an app update: set the text
     * value of `notifierConfig/baseUrl` in Firebase Realtime Database (e.g.
     * "https://new-address.example.com"). The app reads it every time the Server
     * tab checks health, and falls back to [DEFAULT_BASE_URL] if it is missing/invalid.
     */
    @Volatile private var loaded = false

    @Volatile
    var baseUrl: String = DEFAULT_BASE_URL
        private set

    private val TRUSTED_HOST_SUFFIXES = listOf("bot-keep.xyz", "wisp.uno")

    private fun isTrustedHost(url: String): Boolean {
        val u = try { java.net.URI(url) } catch (e: Exception) { return false }
        val host = u.host?.lowercase() ?: return false
        return u.scheme == "https" && TRUSTED_HOST_SUFFIXES.any { host == it || host.endsWith(".$it") }
    }

    /** Kept for existing callers. */
    val BASE_URL: String get() = baseUrl

    /** Reads `notifierConfig/baseUrl` from RTDB (public read) and updates [baseUrl]. */
    suspend fun refreshBaseUrl() = withContext(Dispatchers.IO) {
        try {
            val url = "${com.darkstore.data.FirebaseAuthService.RTDB_URL}notifierConfig/baseUrl.json"
            client.newCall(Request.Builder().url(url).get().build()).execute().use { r ->
                if (!r.isSuccessful) return@use
                val v = r.body?.string().orEmpty().trim().trim('"').trim().trimEnd('/')
                loaded = true
                // SECURITY: this address receives the admin's ID token as a Bearer header, and the
                // value is read from a database node. Only accept HTTPS on a trusted host so a
                // tampered/poisoned node can never redirect the admin token to an attacker.
                baseUrl = if (isTrustedHost(v)) v else DEFAULT_BASE_URL
            }
        } catch (e: Exception) {
            // keep whatever we had (default or last good value)
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    data class Health(
        val online: Boolean,
        val latencyMs: Long = 0,
        val version: String = "",
        val uptimeS: Long = 0,
        val error: String = ""
    )

    /** Public ping — no login needed. Anything but a valid JSON 200 counts as OFFLINE. */
    suspend fun health(): Health = withContext(Dispatchers.IO) {
        var started = System.currentTimeMillis()
        try {
            refreshBaseUrl()
            started = System.currentTimeMillis()
            client.newCall(Request.Builder().url("$baseUrl/health").get().build()).execute().use { r ->
                val ms = System.currentTimeMillis() - started
                val body = r.body?.string().orEmpty()
                if (!r.isSuccessful) return@use Health(false, ms, error = "HTTP ${r.code}")
                val j = JSONObject(body)
                if (!j.optBoolean("ok")) return@use Health(false, ms, error = "Unexpected reply")
                Health(true, ms, j.optString("version"), j.optLong("uptime_s"))
            }
        } catch (e: java.net.UnknownHostException) {
            Health(false, error = "Address not found")
        } catch (e: java.net.SocketTimeoutException) {
            Health(false, error = "Timed out")
        } catch (e: Exception) {
            Health(false, error = e.message?.take(60) ?: "No response")
        }
    }

    /** Admin-only details. Returns null on any failure (401 = not an admin / expired token). */
    suspend fun status(idToken: String): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("$baseUrl/status").header("Authorization", "Bearer $idToken").get().build()
            client.newCall(req).execute().use { r ->
                if (r.isSuccessful) JSONObject(r.body?.string().orEmpty()) else null
            }
        } catch (e: Exception) {
            null
        }
    }

    /** POST an admin action. Returns (success, message). */
    suspend fun action(path: String, idToken: String): Pair<Boolean, JSONObject?> = withContext(Dispatchers.IO) {
        if (!loaded) refreshBaseUrl()
        try {
            val req = Request.Builder().url("$baseUrl$path")
                .header("Authorization", "Bearer $idToken")
                .post("{}".toRequestBody(null)).build()
            client.newCall(req).execute().use { r ->
                val j = try { JSONObject(r.body?.string().orEmpty()) } catch (e: Exception) { null }
                Pair(r.isSuccessful && j?.optBoolean("ok") == true, j)
            }
        } catch (e: Exception) {
            Pair(false, null)
        }
    }

    /** POST a JSON body to an admin endpoint. Returns the parsed reply (or null if unreachable). */
    suspend fun postJson(path: String, idToken: String, body: JSONObject): JSONObject? = withContext(Dispatchers.IO) {
        if (!loaded) refreshBaseUrl()
        try {
            val req = Request.Builder().url("$baseUrl$path")
                .header("Authorization", "Bearer $idToken")
                .post(body.toString().toRequestBody("application/json".toMediaType())).build()
            client.newCall(req).execute().use { r ->
                try { JSONObject(r.body?.string().orEmpty()) } catch (e: Exception) { null }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Streams a file to the DarkStore server (raw bytes, with progress). The server - not the app - holds the
     * storage API key. Returns the server's JSON reply, or null if it can't be reached.
     */
    suspend fun uploadFile(
        path: String, idToken: String, fileName: String, file: java.io.File, onProgress: (Float) -> Unit
    ): JSONObject? = withContext(Dispatchers.IO) {
        if (!loaded) refreshBaseUrl()
        try {
            val total = file.length()
            val body = object : okhttp3.RequestBody() {
                override fun contentType() = "application/octet-stream".toMediaType()
                override fun contentLength() = total
                override fun writeTo(sink: okio.BufferedSink) {
                    file.inputStream().use { input ->
                        val buf = ByteArray(64 * 1024)
                        var sent = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            sink.write(buf, 0, n)
                            sent += n
                            onProgress(if (total > 0) sent.toFloat() / total else 1f)
                        }
                    }
                }
            }
            val req = Request.Builder().url("$baseUrl$path")
                .header("Authorization", "Bearer $idToken")
                .header("X-File-Name", fileName)
                .post(body).build()
            val longClient = client.newBuilder()
                .writeTimeout(15, TimeUnit.MINUTES).readTimeout(15, TimeUnit.MINUTES).callTimeout(0, TimeUnit.SECONDS).build()
            longClient.newCall(req).execute().use { r ->
                try { JSONObject(r.body?.string().orEmpty()) } catch (e: Exception) { null }
            }
        } catch (e: Exception) {
            null
        }
    }
}
