package com.example.data

import java.io.Serializable

data class AppEntity(
    val id: String,
    val name: String,
    val developer: String,
    val version: String,
    val size: String,
    val category: String,
    val rating: String,
    val description: String,
    val logo: String,
    val screenshots: String, // Comma separated URLs
    val apkUrl: String,
    val packageName: String,
    val isFeatured: Boolean = false,
    val isPremium: Boolean = false,
    val price: String = "",
    val isUpcoming: Boolean = false,
    val isPopular: Boolean = false,
    val isRecent: Boolean = false,
    val versionCode: Int = 1,
    val isApproved: Boolean = true,
    val submittedBy: String = "",
    val hasAds: Boolean = false,
    val isSuspended: Boolean = false,
    val suspensionReason: String = "",
    val reportsJson: String = "", // Semicolon or comma-separated user reports
    // Version history: a JSON-encoded array of every previously-published version
    // of this app, appended to (never overwritten) each time an update is
    // approved — so no published version is ever lost, per the version-history
    // requirement. Each entry: {versionName, versionCode, apkUrl, changelog,
    // publishedAt}.
    val versionHistoryJson: String = "",
    val changelog: String = ""
) : Serializable

/**
 * One entry in an app's version history — appended to (never overwritten)
 * every time an update is approved, so no previously-published version is
 * ever lost. Encoded as JSON into AppEntity.versionHistoryJson.
 */
data class AppVersionHistoryEntry(
    val versionName: String,
    val versionCode: Int,
    val apkUrl: String,
    val changelog: String,
    val publishedAt: Long
) : Serializable
