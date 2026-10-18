package com.darkstore.data

/**
 * What the "Your account is suspended" popup shows. Addresses are always masked (da***e@gmail.com).
 * [canContinue] = the person has a FIRST account on this phone they can keep using.
 */
data class SuspendedNotice(
    val reason: String,
    val yourMasked: String = "",
    val primaryMasked: String = "",
    val canContinue: Boolean = false
)
