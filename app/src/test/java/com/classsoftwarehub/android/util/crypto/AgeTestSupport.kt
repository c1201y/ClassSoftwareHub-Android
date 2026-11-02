package com.classsoftwarehub.android.util.crypto

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Base64

/**
 * 单元测试用的 age 解密支持：把官方 CCTV 向量 / 自加密密文解开做校验。
 * 产品代码只加密不解密（私钥不在客户端），解密逻辑仅在这里为测试服务。
 */
object AgeTestSupport {

    private const val CHUNK = 64 * 1024

    fun hex(text: String): ByteArray {
        val clean = text.replace(" ", "").replace("\n", "")
        require(clean.length % 2 == 0)
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    fun sha256Hex(data: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }

    fun stripArmor(armored: String): ByteArray {
        val lines = armored.lines().filter { it.isNotBlank() }
        require(lines.first().startsWith("-----BEGIN"))
        require(lines.last().startsWith("-----END"))
        val body = lines.subList(1, lines.size - 1).joinToString("")
        return Base64.getDecoder().decode(body)
    }

    /** age X25519 解密：header → 包装的 file key → payload 流。 */
    fun decrypt(file: ByteArray, secretScalar: ByteArray): ByteArray {
        // ── 切出 header 的 4 行 ──
        fun lineAt(start: Int): Pair<String, Int> {
            var end = start
            while (end < file.size && file[end] != '\n'.code.toByte()) end++
            return String(file, start, end - start, Charsets.US_ASCII) to (end + 1)
        }
        var cursor = 0
        val (line1, after1) = lineAt(cursor); cursor = after1
        val (line2, after2) = lineAt(cursor); cursor = after2
        val (line3, after3) = lineAt(cursor); cursor = after3
        val (line4, payloadStart) = lineAt(cursor)

        require(line1 == "age-encryption.org/v1")
        require(line2.startsWith("-> X25519 "))
        require(line4.startsWith("--- "))

        val ephemeralShare = AgeEncryption.Base64NoPad.decode(line2.removePrefix("-> X25519 "))
        val body = AgeEncryption.Base64NoPad.decode(line3)
        val mac = AgeEncryption.Base64NoPad.decode(line4.removePrefix("--- "))

        // ── X25519 → wrapKey → fileKey ──
        val sharedSecret = AgeEncryption.X25519.scalarMult(secretScalar, ephemeralShare)
        val recipientPub = AgeEncryption.X25519.scalarMult(secretScalar, AgeEncryption.X25519.BASEPOINT)
        val salt = ByteArray(64)
        ephemeralShare.copyInto(salt, 0)
        recipientPub.copyInto(salt, 32)
        val wrapKey = AgeEncryption.Hkdf.sha256(
            sharedSecret, salt, "age-encryption.org/v1/X25519".toByteArray(Charsets.US_ASCII), 32,
        )
        val fileKey = ChaCha20Poly1305.open(wrapKey, ByteArray(12), body)
        require(fileKey.size == 16)

        // ── 头部 MAC 校验 ──
        val headerNoMac = "$line1\n$line2\n$line3\n---"
        val hmacKey = AgeEncryption.Hkdf.sha256(fileKey, null, "header".toByteArray(Charsets.US_ASCII), 32)
        val expectedMac = AgeEncryption.hmacSha256(hmacKey, headerNoMac.toByteArray(Charsets.US_ASCII))
        require(expectedMac.contentEquals(mac)) { "header MAC mismatch" }

        // ── payload 流解密 ──
        val payloadNonce = file.copyOfRange(payloadStart, payloadStart + 16)
        val streamKey = AgeEncryption.Hkdf.sha256(
            fileKey, payloadNonce, "payload".toByteArray(Charsets.US_ASCII), 32,
        )
        val rest = file.copyOfRange(payloadStart + 16, file.size)
        val out = ByteArrayOutputStream()
        var offset = 0
        var counter = 0L
        while (offset < rest.size) {
            val remaining = rest.size - offset
            val chunkLength = if (remaining > CHUNK + 16) CHUNK + 16 else remaining
            val nonce = ByteArray(12)
            for (i in 10 downTo 0) nonce[i] = ((counter shr ((10 - i) * 8)) and 0xff).toByte()
            nonce[11] = if (remaining <= CHUNK + 16) 1 else 0

            val chunk = rest.copyOfRange(offset, offset + chunkLength)
            out.write(ChaCha20Poly1305.open(streamKey, nonce, chunk))
            offset += chunkLength
            counter++
        }
        return out.toByteArray()
    }
}
