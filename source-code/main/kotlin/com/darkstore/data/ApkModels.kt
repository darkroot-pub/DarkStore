package com.darkstore.data

/** One uploaded APK as the DarkStore server reports it. */
data class ApkFile(
    val id: String,
    val name: String,
    val size: Long,
    val email: String,
    val createdAt: Long,
    val downloads: Int,
    val inUse: Boolean
)

/** What a user sees in the submit form: may I upload, and how much room is left. */
data class ApkUploadInfo(
    val enabled: Boolean,
    val premium: Boolean,
    val maxFileMB: Int,
    val perUserFiles: Int,
    val perUserMB: Int,
    val usedFiles: Int,
    val usedBytes: Long,
    val files: List<ApkFile>
)

/** Result of a finished upload: the download link plus facts read from the APK itself. */
data class ApkUploadResult(
    val url: String,
    val sha256: String,
    val size: Long,
    val packageName: String,
    val versionName: String,
    val versionCode: Int
)

/** Admin view of the whole storage. */
data class ApkAdminStatus(
    val enabled: Boolean,
    val maxFileMB: Int,
    val perUserFiles: Int,
    val perUserMB: Int,
    val configured: Boolean,
    val keyHint: String,
    val baseUrl: String,
    val totalFiles: Int,
    val totalBytes: Long,
    val users: Int,
    val files: List<ApkFile>
)
