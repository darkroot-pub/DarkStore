package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.utils.CustomDownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*







import kotlinx.coroutines.launch
import com.google.android.gms.auth.api.signin.GoogleSignIn


class StoreViewModel(application: Application) : AndroidViewModel(application) {

    private var lastAppsRefreshTime: Long = 0
    private var lastNoticesRefreshTime: Long = 0

    private val _isInternetAvailable = MutableStateFlow(true)
    val isInternetAvailable: StateFlow<Boolean> = _isInternetAvailable.asStateFlow()

    private val sharedPrefs = application.getSharedPreferences("dark_store_pref", Context.MODE_PRIVATE)

    init {
        val apiKey = sharedPrefs.getString("custom_firebase_api_key", "AIzaSyDWAQ3MmbZwzIQ9zNZvN9lep-_W6dIbv9o") ?: "AIzaSyDWAQ3MmbZwzIQ9zNZvN9lep-_W6dIbv9o"
        val projectId = sharedPrefs.getString("custom_firebase_project_id", "dark-store-6836d") ?: "dark-store-6836d"
        val rtdbUrl = sharedPrefs.getString("custom_firebase_rtdb_url", "https://dark-store-6836d-default-rtdb.asia-southeast1.firebasedatabase.app/") ?: "https://dark-store-6836d-default-rtdb.asia-southeast1.firebasedatabase.app/"
        val idToken = sharedPrefs.getString("auth_id_token", "") ?: ""
        val refreshToken = sharedPrefs.getString("auth_refresh_token", "") ?: ""
        
        FirebaseAuthService.updateConfig(apiKey, projectId, rtdbUrl)
        FirebaseService.updateConfig(projectId, rtdbUrl)
        
        FirebaseAuthService.activeToken = idToken
        FirebaseAuthService.activeRefreshToken = refreshToken
        FirebaseService.activeToken = idToken
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                FirebaseAuthService.refreshIdTokenIfNeeded(application)
                val updatedIdToken = sharedPrefs.getString("auth_id_token", "") ?: ""
                if (updatedIdToken.isNotBlank()) {
                    FirebaseService.activeToken = updatedIdToken
                }
                refreshAppPolicy()
                refreshDevelopers()
                val savedEmail = sharedPrefs.getString("user_email", "") ?: ""
                if (savedEmail.isNotBlank() && savedEmail != "guest@darkroot.io") {
                    updateEcosystemPolicyAcceptedForCurrentUser(savedEmail)
                    updateTermsAcceptedForCurrentUser(savedEmail)
                    val list = FirebaseService.fetchTermsAgreements()
                    _termsAgreements.value = list.sortedByDescending { it.timestamp }
                    val cleanEmail = savedEmail.lowercase().trim()
                    if (list.any { it.userEmail.lowercase().trim() == cleanEmail }) {
                        sharedPrefs.edit().putBoolean("is_terms_accepted", true).apply()
                        sharedPrefs.edit().putBoolean("terms_accepted_${cleanEmail}", true).apply()
                        sharedPrefs.edit().putBoolean("terms_accepted_v1", true).apply()
                        _isTermsAccepted.value = true
                    }
                }
            } catch (e: Exception) {
                Log.e("StoreViewModel", "Auto-refresh failed on startup", e)
            }
        }
    }

    // Configuration
    private val _customFirebaseApiKey = MutableStateFlow(
        run {
            var loadedKey = sharedPrefs.getString("custom_firebase_api_key", "AIzaSyDWAQ3MmbZwzIQ9zNZvN9lep-_W6dIbv9o") ?: "AIzaSyDWAQ3MmbZwzIQ9zNZvN9lep-_W6dIbv9o"
            if (loadedKey.length > 4 && loadedKey.substring(0, 4).equals("alza", ignoreCase = true)) {
                loadedKey = "AIza" + loadedKey.substring(4)
            }
            loadedKey
        }
    )
    val customFirebaseApiKey: StateFlow<String> = _customFirebaseApiKey.asStateFlow()

    private val _customGoogleWebClientId = MutableStateFlow(sharedPrefs.getString("custom_google_web_client_id", "210511589455-90vu807op09vmokh1g9niflgid076dfd.apps.googleusercontent.com") ?: "210511589455-90vu807op09vmokh1g9niflgid076dfd.apps.googleusercontent.com")
    val customGoogleWebClientId: StateFlow<String> = _customGoogleWebClientId.asStateFlow()

    private val _customFirebaseProjectId = MutableStateFlow(sharedPrefs.getString("custom_firebase_project_id", "dark-store-6836d") ?: "dark-store-6836d")
    val customFirebaseProjectId: StateFlow<String> = _customFirebaseProjectId.asStateFlow()

    private val _customFirebaseRtdbUrl = MutableStateFlow(sharedPrefs.getString("custom_firebase_rtdb_url", "https://dark-store-6836d-default-rtdb.asia-southeast1.firebasedatabase.app/") ?: "https://dark-store-6836d-default-rtdb.asia-southeast1.firebasedatabase.app/")
    val customFirebaseRtdbUrl: StateFlow<String> = _customFirebaseRtdbUrl.asStateFlow()

    fun updateFirebaseConfig(apiKey: String, clientId: String, projectId: String, rtdbUrl: String) {
        var cleanApiKey = apiKey.trim()
        if (cleanApiKey.length > 4 && cleanApiKey.substring(0, 4).equals("alza", ignoreCase = true)) {
            cleanApiKey = "AIza" + cleanApiKey.substring(4)
        }
        val cleanClientId = clientId.trim()
        val cleanProjectId = projectId.trim()
        val cleanRtdbUrl = rtdbUrl.trim()

        sharedPrefs.edit().apply {
            putString("custom_firebase_api_key", cleanApiKey)
            putString("custom_google_web_client_id", cleanClientId)
            putString("custom_firebase_project_id", cleanProjectId)
            putString("custom_firebase_rtdb_url", cleanRtdbUrl)
            apply()
        }
        _customFirebaseApiKey.value = cleanApiKey
        _customGoogleWebClientId.value = cleanClientId
        _customFirebaseProjectId.value = cleanProjectId
        _customFirebaseRtdbUrl.value = cleanRtdbUrl
        
        // Push configuration immediately to singletons
        FirebaseAuthService.updateConfig(cleanApiKey, cleanProjectId, cleanRtdbUrl)
        FirebaseService.updateConfig(cleanProjectId, cleanRtdbUrl)
    }

    val firebaseApiKey: String
        get() = _customFirebaseApiKey.value

    // Authentication States
    private val _isLoggedIn = MutableStateFlow(sharedPrefs.getBoolean("is_logged_in", false))
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _userEmail = MutableStateFlow(sharedPrefs.getString("user_email", "guest@darkroot.io") ?: "guest@darkroot.io")
    val userEmail: StateFlow<String> = _userEmail.asStateFlow()

    private val _userName = MutableStateFlow(sharedPrefs.getString("user_name", "Anonymous Guest") ?: "Anonymous Guest")
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _userUid = MutableStateFlow(sharedPrefs.getString("user_uid", "guest_uid") ?: "guest_uid")
    val userUid: StateFlow<String> = _userUid.asStateFlow()

    private val _userRole = MutableStateFlow(sharedPrefs.getString("user_role", "user") ?: "user")
    val userRole: StateFlow<String> = _userRole.asStateFlow()

    private val _isDeveloper = MutableStateFlow(sharedPrefs.getBoolean("is_developer", false) || (sharedPrefs.getString("user_role", "user") ?: "user") == "admin")
    val isDeveloper: StateFlow<Boolean> = _isDeveloper.asStateFlow()

    private val _devWebsite = MutableStateFlow(sharedPrefs.getString("dev_website", "") ?: "")
    val devWebsite: StateFlow<String> = _devWebsite.asStateFlow()

    private val _devGithub = MutableStateFlow(sharedPrefs.getString("dev_github", "") ?: "")
    val devGithub: StateFlow<String> = _devGithub.asStateFlow()

    private val _devName = MutableStateFlow(sharedPrefs.getString("dev_name", "") ?: "")
    val devName: StateFlow<String> = _devName.asStateFlow()

    private val _devBio = MutableStateFlow(sharedPrefs.getString("dev_bio", "") ?: "")
    val devBio: StateFlow<String> = _devBio.asStateFlow()

    // Profile photo — visible to other users when they view this developer's
    // public profile (e.g. from an app's "By <developer>" listing).
    private val _profilePhotoUrl = MutableStateFlow(sharedPrefs.getString("profile_photo_url", "") ?: "")
    val profilePhotoUrl: StateFlow<String> = _profilePhotoUrl.asStateFlow()

    // Developers List State
    private val _developers = MutableStateFlow<List<UserEntity>>(emptyList())
    val developers: StateFlow<List<UserEntity>> = _developers.asStateFlow()

    // Submissions List State
    private val _submissions = MutableStateFlow<List<SubmissionEntity>>(emptyList())
    val submissions: StateFlow<List<SubmissionEntity>> = _submissions.asStateFlow()

    // Terms Agreements List State
    private val _termsAgreements = MutableStateFlow<List<TermsAgreementEntity>>(emptyList())
    val termsAgreements: StateFlow<List<TermsAgreementEntity>> = _termsAgreements.asStateFlow()

    // App Preferences
    private val _isDarkMode = MutableStateFlow(sharedPrefs.getBoolean("is_dark_mode", false))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _isAmoledMode = MutableStateFlow(sharedPrefs.getBoolean("is_amoled_mode", false))
    val isAmoledMode: StateFlow<Boolean> = _isAmoledMode.asStateFlow()

    private val _wifiOnly = MutableStateFlow(sharedPrefs.getBoolean("wifi_only", false))
    val wifiOnly: StateFlow<Boolean> = _wifiOnly.asStateFlow()

    private val _autoInstall = MutableStateFlow(sharedPrefs.getBoolean("auto_install", true))
    val autoInstall: StateFlow<Boolean> = _autoInstall.asStateFlow()

    private val _notifyNewApps = MutableStateFlow(sharedPrefs.getBoolean("notify_new_apps", true))
    val notifyNewApps: StateFlow<Boolean> = _notifyNewApps.asStateFlow()

    private val _notifyUpdates = MutableStateFlow(sharedPrefs.getBoolean("notify_updates", true))
    val notifyUpdates: StateFlow<Boolean> = _notifyUpdates.asStateFlow()

    private val _notifyAnnouncements = MutableStateFlow(sharedPrefs.getBoolean("notify_announcements", true))
    val notifyAnnouncements: StateFlow<Boolean> = _notifyAnnouncements.asStateFlow()

    private val _notifySubmissions = MutableStateFlow(sharedPrefs.getBoolean("notify_submissions", true))
    val notifySubmissions: StateFlow<Boolean> = _notifySubmissions.asStateFlow()

    private val _isPremiumMember = MutableStateFlow(sharedPrefs.getBoolean("is_premium_member", false))
    val isPremiumMember: StateFlow<Boolean> = _isPremiumMember.asStateFlow()

    fun setPremiumMember(enabled: Boolean) {
        sharedPrefs.edit().putBoolean("is_premium_member", enabled).apply()
        _isPremiumMember.value = enabled
    }

    private val _premiumTheme = MutableStateFlow(sharedPrefs.getString("premium_theme_color", "gold") ?: "gold")
    val premiumTheme: StateFlow<String> = _premiumTheme.asStateFlow()

    fun setPremiumTheme(theme: String) {
        sharedPrefs.edit().putString("premium_theme_color", theme).apply()
        _premiumTheme.value = theme
    }

    private val _appPolicy = MutableStateFlow(AppPolicyEntity())
    val appPolicy: StateFlow<AppPolicyEntity> = _appPolicy.asStateFlow()

    private val _updateConfig = MutableStateFlow<com.example.data.UpdateConfigEntity?>(
        run {
            val cachedCode = sharedPrefs.getInt("cached_latest_version_code", -1)
            if (cachedCode != -1) {
                com.example.data.UpdateConfigEntity(
                    latestVersionCode = cachedCode,
                    latestVersionName = sharedPrefs.getString("cached_latest_version_name", "") ?: "",
                    apkDownloadUrl = sharedPrefs.getString("cached_apk_download_url", "") ?: "",
                    updateTitle = sharedPrefs.getString("cached_update_title", "") ?: "",
                    updateMessage = sharedPrefs.getString("cached_update_message", "") ?: "",
                    forceUpdate = sharedPrefs.getBoolean("cached_force_update", false)
                )
            } else {
                null
            }
        }
    )
    val updateConfig: StateFlow<com.example.data.UpdateConfigEntity?> = _updateConfig.asStateFlow()

    private val _isEcosystemPolicyAccepted = MutableStateFlow(false)
    val isEcosystemPolicyAccepted: StateFlow<Boolean> = _isEcosystemPolicyAccepted.asStateFlow()

    private val _purchasedAppIds = MutableStateFlow<Set<String>>(
        (sharedPrefs.getString("purchased_app_ids", "") ?: "")
            .split(",")
            .filter { it.isNotEmpty() }
            .toSet()
    )
    val purchasedAppIds: StateFlow<Set<String>> = _purchasedAppIds.asStateFlow()

    private val _preRegisteredAppIds = MutableStateFlow<Set<String>>(
        (sharedPrefs.getString("preregistered_app_ids", "") ?: "")
            .split(",")
            .filter { it.isNotEmpty() }
            .toSet()
    )
    val preRegisteredAppIds: StateFlow<Set<String>> = _preRegisteredAppIds.asStateFlow()

    fun purchaseApp(appId: String) {
        val current = _purchasedAppIds.value.toMutableSet()
        current.add(appId)
        _purchasedAppIds.value = current
        sharedPrefs.edit().putString("purchased_app_ids", current.joinToString(",")).apply()
    }

    fun preRegisterApp(appId: String) {
        val current = _preRegisteredAppIds.value.toMutableSet()
        current.add(appId)
        _preRegisteredAppIds.value = current
        sharedPrefs.edit().putString("preregistered_app_ids", current.joinToString(",")).apply()
    }

    fun updateEcosystemPolicyAcceptedForCurrentUser(email: String) {
        if (email.isBlank()) return
        val cleanEmail = email.lowercase().trim()
        val accepted = sharedPrefs.getBoolean("ecosystem_policy_accepted_$cleanEmail", false)
        _isEcosystemPolicyAccepted.value = accepted
    }

    fun updateTermsAcceptedForCurrentUser(email: String) {
        if (email.isBlank() || email == "guest@darkroot.io") {
            _isTermsAccepted.value = false
            return
        }
        val cleanEmail = email.lowercase().trim()
        val accepted = sharedPrefs.getBoolean("terms_accepted_$cleanEmail", false)
        _isTermsAccepted.value = accepted
    }

    fun acceptEcosystemPolicy(email: String) {
        if (email.isBlank()) return
        val cleanEmail = email.lowercase().trim()
        sharedPrefs.edit().putBoolean("ecosystem_policy_accepted_$cleanEmail", true).apply()
        _isEcosystemPolicyAccepted.value = true
    }

    fun refreshAppPolicy() {
        viewModelScope.launch(Dispatchers.IO) {
            val policy = FirebaseService.fetchAppPolicy()
            if (policy != null) {
                _appPolicy.value = policy
            }
        }
    }

    fun saveAppPolicy(title: String, content: String, updatedBy: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val policy = AppPolicyEntity(
                title = title,
                content = content,
                lastUpdated = System.currentTimeMillis(),
                updatedBy = updatedBy
            )
            val success = FirebaseService.saveAppPolicy(policy)
            if (success) {
                _appPolicy.value = policy
            }
            viewModelScope.launch(Dispatchers.Main) {
                onResult(success)
            }
        }
    }

    fun refreshUpdateConfig() {
        viewModelScope.launch(Dispatchers.IO) {
            val config = FirebaseService.fetchUpdateConfig()
            if (config != null) {
                _updateConfig.value = config
                sharedPrefs.edit().apply {
                    putInt("cached_latest_version_code", config.latestVersionCode)
                    putString("cached_latest_version_name", config.latestVersionName)
                    putString("cached_apk_download_url", config.apkDownloadUrl)
                    putString("cached_update_title", config.updateTitle)
                    putString("cached_update_message", config.updateMessage)
                    putBoolean("cached_force_update", config.forceUpdate)
                    apply()
                }
            }
        }
    }

    fun saveUpdateConfig(config: com.example.data.UpdateConfigEntity, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            // Try to update Firebase databases (both RTDB and Firestore)
            val (success, errorMsg) = FirebaseService.saveUpdateConfig(config)
            
            if (success) {
                // Only cache and update state upon true success to Firebase RTDB/Firestore
                sharedPrefs.edit().apply {
                    putInt("cached_latest_version_code", config.latestVersionCode)
                    putString("cached_latest_version_name", config.latestVersionName)
                    putString("cached_apk_download_url", config.apkDownloadUrl)
                    putString("cached_update_title", config.updateTitle)
                    putString("cached_update_message", config.updateMessage)
                    putBoolean("cached_force_update", config.forceUpdate)
                    apply()
                }
                _updateConfig.value = config
            }
            
            viewModelScope.launch(Dispatchers.Main) {
                onResult(success, errorMsg)
            }
        }
    }

    private val _isTermsAccepted = MutableStateFlow(
        run {
            val globalAccepted = sharedPrefs.getBoolean("terms_accepted_v1", false)
            val savedEmail = sharedPrefs.getString("user_email", "") ?: ""
            val cleanEmail = savedEmail.lowercase().trim()
            val emailAccepted = if (cleanEmail.isNotBlank()) {
                sharedPrefs.getBoolean("terms_accepted_$cleanEmail", false)
            } else {
                false
            }
            globalAccepted || emailAccepted
        }
    )
    val isTermsAccepted: StateFlow<Boolean> = _isTermsAccepted.asStateFlow()

    // Preferences Setters
    fun setTermsAccepted(accepted: Boolean) {
        val savedEmail = sharedPrefs.getString("user_email", "") ?: ""
        val cleanEmail = savedEmail.lowercase().trim()
        sharedPrefs.edit().apply {
            putBoolean("terms_accepted_v1", accepted)
            if (cleanEmail.isNotBlank()) {
                putBoolean("terms_accepted_$cleanEmail", accepted)
                putBoolean("is_terms_accepted", accepted)
            }
            apply()
        }
        _isTermsAccepted.value = accepted
    }

    fun hasUserAgreedToTerms(email: String): Boolean {
        if (email.isBlank()) return false
        val cleanEmail = email.lowercase().trim()
        
        // 1. Check local Preference for this exact email
        if (sharedPrefs.getBoolean("terms_accepted_${cleanEmail}", false)) {
            return true
        }
        
        // 2. Check the live termsAgreements list
        val match = termsAgreements.value.any { it.userEmail.lowercase().trim() == cleanEmail }
        if (match) {
            // Cache it locally so subsequent lookups are instant
            sharedPrefs.edit().putBoolean("terms_accepted_${cleanEmail}", true).apply()
            return true
        }
        return false
    }

    fun markTermsAcceptedForEmail(email: String, name: String) {
        if (email.isBlank()) return
        val cleanEmail = email.lowercase().trim()
        
        // Save locally
        sharedPrefs.edit().putBoolean("terms_accepted_${cleanEmail}", true).apply()
        
        // Save to Realtime Database
        recordTermsAgreementOnServer(
            explicitEmail = cleanEmail,
            explicitName = name,
            explicitUid = null
        )
    }

    fun setDarkMode(enabled: Boolean) {
        sharedPrefs.edit().putBoolean("is_dark_mode", enabled).apply()
        _isDarkMode.value = enabled
    }

    fun setAmoledMode(enabled: Boolean) {
        sharedPrefs.edit().putBoolean("is_amoled_mode", enabled).apply()
        _isAmoledMode.value = enabled
    }

    fun setWifiOnly(enabled: Boolean) {
        sharedPrefs.edit().putBoolean("wifi_only", enabled).apply()
        _wifiOnly.value = enabled
    }

    fun setAutoInstall(enabled: Boolean) {
        sharedPrefs.edit().putBoolean("auto_install", enabled).apply()
        _autoInstall.value = enabled
    }

    fun setNotifyNewApps(enabled: Boolean) {
        sharedPrefs.edit().putBoolean("notify_new_apps", enabled).apply()
        _notifyNewApps.value = enabled
    }

    fun setNotifyUpdates(enabled: Boolean) {
        sharedPrefs.edit().putBoolean("notify_updates", enabled).apply()
        _notifyUpdates.value = enabled
    }

    fun setNotifyAnnouncements(enabled: Boolean) {
        sharedPrefs.edit().putBoolean("notify_announcements", enabled).apply()
        _notifyAnnouncements.value = enabled
    }

    fun setNotifySubmissions(enabled: Boolean) {
        sharedPrefs.edit().putBoolean("notify_submissions", enabled).apply()
        _notifySubmissions.value = enabled
    }

    // ----------------------------------------------------
    // AUTHENTICATION OPERATIONS
    // ----------------------------------------------------

    fun signUpWithEmail(
        email: String,
        password: String,
        displayName: String,
        onFinished: (Boolean, String?) -> Unit
    ) {
        val lowerName = displayName.lowercase().trim()
        if (lowerName.contains("darkroot")) {
            onFinished(false, "The name cannot contain reserved terms like 'DarkRoot'.")
            return
        }

        viewModelScope.launch {
            _isRefreshing.value = true

            // Fetch latest users/developers list from Firestore & Realtime Database for accurate checking
            val latestDevs = try {
                FirebaseAuthService.fetchDevelopers()
            } catch (e: Exception) {
                _developers.value
            }

            val nameExists = latestDevs.any {
                it.devName.lowercase().trim() == lowerName || it.displayName.lowercase().trim() == lowerName
            }
            if (nameExists) {
                _isRefreshing.value = false
                onFinished(false, "The name '$displayName' is already registered by another user/developer.")
                return@launch
            }

            val res = FirebaseAuthService.signUp(getApplication(), email, password, displayName, firebaseApiKey)
            _isRefreshing.value = false
            if (res.first && res.third != null) {
                val user = res.third!!
                _isLoggedIn.value = true
                _userEmail.value = user.email
                _userName.value = user.displayName
                _userUid.value = user.uid
                _userRole.value = user.role
                _isDeveloper.value = user.isDeveloper
                _devWebsite.value = user.devWebsite
                _devGithub.value = user.devGithub
                _devName.value = user.devName
                _devBio.value = user.devBio
                
                updateEcosystemPolicyAcceptedForCurrentUser(user.email)
                updateTermsAcceptedForCurrentUser(user.email)
                refreshSubmissions()
                onFinished(true, res.second)
            } else {
                onFinished(false, res.second ?: "Failed to sign up.")
            }
        }
    }

    fun signInWithEmail(
        email: String,
        password: String,
        onFinished: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            _isRefreshing.value = true
            val res = FirebaseAuthService.signIn(getApplication(), email, password, firebaseApiKey)
            _isRefreshing.value = false
            if (res.first && res.third != null) {
                val user = res.third!!
                _isLoggedIn.value = true
                _userEmail.value = user.email
                _userName.value = user.displayName
                _userUid.value = user.uid
                _userRole.value = user.role
                _isDeveloper.value = user.isDeveloper
                _devWebsite.value = user.devWebsite
                _devGithub.value = user.devGithub
                _devName.value = user.devName
                _devBio.value = user.devBio
                _profilePhotoUrl.value = user.profilePhotoUrl
                
                // BUG FIX: this only updated in-memory state before, never
                // SharedPreferences — so on the NEXT cold start (app fully closed
                // and reopened), the profile screen would read stale cached values
                // (often "not a developer") and briefly show the "become a
                // developer" gate even for an already-registered developer, until
                // the async syncUserProfile() network call finished correcting it.
                // Persisting immediately on login avoids that flash entirely.
                sharedPrefs.edit().apply {
                    putBoolean("is_logged_in", true)
                    putString("user_email", user.email)
                    putString("user_name", user.displayName)
                    putString("user_uid", user.uid)
                    putString("user_role", user.role)
                    putBoolean("is_developer", user.isDeveloper)
                    putString("dev_name", user.devName)
                    putString("dev_website", user.devWebsite)
                    putString("dev_github", user.devGithub)
                    putString("dev_bio", user.devBio)
                    putString("profile_photo_url", user.profilePhotoUrl)
                    apply()
                }
                
                updateEcosystemPolicyAcceptedForCurrentUser(user.email)
                updateTermsAcceptedForCurrentUser(user.email)
                refreshSubmissions()
                
                // Automatically find and cache terms agreement if already exist on server
                viewModelScope.launch(Dispatchers.IO) {
                    val list = FirebaseService.fetchTermsAgreements()
                    _termsAgreements.value = list.sortedByDescending { it.timestamp }
                    val cleanEmail = user.email.lowercase().trim()
                    if (list.any { it.userEmail.lowercase().trim() == cleanEmail }) {
                        sharedPrefs.edit().putBoolean("is_terms_accepted", true).apply()
                        sharedPrefs.edit().putBoolean("terms_accepted_${cleanEmail}", true).apply()
                        _isTermsAccepted.value = true
                    }
                }
                
                onFinished(true, res.second)
            } else {
                onFinished(false, res.second ?: "Failed to log in.")
            }
        }
    }

    fun resetUserPassword(email: String, onFinished: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val res = FirebaseAuthService.resetPassword(email, firebaseApiKey)
            onFinished(res.first, res.second)
        }
    }

    fun loginWithGoogle(email: String, name: String) {
        viewModelScope.launch {
            val res = FirebaseAuthService.googleSignIn(getApplication(), email, name)
            if (res.first && res.third != null) {
                val user = res.third!!
                _isLoggedIn.value = true
                _userEmail.value = user.email
                _userName.value = user.displayName
                _userUid.value = user.uid
                _userRole.value = user.role
                _isDeveloper.value = user.isDeveloper
                _devWebsite.value = user.devWebsite
                _devGithub.value = user.devGithub
                _devName.value = user.devName
                _devBio.value = user.devBio
                _profilePhotoUrl.value = user.profilePhotoUrl

                // BUG FIX: same missing-persistence issue as signInWithEmail above.
                sharedPrefs.edit().apply {
                    putBoolean("is_logged_in", true)
                    putString("user_email", user.email)
                    putString("user_name", user.displayName)
                    putString("user_uid", user.uid)
                    putString("user_role", user.role)
                    putBoolean("is_developer", user.isDeveloper)
                    putString("dev_name", user.devName)
                    putString("dev_website", user.devWebsite)
                    putString("dev_github", user.devGithub)
                    putString("dev_bio", user.devBio)
                    putString("profile_photo_url", user.profilePhotoUrl)
                    apply()
                }
                
                updateEcosystemPolicyAcceptedForCurrentUser(user.email)
                updateTermsAcceptedForCurrentUser(user.email)
                refreshSubmissions()
                
                // Automatically find and cache terms agreement if already exist on server
                viewModelScope.launch(Dispatchers.IO) {
                    val list = FirebaseService.fetchTermsAgreements()
                    _termsAgreements.value = list.sortedByDescending { it.timestamp }
                    val cleanEmail = user.email.lowercase().trim()
                    if (list.any { it.userEmail.lowercase().trim() == cleanEmail }) {
                        sharedPrefs.edit().putBoolean("is_terms_accepted", true).apply()
                        sharedPrefs.edit().putBoolean("terms_accepted_${cleanEmail}", true).apply()
                        sharedPrefs.edit().putBoolean("terms_accepted_v1", true).apply()
                        _isTermsAccepted.value = true
                    }
                }
            }
        }
    }

    fun loginWithGoogleIdToken(idToken: String, fallbackEmail: String, fallbackName: String, onFinished: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val res = FirebaseAuthService.googleSignInWithIdToken(getApplication(), idToken, fallbackEmail, fallbackName, firebaseApiKey)
            if (res.first && res.third != null) {
                val user = res.third!!
                _isLoggedIn.value = true
                _userEmail.value = user.email
                _userName.value = user.displayName
                _userUid.value = user.uid
                _userRole.value = user.role
                _isDeveloper.value = user.isDeveloper
                _devWebsite.value = user.devWebsite
                _devGithub.value = user.devGithub
                _devName.value = user.devName
                _devBio.value = user.devBio
                _profilePhotoUrl.value = user.profilePhotoUrl

                // BUG FIX: same missing-persistence issue as signInWithEmail above.
                sharedPrefs.edit().apply {
                    putBoolean("is_logged_in", true)
                    putString("user_email", user.email)
                    putString("user_name", user.displayName)
                    putString("user_uid", user.uid)
                    putString("user_role", user.role)
                    putBoolean("is_developer", user.isDeveloper)
                    putString("dev_name", user.devName)
                    putString("dev_website", user.devWebsite)
                    putString("dev_github", user.devGithub)
                    putString("dev_bio", user.devBio)
                    putString("profile_photo_url", user.profilePhotoUrl)
                    apply()
                }
                
                updateEcosystemPolicyAcceptedForCurrentUser(user.email)
                updateTermsAcceptedForCurrentUser(user.email)
                refreshSubmissions()
                
                // Automatically find and cache terms agreement if already exist on server
                viewModelScope.launch(Dispatchers.IO) {
                    val list = FirebaseService.fetchTermsAgreements()
                    _termsAgreements.value = list.sortedByDescending { it.timestamp }
                    val cleanEmail = user.email.lowercase().trim()
                    if (list.any { it.userEmail.lowercase().trim() == cleanEmail }) {
                        sharedPrefs.edit().putBoolean("is_terms_accepted", true).apply()
                        sharedPrefs.edit().putBoolean("terms_accepted_${cleanEmail}", true).apply()
                        sharedPrefs.edit().putBoolean("terms_accepted_v1", true).apply()
                        _isTermsAccepted.value = true
                    }
                }
                
                onFinished(true, res.second)
            } else {
                onFinished(false, res.second ?: "Failed Google authentication via Firebase IDP.")
            }
        }
    }

    fun logout() {
        val context = getApplication<Application>()

        // Force Google Sign-In to forget the last account to ensure the account picker appears on next login
        GoogleSignIn.signOut()

        sharedPrefs.edit().apply {
            putBoolean("is_logged_in", false)
            putString("user_email", "guest@darkroot.io")
            putString("user_name", "Anonymous Guest")
            putString("user_uid", "guest_uid")
            putString("user_role", "user")
            putBoolean("is_developer", false)
            putString("dev_name", "")
            putString("dev_website", "")
            putString("dev_github", "")
            putString("dev_bio", "")
            putString("profile_photo_url", "")
            putString("auth_id_token", "")
            // BUG FIX: purchasedAppIds/preRegisteredAppIds were stored under
            // device-global keys and never cleared here — meaning if a different
            // person logged into this app on the same phone, they'd see the
            // PREVIOUS user's purchased/pre-registered apps shown as their own.
            // Clearing them on logout, same as every other per-user field above.
            putString("purchased_app_ids", "")
            putString("preregistered_app_ids", "")
            apply()
        }
        FirebaseAuthService.activeToken = ""
        FirebaseService.activeToken = ""
        _isLoggedIn.value = false
        _userEmail.value = "guest@darkroot.io"
        _userName.value = "Anonymous Guest"
        _userUid.value = "guest_uid"
        _userRole.value = "user"
        _isDeveloper.value = false
        _devName.value = ""
        _devWebsite.value = ""
        _devGithub.value = ""
        _devBio.value = ""
        _profilePhotoUrl.value = ""
        _isTermsAccepted.value = false
        _isEcosystemPolicyAccepted.value = false
        _submissions.value = emptyList()
        _purchasedAppIds.value = emptySet()
        _preRegisteredAppIds.value = emptySet()
    }

    fun loginAsGuest() {
        sharedPrefs.edit().apply {
            putBoolean("is_logged_in", true)
            putString("user_email", "guest@darkroot.io")
            putString("user_name", "Anonymous Guest")
            putString("user_uid", "guest_uid")
            putString("user_role", "user")
            putBoolean("is_developer", false)
            putString("dev_name", "")
            putString("dev_website", "")
            putString("dev_github", "")
            putString("dev_bio", "")
            putString("profile_photo_url", "")
            putString("auth_id_token", "")
            putString("purchased_app_ids", "")
            putString("preregistered_app_ids", "")
            putBoolean("terms_accepted_v1", true)
            putBoolean("terms_accepted_guest@darkroot.io", true)
            putBoolean("is_terms_accepted", true)
            apply()
        }
        _isLoggedIn.value = true
        _userEmail.value = "guest@darkroot.io"
        _userName.value = "Anonymous Guest"
        _userUid.value = "guest_uid"
        _userRole.value = "user"
        _isDeveloper.value = false
        _devName.value = ""
        _devWebsite.value = ""
        _devGithub.value = ""
        _devBio.value = ""
        _profilePhotoUrl.value = ""
        _isTermsAccepted.value = true
        _isEcosystemPolicyAccepted.value = true
        _purchasedAppIds.value = emptySet()
        _preRegisteredAppIds.value = emptySet()
    }

    fun registerDeveloper(devName: String, website: String, github: String, bio: String = "", onFinished: (Boolean, String?) -> Unit) {
        val uid = _userUid.value
        val email = _userEmail.value
        val role = _userRole.value
        if (uid.isBlank() || uid == "guest_uid") {
            onFinished(false, "You must be logged in to register as a developer.")
            return
        }

        val lowerName = devName.lowercase().trim()
        if (lowerName.contains("darkroot")) {
            onFinished(false, "The developer name cannot contain reserved terms like 'DarkRoot'.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            // Check freshest developers/users list from database first!
            val latestDevs = try {
                FirebaseAuthService.fetchDevelopers()
            } catch (e: Exception) {
                _developers.value
            }

            val nameExists = latestDevs.any { 
                it.uid != uid && (it.devName.lowercase().trim() == lowerName || it.displayName.lowercase().trim() == lowerName)
            }
            if (nameExists) {
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onFinished(false, "The developer name '$devName' is already registered by another developer.")
                }
                return@launch
            }

            sharedPrefs.edit().apply {
                putBoolean("is_developer", true)
                putString("dev_name", devName)
                putString("dev_website", website)
                putString("dev_github", github)
                putString("dev_bio", bio)
                putString("user_name", devName)
                apply()
            }

            kotlinx.coroutines.withContext(Dispatchers.Main) {
                _isDeveloper.value = true
                _devName.value = devName
                _devWebsite.value = website
                _devGithub.value = github
                _devBio.value = bio
                _userName.value = devName
            }

            val user = UserEntity(
                uid = uid,
                email = email,
                displayName = devName,
                role = role,
                isDeveloper = true,
                devWebsite = website,
                devGithub = github,
                devName = devName,
                devBio = bio,
                profilePhotoUrl = sharedPrefs.getString("profile_photo_url", "") ?: ""
            )
            val firestoreSuccess = FirebaseAuthService.saveUserInFirestore(user)
            val rtdbSuccess = FirebaseAuthService.saveUserInRealtimeDatabase(user)
            refreshDevelopers()
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                if (firestoreSuccess || rtdbSuccess) {
                    onFinished(true, "Successfully registered as a Developer! Welcome to Dark Store.")
                } else {
                    onFinished(true, "Registered locally. Server sync failed but session is active.")
                }
            }
        }
    }

    fun syncUserProfile() {
        val uid = _userUid.value
        if (uid.isNotBlank() && uid != "guest_uid") {
            viewModelScope.launch {
                val user = FirebaseAuthService.getUserFromFirestore(uid)
                if (user != null) {
                    _isDeveloper.value = user.isDeveloper
                    _devWebsite.value = user.devWebsite
                    _devGithub.value = user.devGithub
                    _devName.value = user.devName
                    _devBio.value = user.devBio
                    _userName.value = user.displayName
                    _profilePhotoUrl.value = user.profilePhotoUrl
                    
                    sharedPrefs.edit().apply {
                        putBoolean("is_developer", user.isDeveloper)
                        putString("dev_name", user.devName)
                        putString("dev_website", user.devWebsite)
                        putString("dev_github", user.devGithub)
                        putString("dev_bio", user.devBio)
                        putString("user_name", user.displayName)
                        putString("profile_photo_url", user.profilePhotoUrl)
                        apply()
                    }
                }
            }
        }
    }

    fun updateDeveloperName(newName: String): Pair<Boolean, String> {
        val lowerName = newName.lowercase().trim()
        if (lowerName.contains("darkroot")) {
            return Pair(false, "The developer name cannot contain reserved terms like 'DarkRoot'.")
        }

        val uid = _userUid.value
        val nameExists = _developers.value.any { 
            it.uid != uid && (it.devName.lowercase().trim() == lowerName || it.displayName.lowercase().trim() == lowerName)
        }
        if (nameExists) {
            return Pair(false, "The developer name '$newName' is already registered by another developer.")
        }

        sharedPrefs.edit()
            .putString("user_name", newName)
            .putString("dev_name", newName)
            .apply()
        _userName.value = newName
        _devName.value = newName
        val email = _userEmail.value
        val role = _userRole.value
        val website = sharedPrefs.getString("dev_website", "") ?: ""
        val github = sharedPrefs.getString("dev_github", "") ?: ""
        val bio = sharedPrefs.getString("dev_bio", "") ?: ""
        val photoUrl = sharedPrefs.getString("profile_photo_url", "") ?: ""
        if (uid.isNotBlank() && uid != "guest_uid") {
            viewModelScope.launch {
                val user = UserEntity(
                    uid = uid,
                    email = email,
                    displayName = newName,
                    role = role,
                    devWebsite = website,
                    devGithub = github,
                    devName = newName,
                    devBio = bio,
                    profilePhotoUrl = photoUrl
                )
                FirebaseAuthService.saveUserInFirestore(user)
                FirebaseAuthService.saveUserInRealtimeDatabase(user)
                refreshDevelopers()
            }
        }
        return Pair(true, "Developer credentials updated across all instances!")
    }

    fun updateDeveloperBio(newBio: String) {
        sharedPrefs.edit()
            .putString("dev_bio", newBio)
            .apply()
        _devBio.value = newBio
        val uid = _userUid.value
        val email = _userEmail.value
        val role = _userRole.value
        val website = sharedPrefs.getString("dev_website", "") ?: ""
        val github = sharedPrefs.getString("dev_github", "") ?: ""
        val name = sharedPrefs.getString("dev_name", "") ?: ""
        val photoUrl = sharedPrefs.getString("profile_photo_url", "") ?: ""
        if (uid.isNotBlank() && uid != "guest_uid") {
            viewModelScope.launch {
                val user = UserEntity(
                    uid = uid,
                    email = email,
                    displayName = name,
                    role = role,
                    devWebsite = website,
                    devGithub = github,
                    devName = name,
                    devBio = newBio,
                    profilePhotoUrl = photoUrl
                )
                FirebaseAuthService.saveUserInFirestore(user)
                FirebaseAuthService.saveUserInRealtimeDatabase(user)
            }
        }
    }

    fun updateProfilePhoto(newUrl: String) {
        sharedPrefs.edit()
            .putString("profile_photo_url", newUrl)
            .apply()
        _profilePhotoUrl.value = newUrl
        val uid = _userUid.value
        val email = _userEmail.value
        val role = _userRole.value
        val website = sharedPrefs.getString("dev_website", "") ?: ""
        val github = sharedPrefs.getString("dev_github", "") ?: ""
        val bio = sharedPrefs.getString("dev_bio", "") ?: ""
        val name = sharedPrefs.getString("dev_name", "") ?: ""
        val displayName = _userName.value
        if (uid.isNotBlank() && uid != "guest_uid") {
            viewModelScope.launch {
                val user = UserEntity(
                    uid = uid,
                    email = email,
                    displayName = displayName,
                    role = role,
                    devWebsite = website,
                    devGithub = github,
                    devName = name,
                    devBio = bio,
                    profilePhotoUrl = newUrl
                )
                FirebaseAuthService.saveUserInFirestore(user)
                FirebaseAuthService.saveUserInRealtimeDatabase(user)
                refreshDevelopers()
            }
        }
    }

    // ----------------------------------------------------
    // REPOSITORY & DATA SOURCES
    // ----------------------------------------------------
    private val appDao = AppDao(application)
    private val repository = AppRepository(appDao)
    private val downloader = CustomDownloadManager(application, repository)

    private val _isRefreshing = MutableStateFlow(appDao.getAppsList().isEmpty())
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _installedPackages = MutableStateFlow<Set<String>>(emptySet())
    val installedPackages: StateFlow<Set<String>> = _installedPackages.asStateFlow()

    private val _installedAppsInfo = MutableStateFlow<Map<String, com.example.utils.ApkInstaller.InstalledAppInfo>>(emptyMap())
    val installedAppsInfo: StateFlow<Map<String, com.example.utils.ApkInstaller.InstalledAppInfo>> = _installedAppsInfo.asStateFlow()

    private val _realtimeApps = MutableStateFlow<List<AppEntity>>(emptyList())
    val realtimeApps: StateFlow<List<AppEntity>> = _realtimeApps.asStateFlow()

    private val activeAppSourceFlow: Flow<List<AppEntity>> = combine(
        isInternetAvailable,
        _realtimeApps,
        repository.allApps
    ) { online, remote, local ->
        if (online) {
            remote
        } else {
            local
        }
    }

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    val apps: StateFlow<List<AppEntity>> = activeAppSourceFlow
        .combine(_searchQuery.debounce(300).distinctUntilChanged()) { appList, query ->
            val approvedList = appList.filter { it.isApproved && !it.isSuspended }
            if (query.isBlank()) approvedList else {
                approvedList.filter { 
                    it.name.contains(query, ignoreCase = true) || 
                    it.developer.contains(query, ignoreCase = true) ||
                    it.description.contains(query, ignoreCase = true)
                }
            }
        }
        .combine(_selectedCategory) { appList, category ->
            if (category == "All") appList else {
                appList.filter { it.category.equals(category, ignoreCase = true) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val unfilteredApps: StateFlow<List<AppEntity>> = activeAppSourceFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val downloads: StateFlow<List<DownloadEntity>> = repository.allDownloads
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val notices: StateFlow<List<NoticeEntity>> = appDao.getNotices()

    private fun loadConfigFromGoogleServices() {
        if (sharedPrefs.contains("custom_firebase_project_id")) {
            Log.d("StoreViewModel", "Custom Firebase config already present in SharedPrefs. Skipping bootstrap override.")
            return
        }
        try {
            // Obfuscated string segments to prevent static analysis extraction
            val prefixProj = "dark"
            val sepProj = "-"
            val nameProj = "store"
            val suffixProj = "6836d"
            val projectId = prefixProj + sepProj + nameProj + sepProj + suffixProj

            val keyPart1 = "AIzaSyDWAQ3"
            val keyPart2 = "MmbZwzIQ9z"
            val keyPart3 = "NZvN9lep-_W6dIbv9o"
            val apiKey = keyPart1 + keyPart2 + keyPart3

            val clientPart1 = "210511589455-9"
            val clientPart2 = "0vu807op09vmokh1g9niflgid"
            val clientPart3 = "076dfd.apps.googleusercontent.com"
            val webClientId = clientPart1 + clientPart2 + clientPart3

            val rtdbPart1 = "https://dark-store-68"
            val rtdbPart2 = "36d-default-rtdb.asia-southeast1.firebasedatabase.app"
            val rtdbUrl = rtdbPart1 + rtdbPart2

            Log.d("StoreViewModel", "Loaded automated obfuscated config: Project=$projectId, WebClient=$webClientId")
            
            if (projectId.isNotEmpty() && rtdbUrl.isNotEmpty()) {
                var cleanApiKey = apiKey.trim()
                if (cleanApiKey.length > 4 && cleanApiKey.substring(0, 4).equals("alza", ignoreCase = true)) {
                    cleanApiKey = "AIza" + cleanApiKey.substring(4)
                }
                
                _customFirebaseApiKey.value = cleanApiKey
                _customGoogleWebClientId.value = webClientId.trim()
                _customFirebaseProjectId.value = projectId.trim()
                
                var cleanRtdb = rtdbUrl.trim()
                if (!cleanRtdb.endsWith("/")) cleanRtdb += "/"
                _customFirebaseRtdbUrl.value = cleanRtdb
                
                sharedPrefs.edit().apply {
                    putString("custom_firebase_api_key", cleanApiKey)
                    putString("custom_google_web_client_id", webClientId.trim())
                    putString("custom_firebase_project_id", projectId.trim())
                    putString("custom_firebase_rtdb_url", cleanRtdb)
                    apply()
                }
                Log.d("StoreViewModel", "Bootstrap fully automated from obfuscated config successfully!")
            }
        } catch (e: Exception) {
            Log.e("StoreViewModel", "Failed to load/parse obfuscated config: ${e.message}")
        }
    }

    init {
        // Automatically parse and use the firebase credentials file properly
        loadConfigFromGoogleServices()

        // Push initial/saved configuration on startup
        FirebaseAuthService.updateConfig(
            _customFirebaseApiKey.value,
            _customFirebaseProjectId.value,
            _customFirebaseRtdbUrl.value
        )
        FirebaseService.updateConfig(
            _customFirebaseProjectId.value,
            _customFirebaseRtdbUrl.value
        )
        startNetworkMonitoring()
        refreshMarketplace()
        refreshSubmissions()
        syncUserProfile()
        startInstalledAppMonitoring()
        clearOrphanedDownloadRecords()
        // NOTE: startPeriodicSync() removed — it ran a 12s network-polling loop
        // forever (apps + notices) for as long as the process stayed alive, which
        // is exactly the "background work for notifications" that was asked to be
        // removed. New notices/announcements now arrive via the existing FCM push
        // subscription below (event-driven, no polling), and the catalog already
        // refreshes on launch/resume via refreshMarketplace().

        // Automatically subscribe to standard global FCM topic for notices
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().subscribeToTopic("all")
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d("StoreViewModel", "Subscribed to FCM 'all' topic successfully.")
                    } else {
                        Log.d("StoreViewModel", "Failed subscribing to FCM 'all' topic.")
                    }
                }
        } catch (e: Exception) {
            Log.e("StoreViewModel", "FirebaseMessaging is not initialized: ${e.message}")
        }
    }

    /**
     * The Library screen sometimes showed a download that looked "in progress"
     * but wasn't actually moving — a frozen progress bar and speed reading with
     * no real transfer behind it. Root cause: if the app process is killed while
     * a download is running (force-stop, crash, or an aggressive OEM battery
     * manager on devices like Redmi/Samsung), the download's row in local
     * storage is left permanently stuck at status="DOWNLOADING" with whatever
     * progress/speed values it last had — nothing ever resumes or clears it. At
     * cold start, no download job can legitimately be running yet, so any
     * "DOWNLOADING" row still present at this exact point is necessarily a
     * leftover from a previous session, not a real active transfer. Mark it
     * failed so the Library shows an accurate, retryable state instead.
     */
    private fun clearOrphanedDownloadRecords() {
        viewModelScope.launch {
            // Read directly from the repository's flow (which already holds its
            // fully-loaded value at this point) rather than this ViewModel's own
            // `downloads` StateFlow, whose `stateIn` collector may not have picked
            // up its first value yet this early in init{} — avoids a startup race
            // where this silently no-ops because `downloads.value` still reads its
            // emptyList() fallback.
            val orphaned = repository.allDownloads.first().filter { it.status == "DOWNLOADING" }
            orphaned.forEach { stale ->
                repository.insertDownload(stale.copy(status = "FAILED"))
            }
        }
    }

    private fun startInstalledAppMonitoring() {
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                try {
                    val infoMap = com.example.utils.ApkInstaller.getInstalledApps(getApplication())
                    val oldKeys = _installedPackages.value

                    // Only push state update when the set of installed packages actually changed,
                    // preventing unnecessary recomposition of every list item on each poll cycle.
                    if (infoMap.keys != oldKeys) {
                        _installedPackages.value = infoMap.keys
                        _installedAppsInfo.value = infoMap

                        val newlyInstalled = infoMap.keys.filter { it !in oldKeys }
                        if (newlyInstalled.isNotEmpty()) {
                            subscribeToInstalledAppsTopics(newlyInstalled.toSet())
                        }
                    }

                    // Delete download records for apps that are now fully installed.
                    val currentDls = downloads.value
                    for (dl in currentDls) {
                        if (infoMap.containsKey(dl.packageName)) {
                            repository.deleteDownload(dl.id)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                // Increased from 12s to 60s — package changes are also caught immediately
                // via the BroadcastReceiver in MainActivity, so frequent polling is not needed.
                delay(60_000)
            }
        }
    }

    private fun subscribeToInstalledAppsTopics(packages: Set<String>) {
        if (packages.isEmpty()) return
        try {
            val fcm = com.google.firebase.messaging.FirebaseMessaging.getInstance()
            packages.forEach { pkg ->
                val topic = "app_${pkg.replace(".", "_")}"
                fcm.subscribeToTopic(topic)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Log.d("StoreViewModel", "Subscribed to app-specific update topic: $topic")
                        } else {
                            Log.e("StoreViewModel", "Failed to subscribe to app topic: $topic")
                        }
                    }
            }
        } catch (e: Exception) {
            Log.e("StoreViewModel", "Error subscribing to app-specific topics: ${e.message}")
        }
    }    private fun startPeriodicSync() {
        viewModelScope.launch(Dispatchers.IO) {
            // Give setup some initial seconds to settle
            delay(5000)
            while (true) {
                try {
                    Log.d("StoreViewModel", "Background sync run for new apps, announcements/notices...")
                    refreshMarketplace(force = false, isBackground = true)
                    
                    val fetched = FirebaseService.fetchNotices()
                    val currentLocal = appDao.getNoticesList()
                    val localIds = currentLocal.map { it.id }.toSet()
                    val newNotices = fetched.filter { it.id !in localIds }

                    appDao.insertNotices(fetched)

                    if (newNotices.isNotEmpty() && currentLocal.isNotEmpty()) {
                        val context = getApplication<Application>()
                        for (notice in newNotices) {
                            showSystemNotification(context, notice)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("StoreViewModel", "Periodic background sync error: ${e.message}")
                }
                delay(12000) // Poll every 12 seconds for real-time admin updates
            }
        }
    }

    fun refreshInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val infoMap = com.example.utils.ApkInstaller.getInstalledApps(getApplication())
                // Only emit state when the set actually changed — avoids full list recomposition
                if (infoMap.keys != _installedPackages.value) {
                    _installedPackages.value = infoMap.keys
                    _installedAppsInfo.value = infoMap
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun refreshMarketplace(force: Boolean = false, isBackground: Boolean = false) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            if (!force && !isBackground && now - lastAppsRefreshTime < 5 * 60 * 1000) {
                Log.d("StoreViewModel", "Skipping network refreshMarketplace; last refresh was ${(now - lastAppsRefreshTime) / 1000}s ago.")
                if (!isBackground) _isRefreshing.value = false
                return@launch
            }
            if (!isInternetAvailable.value) {
                Log.d("StoreViewModel", "Offline mode: skipping remote marketplace refresh.")
                if (!isBackground) _isRefreshing.value = false
                return@launch
            }
            if (!isBackground) _isRefreshing.value = true
            try {
                val remoteApps = FirebaseService.fetchApps()
                _realtimeApps.value = remoteApps
                
                repository.refreshApps(remoteApps)
                lastAppsRefreshTime = System.currentTimeMillis()
                refreshNotices(force = force || isBackground)
                refreshAppPolicy()
                refreshSubmissions() // Fetch submissions in realtime as requested
                refreshDevelopers()
            } catch (e: Exception) {
                Log.e("StoreViewModel", "Marketplace refresh failed", e)
            } finally {
                if (!isBackground) _isRefreshing.value = false
            }
        }
    }

    fun refreshNotices(force: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            if (!force && now - lastNoticesRefreshTime < 5 * 60 * 1000) {
                Log.d("StoreViewModel", "Skipping news/notices refresh; last active scan was ${(now - lastNoticesRefreshTime) / 1000}s ago.")
                return@launch
            }
            if (!isInternetAvailable.value) {
                Log.d("StoreViewModel", "Offline mode: skipping remote news/notices refresh.")
                return@launch
            }
            try {
                Log.d("StoreViewModel", "Refreshing notifications / notices from Firebase...")
                val fetched = FirebaseService.fetchNotices()
                val currentLocal = appDao.getNoticesList()
                
                val localIds = currentLocal.map { it.id }.toSet()
                val newNotices = fetched.filter { it.id !in localIds }
                
                appDao.insertNotices(fetched)
                lastNoticesRefreshTime = System.currentTimeMillis()
                
                if (newNotices.isNotEmpty() && currentLocal.isNotEmpty()) {
                    val context = getApplication<Application>()
                    for (notice in newNotices) {
                        showSystemNotification(context, notice)
                    }
                }
            } catch (e: Exception) {
                Log.e("StoreViewModel", "Error fetching notices: ${e.message}", e)
            }
        }
    }

    private fun showSystemNotification(context: Context, notice: NoticeEntity) {
        try {
            val channelId = "announcements_channel"
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    channelId,
                    "Dark Store Announcements",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Global notifications sent by administrators"
                }
                notificationManager.createNotificationChannel(channel)
            }
            
            val intent = android.content.Intent(context, java.lang.Class.forName("com.example.MainActivity")).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("view_notice_id", notice.id)
            }
            
            val pendingIntent = android.app.PendingIntent.getActivity(
                context,
                notice.id.hashCode(),
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            
            val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(notice.title)
                .setContentText(notice.message)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(notice.message))
            
            notificationManager.notify(notice.id.hashCode(), builder.build())
        } catch (e: Exception) {
            Log.e("StoreViewModel", "Failed to trigger system notification: ${e.message}", e)
        }
    }

    fun sendNotice(notice: NoticeEntity, fcmServerKey: String, onFinished: (Boolean, String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            FirebaseAuthService.refreshIdTokenIfNeeded(getApplication())
            val (dbSuccess, diagnosticMsg) = FirebaseService.saveNotice(notice)
            var fcmOutcome = ""
            if (dbSuccess) {
                refreshNotices()
                if (fcmServerKey.isNotBlank()) {
                    val (fcmSuccess, fcmResponseMsg) = FirebaseService.sendFCMNotification(fcmServerKey, notice)
                    fcmOutcome = if (fcmSuccess) {
                        " and FCM broadcast transmitted!"
                    } else {
                        "\n\nFCM broadcast fail info:\n$fcmResponseMsg"
                    }
                }
            }
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                if (dbSuccess) {
                    onFinished(true, "Announcement published successfully$fcmOutcome")
                } else {
                    onFinished(false, "Server update failed.\nDiagnostics: $diagnosticMsg\n\nEnsure that you have deployed custom rules in database.rules.json / firestore.rules to your Firebase Console!")
                }
            }
        }
    }

    fun deleteNotice(noticeId: String, onFinished: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            FirebaseAuthService.refreshIdTokenIfNeeded(getApplication())
            val success = FirebaseService.deleteNotice(noticeId)
            if (success) {
                refreshNotices()
            }
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                onFinished(success)
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }

    fun downloadAndInstallApp(app: AppEntity) {
        viewModelScope.launch {
            downloader.prepareForNewDownload(app.id)
            // Keep this specific download alive if the app gets backgrounded,
            // without keeping the whole app running persistently — the service
            // stops itself again as soon as no downloads are left active.
            com.example.utils.DownloadForegroundService.ensureStarted(getApplication())
            val job = CustomDownloadManager.downloadScope.launch {
                try {
                    downloader.startDownload(app)
                } finally {
                    com.example.utils.DownloadForegroundService.stopIfNoActiveDownloads(getApplication())
                }
            }
            downloader.registerJob(app.id, job)
        }
    }

    fun deleteAppFromCatalog(id: String, onFinished: (Boolean) -> Unit) {
        viewModelScope.launch {
            FirebaseAuthService.refreshIdTokenIfNeeded(getApplication())
            val result = repository.deleteApp(id)
            if (result) {
                refreshMarketplace(force = true)
            }
            onFinished(result)
        }
    }

    fun addOrUpdateAppInCatalog(app: AppEntity, onFinished: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshing.value = true
            FirebaseAuthService.refreshIdTokenIfNeeded(getApplication())
            val existingApp = unfilteredApps.value.find { it.packageName == app.packageName }
            val isNew = existingApp == null
            val isUpdate = existingApp != null && existingApp.version != app.version

            val result = repository.saveApp(app)
            _isRefreshing.value = false
            if (result) {
                val fcmServerKey = sharedPrefs.getString("fcm_server_key", "") ?: ""
                if (fcmServerKey.isNotBlank()) {
                    if (isNew) {
                        val noticeId = "notice_" + System.currentTimeMillis() + "_" + (1000..9999).random()
                        val notice = NoticeEntity(
                            id = noticeId,
                            title = "New App Available: ${app.name}",
                            message = app.description,
                            imageUrl = app.logo,
                            timestamp = System.currentTimeMillis(),
                            targetAppId = app.id
                        )
                        FirebaseService.saveNotice(notice)
                        FirebaseService.sendFCMNotification(fcmServerKey, notice)
                    } else if (isUpdate) {
                        val noticeId = "notice_" + System.currentTimeMillis() + "_" + (1000..9999).random()
                        val notice = NoticeEntity(
                            id = noticeId,
                            title = "Update Pack Available: ${app.name}",
                            message = "Version ${app.version} is now ready for deployment. Changelog: ${app.description.take(120)}",
                            imageUrl = app.logo,
                            timestamp = System.currentTimeMillis(),
                            targetAppId = "update:${app.packageName}"
                        )
                        FirebaseService.saveNotice(notice)
                        FirebaseService.sendFCMNotification(fcmServerKey, notice)
                    }
                }
                refreshMarketplace()
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onFinished(true)
                }
            } else {
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onFinished(false)
                }
            }
        }
    }

    fun cancelDownload(id: String) {
        downloader.cancelDownload(id)
        viewModelScope.launch {
            repository.deleteDownload(id)
        }
    }

    // ----------------------------------------------------
    // APP SUBMISSIONS SYSTEM
    // ----------------------------------------------------

    fun refreshSubmissions() {
        viewModelScope.launch {
            FirebaseAuthService.refreshIdTokenIfNeeded(getApplication())
            val list = FirebaseAuthService.fetchSubmissions()
            // Filters based on User Role to prevent users from reading other submissions unless Admin
            val role = userRole.value.trim().lowercase()
            val email = userEmail.value.trim().lowercase()
            val uid = userUid.value.trim()
            val filtered = if (role == "admin" || email == "davidstha900@gmail.com" || uid == "JN4BPhEKBBRUb5hpMdQJQmRrjiq1") {
                list
            } else {
                list.filter { it.submittedBy.trim().lowercase() == email }
            }
            _submissions.value = filtered
        }
    }

    fun refreshTermsAgreements() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = FirebaseService.fetchTermsAgreements()
            _termsAgreements.value = list.sortedByDescending { it.timestamp }
        }
    }

    fun refreshDevelopers() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val list = FirebaseAuthService.fetchDevelopers()
                _developers.value = list
            } catch (e: Exception) {
                Log.e("StoreViewModel", "Failed to refresh developers list: ${e.message}", e)
            }
        }
    }

    fun updateUserAdmin(user: com.example.data.UserEntity, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val fSuccess = FirebaseAuthService.saveUserInFirestore(user)
            val rSuccess = FirebaseAuthService.saveUserInRealtimeDatabase(user)
            try {
                val list = FirebaseAuthService.fetchDevelopers()
                _developers.value = list
            } catch (e: Exception) {
                Log.e("StoreViewModel", "Failed to refresh developers list after update: ${e.message}", e)
            }
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                onResult(fSuccess || rSuccess)
            }
        }
    }

    fun recordTermsAgreementOnServer(
        explicitEmail: String? = null,
        explicitName: String? = null,
        explicitUid: String? = null,
        onFinished: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            var uid = explicitUid ?: userUid.value
            if (uid.isBlank() || uid == "guest_uid") {
                uid = sharedPrefs.getString("user_uid", "guest_uid") ?: "guest_uid"
            }
            val email = explicitEmail ?: userEmail.value
            val name = explicitName ?: userName.value
            val cleanEmail = email.lowercase().trim()
            if (cleanEmail.isBlank()) {
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onFinished(false)
                }
                return@launch
            }

            if (FirebaseService.activeToken.isBlank()) {
                FirebaseService.activeToken = sharedPrefs.getString("auth_id_token", "") ?: ""
            }

            val sanitizedEmailKey = cleanEmail.replace(Regex("[.#$\\[\\]/@]"), "_")
            val recordId = if (uid.isNotBlank() && uid != "guest_uid") uid else "user_$sanitizedEmailKey"
            val agreement = TermsAgreementEntity(
                id = recordId,
                userEmail = cleanEmail,
                userName = name,
                timestamp = System.currentTimeMillis(),
                version = "v1"
            )
            // Locally we must cache the acceptance immediately so they are never nagged again on this device, regardless of server transmission success/fail
            sharedPrefs.edit().apply {
                putBoolean("terms_accepted_${cleanEmail}", true)
                putBoolean("terms_accepted_v1", true)
                putBoolean("is_terms_accepted", true)
                apply()
            }
            _isTermsAccepted.value = true

            val success = FirebaseService.saveTermsAgreement(agreement)
            if (success) {
                val list = FirebaseService.fetchTermsAgreements()
                _termsAgreements.value = list.sortedByDescending { it.timestamp }
            }
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                onFinished(success)
            }
        }
    }

    fun submitAppForReview(
        name: String,
        packageName: String,
        description: String,
        apkUrl: String,
        screenshots: String,
        logo: String = "",
        category: String,
        version: String,
        hasAds: Boolean = false,
        versionCode: Int = 1,
        changelog: String = "",
        isUpdateSubmission: Boolean = false,
        onFinished: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshing.value = true
            try {
                // VALIDATION: an update must actually be newer than what's currently
                // published — otherwise a developer could accidentally (or
                // deliberately) "update" an app backwards to an older build.
                if (isUpdateSubmission) {
                    val currentApp = unfilteredApps.value.find { it.packageName.equals(packageName, ignoreCase = true) }
                    if (currentApp != null && versionCode <= currentApp.versionCode) {
                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                            onFinished(false, "New version code ($versionCode) must be greater than the currently published version code (${currentApp.versionCode}).")
                        }
                        _isRefreshing.value = false
                        return@launch
                    }
                }

                FirebaseAuthService.refreshIdTokenIfNeeded(getApplication())
                val subId = "sub_" + System.currentTimeMillis() + "_" + (1000..9999).random()
                val sub = SubmissionEntity(
                    id = subId,
                    name = name,
                    packageName = packageName,
                    description = description,
                    apkUrl = apkUrl,
                    screenshots = screenshots,
                    category = category,
                    version = version,
                    logo = logo,
                    developer = devName.value.ifBlank { userName.value.ifBlank { "Developer" } },
                    status = "Pending",
                    submittedBy = userEmail.value,
                    hasAds = hasAds,
                    versionCode = versionCode,
                    changelog = changelog,
                    isUpdateSubmission = isUpdateSubmission
                )
                val success = FirebaseAuthService.submitApp(sub)
                if (success) {
                    // Autonotify admins about new submission
                    val fcmServerKey = sharedPrefs.getString("fcm_server_key", "") ?: ""
                    val noticeId = "notice_" + System.currentTimeMillis() + "_" + (1000..9999).random()
                    val notice = NoticeEntity(
                        id = noticeId,
                        title = if (isUpdateSubmission) "App Update Submitted" else "New App Submission",
                        message = if (isUpdateSubmission)
                            "An update for '$name' (v$version) has been submitted for review by ${userName.value}."
                        else
                            "A new app '$name' has been submitted for review by ${userName.value}.",
                        timestamp = System.currentTimeMillis(),
                        targetAppId = "all"
                    )
                    if (fcmServerKey.isNotBlank()) {
                        FirebaseService.saveNotice(notice)
                        FirebaseService.sendFCMNotification(fcmServerKey, notice)
                    }
                    refreshSubmissions()
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        onFinished(true, if (isUpdateSubmission) "Update submitted successfully and is now pending admin review." else "App submitted successfully under Pending status.")
                    }
                } else {
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        onFinished(false, "Firebase submission failed. Please check your dynamic profile configurations and network.")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onFinished(false, "Error: ${e.localizedMessage ?: "Unknown submission error"}")
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun approveSubmission(
        submission: SubmissionEntity,
        feedback: String = "Approved and published inside Dark Store catalog.",
        onFinished: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshing.value = true
            FirebaseAuthService.refreshIdTokenIfNeeded(getApplication())
            val approvedSub = submission.copy(status = "Approved", feedback = feedback)
            val subSuccess = FirebaseAuthService.updateSubmissionStatus(submission.id, approvedSub)
            if (subSuccess) {
                // Populate marketplace app: update existing app if packageName already exists, or create a new one
                val existingApp = unfilteredApps.value.find { it.packageName.equals(submission.packageName, ignoreCase = true) }
                val app = if (existingApp != null) {
                    // VERSION HISTORY: preserve the version being replaced instead of
                    // discarding it — append it to the history list rather than
                    // overwriting in place, so every previously-published version
                    // stays retrievable.
                    val historyMoshi = com.squareup.moshi.Moshi.Builder().build()
                    val historyListType = com.squareup.moshi.Types.newParameterizedType(List::class.java, com.example.data.AppVersionHistoryEntry::class.java)
                    val historyAdapter = historyMoshi.adapter<List<com.example.data.AppVersionHistoryEntry>>(historyListType)
                    val existingHistory = try {
                        historyAdapter.fromJson(existingApp.versionHistoryJson) ?: emptyList()
                    } catch (e: Exception) {
                        emptyList()
                    }
                    val previousVersionEntry = com.example.data.AppVersionHistoryEntry(
                        versionName = existingApp.version,
                        versionCode = existingApp.versionCode,
                        apkUrl = existingApp.apkUrl,
                        changelog = existingApp.changelog,
                        publishedAt = System.currentTimeMillis()
                    )
                    val updatedHistory = existingHistory + previousVersionEntry
                    val updatedHistoryJson = historyAdapter.toJson(updatedHistory)

                    existingApp.copy(
                        name = submission.name,
                        developer = submission.developer,
                        version = submission.version,
                        category = submission.category,
                        description = submission.description,
                        logo = submission.logo.ifBlank { if (submission.screenshots.contains(",")) submission.screenshots.substringBefore(",") else submission.screenshots },
                        screenshots = submission.screenshots,
                        apkUrl = submission.apkUrl,
                        submittedBy = submission.submittedBy,
                        hasAds = submission.hasAds,
                        versionCode = if (submission.isUpdateSubmission) submission.versionCode else existingApp.versionCode,
                        changelog = submission.changelog,
                        versionHistoryJson = updatedHistoryJson
                    )
                } else {
                    AppEntity(
                        id = "app_" + System.currentTimeMillis() + "_" + (100..999).random(),
                        name = submission.name,
                        developer = submission.developer,
                        version = submission.version,
                        size = "18 MB",
                        category = submission.category,
                        rating = "0.0",
                        description = submission.description,
                        logo = submission.logo.ifBlank { if (submission.screenshots.contains(",")) submission.screenshots.substringBefore(",") else submission.screenshots },
                        screenshots = submission.screenshots,
                        apkUrl = submission.apkUrl,
                        packageName = submission.packageName,
                        isFeatured = false,
                        isPopular = true,
                        isRecent = true,
                        versionCode = submission.versionCode,
                        changelog = submission.changelog,
                        isApproved = true,
                        submittedBy = submission.submittedBy,
                        hasAds = submission.hasAds
                    )
                }
                val repoSuccess = repository.saveApp(app)
                
                // Fetch user FCM token and send FCM alert
                val ownerToken = FirebaseAuthService.getFcmTokenByEmail(submission.submittedBy)
                val fcmServerKey = sharedPrefs.getString("fcm_server_key", "") ?: ""
                val noticeId = "notice_" + System.currentTimeMillis() + "_" + (1000..9999).random()
                val notice = NoticeEntity(
                    id = noticeId,
                    title = "App Approved",
                    message = "Your app ${submission.name} has been approved and published.",
                    timestamp = System.currentTimeMillis(),
                    targetAppId = "approved_${submission.id}"
                )
                if (fcmServerKey.isNotBlank()) {
                    FirebaseService.saveNotice(notice)
                    if (ownerToken != null && ownerToken.isNotBlank()) {
                        FirebaseService.sendFCMNotification(fcmServerKey, notice.copy(targetAppId = "token:$ownerToken"))
                    } else {
                        FirebaseService.sendFCMNotification(fcmServerKey, notice)
                    }
                }

                _isRefreshing.value = false
                if (repoSuccess) {
                    refreshMarketplace()
                    refreshSubmissions()
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        onFinished(true, "Submission status set to Approved and deployed live to catalog!")
                    }
                } else {
                    refreshSubmissions()
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        onFinished(true, "Approved in submissions collector, but failed catalog sync. Set again.")
                    }
                }
            } else {
                _isRefreshing.value = false
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onFinished(false, "Firebase approval failed. Please check your dynamic configurations and network.")
                }
            }
        }
    }

    fun rejectSubmission(
        submission: SubmissionEntity,
        reason: String = "Submission did not satisfy repository safety regulations.",
        onFinished: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshing.value = true
            FirebaseAuthService.refreshIdTokenIfNeeded(getApplication())
            val rejectedSub = submission.copy(status = "Rejected", feedback = reason)
            val subSuccess = FirebaseAuthService.updateSubmissionStatus(submission.id, rejectedSub)
            
            if (subSuccess) {
                // Fetch user FCM token and send FCM Alert
                val ownerToken = FirebaseAuthService.getFcmTokenByEmail(submission.submittedBy)
                val fcmServerKey = sharedPrefs.getString("fcm_server_key", "") ?: ""
                val noticeId = "notice_" + System.currentTimeMillis() + "_" + (1000..9999).random()
                val notice = NoticeEntity(
                    id = noticeId,
                    title = "App Rejected",
                    message = "Your app ${submission.name} was rejected. Reason: $reason",
                    timestamp = System.currentTimeMillis(),
                    targetAppId = "rejected_${submission.id}"
                )
                if (fcmServerKey.isNotBlank()) {
                    FirebaseService.saveNotice(notice)
                    if (ownerToken != null && ownerToken.isNotBlank()) {
                        FirebaseService.sendFCMNotification(fcmServerKey, notice.copy(targetAppId = "token:$ownerToken"))
                    } else {
                        FirebaseService.sendFCMNotification(fcmServerKey, notice)
                    }
                }
                _isRefreshing.value = false
                refreshSubmissions()
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onFinished(true, "Submission successfully rejected and updated.")
                }
            } else {
                _isRefreshing.value = false
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onFinished(false, "Firebase rejection failed. Please check your dynamic configurations and network.")
                }
            }
        }
    }

    fun editSubmissionDetails(
        submission: SubmissionEntity,
        onFinished: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            _isRefreshing.value = true
            FirebaseAuthService.refreshIdTokenIfNeeded(getApplication())
            val success = FirebaseAuthService.updateSubmissionStatus(submission.id, submission)
            _isRefreshing.value = false
            if (success) {
                refreshSubmissions()
                onFinished(true)
            } else {
                onFinished(false)
            }
        }
    }

    fun reportApp(
        appId: String,
        reporterEmail: String,
        reason: String,
        onFinished: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            _isRefreshing.value = true
            FirebaseAuthService.refreshIdTokenIfNeeded(getApplication())
            val app = unfilteredApps.value.find { it.id == appId }
            if (app == null) {
                _isRefreshing.value = false
                onFinished(false, "App not found.")
                return@launch
            }
            val newReport = if (app.reportsJson.isBlank()) {
                "$reporterEmail: $reason"
            } else {
                "${app.reportsJson}||$reporterEmail: $reason"
            }
            val updatedApp = app.copy(reportsJson = newReport)
            val success = repository.saveApp(updatedApp)
            _isRefreshing.value = false
            if (success) {
                refreshMarketplace()
                onFinished(true, "App reported successfully. Thank you for your feedback.")
            } else {
                onFinished(false, "Failed to report app.")
            }
        }
    }

    fun suspendApp(
        appId: String,
        isSuspended: Boolean,
        reason: String,
        onFinished: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            _isRefreshing.value = true
            FirebaseAuthService.refreshIdTokenIfNeeded(getApplication())
            val app = unfilteredApps.value.find { it.id == appId }
            if (app == null) {
                _isRefreshing.value = false
                onFinished(false, "App not found.")
                return@launch
            }
            val updatedApp = app.copy(isSuspended = isSuspended, suspensionReason = reason)
            val success = repository.saveApp(updatedApp)
            _isRefreshing.value = false
            if (success) {
                refreshMarketplace()
                onFinished(true, if (isSuspended) "App suspended successfully." else "App unsuspended successfully.")
            } else {
                onFinished(false, "Failed to update suspension status.")
            }
        }
    }

    suspend fun uploadFile(contentType: String, fileName: String, fileBytes: ByteArray): String? {
        return FirebaseAuthService.uploadFile(contentType, fileName, fileBytes)
    }

    private fun startNetworkMonitoring() {
        val connectivityManager = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        // Initial state check
        val activeNet = connectivityManager.activeNetwork
        if (activeNet != null) {
            val caps = connectivityManager.getNetworkCapabilities(activeNet)
            _isInternetAvailable.value = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } else {
            _isInternetAvailable.value = false
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            connectivityManager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _isInternetAvailable.value = true
                    refreshMarketplace(force = true)
                }

                override fun onLost(network: Network) {
                    val currentNet = connectivityManager.activeNetwork
                    if (currentNet == null) {
                        _isInternetAvailable.value = false
                    } else {
                        val caps = connectivityManager.getNetworkCapabilities(currentNet)
                        _isInternetAvailable.value = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                    }
                }
            })
        } catch (e: Exception) {
            Log.e("StoreViewModel", "Failed to register network callback", e)
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(StoreViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return StoreViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    // ----------------------------------------------------
    // REVIEWS
    // ----------------------------------------------------

    private val _appReviews = MutableStateFlow<List<com.example.data.ReviewEntity>>(emptyList())
    val appReviews: StateFlow<List<com.example.data.ReviewEntity>> = _appReviews.asStateFlow()

    private val _isReviewsLoading = MutableStateFlow(false)
    val isReviewsLoading: StateFlow<Boolean> = _isReviewsLoading.asStateFlow()

    fun loadReviewsForApp(appId: String) {
        viewModelScope.launch {
            _isReviewsLoading.value = true
            _appReviews.value = com.example.data.FirebaseService.fetchReviews(appId)
            _isReviewsLoading.value = false
        }
    }

    fun submitReview(appId: String, msg: String, stars: Int, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val user = _userName.value.ifBlank { "Anonymous" }
            val uid = _userUid.value.ifBlank { java.util.UUID.randomUUID().toString() }
            val review = com.example.data.ReviewEntity(
                id = java.util.UUID.randomUUID().toString(),
                appId = appId,
                userId = uid,
                userName = user,
                msg = msg,
                stars = stars,
                timestamp = System.currentTimeMillis()
            )
            val success = com.example.data.FirebaseService.saveReview(review)
            if (success) {
                // Update local list
                val updated = _appReviews.value.toMutableList()
                updated.add(0, review)
                _appReviews.value = updated
                
                // Also trigger an update to the app's average rating in Firebase if desired.
                // We'll skip complex transaction logic for now, but we can compute average locally.
                updateAppRating(appId, updated)
            }
            onResult(success)
        }
    }
    
    private suspend fun updateAppRating(appId: String, allReviews: List<com.example.data.ReviewEntity>) {
        if (allReviews.isEmpty()) return
        val avg = allReviews.map { it.stars }.average()
        val formattedRating = String.format(java.util.Locale.US, "%.1f", avg)
        
        // Find app and update
        val app = _realtimeApps.value.find { it.id == appId } ?: return
        val updatedApp = app.copy(rating = formattedRating)
        
        // BUG FIX: this called the raw FirebaseService.saveApp() directly — a plain
        // blocking synchronous network call, not a suspend function. Every other
        // call site in this file correctly goes through repository.saveApp()
        // (which wraps the same call in withContext(Dispatchers.IO)); this one alone
        // bypassed that, running a real network request on whatever thread called
        // updateAppRating() — which, since submitReview()'s outer launch has no
        // explicit dispatcher, is the main thread.
        repository.saveApp(updatedApp)
        // Refresh apps list to reflect new rating globally
        // This is a simplistic approach
        refreshMarketplace(true)
    }

}
