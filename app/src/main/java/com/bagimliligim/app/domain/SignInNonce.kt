package com.bagimliligim.app.domain

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** One fresh challenge per attempt; Google receives the hash, Supabase receives the raw nonce. */
class SignInNonce private constructor(val raw: String) {
    val sha256: String = MessageDigest.getInstance("SHA-256")
        .digest(raw.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    companion object {
        fun create(): SignInNonce {
            val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
            return SignInNonce(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes))
        }
    }
}
