package com.example.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale

object GoogleDriveHelper {
    private const val TAG = "GoogleDriveHelper"

    // Use a shared client with cookie jar for size checking to preserve sessions
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .cookieJar(InMemoryCookieJar())
        .build()

    /**
     * Checks if the URL matches standard Google Drive sharing/download patterns.
     */
    fun isGoogleDriveUrl(url: String?): Boolean {
        if (url == null) return false
        return url.contains("drive.google.com", ignoreCase = true) ||
               url.contains("docs.google.com", ignoreCase = true)
    }

    /**
     * Safely extracts the File ID from various Google Drive link formats:
     * - https://drive.google.com/file/d/FILE_ID/view
     * - https://drive.google.com/open?id=FILE_ID
     * - https://drive.google.com/uc?id=FILE_ID
     */
    fun extractFileId(url: String?): String? {
        if (url == null) return null
        if (!isGoogleDriveUrl(url)) return null

        try {
            // Format 1: /file/d/FILE_ID
            val fileDRegex = "/file/d/([^/\\s?#]+)".toRegex(RegexOption.IGNORE_CASE)
            val matchD = fileDRegex.find(url)
            if (matchD != null) {
                return matchD.groupValues[1]
            }

            // Format 2: id=FILE_ID in query params
            val idRegex = "[?&]id=([^&\\s#]+)".toRegex(RegexOption.IGNORE_CASE)
            val matchId = idRegex.find(url)
            if (matchId != null) {
                return matchId.groupValues[1]
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting Google Drive File ID: ${e.message}", e)
        }
        return null
    }

    /**
     * Converts a supported Google Drive sharing link into a direct runtime download URL.
     * Returns the original URL unchanged if it is not a supported Google Drive link.
     */
    fun getDownloadUrl(url: String, confirmToken: String? = null): String {
        val fileId = extractFileId(url)
        return if (fileId != null) {
            val token = confirmToken ?: "t"
            "https://drive.usercontent.google.com/download?id=$fileId&export=download&confirm=$token"
        } else {
            url
        }
    }

    /**
     * Extracts Google Drive confirmation token from the HTML body.
     */
    fun extractConfirmToken(htmlBody: String): String? {
        // Pattern 1: URL parameter confirm=XXXX
        val urlParamRegex = """confirm=([^"&'\s>]+)""".toRegex(RegexOption.IGNORE_CASE)
        val matchParam = urlParamRegex.find(htmlBody)
        if (matchParam != null) {
            return matchParam.groupValues[1]
        }

        // Pattern 2: Form hidden input <input type="hidden" name="confirm" value="XXXX">
        val inputTagRegex = """name="confirm"\s+value="([^"]+)"""".toRegex(RegexOption.IGNORE_CASE)
        val matchInput = inputTagRegex.find(htmlBody)
        if (matchInput != null) {
            return matchInput.groupValues[1]
        }

        // Pattern 3: Form hidden input alternate order <input value="XXXX" type="hidden" name="confirm">
        val inputTagRegex2 = """value="([^"]+)"\s+name="confirm"""".toRegex(RegexOption.IGNORE_CASE)
        val matchInput2 = inputTagRegex2.find(htmlBody)
        if (matchInput2 != null) {
            return matchInput2.groupValues[1]
        }
        
        // Pattern 4: any token-like confirm string in form actions or scripts
        val confirmTokenRegex = """["']confirm["']\s*,\s*["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE)
        val matchScript = confirmTokenRegex.find(htmlBody)
        if (matchScript != null) {
            return matchScript.groupValues[1]
        }

        // Pattern 5: Look for any href/action containing confirm=
        val hrefConfirmRegex = """href="[^"]*confirm=([^"&]+)""".toRegex(RegexOption.IGNORE_CASE)
        val matchHref = hrefConfirmRegex.find(htmlBody)
        if (matchHref != null) {
            return matchHref.groupValues[1]
        }

        return null
    }

    /**
     * Performs size detection via HEAD (or fallback GET) request, converting Google Drive URLs first.
     */
    suspend fun checkApkSize(url: String): Long = withContext(Dispatchers.IO) {
        var convertedUrl = getDownloadUrl(url)
        val fileId = extractFileId(url)

        val request = Request.Builder()
            .url(convertedUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val contentType = response.body?.contentType()?.toString() ?: ""
                    val contentDisposition = response.headers["Content-Disposition"] ?: ""
                    
                    if (contentType.contains("text/html", ignoreCase = true) && !contentDisposition.contains("attachment", ignoreCase = true)) {
                        val htmlBody = response.body?.string() ?: ""
                        val token = extractConfirmToken(htmlBody)
                        if (token != null && fileId != null) {
                            convertedUrl = "https://drive.usercontent.google.com/download?id=$fileId&export=download&confirm=$token"
                            Log.d(TAG, "Extracted confirm token during size detection: $token. Retrying size check...")
                            
                            val retryRequest = Request.Builder()
                                .url(convertedUrl)
                                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36")
                                .build()
                            client.newCall(retryRequest).execute().use { retryResponse ->
                                if (retryResponse.isSuccessful) {
                                    val length = retryResponse.body?.contentLength()
                                    if (length != null && length > 0) {
                                        return@withContext length
                                    }
                                }
                            }
                        }
                    } else {
                        val length = response.body?.contentLength()
                        if (length != null && length > 0) {
                            return@withContext length
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed request for size detection: ${e.message}")
        }
        
        return@withContext 0L
    }
}
