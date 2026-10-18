package com.darkstore.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.UnityAds
import com.unity3d.services.banners.BannerErrorInfo
import com.unity3d.services.banners.BannerView
import com.unity3d.services.banners.UnityBannerSize
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the single Unity Ads banner for the home-screen promo slot.
 *
 * Contract with the UI:
 *  - [adReady] is true ONLY while an ad has actually loaded AND the admin switch is on.
 *    The UI shows the original promo banner whenever it is false (disabled, loading,
 *    failed, timed out, offline) — so there is never a blank/loading placeholder.
 *  - Everything here runs on the main thread (SDK callbacks are re-posted to it).
 *  - The SDK is initialised at most once per process; Firebase config updates only
 *    call [applyConfig], which never re-initialises it.
 *
 * Ad IDs below are public identifiers (not secrets). Test vs production ads is an
 * explicit admin-controlled flag (`unityAdsConfig/testMode`, default = TEST).
 * NOTE: the SDK fixes test mode at its first initialisation, so flipping testMode
 * takes effect the next time the app process starts.
 */
object UnityAdsManager {
    private const val TAG = "UnityAdsManager"

    const val GAME_ID = "800395384"
    const val BANNER_PLACEMENT_ID = "BP_Banner_Android"

    private const val LOAD_TIMEOUT_MS = 15_000L
    private const val RETRY_BASE_MS = 60_000L
    private const val RETRY_MAX_MS = 10 * 60_000L

    private enum class InitState { NONE, INITIALIZING, READY }

    private val main = Handler(Looper.getMainLooper())

    private val _adReady = MutableStateFlow(false)
    val adReady: StateFlow<Boolean> = _adReady.asStateFlow()

    private var appContext: Context? = null
    private var wantEnabled = false
    private var testMode = true
    private var initState = InitState.NONE
    private var banner: BannerView? = null
    private var loading = false
    private var failures = 0

    private val timeoutRunnable = Runnable { onLoadFailed("timeout") }
    private val retryRunnable = Runnable { if (wantEnabled) requestAd() }

    /** The loaded banner view for the UI to host, or null when there is no ad. */
    fun bannerView(): BannerView? = if (_adReady.value) banner else null

    /** Call on the main thread whenever the admin config changes (and at startup). */
    fun applyConfig(context: Context, enabled: Boolean, testMode: Boolean) {
        appContext = context.applicationContext
        this.testMode = testMode
        wantEnabled = enabled
        if (!enabled) {
            teardown()
            return
        }
        if (banner != null || loading) return // already have / already requesting one
        requestAd()
    }

    /** Call from Activity.onDestroy: frees the view + listeners and stops retries. */
    fun release() {
        wantEnabled = false
        teardown()
    }

    // ───────────────────────── internals ─────────────────────────

    private fun requestAd() {
        val ctx = appContext ?: return
        if (!wantEnabled || banner != null || loading) return
        when {
            initState == InitState.READY || UnityAds.isInitialized -> {
                initState = InitState.READY
                loadBanner(ctx)
            }
            initState == InitState.INITIALIZING -> Unit // loadBanner() runs when init completes
            else -> initialize(ctx)
        }
    }

    private fun initialize(ctx: Context) {
        initState = InitState.INITIALIZING
        try {
            UnityAds.initialize(ctx, GAME_ID, testMode, object : IUnityAdsInitializationListener {
                override fun onInitializationComplete() {
                    main.post {
                        initState = InitState.READY
                        if (wantEnabled) requestAd()
                    }
                }

                override fun onInitializationFailed(error: UnityAds.UnityAdsInitializationError?, message: String?) {
                    Log.w(TAG, "init failed: $error $message")
                    main.post {
                        initState = InitState.NONE // allow a later retry
                        scheduleRetry()
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "initialize threw: ${e.message}", e)
            initState = InitState.NONE
            scheduleRetry()
        }
    }

    private fun loadBanner(ctx: Context) {
        loading = true
        try {
            val view = BannerView(ctx, BANNER_PLACEMENT_ID, UnityBannerSize(320, 50))
            view.listener = object : BannerView.IListener {
                override fun onBannerLoaded(bannerAdView: BannerView) {
                    main.post {
                        if (banner === view && wantEnabled) {
                            main.removeCallbacks(timeoutRunnable)
                            loading = false
                            failures = 0
                            _adReady.value = true
                        } else {
                            safeDestroy(view) // stale result (disabled meanwhile / replaced)
                        }
                    }
                }

                override fun onBannerFailedToLoad(bannerAdView: BannerView, errorInfo: BannerErrorInfo) {
                    Log.w(TAG, "banner failed: ${errorInfo.errorCode} ${errorInfo.errorMessage}")
                    main.post { if (banner === view) onLoadFailed("no ad") else safeDestroy(view) }
                }

                override fun onBannerShown(bannerAdView: BannerView) {}
                override fun onBannerClick(bannerAdView: BannerView) {}
                override fun onBannerLeftApplication(bannerAdView: BannerView) {}
            }
            banner = view
            main.removeCallbacks(timeoutRunnable)
            main.postDelayed(timeoutRunnable, LOAD_TIMEOUT_MS)
            view.load()
        } catch (e: Exception) {
            Log.e(TAG, "loadBanner threw: ${e.message}", e)
            onLoadFailed("exception")
        }
    }

    private fun onLoadFailed(reason: String) {
        Log.w(TAG, "banner not available ($reason) — original promo stays visible")
        main.removeCallbacks(timeoutRunnable)
        banner?.let { safeDestroy(it) }
        banner = null
        loading = false
        _adReady.value = false
        scheduleRetry()
    }

    private fun scheduleRetry() {
        main.removeCallbacks(retryRunnable)
        if (!wantEnabled) return
        failures++
        val delay = minOf(RETRY_BASE_MS shl (failures - 1).coerceAtMost(4), RETRY_MAX_MS)
        main.postDelayed(retryRunnable, delay)
    }

    private fun teardown() {
        main.removeCallbacks(timeoutRunnable)
        main.removeCallbacks(retryRunnable)
        _adReady.value = false
        banner?.let { safeDestroy(it) }
        banner = null
        loading = false
        failures = 0
    }

    private fun safeDestroy(view: BannerView) {
        try {
            (view.parent as? android.view.ViewGroup)?.removeView(view)
            view.listener = null
            view.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "destroy failed: ${e.message}")
        }
    }
}
