package com.darkstore.utils

import android.content.Context
import android.provider.Settings
import java.security.MessageDigest

/** Helpers used by the referral anti-cheat system. */
object FraudRules {
    /** Throw-away mailbox providers: accounts made with them can't take part in referrals. */
    private val DISPOSABLE_DOMAINS = setOf(
        "mailinator.com", "guerrillamail.com", "guerrillamail.net", "sharklasers.com", "10minutemail.com",
        "10minutemail.net", "tempmail.com", "temp-mail.org", "tempmail.net", "yopmail.com", "yopmail.net",
        "trashmail.com", "trashmail.net", "getnada.com", "dispostable.com", "maildrop.cc", "fakeinbox.com",
        "throwawaymail.com", "mohmal.com", "emailondeck.com", "mintemail.com", "mytemp.email", "tempr.email",
        "burnermail.io", "spam4.me", "grr.la", "moakt.com", "mailnesia.com", "tmpmail.org", "tmpmail.net",
        "inboxkitten.com", "mailcatch.com", "discard.email", "tempinbox.com", "emailfake.com", "fakemail.net"
    )

    fun isDisposableEmail(email: String): Boolean {
        val domain = email.trim().lowercase().substringAfter('@', "")
        return domain.isNotEmpty() && DISPOSABLE_DOMAINS.any { domain == it || domain.endsWith(".$it") }
    }

    /**
     * A one-way fingerprint of this phone (hash of the Android ID + a DarkStore salt). The real
     * Android ID never leaves the device. Two accounts used on the same phone share this hash.
     */
    fun deviceHash(context: Context): String {
        val id = try {
            Settings.Secure.getString(context.applicationContext.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        } catch (e: Exception) { "" }
        val raw = MessageDigest.getInstance("SHA-256").digest("darkstore-device|$id".toByteArray())
        return raw.joinToString("") { "%02x".format(it) }.take(32)
    }

    /** Emulators / virtual phones make unlimited "new phones" - they get no referral rights. */
    fun isEmulator(): Boolean {
        val b = android.os.Build.FINGERPRINT.lowercase()
        val model = android.os.Build.MODEL.lowercase()
        val maker = android.os.Build.MANUFACTURER.lowercase()
        val hw = android.os.Build.HARDWARE.lowercase()
        val product = android.os.Build.PRODUCT.lowercase()
        return b.startsWith("generic") || b.contains("emulator") || b.contains("sdk_gphone") ||
            model.contains("emulator") || model.contains("android sdk built for") ||
            maker.contains("genymotion") || hw.contains("goldfish") || hw.contains("ranchu") ||
            product.contains("sdk_gphone") || product == "sdk" || product == "vbox86p"
    }
}
