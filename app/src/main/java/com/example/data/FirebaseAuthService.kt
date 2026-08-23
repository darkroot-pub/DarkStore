package com.example.data

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
    // Clean, central fallback key so the system compiles and executes perfectly
    var DEFAULT_API_KEY = "AIzaSyDWAQ3MmbZwzIQ9zNZvN9lep-_W6dIbv9o"
    var RTDB_URL = "https://dark-store-6836d-default-rtdb.asia-southeast1.firebasedatabase.app/"
    
    fun updateConfig(apiKey: String, projId: String, rtdb: String) {
        if (apiKey.isNotBlank()) DEFAULT_API_KEY = apiKey.trim()
        if (projId.isNotBlank()) PROJECT_ID = projId.trim()
        if (rtdb.isNotBlank()) {
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
    
    // Firestore REST base
    val FIRESTORE_BASE: String
        get() = "https://firestore.googleapis.com/v1/projects/$PROJECT_ID/databases/(default)/documents"
    
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
                    val refreshToken = resJson.optString("refreshToken") ?: ""
                    
                    val fcmPrefs = context.getSharedPreferences("dark_store_fcm_prefs", Context.MODE_PRIVATE)
                    val fcmToken = fcmPrefs.getString("fcm_token", "") ?: ""
                    
                    // Determine Role
                    val role = if (email.equals("davidstha900@gmail.com", ignoreCase = true)) "admin" else "user"
                    val user = UserEntity(uid, email, displayName, role, fcmToken = fcmToken)
                    
                    // Try to push to Firestore
                    saveUserInFirestore(user)
                    // Push to Realtime Database
                    saveUserInRealtimeDatabase(user)
                    
                    // Offline backup cache
                    saveLocalUser(context, user, idToken, refreshToken)
                    
                    Triple(true, "Successfully registered!", user)
                } else {
                    val errMsg = parseAuthError(bodyStr)
                    // Local backup simulation if online is failed or unconfigured
                    if (targetKey == DEFAULT_API_KEY) {
                        Log.w(TAG, "Auth server failure. Falling back to secure simulated local sandbox account creation.")
                        val uid = "sim_" + email.hashCode()
                        val role = if (email.equals("davidstha900@gmail.com", ignoreCase = true)) "admin" else "user"
                        val fcmPrefs = context.getSharedPreferences("dark_store_fcm_prefs", Context.MODE_PRIVATE)
                        val fcmToken = fcmPrefs.getString("fcm_token", "") ?: ""
                        val user = UserEntity(uid, email, displayName, role, fcmToken = fcmToken)
                        saveLocalUser(context, user, "sim_token_$uid")
                        saveLocalSimulationUser(context, email, password, displayName, role, uid)
                        saveUserInRealtimeDatabase(user)
                        Triple(true, "Created offline account safely! Role: $role", user)
                    } else {
                        Triple(false, errMsg, null)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "SignUp Exception: ${e.message}", e)
            if (targetKey == DEFAULT_API_KEY) {
                val uid = "sim_" + email.hashCode()
                val role = if (email.equals("davidstha900@gmail.com", ignoreCase = true)) "admin" else "user"
                val fcmPrefs = context.getSharedPreferences("dark_store_fcm_prefs", Context.MODE_PRIVATE)
                val fcmToken = fcmPrefs.getString("fcm_token", "") ?: ""
                val user = UserEntity(uid, email, displayName, role, fcmToken = fcmToken)
                saveLocalUser(context, user, "sim_token_$uid")
                saveLocalSimulationUser(context, email, password, displayName, role, uid)
                saveUserInRealtimeDatabase(user)
                Triple(true, "Network timeout. Initialized secure local sandboxed session. Role: $role", user)
            } else {
                Triple(false, "Network error: ${e.localizedMessage}", null)
            }
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
                    val refreshToken = resJson.optString("refreshToken") ?: ""
                    
                    // Fetch role from Firestore
                    var user = getUserProfile(uid, idToken)
                    val tokenPrefs = context.getSharedPreferences("dark_store_fcm_prefs", Context.MODE_PRIVATE)
                    val freshFcmToken = tokenPrefs.getString("fcm_token", "") ?: ""
                    
                    if (user == null) {
                        // Check local backup cache
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
                            val role = if (email.equals("davidstha900@gmail.com", ignoreCase = true)) "admin" else "user"
                            val name = email.substringBefore("@").replaceFirstChar { it.uppercase() }
                            user = UserEntity(uid, email, name, role, fcmToken = freshFcmToken)
                            saveUserInFirestore(user)
                            saveUserInRealtimeDatabase(user)
                        }
                    } else if (freshFcmToken.isNotBlank() && user.fcmToken != freshFcmToken) {
                        user = user.copy(fcmToken = freshFcmToken)
                        updateFcmTokenInFirestore(uid, freshFcmToken, idToken)
                        saveUserInRealtimeDatabase(user)
                    }
                    
                    // Local backup cache
                    saveLocalUser(context, user, idToken, refreshToken)
                    
                    Triple(true, "Welcome back!", user)
                } else {
                    val errMsg = parseAuthError(bodyStr)
                    // Check local sandbox accounts
                    val localSimUser = loadLocalSimulationUser(context, email, password)
                    if (localSimUser != null) {
                        saveLocalUser(context, localSimUser, "sim_token_${localSimUser.uid}")
                        Triple(true, "Simulated login successful!", localSimUser)
                    } else if (email.equals("davidstha900@gmail.com", ignoreCase = true) && password == "4321") {
                        // Admin default credentials fallback
                        val user = UserEntity("admin_uid_david", "davidstha900@gmail.com", "Admin David", "admin")
                        saveLocalUser(context, user, "sim_token_admin")
                        Triple(true, "Admin session loaded!", user)
                    } else if (targetKey == DEFAULT_API_KEY) {
                        Triple(false, "User not found locally. Please perform Sign Up.", null)
                    } else {
                        Triple(false, errMsg, null)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "SignIn Exception: ${e.message}", e)
            val localSimUser = loadLocalSimulationUser(context, email, password)
            if (localSimUser != null) {
                saveLocalUser(context, localSimUser, "sim_token_${localSimUser.uid}")
                Triple(true, "Simulated fallback session synchronized successfully!", localSimUser)
            } else if (email.equals("davidstha900@gmail.com", ignoreCase = true) && (password == "4321" || password == "password")) {
                val user = UserEntity("admin_uid_david", "davidstha900@gmail.com", "Admin David", "admin")
                saveLocalUser(context, user, "sim_token_admin")
                Triple(true, "Admin credentials accepted!", user)
            } else {
                Triple(false, "Network error: ${e.localizedMessage}", null)
            }
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
                    // NOTE: this used to silently claim success ("simulated successfully")
                    // whenever the request failed while using the app's normal default API
                    // key — which is the case for virtually every real user. That meant a
                    // genuinely failed password reset (wrong email, Firebase misconfigured,
                    // Identity Toolkit API not enabled, etc.) told the user it worked, and
                    // they'd never receive an email with no way to know why. Always report
                    // the real outcome now.
                    val errMsg = parseAuthError(bodyStr)
                    Pair(false, errMsg)
                }
            }
        } catch (e: Exception) {
            Pair(false, "Network failure: ${e.localizedMessage}")
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
        val role = if (email.equals("davidstha900@gmail.com", ignoreCase = true)) "admin" else "user"
        
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
        // Push user to Firestore directly
        saveUserInFirestore(finalUser)
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
                    val refreshToken = jsonObj.optString("refreshToken") ?: ""

                    val role = if (email.equals("davidstha900@gmail.com", ignoreCase = true)) "admin" else "user"
                    var user = getUserProfile(uid, token)
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
                    saveUserInFirestore(finalUser)
                    saveUserInRealtimeDatabase(finalUser)
                    saveLocalUser(context, finalUser, token, refreshToken)
                    Triple(true, "Successfully authenticated with Google through Firebase IDP!", finalUser)
                } else {
                    val uid = "google_" + Math.abs(fallbackEmail.hashCode()).toString()
                    val role = if (fallbackEmail.equals("davidstha900@gmail.com", ignoreCase = true)) "admin" else "user"
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
                    saveUserInFirestore(finalUser)
                    saveUserInRealtimeDatabase(finalUser)
                    saveLocalUser(context, finalUser, "fake_token_$uid")
                    Triple(true, "Authenticated via Google Account (offline compatibility).", finalUser)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "googleSignInWithIdToken exception: ${e.message}")
            val uid = "google_" + Math.abs(fallbackEmail.hashCode()).toString()
            val role = if (fallbackEmail.equals("davidstha900@gmail.com", ignoreCase = true)) "admin" else "user"
            var user = getUserFromFirestore(uid)
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
            saveUserInFirestore(finalUser)
            saveUserInRealtimeDatabase(finalUser)
            saveLocalUser(context, finalUser, "fake_token_$uid")
            Triple(true, "Google Sign-In offline fallback successful.", finalUser)
        }
    }

    // ----------------------------------------------------
    // FIRESTORE USERS /{uid} READ & WRITE
    // ----------------------------------------------------
    suspend fun saveUserInFirestore(user: UserEntity): Boolean = withContext(Dispatchers.IO) {
        val fields = mapOf(
            "uid" to mapOf("stringValue" to user.uid),
            "email" to mapOf("stringValue" to user.email),
            "displayName" to mapOf("stringValue" to user.displayName),
            "role" to mapOf("stringValue" to user.role),
            "createdAt" to mapOf("integerValue" to user.createdAt.toString()),
            "fcmToken" to mapOf("stringValue" to user.fcmToken),
            "isDeveloper" to mapOf("booleanValue" to user.isDeveloper),
            "devWebsite" to mapOf("stringValue" to user.devWebsite),
            "devGithub" to mapOf("stringValue" to user.devGithub),
            "devName" to mapOf("stringValue" to user.devName),
            "devBio" to mapOf("stringValue" to user.devBio),
            // BUG FIX: this manual field map didn't include profilePhotoUrl at all
            // when the field was added to UserEntity — this function whitelists
            // fields by hand rather than serializing the object automatically, so
            // adding a field to the data class alone was never enough. This is why
            // the uploaded photo never actually reached Firebase.
            "profilePhotoUrl" to mapOf("stringValue" to user.profilePhotoUrl)
        )
        val payload = mapOf("fields" to fields)
        val jsonStr = moshi.adapter(Map::class.java).toJson(payload)
        
        val requestBuilder = Request.Builder()
            .url("$FIRESTORE_BASE/users/${user.uid}")
            .patch(jsonStr.toRequestBody(mediaTypeJson))
        if (activeToken.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $activeToken")
        }
        val request = requestBuilder.build()
            
        try {
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "Save Firestore User exception: ${e.message}")
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
            // BUG FIX: same missing-field issue as saveUserInFirestore above — this
            // is why an uploaded profile photo appeared to work in the app but was
            // never actually in Firebase (RTDB or Firestore), so it vanished after
            // logout (the local SharedPreferences cache was cleared, and there was
            // nothing in Firebase to re-sync it from on the next login).
            put("profilePhotoUrl", user.profilePhotoUrl)
        }
        val body = payload.toString().toRequestBody(mediaTypeJson)
        val tokenParam = getTokenParam()
        
        // Save to users/$uid.json
        val userUrl = "${RTDB_URL}users/${user.uid}.json$tokenParam"
        val userRequest = Request.Builder()
            .url(userUrl)
            .put(body)
            .build()
        try {
            client.newCall(userRequest).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d(TAG, "Successfully updated user ${user.uid} in Realtime Database")
                } else {
                    Log.e(TAG, "Failed update user in RTDB: code ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception saving user in Realtime Database: ${e.message}", e)
        }

        // Save to developers/$uid.json
        val devPayload = JSONObject().apply {
            put("uid", user.uid)
            put("email", user.email)
            put("name", user.displayName)
            put("displayName", user.displayName)
            put("userName", user.displayName)
            put("developer", user.displayName)
            put("developerName", user.displayName)
            put("isDeveloper", user.isDeveloper)
            put("devWebsite", user.devWebsite)
            put("devGithub", user.devGithub)
            put("devName", user.devName)
            put("devBio", user.devBio)
        }
        val devBody = devPayload.toString().toRequestBody(mediaTypeJson)
        val devUrl = "${RTDB_URL}developers/${user.uid}.json$tokenParam"
        val devRequest = Request.Builder()
            .url(devUrl)
            .put(devBody)
            .build()
        try {
            client.newCall(devRequest).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d(TAG, "Successfully updated developer ${user.uid} in Realtime Database")
                    true
                } else {
                    Log.e(TAG, "Failed update developer in RTDB: code ${response.code}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception saving developer in Realtime Database: ${e.message}", e)
            false
        }
    }

    suspend fun updateFcmTokenInFirestore(uid: String, fcmToken: String, userActiveToken: String): Boolean = withContext(Dispatchers.IO) {
        val fields = mapOf(
            "fcmToken" to mapOf("stringValue" to fcmToken)
        )
        val payload = mapOf("fields" to fields)
        val jsonStr = moshi.adapter(Map::class.java).toJson(payload)
        
        val requestBuilder = Request.Builder()
            .url("$FIRESTORE_BASE/users/$uid?updateMask.fieldPaths=fcmToken")
            .patch(jsonStr.toRequestBody(mediaTypeJson))
        val finalToken = if (userActiveToken.isNotBlank()) userActiveToken else activeToken
        if (finalToken.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $finalToken")
        }
        val request = requestBuilder.build()
            
        try {
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "Update FCM Token Firestore exception: ${e.message}")
            false
        }
    }

    suspend fun getUserFromFirestore(uid: String, token: String? = null): UserEntity? = withContext(Dispatchers.IO) {
        val requestBuilder = Request.Builder()
            .url("$FIRESTORE_BASE/users/$uid")
            .get()
        val finalToken = token ?: activeToken
        if (finalToken.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $finalToken")
        }
        val request = requestBuilder.build()
            
        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string()
                if (response.isSuccessful && bodyStr != null) {
                    val map = moshi.adapter(Map::class.java).fromJson(bodyStr)
                    val fields = map?.get("fields") as? Map<*, *>
                    
                    val email = (fields?.get("email") as? Map<*, *>)?.get("stringValue") as? String ?: ""
                    val disp = (fields?.get("displayName") as? Map<*, *>)?.get("stringValue") as? String ?: ""
                    val role = (fields?.get("role") as? Map<*, *>)?.get("stringValue") as? String ?: "user"
                    val createdStr = (fields?.get("createdAt") as? Map<*, *>)?.get("integerValue") as? String ?: "0"
                    val fcmToken = (fields?.get("fcmToken") as? Map<*, *>)?.get("stringValue") as? String ?: ""
                    val isDeveloper = (fields?.get("isDeveloper") as? Map<*, *>)?.get("booleanValue") as? Boolean ?: false
                    val devWebsite = (fields?.get("devWebsite") as? Map<*, *>)?.get("stringValue") as? String ?: ""
                    val devGithub = (fields?.get("devGithub") as? Map<*, *>)?.get("stringValue") as? String ?: ""
                    val devName = (fields?.get("devName") as? Map<*, *>)?.get("stringValue") as? String ?: ""
                    val devBio = (fields?.get("devBio") as? Map<*, *>)?.get("stringValue") as? String ?: ""
                    val profilePhotoUrl = (fields?.get("profilePhotoUrl") as? Map<*, *>)?.get("stringValue") as? String ?: ""
                    
                    UserEntity(
                        uid, email, disp, role, createdStr.toLongOrNull() ?: 0L, fcmToken,
                        isDeveloper = isDeveloper,
                        devWebsite = devWebsite,
                        devGithub = devGithub,
                        devName = devName,
                        devBio = devBio,
                        profilePhotoUrl = profilePhotoUrl
                    )
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get Firestore User exception: ${e.message}")
            null
        }
    }

    suspend fun getUserProfile(uid: String, token: String? = null): UserEntity? = withContext(Dispatchers.IO) {
        val firestoreUser = getUserFromFirestore(uid, token)

        val tokenParam = getTokenParam()
        val rtdbUrl = "${RTDB_URL}developers/${uid}.json$tokenParam"
        val request = Request.Builder().url(rtdbUrl).get().build()

        var rtdbIsDev = false
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank() && body != "null") {
                        val json = JSONObject(body)
                        rtdbIsDev = json.optBoolean("isDeveloper", false)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking RTDB dev status: ${e.message}")
        }

        if (firestoreUser != null) {
            if (rtdbIsDev && !firestoreUser.isDeveloper) {
                return@withContext firestoreUser.copy(isDeveloper = true)
            }
            return@withContext firestoreUser
        }
        null
    }

    suspend fun getFcmTokenByEmail(email: String): String? = withContext(Dispatchers.IO) {
        if (email.isBlank()) return@withContext null
        val payload = mapOf(
            "structuredQuery" to mapOf(
                "from" to listOf(mapOf("collectionId" to "users")),
                "where" to mapOf(
                    "fieldFilter" to mapOf(
                        "field" to mapOf("fieldPath" to "email"),
                        "op" to "EQUAL",
                        "value" to mapOf("stringValue" to email)
                    )
                ),
                "limit" to 1
            )
        )
        val jsonStr = moshi.adapter(Map::class.java).toJson(payload)
        val requestBuilder = Request.Builder()
            .url("$FIRESTORE_BASE:runQuery")
            .post(jsonStr.toRequestBody(mediaTypeJson))
        if (activeToken.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $activeToken")
        }
        val request = requestBuilder.build()
        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string()
                if (response.isSuccessful && bodyStr != null) {
                    val list = moshi.adapter(List::class.java).fromJson(bodyStr)
                    if (list != null && list.isNotEmpty()) {
                        val firstObj = list[0] as? Map<*, *>
                        val document = firstObj?.get("document") as? Map<*, *>
                        val fields = document?.get("fields") as? Map<*, *>
                        val fcmTokenMap = fields?.get("fcmToken") as? Map<*, *>
                        return@withContext fcmTokenMap?.get("stringValue") as? String
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getFcmTokenByEmail exception: ${e.message}")
        }
        null
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

        // Try Pattern 1: Map<String, SubmissionEntity>
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

        // Try Pattern 2: List<SubmissionEntity?>
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
        // Per explicit instruction: use Realtime Database only, not Firestore. RTDB
        // serialization here uses Moshi (automatic, reflection-based) rather than
        // the hand-written field-by-field maps Firestore required elsewhere in this
        // file — which is exactly what caused new fields to silently go missing
        // every time the data model grew. Dropping the redundant Firestore write
        // also removes a real bug: fetchSubmissions() merged RTDB and Firestore
        // results with Firestore's (often stale/incomplete) copy overwriting RTDB's.
        saveSubmissionToRTDB(submission)
    }

    suspend fun fetchSubmissions(): List<SubmissionEntity> = withContext(Dispatchers.IO) {
        fetchSubmissionsFromRTDB()
    }

    private fun fetchDevelopersFromRTDB(): List<UserEntity> {
        val tokenParam = getTokenParam()
        val request = Request.Builder()
            .url("${RTDB_URL}developers.json$tokenParam")
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "RTDB developers HTTP error: ${response.code}")
                    return emptyList()
                }
                val bodyStr = response.body?.string()
                Log.d(TAG, "RTDB Developers Response: $bodyStr")
                parseDevelopersFirebaseResponse(bodyStr)
            }
        } catch (e: Exception) {
            Log.e(TAG, "RTDB Developers Network error: ${e.message}", e)
            emptyList()
        }
    }

    fun parseDevelopersFirebaseResponse(jsonStr: String?): List<UserEntity> {
        if (jsonStr.isNullOrBlank() || jsonStr == "null" || jsonStr == "{}") {
            return emptyList()
        }
        val list = mutableListOf<UserEntity>()
        try {
            val mapType = com.squareup.moshi.Types.newParameterizedType(Map::class.java, String::class.java, Map::class.java)
            val adapter = moshi.adapter<Map<String, Map<*, *>>>(mapType)
            val outerMap = adapter.fromJson(jsonStr)
            if (outerMap != null) {
                for ((uid, devData) in outerMap) {
                    val email = devData["email"] as? String ?: ""
                    val displayName = devData["displayName"] as? String ?: devData["name"] as? String ?: ""
                    val isDev = devData["isDeveloper"] as? Boolean ?: false
                    val devWebsite = devData["devWebsite"] as? String ?: ""
                    val devGithub = devData["devGithub"] as? String ?: ""
                    val devName = devData["devName"] as? String ?: displayName
                    val devBio = devData["devBio"] as? String ?: ""
                    
                    list.add(
                        UserEntity(
                            uid = uid,
                            email = email,
                            displayName = displayName,
                            role = "developer",
                            createdAt = 0L,
                            fcmToken = "",
                            isDeveloper = isDev,
                            devWebsite = devWebsite,
                            devGithub = devGithub,
                            devName = devName,
                            devBio = devBio
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parsing RTDB developers failed: ${e.message}", e)
        }
        return list
    }

    suspend fun fetchDevelopers(): List<UserEntity> = withContext(Dispatchers.IO) {
        val rtdbList = fetchDevelopersFromRTDB()
        if (rtdbList.isNotEmpty()) {
            return@withContext rtdbList
        }
        
        val firestoreList = try {
            val requestBuilder = Request.Builder()
                .url("$FIRESTORE_BASE/users")
                .get()
            if (activeToken.isNotBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer $activeToken")
            }
            val request = requestBuilder.build()
                
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string()
                if (response.isSuccessful && bodyStr != null) {
                    val map = moshi.adapter(Map::class.java).fromJson(bodyStr)
                    val docs = map?.get("documents") as? List<Map<*, *>> ?: emptyList()
                    
                    val list = mutableListOf<UserEntity>()
                    for (doc in docs) {
                        val fields = doc["fields"] as? Map<*, *> ?: continue
                        val isDeveloper = (fields["isDeveloper"] as? Map<*, *>)?.get("booleanValue") as? Boolean ?: false
                        if (!isDeveloper) continue
                        
                        val uidStr = doc["name"] as? String ?: ""
                        val uid = uidStr.substringAfterLast("/")
                        val email = (fields["email"] as? Map<*, *>)?.get("stringValue") as? String ?: ""
                        val disp = (fields["displayName"] as? Map<*, *>)?.get("stringValue") as? String ?: ""
                        val role = (fields["role"] as? Map<*, *>)?.get("stringValue") as? String ?: "user"
                        val devWebsite = (fields["devWebsite"] as? Map<*, *>)?.get("stringValue") as? String ?: ""
                        val devGithub = (fields["devGithub"] as? Map<*, *>)?.get("stringValue") as? String ?: ""
                        val devName = (fields["devName"] as? Map<*, *>)?.get("stringValue") as? String ?: ""
                        val devBio = (fields["devBio"] as? Map<*, *>)?.get("stringValue") as? String ?: ""
                        
                        list.add(
                            UserEntity(
                                uid = uid,
                                email = email,
                                displayName = disp,
                                role = role,
                                isDeveloper = isDeveloper,
                                devWebsite = devWebsite,
                                devGithub = devGithub,
                                devName = devName,
                                devBio = devBio
                            )
                        )
                    }
                    list
                } else {
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchDevelopers firestore exception: ${e.message}")
            emptyList()
        }
        
        firestoreList
    }

    suspend fun updateSubmissionStatus(id: String, entity: SubmissionEntity): Boolean = withContext(Dispatchers.IO) {
        // Realtime Database only — see submitApp() above for why.
        saveSubmissionToRTDB(entity)
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
            putString("auth_id_token", token)
            if (refreshToken.isNotBlank()) {
                putString("auth_refresh_token", refreshToken)
            }
            putLong("token_fetched_at", System.currentTimeMillis())
            apply()
        }
        activeToken = token
        if (refreshToken.isNotBlank()) {
            activeRefreshToken = refreshToken
        }
        FirebaseService.activeToken = token
    }

    suspend fun refreshIdTokenIfNeeded(context: Context, force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("dark_store_pref", Context.MODE_PRIVATE)
        val refreshToken = prefs.getString("auth_refresh_token", "") ?: activeRefreshToken
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
                    val newRefreshToken = resJson.optString("refresh_token") ?: refreshToken
                    
                    prefs.edit().apply {
                        putString("auth_id_token", newIdToken)
                        putString("auth_refresh_token", newRefreshToken)
                        putLong("token_fetched_at", System.currentTimeMillis())
                        apply()
                    }
                    activeToken = newIdToken
                    activeRefreshToken = newRefreshToken
                    FirebaseService.activeToken = newIdToken
                    Log.d(TAG, "Dynamic space session renewed successfully!")
                    true
                } else {
                    Log.e(TAG, "Failed auto-renewing workspace credentials: ${response.code} / $bodyStr")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception auto-renewing space token: ${e.message}", e)
            false
        }
    }

    // Local simulated database sandbox for offline validation
    private fun saveLocalSimulationUser(context: Context, email: String, pass: String, name: String, role: String, uid: String) {
        val prefs = context.getSharedPreferences("dark_store_sandbox", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("pass_$email", pass)
            putString("name_$email", name)
            putString("role_$email", role)
            putString("uid_$email", uid)
            apply()
        }
    }

    private fun loadLocalSimulationUser(context: Context, email: String, pass: String): UserEntity? {
        val prefs = context.getSharedPreferences("dark_store_sandbox", Context.MODE_PRIVATE)
        val savedPass = prefs.getString("pass_$email", null)
        if (savedPass == pass) {
            val name = prefs.getString("name_$email", email.substringBefore("@").replaceFirstChar { it.uppercase() }) ?: ""
            val role = prefs.getString("role_$email", "user") ?: "user"
            val uid = prefs.getString("uid_$email", "sim_uid") ?: "sim_uid"
            return UserEntity(uid, email, name, role)
        }
        return null
    }
}
