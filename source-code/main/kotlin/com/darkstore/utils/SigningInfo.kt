package com.darkstore.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/**
 * Shows which key signed THIS installed app, and whether Android verified the website link.
 * Android only opens shared links straight in the app when the SHA-256 listed in the website's
 * /.well-known/assetlinks.json equals the SHA-256 printed here.
 */
object SigningInfo {
    data class Fingerprints(val sha256: String, val sha1: String)

    @Suppress("DEPRECATION")
    fun fingerprints(context: Context): Fingerprints? = try {
        val pm = context.packageManager
        val sig = if (Build.VERSION.SDK_INT >= 28) {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners?.firstOrNull()
        } else {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures?.firstOrNull()
        }
        val bytes = sig?.toByteArray()
        if (bytes == null) null else {
            fun hex(alg: String) = MessageDigest.getInstance(alg).digest(bytes).joinToString(":") { "%02X".format(it) }
            Fingerprints(hex("SHA-256"), hex("SHA-1"))
        }
    } catch (e: Exception) { null }

    /** Verification state of [host] for this app (Android 12+). */
    fun linkState(context: Context, host: String): String {
        if (Build.VERSION.SDK_INT < 31) return "Check needs Android 12 or newer"
        return try {
            val m = context.getSystemService(android.content.pm.verify.domain.DomainVerificationManager::class.java)
                ?: return "Unknown"
            val st = m.getDomainVerificationUserState(context.packageName) ?: return "Unknown"
            when (st.hostToStateMap[host]) {
                android.content.pm.verify.domain.DomainVerificationUserState.DOMAIN_STATE_VERIFIED -> "Verified - links open directly in the app"
                android.content.pm.verify.domain.DomainVerificationUserState.DOMAIN_STATE_SELECTED -> "Enabled manually by the user"
                null -> "Link not registered in this build"
                else -> "NOT verified - the website fingerprint doesn't match this build"
            }
        } catch (e: Exception) { "Unknown" }
    }
}
