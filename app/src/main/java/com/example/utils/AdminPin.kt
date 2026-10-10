package com.example.utils

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Admin Console PIN handling. The PIN itself is NEVER stored — only a salted PBKDF2 hash,
 * so nobody (not even someone reading the database) can recover it.
 */
object AdminPin {
    private const val ITERATIONS = 120_000
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"

    data class Record(val salt: String, val hash: String, val iterations: Int)

    private fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, 256)
        return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
    }

    fun create(pin: String): Record {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = derive(pin, salt, ITERATIONS)
        return Record(
            Base64.encodeToString(salt, Base64.NO_WRAP),
            Base64.encodeToString(hash, Base64.NO_WRAP),
            ITERATIONS
        )
    }

    fun verify(pin: String, record: Record): Boolean = try {
        val expected = Base64.decode(record.hash, Base64.NO_WRAP)
        val actual = derive(pin, Base64.decode(record.salt, Base64.NO_WRAP), record.iterations)
        MessageDigest.isEqual(expected, actual)   // constant-time compare
    } catch (e: Exception) {
        false
    }

    fun isValidFormat(pin: String): Boolean = pin.length in 4..12 && pin.all { it.isDigit() }
}
