package com.example.data

import java.io.Serializable

data class ReviewEntity(
    val id: String,
    val appId: String,
    val userId: String,
    val userName: String,
    val msg: String,
    val stars: Int,
    val timestamp: Long
) : Serializable
