package com.example.data

import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers

object FirebaseService {
    private const val TAG = "FirebaseService"

    @Volatile
    var activeToken: String = ""

    fun getTokenParam(): String {
        val token = activeToken
        return if (token.isNotBlank() && !token.startsWith("sim_") && !token.startsWith("fake_")) {
            "?auth=$token"
        } else {
            ""
        }
    }

    fun isRealToken(): Boolean {
        val token = activeToken
        return token.isNotBlank() && !token.startsWith("sim_") && !token.startsWith("fake_")
    }

    var RTDB_URL = "https://dark-store-6836d-default-rtdb.asia-southeast1.firebasedatabase.app/"

    fun updateConfig(projId: String, rtdb: String) {
        if (rtdb.isNotBlank()) {
            var url = rtdb.trim()
            if (!url.endsWith("/")) url += "/"
            RTDB_URL = url
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // ----------------------------------------------------
    // REALTIME DATABASE PARSER & ENDPOINTS
    // ----------------------------------------------------

    fun parseFirebaseResponse(jsonStr: String?): List<AppEntity> {
        if (jsonStr.isNullOrBlank() || jsonStr == "null" || jsonStr == "{}") {
            return emptyList()
        }

        // Try Pattern 1: Map<String, AppEntity>
        try {
            val mapType = Types.newParameterizedType(Map::class.java, String::class.java, AppEntity::class.java)
            val adapter = moshi.adapter<Map<String, AppEntity>>(mapType)
            val map = adapter.fromJson(jsonStr)
            if (map != null) {
                return map.values.toList()
            }
        } catch (e: Exception) {
            Log.d(TAG, "Parsing RTDB response as map failed, trying as sparse list...")
        }

        // Try Pattern 2: List<AppEntity?>
        try {
            val listType = Types.newParameterizedType(List::class.java, AppEntity::class.java)
            val adapter = moshi.adapter<List<AppEntity?>>(listType)
            val list = adapter.fromJson(jsonStr)
            if (list != null) {
                return list.filterNotNull()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parsing RTDB response as list failed: ${e.message}", e)
        }

        return emptyList()
    }

    private fun fetchAppsFromRTDB(): List<AppEntity> {
        val tokenParam = getTokenParam()
        val request = Request.Builder()
            .url("${RTDB_URL}apps.json$tokenParam")
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "RTDB HTTP error: ${response.code}")
                    return emptyList()
                }
                val bodyStr = response.body?.string()
                Log.d(TAG, "RTDB Response: $bodyStr")
                parseFirebaseResponse(bodyStr)
            }
        } catch (e: Exception) {
            Log.e(TAG, "RTDB Network error: ${e.message}", e)
            emptyList()
        }
    }

    private fun saveAppToRTDB(app: AppEntity): Boolean {
        val adapter = moshi.adapter(AppEntity::class.java)
        val jsonStr = adapter.toJson(app)

        val body = jsonStr.toRequestBody(jsonMediaType)
        val tokenParam = getTokenParam()
        val request = Request.Builder()
            .url("${RTDB_URL}apps/${app.id}.json$tokenParam")
            .put(body)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d(TAG, "Successfully updated App ${app.name} in RTDB")
                    true
                } else {
                    Log.e(TAG, "Failed update App in RTDB: code ${response.code}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception saving RTDB app: ${e.message}", e)
            false
        }
    }

    private fun deleteAppFromRTDB(id: String): Boolean {
        val tokenParam = getTokenParam()
        val request = Request.Builder()
            .url("${RTDB_URL}apps/$id.json$tokenParam")
            .delete()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d(TAG, "Successfully deleted App $id from RTDB")
                    true
                } else {
                    Log.e(TAG, "Failed delete App from RTDB: code ${response.code}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception deleting RTDB app: ${e.message}", e)
            false
        }
    }

    // ----------------------------------------------------
    // DUAL DYNAMIC PUBLIC METHODS
    // ----------------------------------------------------

    suspend fun fetchApps(): List<AppEntity> = coroutineScope {
        async(Dispatchers.IO) { fetchAppsFromRTDB() }.await()
    }

    fun saveApp(app: AppEntity): Boolean {
        return saveAppToRTDB(app)
    }

    fun deleteApp(id: String): Boolean {
        return deleteAppFromRTDB(id)
    }

    // ----------------------------------------------------
    // NOTICES & ANNOUNCEMENTS CAPABILITIES
    // ----------------------------------------------------

    fun parseRTDBNoticesResponse(jsonStr: String?): List<NoticeEntity> {
        if (jsonStr.isNullOrBlank() || jsonStr == "null" || jsonStr == "{}") {
            return emptyList()
        }
        try {
            val mapType = Types.newParameterizedType(Map::class.java, String::class.java, NoticeEntity::class.java)
            val adapter = moshi.adapter<Map<String, NoticeEntity>>(mapType)
            val map = adapter.fromJson(jsonStr)
            if (map != null) {
                return map.values.toList()
            }
        } catch (e: Exception) {
            Log.d(TAG, "Parsing RTDB notices response as Map failed, trying as List...")
        }

        try {
            val listType = Types.newParameterizedType(List::class.java, NoticeEntity::class.java)
            val adapter = moshi.adapter<List<NoticeEntity?>>(listType)
            val list = adapter.fromJson(jsonStr)
            if (list != null) {
                return list.filterNotNull()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parsing RTDB notices response as List failed: ${e.message}", e)
        }
        return emptyList()
    }

    private fun fetchNoticesFromRTDB(): List<NoticeEntity> {
        val tokenParam = getTokenParam()
        val request = Request.Builder()
            .url("${RTDB_URL}notices.json$tokenParam")
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "RTDB notices HTTP error: ${response.code}")
                    return emptyList()
                }
                val bodyStr = response.body?.string()
                Log.d(TAG, "RTDB notices response: $bodyStr")
                parseRTDBNoticesResponse(bodyStr)
            }
        } catch (e: Exception) {
            Log.e(TAG, "RTDB notices network error: ${e.message}", e)
            emptyList()
        }
    }

    private fun saveNoticeToRTDB(notice: NoticeEntity): Pair<Boolean, String> {
        val adapter = moshi.adapter(NoticeEntity::class.java)
        val jsonStr = adapter.toJson(notice)
        val body = jsonStr.toRequestBody(jsonMediaType)
        val tokenParam = getTokenParam()
        val request = Request.Builder()
            .url("${RTDB_URL}notices/${notice.id}.json$tokenParam")
            .put(body)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d(TAG, "Successfully updated Notice ${notice.title} in RTDB")
                    Pair(true, "RTDB: Success")
                } else {
                    val code = response.code
                    val msg = response.body?.string()?.take(100) ?: ""
                    Log.e(TAG, "Failed update Notice in RTDB: code $code, msg: $msg")
                    Pair(false, "RTDB code $code ($msg)")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception saving RTDB notice: ${e.message}", e)
            Pair(false, "RTDB error: ${e.message}")
        }
    }

    suspend fun fetchNotices(): List<NoticeEntity> = coroutineScope {
        async(Dispatchers.IO) { fetchNoticesFromRTDB() }.await()
    }

    fun saveNotice(notice: NoticeEntity): Pair<Boolean, String> {
        val rtdbRes = saveNoticeToRTDB(notice)
        return Pair(rtdbRes.first, "Realtime Database: ${rtdbRes.second}")
    }

    private fun deleteNoticeFromRTDB(id: String): Boolean {
        val tokenParam = getTokenParam()
        val request = Request.Builder()
            .url("${RTDB_URL}notices/$id.json$tokenParam")
            .delete()
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception deleting RTDB notice: ${e.message}", e)
            false
        }
    }

    fun deleteNotice(id: String): Boolean {
        return deleteNoticeFromRTDB(id)
    }

    @Suppress("UNCHECKED_CAST")
    fun sendFCMNotification(serverKey: String, notice: NoticeEntity): Pair<Boolean, String> {
        if (serverKey.trim().isBlank()) return Pair(false, "Server Key is blank")

        var topicName: String? = null
        val target = if (notice.targetAppId.startsWith("token:")) {
            notice.targetAppId.substringAfter("token:")
        } else {
            val tName = if (notice.targetAppId == "all") "all" else "app_${notice.targetAppId.replace(".", "_")}"
            topicName = tName
            "/topics/$tName"
        }

        val payload = mapOf(
            "to" to target,
            "priority" to "high",
            "time_to_live" to 2419200, // 4 weeks delivery window
            "notification" to mapOf(
                "title" to notice.title,
                "body" to notice.message,
                "sound" to "default",
                "android_channel_id" to "announcements_channel"
            ),
            "data" to mapOf(
                "id" to notice.id,
                "title" to notice.title,
                "message" to notice.message,
                "imageUrl" to notice.imageUrl,
                "targetAppId" to notice.targetAppId,
                "timestamp" to notice.timestamp.toString()
            )
        )

        return try {
            val adapter = moshi.adapter(Map::class.java)
            val jsonStr = adapter.toJson(payload)
            val body = jsonStr.toRequestBody(jsonMediaType)

            val request = Request.Builder()
                .url("https://fcm.googleapis.com/fcm/send")
                .addHeader("Authorization", "key=${serverKey.trim()}")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d(TAG, "Successfully broadcast FCM push notification to: ${topicName ?: "Direct Device Token"}")
                    Pair(true, "Successfully transmitted to ${topicName?.let { "FCM topic '$it'" } ?: "direct client token"}")
                } else {
                    val errorBody = response.body?.string()?.take(150) ?: ""
                    Log.e(TAG, "Failed FCM send. HTTP code: ${response.code}, body: $errorBody")
                    Pair(false, "FCM Server returned HTTP ${response.code}: $errorBody")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during FCM broadcast transmission: ${e.message}", e)
            Pair(false, "Exception: ${e.message}")
        }
    }

    fun saveTermsAgreement(agreement: TermsAgreementEntity): Boolean {
        return try {
            val adapter = moshi.adapter(TermsAgreementEntity::class.java)
            val jsonStr = adapter.toJson(agreement)
            val body = jsonStr.toRequestBody(jsonMediaType)
            val tokenParam = getTokenParam()
            val request = Request.Builder()
                .url("${RTDB_URL}terms_agreements/${agreement.id}.json$tokenParam")
                .put(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d(TAG, "Successfully updated terms agreement for ${agreement.userEmail} in RTDB")
                    true
                } else {
                    Log.e(TAG, "Failed update terms agreement in RTDB: code ${response.code}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception saving RTDB terms agreement: ${e.message}", e)
            false
        }
    }

    fun parseTermsAgreementsResponse(jsonStr: String?): List<TermsAgreementEntity> {
        if (jsonStr.isNullOrBlank() || jsonStr == "null" || jsonStr == "{}") {
            return emptyList()
        }
        try {
            val mapType = com.squareup.moshi.Types.newParameterizedType(Map::class.java, String::class.java, TermsAgreementEntity::class.java)
            val adapter = moshi.adapter<Map<String, TermsAgreementEntity>>(mapType)
            val map = adapter.fromJson(jsonStr)
            if (!map.isNullOrEmpty()) {
                return map.values.filterNotNull()
            }
        } catch (e: Exception) {
            Log.d(TAG, "Parsing RTDB terms agreements as map failed, trying as list: ${e.message}")
        }
        try {
            val listType = com.squareup.moshi.Types.newParameterizedType(List::class.java, TermsAgreementEntity::class.java)
            val adapter = moshi.adapter<List<TermsAgreementEntity>>(listType)
            val list = adapter.fromJson(jsonStr)
            if (list != null) {
                return list.filterNotNull()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parsing RTDB terms agreements as list failed: ${e.message}", e)
        }
        return emptyList()
    }

    fun fetchTermsAgreements(): List<TermsAgreementEntity> {
        return try {
            val tokenParam = getTokenParam()
            val request = Request.Builder()
                .url("${RTDB_URL}terms_agreements.json$tokenParam")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "RTDB terms agreements HTTP error: ${response.code}")
                    return emptyList()
                }
                val bodyStr = response.body?.string()
                Log.d(TAG, "RTDB TermsAgreements Response: $bodyStr")
                parseTermsAgreementsResponse(bodyStr)
            }
        } catch (e: Exception) {
            Log.e(TAG, "RTDB TermsAgreements Network error: ${e.message}", e)
            emptyList()
        }
    }

    fun saveAppPolicy(policy: AppPolicyEntity): Boolean {
        return try {
            val adapter = moshi.adapter(AppPolicyEntity::class.java)
            val jsonStr = adapter.toJson(policy)
            val body = jsonStr.toRequestBody(jsonMediaType)
            val tokenParam = getTokenParam()
            val request = Request.Builder()
                .url("${RTDB_URL}app_policy.json$tokenParam")
                .put(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d(TAG, "Successfully updated app policy in RTDB")
                    true
                } else {
                    Log.e(TAG, "Failed to update app policy in RTDB: code ${response.code}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception saving RTDB app policy: ${e.message}", e)
            false
        }
    }

    fun fetchAppPolicy(): AppPolicyEntity? {
        return try {
            val tokenParam = getTokenParam()
            val request = Request.Builder()
                .url("${RTDB_URL}app_policy.json$tokenParam")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "RTDB app policy HTTP error: ${response.code}")
                    return null
                }
                val bodyStr = response.body?.string()
                if (bodyStr.isNullOrBlank() || bodyStr == "null" || bodyStr == "{}") {
                    return null
                }
                val adapter = moshi.adapter(AppPolicyEntity::class.java)
                adapter.fromJson(bodyStr)
            }
        } catch (e: Exception) {
            Log.e(TAG, "RTDB AppPolicy Network error: ${e.message}", e)
            null
        }
    }

    fun fetchUpdateConfig(): UpdateConfigEntity? {
        try {
            val tokenParam = getTokenParam()
            val request = Request.Builder()
                .url("${RTDB_URL}DarkStoreUpdate.json$tokenParam")
                .get()
                .build()
            val update = client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string()
                    Log.d(TAG, "RTDB Update Response: $bodyStr")
                    if (!bodyStr.isNullOrBlank() && bodyStr != "null" && bodyStr != "{}") {
                        val adapter = moshi.adapter(UpdateConfigEntity::class.java)
                        adapter.fromJson(bodyStr)
                    } else null
                } else {
                    Log.e(TAG, "RTDB update check HTTP error: ${response.code}")
                    null
                }
            }
            if (update != null && update.latestVersionCode > 0) {
                return update
            }
        } catch (e: Exception) {
            Log.e(TAG, "RTDB update check Network error: ${e.message}")
        }
        return null
    }

    fun saveUpdateConfig(config: UpdateConfigEntity): Pair<Boolean, String?> {
        val errors = mutableListOf<String>()
        var rtdbSuccess = false

        try {
            val adapter = moshi.adapter(UpdateConfigEntity::class.java)
            val jsonStr = adapter.toJson(config)
            val body = jsonStr.toRequestBody(jsonMediaType)
            val tokenParam = getTokenParam()
            val requestUrl = "${RTDB_URL}DarkStoreUpdate.json$tokenParam"
            val request = Request.Builder()
                .url(requestUrl)
                .put(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d(TAG, "Successfully updated update config in RTDB")
                    rtdbSuccess = true
                } else {
                    val code = response.code
                    val errBody = response.body?.string() ?: ""
                    var errMsg = "RTDB Response HTTP $code"
                    if (errBody.isNotBlank()) {
                        errMsg += ": $errBody"
                    }
                    if (code == 401 || code == 403) {
                        errMsg += " (Authentication failure - check if database rules require auth, or if you are using a simulated/guest user)"
                    }
                    Log.e(TAG, "Failed to update update config in RTDB: $errMsg")
                    errors.add(errMsg)
                }
            }
        } catch (e: Exception) {
            val exMsg = "RTDB Exception: ${e.localizedMessage ?: e.message}"
            Log.e(TAG, "Exception saving RTDB update config: ${e.message}", e)
            errors.add(exMsg)
        }

        val overallSuccess = rtdbSuccess

        if (!overallSuccess && !isRealToken()) {
            errors.add("Active session is running in Sandbox Simulation / Local Guest mode. No real authorization token is available to push to live database. Go to developer settings to verify real credentials.")
        }

        val combinedErrors = if (errors.isNotEmpty()) errors.joinToString("\n• ") else null
        return Pair(overallSuccess, combinedErrors)
    }

    suspend fun fetchReviews(appId: String): List<ReviewEntity> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val reviews = mutableListOf<ReviewEntity>()
        val tokenParam = getTokenParam()
        val requestUrl = "${RTDB_URL}reviews/$appId.json$tokenParam"

        val request = Request.Builder()
            .url(requestUrl)
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string()
                    if (!bodyStr.isNullOrBlank() && bodyStr != "null") {
                        val mapType = Types.newParameterizedType(Map::class.java, String::class.java, ReviewEntity::class.java)
                        val adapter = moshi.adapter<Map<String, ReviewEntity>>(mapType)
                        val map = adapter.fromJson(bodyStr)
                        if (map != null) {
                            reviews.addAll(map.values)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching reviews: ${e.message}")
        }

        return@withContext reviews.sortedByDescending { it.timestamp }
    }

    suspend fun saveReview(review: ReviewEntity): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val adapter = moshi.adapter(ReviewEntity::class.java)
        val jsonStr = adapter.toJson(review)
        val body = jsonStr.toRequestBody(jsonMediaType)

        val tokenParam = getTokenParam()
        val requestUrl = "${RTDB_URL}reviews/${review.appId}/${review.id}.json$tokenParam"

        val request = Request.Builder()
            .url(requestUrl)
            .put(body)
            .build()

        try {
            return@withContext client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving review: ${e.message}")
            return@withContext false
        }
    }
}
