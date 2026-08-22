package com.example.data

import java.io.Serializable

data class SubmissionEntity(
    val id: String,
    val name: String,
    val packageName: String,
    val description: String,
    val apkUrl: String,
    val screenshots: String, // Comma separated URLs
    val category: String,
    val version: String,
    val logo: String = "",
    val developer: String = "Developer",
    val status: String = "Pending", // "Pending", "Approved", "Rejected"
    val submittedBy: String, // User Email
    val feedback: String = "", // Optional admin feedback for rejection/approval
    val createdAt: Long = System.currentTimeMillis(),
    val hasAds: Boolean = false,
    // Update-submission tracking: distinguishes a brand-new app submission from an
    // update to an app the developer already has published, so version-code
    // validation and version history only apply where they should.
    val versionCode: Int = 1,
    val changelog: String = "",
    val isUpdateSubmission: Boolean = false
) : Serializable
