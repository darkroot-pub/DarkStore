package com.example.utils

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
    /** Change this if the server's address ever changes. No trailing slash. */
    const val BASE_URL = "https://dark-darkstore.wisp.uno"

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
        val started = System.currentTimeMillis()
        try {
            client.newCall(Request.Builder().url("$BASE_URL/health").get().build()).execute().use { r ->
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
            val req = Request.Builder().url("$BASE_URL/status").header("Authorization", "Bearer $idToken").get().build()
            client.newCall(req).execute().use { r ->
                if (r.isSuccessful) JSONObject(r.body?.string().orEmpty()) else null
            }
        } catch (e: Exception) {
            null
        }
    }

    /** POST an admin action. Returns (success, message). */
    suspend fun action(path: String, idToken: String): Pair<Boolean, JSONObject?> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("$BASE_URL$path")
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
        try {
            val req = Request.Builder().url("$BASE_URL$path")
                .header("Authorization", "Bearer $idToken")
                .post(body.toString().toRequestBody("application/json".toMediaType())).build()
            client.newCall(req).execute().use { r ->
                try { JSONObject(r.body?.string().orEmpty()) } catch (e: Exception) { null }
            }
        } catch (e: Exception) {
            null
        }
    }
}
