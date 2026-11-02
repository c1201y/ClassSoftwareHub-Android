package com.classsoftwarehub.android.util

import java.security.MessageDigest

object HashUtils {

    fun md5(text: String): String = digest("MD5", text)

    fun sha1(text: String): String = digest("SHA-1", text)

    fun sha256(text: String): String = digest("SHA-256", text)

    private fun digest(algorithm: String, text: String): String =
        MessageDigest.getInstance(algorithm)
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
