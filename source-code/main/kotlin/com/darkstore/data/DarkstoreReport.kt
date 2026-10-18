package com.darkstore.data

/** A user-submitted report / feedback about DarkStore itself (not about a single app). */
data class DarkstoreReport(
    val id: String,
    val category: String,
    val message: String,
    val reporterUid: String,
    val reporterEmail: String,
    val reporterName: String,
    val appVersion: String,
    val device: String,
    val createdAt: Long
)
