package com.darkstore.data

import android.content.Context
import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

object FirebaseAuthService {
    private const val TAG = "FirebaseAuthService"

    @Volatile
    var activeToken: String = ""

    @Volatile
    var activeRefreshToken: String = ""

    // Configurable API Details
    var PROJECT_ID = "dark-store-6836d"
    var DEFAULT_API_KEY = "AIzaSyDWAQ3MmbZwzIQ9zNZvN9lep-_W6dIbv9o"
    var RTDB_URL = "https://dark-store-6836d-default-rtdb.asia-southeast1.firebasedatabase.app/"

    /**
     * SECURITY: the database address receives the user's auth token with every request, so it
     * must be HTTPS and a real Firebase host. Anything else (typed into the config screen,
     * planted by a phishing instruction) is ignored instead of being trusted.
     */
    fun isTrustedRtdbUrl(url: String): Boolean {
        val u = try { java.net.URI(url.trim()) } catch (e: Exception) { return false }
        val host = u.host?.lowercase() ?: return false
        return u.scheme == "https" &&
            (host.endsWith(".firebasedatabase.app") || host.endsWith(".firebaseio.com"))
    }

    fun updateConfig(apiKey: String, projId: String, rtdb: String) {
        if (apiKey.isNotBlank()) DEFAULT_API_KEY = apiKey.trim()
        if (projId.isNotBlank()) PROJECT_ID = projId.trim()
        if (rtdb.isNotBlank() && isTrustedRtdbUrl(rtdb)) {
            var url = rtdb.trim()
            if (!url.endsWith("/")) url += "/"
            RTDB_URL = url
        }
    }

    fun getTokenParam(): String {
        val token = activeToken
        return if (token.isNotBlank() && !token.startsWith("sim_") && !token.startsWith("fake_")) {
            "?auth=$token"
        } else {
            ""
        }
    }

    // Firebase Auth REST URLs
    private const val SIGN_UP_URL = "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key="
    private const val SIGN_IN_URL = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key="
    private const val RESET_PWD_URL = "https://identitytoolkit.googleapis.com/v1/accounts:sendOobCode?key="
    private const val LOOKUP_URL = "https://identitytoolkit.googleapis.com/v1/accounts:lookup?key="

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val mediaTypeJson = "application/json; charset=utf-8".toMediaType()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    // ----------------------------------------------------
    // EMAIL & PASSWORD SIGN UP
    // ----------------------------------------------------
    suspend fun signUp(
        context: Context,
        email: String,
        password: String,
        displayName: String,
        apiKey: String
    ): Triple<Boolean, String?, UserEntity?> = withContext(Dispatchers.IO) {
        val targetKey = if (apiKey.isBlank() || apiKey.startsWith("AIzaSy_placeholder")) DEFAULT_API_KEY else apiKey

        // Fake-account protection: temporary mailboxes can't create DarkStore accounts.
        if (com.darkstore.utils.FraudRules.isDisposableEmail(email)) {
            return@withContext Triple(false, "Please sign up with a real email address (temporary email services aren't allowed).", null)
        }

        val payload = JSONObject().apply {
            put("email", email)
            put("password", password)
            put("returnSecureToken", true)
        }

        val request = Request.Builder()
            .url("$SIGN_UP_URL$targetKey")
            .post(payload.toString().toRequestBody(mediaTypeJson))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string()
                if (response.isSuccessful && bodyStr != null) {
                    val resJson = JSONObject(bodyStr)
                    val uid = resJson.getString("localId")
                    val idToken = resJson.getString("idToken")
                    activeToken = idToken // active BEFORE any DB read/write below — rules need auth
                    val refreshToken = resJson.optString("refreshToken") ?: ""

                    val fcmPrefs = context.getSharedPreferences("dark_store_fcm_prefs", Context.MODE_PRIVATE)
                    val fcmToken = fcmPrefs.getString("fcm_token", "") ?: ""

                    // Determine Role
                    val role = "user" /* SECURITY: roles are NEVER granted by the client; admin is set server-side only */
                    val user = UserEntity(uid, email, displayName, role, fcmToken = fcmToken, isEmailVerified = false)

                    // Push to Realtime Database
                    saveUserInRealtimeDatabase(user)

                    // Offline backup cache
                    saveLocalUser(context, user, idToken, refreshToken)

                    // Fire off the verification email right away — this is a real
                    // Firebase account with a real inbox behind it, so ask Firebase
                    // to send its standard "verify your email" template immediately
                    // rather than making the user hunt for a button first.
                    val (sent, sendMsg) = sendEmailVerification(idToken, targetKey)
                    val confirmationMsg = if (sent) {
                        "Successfully registered! We've sent a verification link to $email."
                    } else {
                        Log.w(TAG, "SignUp succeeded but verification email failed to send: $sendMsg")
                        "Successfully registered! (Couldn't send the verification email right now — you can resend it from the app.)"
                    }

                    Triple(true, confirmationMsg, user)
                } else {
                    // Firebase actually responded here — this is a REAL error
                    // (EMAIL_EXISTS, WEAK_PASSWORD, OPERATION_NOT_ALLOWED if
                    // Email/Password sign-in is disabled in the Firebase
                    // console, etc.), not a connectivity problem. The old
                    // behavior silently swallowed this and faked a successful
                    // "sandbox" account instead — which is exactly why signups
                    // could appear to "work" (instant login, no error) while
                    // no real Firebase account was ever created and no
                    // verification email was ever possible. Show the real
                    // error instead of hiding it behind a fake success.
                    val errMsg = parseAuthError(bodyStr)
                    Log.w(TAG, "SignUp rejected by Firebase: $errMsg")
                    Triple(false, errMsg, null)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "SignUp Exception: ${e.message}", e)
            // SECURITY: no offline "sandbox" account any more. It stored the password in plain
            // text and could mint a local admin session. A network failure is just an error.
            Triple(false, "Network error. Please check your connection and try again.", null)
        }
    }

    // ----------------------------------------------------
    // EMAIL & PASSWORD SIGN IN
    // ----------------------------------------------------
    suspend fun signIn(
        context: Context,
        email: String,
        password: String,
        apiKey: String
    ): Triple<Boolean, String?, UserEntity?> = withContext(Dispatchers.IO) {
        val targetKey = if (apiKey.isBlank() || apiKey.startsWith("AIzaSy_placeholder")) DEFAULT_API_KEY else apiKey

        val payload = JSONObject().apply {
            put("email", email)
            put("password", password)
            put("returnSecureToken", true)
        }

        val request = Request.Builder()
            .url("$SIGN_IN_URL$targetKey")
            .post(payload.toString().toRequestBody(mediaTypeJson))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string()
                if (response.isSuccessful && bodyStr != null) {
                    val resJson = JSONObject(bodyStr)
                    val uid = resJson.getString("localId")
                    val idToken = resJson.getString("idToken")
                    activeToken = idToken // active BEFORE any DB read/write below — rules need auth
                    val refreshToken = resJson.optString("refreshToken") ?: ""

                    var user = getUserProfile(uid, idToken)
                    val tokenPrefs = context.getSharedPreferences("dark_store_fcm_prefs", Context.MODE_PRIVATE)
                    val freshFcmToken = tokenPrefs.getString("fcm_token", "") ?: ""

                    if (user == null) {
                        val prefs = context.getSharedPreferences("dark_store_pref", Context.MODE_PRIVATE)
                        val cachedUid = prefs.getString("user_uid", "") ?: ""
                        val cachedEmail = prefs.getString("user_email", "") ?: ""
                        if ((cachedUid == uid || cachedEmail.equals(email, ignoreCase = true))) {
                            user = UserEntity(
                                uid = uid,
                                email = email,
                                displayName = prefs.getString("user_name", email.substringBefore("@").replaceFirstChar { it.uppercase() }) ?: "",
                                role = prefs.getString("user_role", "user") ?: "user",
                                isDeveloper = prefs.getBoolean("is_developer", false),
                                devWebsite = prefs.getString("dev_website", "") ?: "",
                                devGithub = prefs.getString("dev_github", "") ?: "",
                                devName = prefs.getString("dev_name", "") ?: "",
                                fcmToken = freshFcmToken
                            )
                        } else {
                            val role = "user" /* SECURITY: roles are NEVER granted by the client; admin is set server-side only */
                            val name = email.substringBefore("@").replaceFirstChar { it.uppercase() }
                            user = UserEntity(uid, email, name, role, fcmToken = freshFcmToken)
                            saveUserInRealtimeDatabase(user)
                        }
                    } else if (freshFcmToken.isNotBlank() && user.fcmToken != freshFcmToken) {
                        user = user.copy(fcmToken = freshFcmToken)
                        saveUserInRealtimeDatabase(user)
                    }

                    // Our RTDB copy of isEmailVerified can go stale — the user may
                    // have clicked the verification link on a different device, or
                    // signed up before we started tracking this at all. Firebase
                    // Auth itself is the real source of truth, so reconcile on every
                    // real login rather than trusting the cached RTDB value.
                    if (isRealAuthToken(idToken) && !user.isEmailVerified) {
                        val actuallyVerified = checkEmailVerified(idToken, targetKey)
                        if (actuallyVerified) {
                            user = user.copy(isEmailVerified = true)
                            saveUserInRealtimeDatabase(user)
                        }
                    }

                    // Block suspended accounts from signing in (by uid or matching email).
                    val (suspended, suspReason) = checkAccountSuspended(user.uid, user.email)
                    if (user.isSuspended || suspended) {
                        val reason = user.suspensionReason.ifBlank { suspReason }.ifBlank { "Please contact support." }
                        return@use suspendedLogin(reason, user.email, idToken)
                    }
                    deviceGate(context, idToken)?.let { why -> return@use suspendedLogin(why, user.email, idToken) }

                    saveLocalUser(context, user, idToken, refreshToken)

                    Triple(true, "Welcome back!", user)
                } else {
                    Triple(false, parseAuthError(bodyStr), null)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "SignIn Exception: ${e.message}", e)
            Triple(false, "Network error. Please check your connection and try again.", null)
        }
    }

    // ----------------------------------------------------
    // PASSWORD RESET
    // ----------------------------------------------------
    suspend fun resetPassword(email: String, apiKey: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val targetKey = if (apiKey.isBlank() || apiKey.startsWith("AIzaSy_placeholder")) DEFAULT_API_KEY else apiKey

        val payload = JSONObject().apply {
            put("requestType", "PASSWORD_RESET")
            put("email", email)
        }

        val request = Request.Builder()
            .url("$RESET_PWD_URL$targetKey")
            .post(payload.toString().toRequestBody(mediaTypeJson))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string()
                if (response.isSuccessful) {
                    Pair(true, "Password reset instructions dispatched to $email!")
                } else {
                    val errMsg = parseAuthError(bodyStr)
                    Pair(false, errMsg)
                }
            }
        } catch (e: Exception) {
            Pair(false, "Network failure: ${e.localizedMessage}")
        }
    }

    // ----------------------------------------------------
    // EMAIL VERIFICATION
    // ----------------------------------------------------

    // Local/simulated/Google sessions have no real Firebase Auth idToken behind
    // them (no genuine inbox to confirm either), so verification is a no-op for
    // those — treated as "nothing to verify" rather than an error.
    private fun isRealAuthToken(idToken: String): Boolean {
        return idToken.isNotBlank() &&
            !idToken.startsWith("sim_") &&
            !idToken.startsWith("fake_") &&
            !idToken.startsWith("google_")
    }

    suspend fun sendEmailVerification(idToken: String, apiKey: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (!isRealAuthToken(idToken)) {
            return@withContext Pair(false, "Email verification isn't available for this sign-in method.")
        }
        val targetKey = if (apiKey.isBlank() || apiKey.startsWith("AIzaSy_placeholder")) DEFAULT_API_KEY else apiKey

        val payload = JSONObject().apply {
            put("requestType", "VERIFY_EMAIL")
            put("idToken", idToken)
        }

        val request = Request.Builder()
            .url("$RESET_PWD_URL$targetKey")
            .post(payload.toString().toRequestBody(mediaTypeJson))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string()
                if (response.isSuccessful) {
                    Pair(true, "Verification email sent! Check your inbox.")
                } else {
                    Pair(false, parseAuthError(bodyStr))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "SendEmailVerification Exception: ${e.message}", e)
            Pair(false, "Network failure: ${e.localizedMessage}")
        }
    }

    // Asks Firebase Auth itself (not our RTDB copy) whether this account's email
    // has been confirmed yet — the source of truth, since the user may have
    // clicked the verification link on a completely different device.
    suspend fun checkEmailVerified(idToken: String, apiKey: String): Boolean = withContext(Dispatchers.IO) {
        if (!isRealAuthToken(idToken)) {
            // Nothing to verify for these sign-in paths — treat as already "verified"
            // so they're never blocked or nagged.
            return@withContext true
        }
        val targetKey = if (apiKey.isBlank() || apiKey.startsWith("AIzaSy_placeholder")) DEFAULT_API_KEY else apiKey
        val payload = JSONObject().apply { put("idToken", idToken) }
        val request = Request.Builder()
            .url("$LOOKUP_URL$targetKey")
            .post(payload.toString().toRequestBody(mediaTypeJson))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string()
                if (response.isSuccessful && bodyStr != null) {
                    val json = JSONObject(bodyStr)
                    val users = json.optJSONArray("users")
                    val firstUser = users?.optJSONObject(0)
                    firstUser?.optBoolean("emailVerified", false) ?: false
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "CheckEmailVerified Exception: ${e.message}", e)
            false
        }
    }

    // ----------------------------------------------------
    // GOOGLE SIGN-IN OAUTH SWAP OR SIMULATION
    // ----------------------------------------------------
    suspend fun googleSignIn(
        context: Context,
        email: String,
        displayName: String
    ): Triple<Boolean, String?, UserEntity?> = withContext(Dispatchers.IO) {
        val uid = "google_" + Math.abs(email.hashCode()).toString()
        val role = "user" /* SECURITY: roles are NEVER granted by the client; admin is set server-side only */

        var user: UserEntity? = getUserProfile(uid)
        if (user == null) {
            val prefs = context.getSharedPreferences("dark_store_pref", Context.MODE_PRIVATE)
            val cachedUid = prefs.getString("user_uid", "") ?: ""
            val cachedEmail = prefs.getString("user_email", "") ?: ""
            if ((cachedUid == uid || cachedEmail.equals(email, ignoreCase = true))) {
                user = UserEntity(
                    uid = uid,
                    email = email,
                    displayName = displayName.ifBlank { prefs.getString("user_name", "") ?: "" },
                    role = prefs.getString("user_role", role) ?: role,
                    isDeveloper = prefs.getBoolean("is_developer", false),
                    devWebsite = prefs.getString("dev_website", "") ?: "",
                    devGithub = prefs.getString("dev_github", "") ?: "",
                    devName = prefs.getString("dev_name", "") ?: ""
                )
            } else {
                user = UserEntity(uid, email, displayName, role)
            }
        } else {
            val existingUser = user!!
            user = existingUser.copy(
                email = email.ifBlank { existingUser.email },
                displayName = displayName.ifBlank { existingUser.displayName }
            )
        }

        val finalUser = user!!
        val (suspended, reason) = checkAccountSuspended(finalUser.uid, finalUser.email)
        if (suspended || finalUser.isSuspended) {
            val msg = (if (finalUser.suspensionReason.isNotBlank()) finalUser.suspensionReason else reason)
                .ifBlank { "Please contact support." }
            return@withContext suspendedLogin(msg, finalUser.email, "")
        }
        saveUserInRealtimeDatabase(finalUser)
        saveLocalUser(context, finalUser, "google_oauth_token_$uid")

        Triple(true, "Authorized via Google Account", finalUser)
    }

    suspend fun googleSignInWithIdToken(
        context: Context,
        idToken: String,
        fallbackEmail: String,
        fallbackName: String,
        apiKey: String
    ): Triple<Boolean, String?, UserEntity?> = withContext(Dispatchers.IO) {
        val targetKey = if (apiKey.isBlank() || apiKey.startsWith("AIzaSy_placeholder")) DEFAULT_API_KEY else apiKey

        val payload = JSONObject().apply {
            put("postBody", "id_token=$idToken&providerId=google.com")
            put("requestUri", "http://localhost")
            put("returnIdpCredential", true)
            put("returnSecureToken", true)
        }

        val request = Request.Builder()
            .url("https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key=$targetKey")
            .post(payload.toString().toRequestBody(mediaTypeJson))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string()
                Log.d(TAG, "signInWithIdp status: ${response.code}, body: $bodyStr")
                if (response.isSuccessful && bodyStr != null) {
                    val jsonObj = JSONObject(bodyStr)
                    val uid = jsonObj.optString("localId") ?: "g_${System.currentTimeMillis()}"
                    val email = jsonObj.optString("email") ?: fallbackEmail
                    val displayName = jsonObj.optString("displayName") ?: fallbackName
                    val token = jsonObj.optString("idToken") ?: ""
                    if (token.isNotBlank()) activeToken = token // active BEFORE the first DB read/write
                    val refreshToken = jsonObj.optString("refreshToken") ?: ""

                    val role = "user" /* SECURITY: roles are NEVER granted by the client; admin is set server-side only */
                    var user = getUserProfile(uid, token)
                    if (user == null) {
                        val prefs = context.getSharedPreferences("dark_store_pref", Context.MODE_PRIVATE)
                        val cachedUid = prefs.getString("user_uid", "") ?: ""
                        val cachedEmail = prefs.getString("user_email", "") ?: ""
                        if ((cachedUid == uid || cachedEmail.equals(fallbackEmail, ignoreCase = true))) {
                            user = UserEntity(
                                uid = uid,
                                email = email,
                                displayName = displayName.ifBlank { prefs.getString("user_name", "") ?: "" },
                                role = prefs.getString("user_role", role) ?: role,
                                isDeveloper = prefs.getBoolean("is_developer", false),
                                devWebsite = prefs.getString("dev_website", "") ?: "",
                                devGithub = prefs.getString("dev_github", "") ?: "",
                                devName = prefs.getString("dev_name", "") ?: ""
                            )
                        } else {
                            user = UserEntity(uid, email, displayName, role)
                        }
                    } else {
                        val existingUser = user!!
                        user = existingUser.copy(
                            email = email.ifBlank { existingUser.email },
                            displayName = displayName.ifBlank { existingUser.displayName }
                        )
                    }

                    val finalUser = user!!
                    val (suspended, reason) = checkAccountSuspended(finalUser.uid, finalUser.email)
                    if (suspended || finalUser.isSuspended) {
                        val msg = (if (finalUser.suspensionReason.isNotBlank()) finalUser.suspensionReason else reason)
                            .ifBlank { "Please contact support." }
                        return@use suspendedLogin(msg, finalUser.email, token)
                    }
                    saveUserInRealtimeDatabase(finalUser)
                    deviceGate(context, token)?.let { why -> return@use suspendedLogin(why, finalUser.email, token) }
                    saveLocalUser(context, finalUser, token, refreshToken)
                    Triple(true, "Successfully authenticated with Google through Firebase IDP!", finalUser)
                } else {
                    val uid = "google_" + Math.abs(fallbackEmail.hashCode()).toString()
                    val role = "user" /* SECURITY: roles are NEVER granted by the client; admin is set server-side only */
                    var user = getUserProfile(uid)
                    if (user == null) {
                        val prefs = context.getSharedPreferences("dark_store_pref", Context.MODE_PRIVATE)
                        val cachedUid = prefs.getString("user_uid", "") ?: ""
                        val cachedEmail = prefs.getString("user_email", "") ?: ""
                        if ((cachedUid == uid || cachedEmail.equals(fallbackEmail, ignoreCase = true))) {
                            user = UserEntity(
                                uid = uid,
                                email = fallbackEmail,
                                displayName = fallbackName.ifBlank { prefs.getString("user_name", "") ?: "" },
                                role = prefs.getString("user_role", role) ?: role,
                                isDeveloper = prefs.getBoolean("is_developer", false),
                                devWebsite = prefs.getString("dev_website", "") ?: "",
                                devGithub = prefs.getString("dev_github", "") ?: "",
                                devName = prefs.getString("dev_name", "") ?: ""
                            )
                        } else {
                            user = UserEntity(uid, fallbackEmail, fallbackName, role)
                        }
                    } else {
                        val existingUser = user!!
                        user = existingUser.copy(
                            email = fallbackEmail.ifBlank { existingUser.email },
                            displayName = fallbackName.ifBlank { existingUser.displayName }
                        )
                    }
                    val finalUser = user!!
                    val (suspended, reason) = checkAccountSuspended(finalUser.uid, finalUser.email)
                    if (suspended || finalUser.isSuspended) {
                        val msg = (if (finalUser.suspensionReason.isNotBlank()) finalUser.suspensionReason else reason)
                            .ifBlank { "Please contact support." }
                        return@use suspendedLogin(msg, finalUser.email, "")
                    }
                    saveUserInRealtimeDatabase(finalUser)
                    saveLocalUser(context, finalUser, "fake_token_$uid")
                    Triple(true, "Authenticated via Google Account (offline compatibility).", finalUser)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "googleSignInWithIdToken exception: ${e.message}")
            val uid = "google_" + Math.abs(fallbackEmail.hashCode()).toString()
            val role = "user" /* SECURITY: roles are NEVER granted by the client; admin is set server-side only */
            var user = getUserProfile(uid)
            if (user == null) {
                val prefs = context.getSharedPreferences("dark_store_pref", Context.MODE_PRIVATE)
                val cachedUid = prefs.getString("user_uid", "") ?: ""
                val cachedEmail = prefs.getString("user_email", "") ?: ""
                val cachedIsDev = prefs.getBoolean("is_developer", false)
                if ((cachedUid == uid || cachedEmail.equals(fallbackEmail, ignoreCase = true)) && cachedIsDev) {
                    user = UserEntity(
                        uid = uid,
                        email = fallbackEmail,
                        displayName = fallbackName.ifBlank { prefs.getString("user_name", "") ?: "" },
                        role = prefs.getString("user_role", role) ?: role,
                        isDeveloper = true,
                        devWebsite = prefs.getString("dev_website", "") ?: "",
                        devGithub = prefs.getString("dev_github", "") ?: "",
                        devName = prefs.getString("dev_name", "") ?: ""
                    )
                } else {
                    user = UserEntity(uid, fallbackEmail, fallbackName, role)
                }
            } else {
                val existingUser = user!!
                user = existingUser.copy(
                    email = fallbackEmail.ifBlank { existingUser.email },
                    displayName = fallbackName.ifBlank { existingUser.displayName }
                )
            }
            val finalUser = user!!
            val (suspended, reason) = checkAccountSuspended(finalUser.uid, finalUser.email)
            if (suspended || finalUser.isSuspended) {
                val msg = (if (finalUser.suspensionReason.isNotBlank()) finalUser.suspensionReason else reason)
                    .ifBlank { "Please contact support." }
                return@withContext suspendedLogin(msg, finalUser.email, "")
            }
            saveUserInRealtimeDatabase(finalUser)
            saveLocalUser(context, finalUser, "fake_token_$uid")
            Triple(true, "Google Sign-In offline fallback successful.", finalUser)
        }
    }


    /**
     * Checks whether this account is suspended in RTDB.
     * Looks up by uid first, then by email (Google sign-in can use a different
     * uid shape than the record an admin suspended from the Users tab).
     */
    private fun checkAccountSuspended(uid: String, email: String): Pair<Boolean, String> {
        try {
            if (uid.isNotBlank()) {
                val tokenParam = getTokenParam()
                val request = Request.Builder()
                    .url("${RTDB_URL}users/${uid}.json$tokenParam")
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrBlank() && body != "null") {
                            val json = JSONObject(body)
                            if (json.optBoolean("isSuspended", false)) {
                                return true to json.optString("suspensionReason", "")
                            }
                        }
                    }
                }
            }
            // Fallback: match any users/{id} with the same email
            if (email.isNotBlank()) {
                for (u in fetchAllUsersFromRTDB()) {
                    if (u.email.equals(email, ignoreCase = true) && u.isSuspended) {
                        return true to u.suspensionReason
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "checkAccountSuspended: ${e.message}")
        }
        return false to ""
    }

    /**
     * Partial update of users/{uid} — only the given fields are written, everything
     * else on the record (fcmToken, isPremiumMember, isSuspended, createdAt…) is
     * left untouched. Profile edits used to go through saveUserInRealtimeDatabase()
     * which is a full PUT built from a UserEntity with defaults, so every profile
     * edit silently wiped the user's fcmToken (breaking push) and premium flag.
     */
    suspend fun patchUserFields(uid: String, fields: Map<String, Any>): Boolean = withContext(Dispatchers.IO) {
        if (uid.isBlank() || uid == "guest_uid" || fields.isEmpty()) return@withContext false
        val payload = JSONObject()
        fields.forEach { (k, v) -> payload.put(k, v) }
        val request = Request.Builder()
            .url("${RTDB_URL}users/$uid.json${getTokenParam()}")
            .patch(payload.toString().toRequestBody(mediaTypeJson))
            .build()
        try {
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            Log.e(TAG, "patchUserFields failed: ${e.message}", e)
            false
        }
    }

    /**
     * Makes sure users/{uid} exists. Accounts whose very first sign-in write was
     * rejected (token not yet active) never got a record, so the admin Users tab
     * could not list them. Only creates when the server answered "no such record"
     * — a failed/denied read is never treated as "missing", so an existing profile
     * can't be overwritten by a transient error.
     */
    suspend fun ensureUserRecord(uid: String, email: String, displayName: String, role: String,
                                 isDeveloper: Boolean, fcmToken: String): Boolean = withContext(Dispatchers.IO) {
        if (uid.isBlank() || uid == "guest_uid" || getTokenParam().isBlank()) return@withContext false
        try {
            val req = Request.Builder().url("${RTDB_URL}users/$uid.json${getTokenParam()}").get().build()
            val missing = client.newCall(req).execute().use { r ->
                r.isSuccessful && (r.body?.string()?.trim() ?: "") == "null"
            }
            if (!missing) return@withContext false
            saveUserInRealtimeDatabase(
                UserEntity(uid = uid, email = email, displayName = displayName,
                    role = role.ifBlank { "user" }, isDeveloper = isDeveloper, fcmToken = fcmToken)
            )
        } catch (e: Exception) {
            Log.e(TAG, "ensureUserRecord failed: ${e.message}")
            false
        }
    }

    suspend fun saveUserInRealtimeDatabase(user: UserEntity): Boolean = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("uid", user.uid)
            put("email", user.email)
            put("displayName", user.displayName)
            put("userName", user.displayName)
            put("developer", user.displayName)
            put("developerName", user.displayName)
            put("role", user.role)
            put("createdAt", user.createdAt)
            put("fcmToken", user.fcmToken)
            put("isDeveloper", user.isDeveloper)
            put("devWebsite", user.devWebsite)
            put("devGithub", user.devGithub)
            put("devName", user.devName)
            put("devBio", user.devBio)
            put("profilePhotoUrl", user.profilePhotoUrl)
            put("devLocation", user.devLocation)
            put("isEmailVerified", user.isEmailVerified)
            put("isPremiumMember", user.isPremiumMember)
            put("isSuspended", user.isSuspended)
            put("suspensionReason", user.suspensionReason)
        }
        val body = payload.toString().toRequestBody(mediaTypeJson)
        val tokenParam = getTokenParam()

        val userUrl = "${RTDB_URL}users/${user.uid}.json$tokenParam"
        val userRequest = Request.Builder()
            .url(userUrl)
            .put(body)
            .build()
        try {
            client.newCall(userRequest).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d(TAG, "Successfully updated user ${user.uid} in Realtime Database")
                    true
                } else {
                    Log.e(TAG, "Failed update user in RTDB: code ${response.code}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception saving user in Realtime Database: ${e.message}", e)
            false
        }
    }

    suspend fun getUserProfile(uid: String, token: String? = null): UserEntity? = withContext(Dispatchers.IO) {
        val tokenParam = getTokenParam()

        var rtdbUser: UserEntity? = null
        try {
            val userUrl = "${RTDB_URL}users/${uid}.json$tokenParam"
            val userRequest = Request.Builder().url(userUrl).get().build()
            client.newCall(userRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank() && body != "null") {
                        val json = JSONObject(body)
                        rtdbUser = UserEntity(
                            uid = uid,
                            email = json.optString("email", ""),
                            displayName = json.optString("displayName", ""),
                            role = json.optString("role", "user"),
                            createdAt = json.optLong("createdAt", 0L),
                            fcmToken = json.optString("fcmToken", ""),
                            isDeveloper = json.optBoolean("isDeveloper", false),
                            devWebsite = json.optString("devWebsite", ""),
                            devGithub = json.optString("devGithub", ""),
                            devName = json.optString("devName", ""),
                            devBio = json.optString("devBio", ""),
                            profilePhotoUrl = json.optString("profilePhotoUrl", ""),
                            devLocation = json.optString("devLocation", ""),
                            // Missing field (accounts created before this feature existed)
                            // defaults to true — never retroactively nag/block old accounts.
                            isEmailVerified = json.optBoolean("isEmailVerified", true),
                            isPremiumMember = json.optBoolean("isPremiumMember", false),
                            isSuspended = json.optBoolean("isSuspended", false),
                            suspensionReason = json.optString("suspensionReason", "")
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching user from RTDB: ${e.message}")
        }

        // NOTE: this used to also read a separate `developers/{uid}` mirror
        // node and merge its isDeveloper/devName in, in case the two nodes
        // had drifted apart. That mirror node has been removed entirely
        // (see fetchAllUsersFromRTDB) — `users/{uid}` above is now the one
        // and only source of truth, so there's nothing left to reconcile.
        rtdbUser
    }

    suspend fun getFcmTokenByEmail(email: String): String? = withContext(Dispatchers.IO) {
        val uid = "google_" + Math.abs(email.hashCode()).toString()
        return@withContext getUserProfile(uid)?.fcmToken
    }

    // ----------------------------------------------------
    // FIREBASE STORAGE OPERATIONS
    // ----------------------------------------------------
    suspend fun uploadFile(
        contentType: String,
        fileName: String,
        fileBytes: ByteArray
    ): String? = withContext(Dispatchers.IO) {
        val buckets = listOf("dark-store-6836d.appspot.com", "dark-store-6836d.firebasestorage.app")
        var lastException: Exception? = null

        for (bucket in buckets) {
            val encodedPath = java.net.URLEncoder.encode("submissions/$fileName", "UTF-8")
            val uploadUrl = "https://firebasestorage.googleapis.com/v0/b/$bucket/o?uploadType=media&name=$encodedPath"

            val mediaType = contentType.toMediaType()
            val request = Request.Builder()
                .url(uploadUrl)
                .post(fileBytes.toRequestBody(mediaType))
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val bodyStr = response.body?.string() ?: ""
                    Log.d(TAG, "Storage upload to $bucket response code: ${response.code}")
                    if (response.isSuccessful) {
                        val tokenMatch = Regex("\"downloadTokens\"\\s*:\\s*\"([^\"]+)\"").find(bodyStr)
                        val token = tokenMatch?.groupValues?.get(1)
                        val publicUrl = "https://firebasestorage.googleapis.com/v0/b/$bucket/o/$encodedPath?alt=media" +
                                if (token != null) "&token=$token" else ""
                        return@withContext publicUrl
                    } else {
                        Log.w(TAG, "Storage upload to $bucket returned failed status: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed uploading to $bucket: ${e.message}")
                lastException = e
            }
        }

        Log.e(TAG, "All upload attempts failed. Last exception: ${lastException?.message}")
        null
    }

    // ----------------------------------------------------
    // SUBMISSIONS COLLECTION OPERATIONS
    // ----------------------------------------------------

    private fun saveSubmissionToRTDB(submission: SubmissionEntity): Boolean {
        val adapter = moshi.adapter(SubmissionEntity::class.java)
        val jsonStr = adapter.toJson(submission)

        val body = jsonStr.toRequestBody(mediaTypeJson)
        val tokenParam = getTokenParam()
        val request = Request.Builder()
            .url("${RTDB_URL}submissions/${submission.id}.json$tokenParam")
            .put(body)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d(TAG, "Successfully updated submission ${submission.name} in RTDB")
                    true
                } else {
                    Log.e(TAG, "Failed update submission in RTDB: code ${response.code}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception saving RTDB submission: ${e.message}", e)
            false
        }
    }

    private fun fetchSubmissionsFromRTDB(): List<SubmissionEntity> {
        val tokenParam = getTokenParam()
        val request = Request.Builder()
            .url("${RTDB_URL}submissions.json$tokenParam")
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "RTDB submissions HTTP error: ${response.code}")
                    return emptyList()
                }
                val bodyStr = response.body?.string()
                Log.d(TAG, "RTDB Submissions Response: $bodyStr")
                parseSubmissionsFirebaseResponse(bodyStr)
            }
        } catch (e: Exception) {
            Log.e(TAG, "RTDB Submissions Network error: ${e.message}", e)
            emptyList()
        }
    }

    fun parseSubmissionsFirebaseResponse(jsonStr: String?): List<SubmissionEntity> {
        if (jsonStr.isNullOrBlank() || jsonStr == "null" || jsonStr == "{}") {
            return emptyList()
        }

        try {
            val mapType = com.squareup.moshi.Types.newParameterizedType(Map::class.java, String::class.java, SubmissionEntity::class.java)
            val adapter = moshi.adapter<Map<String, SubmissionEntity>>(mapType)
            val map = adapter.fromJson(jsonStr)
            if (map != null) {
                return map.values.toList()
            }
        } catch (e: Exception) {
            Log.d(TAG, "Parsing RTDB response as Map failed, trying as List...")
        }

        try {
            val listType = com.squareup.moshi.Types.newParameterizedType(List::class.java, SubmissionEntity::class.java)
            val adapter = moshi.adapter<List<SubmissionEntity?>>(listType)
            val list = adapter.fromJson(jsonStr)
            if (list != null) {
                return list.filterNotNull()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parsing RTDB response as list failed: ${e.message}", e)
        }

        return emptyList()
    }

    suspend fun submitApp(submission: SubmissionEntity): Boolean = withContext(Dispatchers.IO) {
        saveSubmissionToRTDB(submission)
    }

    suspend fun fetchSubmissions(): List<SubmissionEntity> = withContext(Dispatchers.IO) {
        fetchSubmissionsFromRTDB()
    }

    // Renamed from the old "developers" mirror node to reading the real
    // `users/` node directly. The admin console's Users tab lists EVERY
    // account, developer or not, so it should read from the one node every
    // account unconditionally has — not a secondary mirror that was only
    // ever written to as a side effect of saveUserInRealtimeDatabase() being
    // called for that particular uid. Any account that predates that mirror,
    // or simply hasn't logged in/edited its profile since, had no record
    // under the old `developers/{uid}` path at all and silently vanished
    // from the Users tab — which is exactly the "isDeveloper:false accounts
    // disappear" symptom this was reported as. Reading `users/` directly
    // means every real account always shows up, regardless of history.
    private fun fetchAllUsersFromRTDB(): List<UserEntity> {
        val tokenParam = getTokenParam()
        val request = Request.Builder()
            .url("${RTDB_URL}users.json$tokenParam")
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "RTDB users HTTP error: ${response.code}")
                    return emptyList()
                }
                val bodyStr = response.body?.string()
                parseAllUsersFirebaseResponse(bodyStr)
            }
        } catch (e: Exception) {
            Log.e(TAG, "RTDB Users Network error: ${e.message}", e)
            emptyList()
        }
    }

    // NOTE: this used to deserialize the WHOLE users node in one shot via
    // Moshi as Map<String, Map<*,*>>. That's a strict, all-or-nothing parse —
    // if even a single one of potentially dozens of user records had any
    // field in a shape Moshi didn't expect (or was somehow malformed —
    // manually edited in the Firebase console, a partial/interrupted write,
    // etc.), the ENTIRE parse threw and the function returned a completely
    // empty list, with nothing logged anywhere the person using the app
    // could see. Every real account could exist correctly in the database
    // and the admin Users tab would still show nothing, with zero indication
    // of why. Rewritten to walk the raw JSON object key by key using
    // org.json (which this codebase already relies on everywhere else for
    // exactly this resilience) so one malformed record is skipped and
    // logged individually — every other real, well-formed account still
    // shows up.
    private fun parseAllUsersFirebaseResponse(jsonStr: String?): List<UserEntity> {
        if (jsonStr.isNullOrBlank() || jsonStr == "null" || jsonStr == "{}") {
            return emptyList()
        }
        val list = mutableListOf<UserEntity>()
        try {
            val outer = JSONObject(jsonStr)
            val uids = outer.keys()
            while (uids.hasNext()) {
                val uid = uids.next()
                try {
                    val data = outer.optJSONObject(uid) ?: continue
                    val displayName = data.optString("displayName", "")
                    list.add(
                        UserEntity(
                            uid = uid,
                            email = data.optString("email", ""),
                            displayName = displayName,
                            role = data.optString("role", "user"),
                            createdAt = data.optLong("createdAt", 0L),
                            fcmToken = data.optString("fcmToken", ""),
                            isDeveloper = data.optBoolean("isDeveloper", false),
                            devWebsite = data.optString("devWebsite", ""),
                            devGithub = data.optString("devGithub", ""),
                            devName = data.optString("devName", "").ifBlank { displayName },
                            devBio = data.optString("devBio", ""),
                            profilePhotoUrl = data.optString("profilePhotoUrl", ""),
                            devLocation = data.optString("devLocation", ""),
                            isEmailVerified = data.optBoolean("isEmailVerified", true),
                            isPremiumMember = data.optBoolean("isPremiumMember", false),
                            isSuspended = data.optBoolean("isSuspended", false),
                            suspensionReason = data.optString("suspensionReason", "")
                        )
                    )
                } catch (entryError: Exception) {
                    // One bad record — skip just this one, keep going.
                    Log.e(TAG, "Skipping malformed user record '$uid': ${entryError.message}", entryError)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parsing RTDB users failed entirely: ${e.message}", e)
        }
        return list
    }

    // Kept the name "fetchDevelopers" since every other call site (name
    // uniqueness checks, the admin Users tab, refreshDevelopers()) already
    // uses it — but it now genuinely returns every user account, which is
    // what all of those call sites actually needed anyway (the uniqueness
    // checks, for instance, need to catch a name clash with ANY account,
    // not just ones that happen to already be developers).
    suspend fun fetchDevelopers(): List<UserEntity> = withContext(Dispatchers.IO) {
        fetchAllUsersFromRTDB()
    }

    suspend fun updateSubmissionStatus(id: String, entity: SubmissionEntity): Boolean = withContext(Dispatchers.IO) {
        val ok = saveSubmissionToRTDB(entity)
        // Review finished (approved / rejected / anything but Pending): free the package so the
        // developer can send the next update.
        if (ok && !entity.status.equals("Pending", ignoreCase = true)) {
            releaseSubmissionLockBlocking(entity.packageName)
        }
        ok
    }

    // ----------------------------------------------------
    // ONE-PENDING-REVIEW-PER-APP LOCK
    // ----------------------------------------------------
    // submissionLocks/{packageKey} = email of the developer whose submission is in review.
    // The database rules only let a node be CREATED when it doesn't exist yet, so even a modified
    // app or a raw API call cannot queue a second review for the same package.
    fun packageLockKey(packageName: String): String =
        packageName.trim().lowercase().replace(Regex("[^a-z0-9_-]"), "_").take(150)

    /** Returns the owner email of the current lock, "" when free, null when the check failed. */
    suspend fun fetchSubmissionLockOwner(packageName: String): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${RTDB_URL}submissionLocks/${packageLockKey(packageName)}.json${getTokenParam()}")
            .get().build()
        try {
            client.newCall(request).execute().use { r ->
                if (!r.isSuccessful) return@withContext null
                val b = r.body?.string()?.trim().orEmpty()
                if (b == "null" || b.isEmpty()) "" else b.trim('"')
            }
        } catch (e: Exception) { null }
    }

    suspend fun acquireSubmissionLock(packageName: String, email: String): Boolean = withContext(Dispatchers.IO) {
        val body = JSONObject.quote(email).toRequestBody(mediaTypeJson)
        val request = Request.Builder()
            .url("${RTDB_URL}submissionLocks/${packageLockKey(packageName)}.json${getTokenParam()}")
            .put(body).build()
        try { client.newCall(request).execute().use { it.isSuccessful } } catch (e: Exception) { false }
    }

    suspend fun releaseSubmissionLock(packageName: String): Boolean = withContext(Dispatchers.IO) {
        releaseSubmissionLockBlocking(packageName)
    }

    private fun releaseSubmissionLockBlocking(packageName: String): Boolean {
        val request = Request.Builder()
            .url("${RTDB_URL}submissionLocks/${packageLockKey(packageName)}.json${getTokenParam()}")
            .delete().build()
        return try { client.newCall(request).execute().use { it.isSuccessful } } catch (e: Exception) { false }
    }


    // ----------------------------------------------------
    // DARKSTORE REPORTS (users -> admin)
    // ----------------------------------------------------
    suspend fun submitDarkstoreReport(r: DarkstoreReport): Boolean = withContext(Dispatchers.IO) {
        val json = JSONObject()
            .put("id", r.id).put("category", r.category).put("message", r.message)
            .put("reporterUid", r.reporterUid).put("reporterEmail", r.reporterEmail)
            .put("reporterName", r.reporterName).put("appVersion", r.appVersion)
            .put("device", r.device).put("createdAt", r.createdAt)
        val request = Request.Builder()
            .url("${RTDB_URL}darkstoreReports/${r.id}.json${getTokenParam()}")
            .put(json.toString().toRequestBody(mediaTypeJson)).build()
        try { client.newCall(request).execute().use { it.isSuccessful } } catch (e: Exception) { false }
    }

    suspend fun fetchDarkstoreReports(): List<DarkstoreReport> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("${RTDB_URL}darkstoreReports.json${getTokenParam()}").get().build()
        try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val body = resp.body?.string()
                if (body.isNullOrBlank() || body == "null") return@withContext emptyList()
                val outer = JSONObject(body)
                val out = mutableListOf<DarkstoreReport>()
                val keys = outer.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val d = outer.optJSONObject(k) ?: continue
                    out.add(DarkstoreReport(
                        id = d.optString("id", k), category = d.optString("category"), message = d.optString("message"),
                        reporterUid = d.optString("reporterUid"), reporterEmail = d.optString("reporterEmail"),
                        reporterName = d.optString("reporterName"), appVersion = d.optString("appVersion"),
                        device = d.optString("device"), createdAt = d.optLong("createdAt")
                    ))
                }
                out.sortedByDescending { it.createdAt }
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun deleteDarkstoreReport(id: String): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("${RTDB_URL}darkstoreReports/${id.replace(Regex("[^A-Za-z0-9_-]"), "")}.json${getTokenParam()}").delete().build()
        try { client.newCall(request).execute().use { it.isSuccessful } } catch (e: Exception) { false }
    }


    // ----------------------------------------------------
    // REFERRALS (invite friends -> Premium)
    // ----------------------------------------------------
    data class ReferralConfig(
        val enabled: Boolean = false,
        val required: Int = 3,            // referrals needed per reward
        val rewardDays: Int = 0,          // 0 = lifetime Premium
        val accountWindowDays: Int = 7    // a friend can only be referred while their account is this new
    )
    data class ReferralStats(val count: Int = 0, val claimed: Int = 0, val premiumUntil: Long = 0L)

    private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    /** null = request failed, "null" = empty node, otherwise the raw JSON body. */
    private fun rtdbGetRaw(path: String): String? = try {
        val req = Request.Builder().url("${RTDB_URL}$path.json${getTokenParam()}").get().build()
        client.newCall(req).execute().use { r -> if (!r.isSuccessful) null else (r.body?.string()?.trim() ?: "null") }
    } catch (e: Exception) { null }

    private fun rtdbPutRaw(path: String, json: String): Boolean = try {
        val req = Request.Builder().url("${RTDB_URL}$path.json${getTokenParam()}").put(json.toRequestBody(mediaTypeJson)).build()
        client.newCall(req).execute().use { it.isSuccessful }
    } catch (e: Exception) { false }

    private fun referralCodeFor(uid: String, salt: Int): String {
        val h = java.security.MessageDigest.getInstance("SHA-256").digest("$uid#$salt".toByteArray())
        return (0 until 8).map { CODE_ALPHABET[(h[it].toInt() and 0xFF) % CODE_ALPHABET.length] }.joinToString("")
    }

    suspend fun fetchReferralConfig(): ReferralConfig = withContext(Dispatchers.IO) {
        val raw = rtdbGetRaw("premiumConfig/referral")
        if (raw == null || raw == "null") return@withContext ReferralConfig()
        try {
            val j = JSONObject(raw)
            ReferralConfig(
                enabled = j.optBoolean("enabled", false),
                required = j.optInt("required", 3).coerceIn(1, 100),
                rewardDays = j.optInt("rewardDays", 0).coerceIn(0, 3650),
                accountWindowDays = j.optInt("accountWindowDays", 7).coerceIn(1, 365)
            )
        } catch (e: Exception) { ReferralConfig() }
    }

    suspend fun saveReferralConfig(c: ReferralConfig): Boolean = withContext(Dispatchers.IO) {
        val j = JSONObject().put("enabled", c.enabled).put("required", c.required)
            .put("rewardDays", c.rewardDays).put("accountWindowDays", c.accountWindowDays)
        rtdbPutRaw("premiumConfig/referral", j.toString())
    }

    suspend fun fetchReferralStats(uid: String): ReferralStats = withContext(Dispatchers.IO) {
        val raw = rtdbGetRaw("referralStats/$uid")
        if (raw == null || raw == "null") return@withContext ReferralStats()
        try {
            val j = JSONObject(raw)
            ReferralStats(j.optInt("count", 0), j.optInt("claimed", 0), j.optLong("premiumUntil", 0L))
        } catch (e: Exception) { ReferralStats() }
    }

    /** Admin: referrer uid -> count, highest first. */
    suspend fun fetchAllReferralCounts(): List<Pair<String, Int>> = withContext(Dispatchers.IO) {
        val raw = rtdbGetRaw("referralStats")
        if (raw == null || raw == "null") return@withContext emptyList()
        try {
            val o = JSONObject(raw)
            val out = mutableListOf<Pair<String, Int>>()
            val it = o.keys()
            while (it.hasNext()) { val k = it.next(); out.add(k to (o.optJSONObject(k)?.optInt("count", 0) ?: 0)) }
            out.filter { it.second > 0 }.sortedByDescending { it.second }
        } catch (e: Exception) { emptyList() }
    }

    // ----------------------------------------------------
    // ANTI-CHEAT (same device, fake accounts, farming)
    // ----------------------------------------------------
    data class FraudConfig(
        val autoSuspend: Boolean = true,      // suspend obvious cheaters automatically
        val maxAccountsPerDevice: Int = 1,    // accounts allowed per phone; every extra account is suspended
        val burstLimit: Int = 5,              // referrals for one person within an hour
        val strikeLimit: Int = 2,             // blocked cheating attempts before the account suspends itself
        val requireVerifiedEmail: Boolean = false // only accounts with a verified email may use referral codes
    )

    data class FraudAlert(
        val id: String,
        val type: String,        // SAME_DEVICE_REFERRAL / DEVICE_FARM / REFERRAL_BURST / DISPOSABLE_EMAIL / REFERRAL_FARMING
        val severity: String,    // HIGH / MEDIUM
        val uid: String,
        val email: String,
        val name: String,
        val relatedUid: String,
        val details: String,
        val createdAt: Long,
        val status: String,      // open / dismissed / resolved
        val autoAction: String   // suspended / flagged / strike
    )


    suspend fun fetchFraudConfig(): FraudConfig = withContext(Dispatchers.IO) {
        val raw = rtdbGetRaw("premiumConfig/fraud")
        if (raw == null || raw == "null") return@withContext FraudConfig()
        try {
            val j = JSONObject(raw)
            FraudConfig(
                autoSuspend = j.optBoolean("autoSuspend", true),
                maxAccountsPerDevice = j.optInt("maxAccountsPerDevice", 1).coerceIn(1, 20),
                burstLimit = j.optInt("burstLimit", 5).coerceIn(2, 100),
                strikeLimit = j.optInt("strikeLimit", 2).coerceIn(1, 10),
                requireVerifiedEmail = j.optBoolean("requireVerifiedEmail", false)
            )
        } catch (e: Exception) { FraudConfig() }
    }

    suspend fun saveFraudConfig(c: FraudConfig): Boolean = withContext(Dispatchers.IO) {
        rtdbPutRaw("premiumConfig/fraud", JSONObject().put("autoSuspend", c.autoSuspend)
            .put("maxAccountsPerDevice", c.maxAccountsPerDevice).put("burstLimit", c.burstLimit)
            .put("strikeLimit", c.strikeLimit).put("requireVerifiedEmail", c.requireVerifiedEmail).toString())
    }

    suspend fun fetchFraudAlerts(): List<FraudAlert> = withContext(Dispatchers.IO) {
        val raw = rtdbGetRaw("fraudAlerts")
        if (raw == null || raw == "null") return@withContext emptyList()
        try {
            val o = JSONObject(raw)
            val out = mutableListOf<FraudAlert>()
            val it = o.keys()
            while (it.hasNext()) {
                val k = it.next(); val d = o.optJSONObject(k) ?: continue
                out.add(FraudAlert(
                    d.optString("id", k), d.optString("type"), d.optString("severity", "MEDIUM"), d.optString("uid"),
                    d.optString("email"), d.optString("name"), d.optString("relatedUid"), d.optString("details"),
                    d.optLong("createdAt"), d.optString("status", "open"), d.optString("autoAction")
                ))
            }
            out.sortedByDescending { it.createdAt }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun setFraudAlertStatus(id: String, status: String): Boolean = withContext(Dispatchers.IO) {
        rtdbPutRaw("fraudAlerts/${id.replace(Regex("[^A-Za-z0-9_-]"), "")}/status", JSONObject.quote(status))
    }


    // ----------------------------------------------------
    // SUSPENDED ACCOUNT AT LOGIN (popup on the login page)
    // ----------------------------------------------------
    data class SuspensionInfo(val reason: String, val yourMasked: String, val primaryMasked: String, val canContinue: Boolean)

    /** Set when a sign-in is refused because the account is suspended; the ViewModel shows the popup. */
    val suspensionEvents = kotlinx.coroutines.flow.MutableStateFlow<SuspensionInfo?>(null)


    /**
     * Registers this phone with the DarkStore server AT LOGIN. If the server says this is an extra account on a phone
     * that already has one (only one account is allowed per phone) it has just suspended it, and we refuse the login
     * right here - the popup shows on the login page and no session is ever saved. null = fine (or server unreachable).
     */
    private suspend fun deviceGate(context: Context, idToken: String): String? {
        if (idToken.isBlank() || idToken.startsWith("google_oauth_token_") || idToken.startsWith("fake_token_")) return null
        val res = com.darkstore.utils.NotifierServer.postJson(
            "/device/register", idToken,
            JSONObject().put("deviceHash", com.darkstore.utils.FraudRules.deviceHash(context))
                .put("emulator", com.darkstore.utils.FraudRules.isEmulator())
        ) ?: return null
        return if (res.optBoolean("suspended")) res.optString("reason").ifBlank { "Only one account is allowed per phone." } else null
    }

    /** da***e@gmail.com - never show a full address on the login screen. */
    fun maskEmail(e: String): String {
        val t = e.trim()
        val at = t.lastIndexOf('@')
        if (at <= 0) return ""
        val local = t.substring(0, at)
        val masked = when {
            local.length <= 2 -> local.take(1) + "***"
            local.length <= 4 -> local.take(1) + "***" + local.takeLast(1)
            else -> local.take(2) + "***" + local.takeLast(1)
        }
        return masked + t.substring(at)
    }

    /**
     * A suspended account tried to sign in: forget its session right now (it must not stay "remembered"),
     * ask the server for the masked addresses + the FIRST account on this phone, and raise the popup event.
     */
    private suspend fun suspendedLogin(reason: String, email: String, idToken: String): Triple<Boolean, String?, UserEntity?> {
        var info = SuspensionInfo(reason, maskEmail(email), "", false)
        val realToken = idToken.isNotBlank() && !idToken.startsWith("google_oauth_token_") && !idToken.startsWith("fake_token_")
        if (realToken) {
            val res = com.darkstore.utils.NotifierServer.postJson("/account/suspension-info", idToken, JSONObject())
            if (res != null && res.optBoolean("ok") && res.optBoolean("suspended")) {
                info = SuspensionInfo(
                    res.optString("reason").ifBlank { reason },
                    res.optString("yourMasked").ifBlank { maskEmail(email) },
                    res.optString("primaryMasked"),
                    res.optBoolean("canContinue")
                )
            }
        }
        activeToken = ""
        activeRefreshToken = ""
        suspensionEvents.value = info
        return Triple(false, "Account suspended: $reason", null)
    }

    suspend fun deleteSubmission(id: String): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${RTDB_URL}submissions/${id}.json${getTokenParam()}")
            .delete().build()
        try { client.newCall(request).execute().use { it.isSuccessful } } catch (e: Exception) { false }
    }

    // ----------------------------------------------------
    // PRIVATE DETAILS & STORAGE
    // ----------------------------------------------------
    private fun parseAuthError(bodyStr: String?): String {
        if (bodyStr.isNullOrBlank()) return "Authentication request failed."
        return try {
            val json = JSONObject(bodyStr)
            val error = json.getJSONObject("error")
            val message = error.getString("message")
            when (message) {
                "EMAIL_EXISTS" -> "This email address is already in use by another user."
                "INVALID_EMAIL" -> "Please enter a valid email address."
                "EMAIL_NOT_FOUND", "INVALID_PASSWORD" -> "Incorrect email credentials or password."
                "WEAK_PASSWORD" -> "The password must be at least 6 characters long."
                "USER_DISABLED" -> "This user account has been disabled."
                else -> message.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
            }
        } catch (e: Exception) {
            "Error: Security authorization failed. Check database keys."
        }
    }

    private fun saveLocalUser(context: Context, user: UserEntity, token: String, refreshToken: String = "") {
        val prefs = context.getSharedPreferences("dark_store_pref", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean("is_logged_in", true)
            putString("user_email", user.email)
            putString("user_name", user.displayName)
            putString("user_uid", user.uid)
            putString("user_role", user.role)
            putBoolean("is_developer", user.isDeveloper)
            putString("dev_website", user.devWebsite)
            putString("dev_github", user.devGithub)
            putString("dev_name", user.devName)
            putString("dev_bio", user.devBio)
            putBoolean("is_email_verified", user.isEmailVerified)
            putLong("token_fetched_at", System.currentTimeMillis())
            apply()
        }
        // SECURITY: credentials go to the Keystore-encrypted store, never plain prefs.
        com.darkstore.utils.SecureStore.putString(context, "auth_id_token", token)
        if (refreshToken.isNotBlank()) {
            com.darkstore.utils.SecureStore.putString(context, "auth_refresh_token", refreshToken)
        }
        activeToken = token
        if (refreshToken.isNotBlank()) {
            activeRefreshToken = refreshToken
        }
        FirebaseService.activeToken = token
    }

    suspend fun refreshIdTokenIfNeeded(context: Context, force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("dark_store_pref", Context.MODE_PRIVATE)
        val refreshToken = com.darkstore.utils.SecureStore.getString(context, "auth_refresh_token").ifBlank { activeRefreshToken }
        val fetchTime = prefs.getLong("token_fetched_at", 0L)
        val isExpired = force || (System.currentTimeMillis() - fetchTime > 50 * 60 * 1000) // 50 minutes threshold

        if (refreshToken.isBlank() || !isExpired || refreshToken.startsWith("sim_token") || refreshToken.startsWith("fake_token")) {
            return@withContext false
        }

        Log.d(TAG, "Dynamic developer session expired or forced. Restoring credentials.")
        val targetKey = if (DEFAULT_API_KEY.isBlank() || DEFAULT_API_KEY.startsWith("AIzaSy_placeholder")) {
            "AIzaSyDWAQ3MmbZwzIQ9zNZvN9lep-_W6dIbv9o"
        } else {
            DEFAULT_API_KEY
        }

        val url = "https://securetoken.googleapis.com/v1/token?key=$targetKey"
        val payload = "grant_type=refresh_token&refresh_token=$refreshToken"
        val body = payload.toRequestBody("application/x-www-form-urlencoded".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string()
                if (response.isSuccessful && bodyStr != null) {
                    val resJson = JSONObject(bodyStr)
                    val newIdToken = resJson.getString("id_token")
                    val newRefreshToken = resJson.optString("refresh_token").ifBlank { refreshToken }

                    com.darkstore.utils.SecureStore.putString(context, "auth_id_token", newIdToken)
                    com.darkstore.utils.SecureStore.putString(context, "auth_refresh_token", newRefreshToken)
                    prefs.edit().putLong("token_fetched_at", System.currentTimeMillis()).apply()
                    activeToken = newIdToken
                    activeRefreshToken = newRefreshToken
                    FirebaseService.activeToken = newIdToken
                    Log.d(TAG, "Dynamic space session renewed successfully!")
                    true
                } else {
                    Log.e(TAG, "Failed auto-renewing credentials: HTTP ${response.code}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception auto-renewing space token: ${e.message}", e)
            false
        }
    }
}
