package com.example.data

import java.io.Serializable

data class UserEntity(
    val uid: String,
    val email: String,
    val displayName: String,
    val role: String, // "user" or "admin"
    val createdAt: Long = System.currentTimeMillis(),
    val fcmToken: String = "",
    val isDeveloper: Boolean = (role == "admin"),
    val devWebsite: String = "",
    val devGithub: String = "",
    val devName: String = "",
    val devBio: String = "",
    val profilePhotoUrl: String = "",
    // Whether this account's email address has been confirmed via the Firebase
    // "click the link we emailed you" flow. Defaults to true so accounts created
    // before this feature existed (and non-email sign-in methods, where there's
    // no real inbox to verify) are never retroactively nagged or blocked.
    val isEmailVerified: Boolean = true
) : Serializable
