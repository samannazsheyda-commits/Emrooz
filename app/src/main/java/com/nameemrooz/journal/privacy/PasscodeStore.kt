package com.nameemrooz.journal.privacy

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Stores only a salted, deliberately expensive hash. The passcode itself never touches disk. */
class PasscodeStore(context: Context) {
    private val preferences = context.getSharedPreferences("local_lock_v2", Context.MODE_PRIVATE)

    fun hasPasscode(): Boolean = preferences.contains(HASH) && preferences.contains(SALT)

    fun set(passcode: String) {
        require(passcode.trim().length >= 4)
        val salt = ByteArray(24).also(SecureRandom()::nextBytes)
        val hash = derive(passcode, salt)
        preferences.edit()
            .putString(SALT, Base64.getEncoder().encodeToString(salt))
            .putString(HASH, Base64.getEncoder().encodeToString(hash))
            .apply()
        salt.fill(0)
        hash.fill(0)
    }

    fun verify(passcode: String): Boolean {
        val salt = preferences.getString(SALT, null)?.let(Base64.getDecoder()::decode) ?: return false
        val expected = preferences.getString(HASH, null)?.let(Base64.getDecoder()::decode) ?: return false
        val actual = derive(passcode, salt)
        val matches = MessageDigest.isEqual(expected, actual)
        salt.fill(0); expected.fill(0); actual.fill(0)
        return matches
    }

    private fun derive(value: String, salt: ByteArray): ByteArray {
        val chars = value.toCharArray()
        return try {
            val spec = PBEKeySpec(chars, salt, 120_000, 256)
            try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
            finally { spec.clearPassword() }
        } finally { chars.fill('\u0000') }
    }

    private companion object {
        const val SALT = "passcode_salt"
        const val HASH = "passcode_hash"
    }
}
