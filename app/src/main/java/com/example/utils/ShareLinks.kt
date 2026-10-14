package com.example.utils

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Share links for apps.
 *
 *  - What people SEND:   https://<your-site>/app.html?id=<appId>   (the DarkStore website page)
 *  - What the APP OPENS: darkstore://app/<appId>                    (handled in MainActivity)
 *
 * The website page tries darkstore://... and shows the download page when DarkStore isn't installed.
 */
object ShareLinks {
    /** Address of the DarkStore website (index.html / app.html are served from its root). */
    const val SHARE_BASE_URL = "https://darkrootstore.vercel.app/"

    private const val SCHEME = "darkstore"
    private val SAFE_ID = Regex("^[A-Za-z0-9_.\\-]{1,120}$")

    fun webLink(appId: String): String =
        SHARE_BASE_URL.trimEnd('/') + "/app.html?id=" + Uri.encode(appId)

    /** Returns a safe app id from an incoming link, or null if it isn't one of ours. */
    fun parseAppId(uri: Uri?): String? {
        uri ?: return null
        val id = when {
            uri.scheme.equals(SCHEME, true) && uri.host.equals("app", true) -> uri.pathSegments.firstOrNull()
            uri.scheme.equals("https", true) && uri.host.equals(Uri.parse(SHARE_BASE_URL).host, true) ->
                uri.getQueryParameter("id")
            else -> null
        }?.trim()
        // Never trust the link: only simple ids are accepted.
        return id?.takeIf { SAFE_ID.matches(it) }
    }

    fun share(context: Context, appId: String, packageName: String, appName: String) {
        val id = if (SAFE_ID.matches(appId)) appId else packageName
        val text = "Check out $appName on DarkRoot Store:\n${webLink(id)}"
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, appName)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, "Share $appName").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
