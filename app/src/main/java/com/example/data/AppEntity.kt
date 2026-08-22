package com.example.data

import androidx.compose.runtime.Immutable
import java.io.Serializable

@Immutable
data class AppEntity(
    val id: String,
    val name: String,
    val developer: String,
    val description: String,
    val category: String,
    val rating: String,
    val size: String,
    val logo: String,
    val apkUrl: String,
    val packageName: String,
    val version: String = "1.0",
    val versionCode: Long = 1L,
    val screenshots: List<String> = emptyList(),
    val isFeatured: Boolean = false,
    val isPremium: Boolean = false,
    val price: String = "",
    val hasAds: Boolean = false,
    val isUpcoming: Boolean = false,
    val releaseDate: String = "",
    val whatsNew: String = "",
    val isAdmin: Boolean = false,
    val status: String = "live",
    val feedback: String = "",
    val isPopular: Boolean = false,
    val isRecent: Boolean = false,
    val isApproved: Boolean = true,
    val submittedBy: String = "",
    val isSuspended: Boolean = false,
    val suspensionReason: String = "",
    val reportsJson: String = ""
) : Serializable
