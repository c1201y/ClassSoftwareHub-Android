package com.classsoftwarehub.android.util.crypto

import java.math.BigInteger

/**
 * ChaCha20-Poly1305（RFC 8439）纯 Kotlin 实现，只服务于 age 联系方式加密。
 *
 * 为什么不用 `javax.crypto.Cipher("ChaCha20-Poly1305")`：
 * Android 上这个算法要 API 28（Conscrypt）起才提供；本项目 minSdk 26，
 * 26/27 的机器会直接抛 NoSuchAlgorithmException。
 * 加密对象是几十字节的短文本，性能不重要，正确性优先 —— 与桌面端
 * `AgeEncryption.ChaChaEncrypt`（.NET ChaCha20Poly1305）逐位兼容。
 */
internal object ChaCha20Poly1305 {

    private const val TAG_BYTES = 16

    /** 加密：nonce 12 字节、AAD 可选；返回「密文 ‖ 16 字节 tag」。 */
    fun seal(
        key: ByteArray,
        nonce: ByteArray,
        plaintext: ByteArray,
        aad: ByteArray = ByteArray(0),
    ): ByteArray {
        require(key.size == 32) { "key must be 32 bytes" }
        require(nonce.size == 12) { "nonce must be 12 bytes" }

        val oneTimeKey = block(key, counter = 0, nonce = nonce).copyOfRange(0, 32)
        val ciphertext = xor(key, counter = 1, nonce = nonce, data = plaintext)
        val tag = poly1305(oneTimeKey, macData(aad, ciphertext))

        val out = ByteArray(ciphertext.size + TAG_BYTES)
        ciphertext.copyInto(out, 0)
        tag.copyInto(out, ciphertext.size)
        return out
    }

    /** 解密（校验 tag）：入参为「密文 ‖ 16 字节 tag」。仅单元测试使用。 */
    fun open(
        key: ByteArray,
        nonce: ByteArray,
        ciphertextAndTag: ByteArray,
        aad: ByteArray = ByteArray(0),
    ): ByteArray {
        require(ciphertextAndTag.size >= TAG_BYTES) { "ciphertext too short" }

        val ciphertext = ciphertextAndTag.copyOfRange(0, ciphertextAndTag.size - TAG_BYTES)
        val tag = ciphertextAndTag.copyOfRange(ciphertextAndTag.size - TAG_BYTES, ciphertextAndTag.size)

        val oneTimeKey = block(key, counter = 0, nonce = nonce).copyOfRange(0, 32)
        val expected = poly1305(oneTimeKey, macData(aad, ciphertext))
        require(constantTimeEquals(expected, tag)) { "Poly1305 tag mismatch" }

        return xor(key, counter = 1, nonce = nonce, data = ciphertext)
    }

    /** ChaCha20 核心块函数：由 (key, counter, nonce) 生成 64 字节密钥流块。 */
    private fun block(key: ByteArray, counter: Int, nonce: ByteArray): ByteArray {
        val state = IntArray(16)
        state[0] = 0x61707865 // "expa"
        state[1] = 0x3320646e // "nd 3"
        state[2] = 0x79622d32 // "2-by"
        state[3] = 0x6b206574 // "te k"
        for (i in 0 until 8) state[4 + i] = leWord(key, i * 4)
        state[12] = counter
        state[13] = leWord(nonce, 0)
        state[14] = leWord(nonce, 4)
        state[15] = leWord(nonce, 8)

        val x = state.copyOf()
        repeat(10) {
            quarterRound(x, 0, 4, 8, 12)
            quarterRound(x, 1, 5, 9, 13)
            quarterRound(x, 2, 6, 10, 14)
            quarterRound(x, 3, 7, 11, 15)
            quarterRound(x, 0, 5, 10, 15)
            quarterRound(x, 1, 6, 11, 12)
            quarterRound(x, 2, 7, 8, 13)
            quarterRound(x, 3, 4, 9, 14)
        }

        val out = ByteArray(64)
        for (i in 0 until 16) {
            val value = x[i] + state[i] // Int 溢出即 32 位环绕，正是规范要的
            out[i * 4] = value.toByte()
            out[i * 4 + 1] = (value ushr 8).toByte()
            out[i * 4 + 2] = (value ushr 16).toByte()
            out[i * 4 + 3] = (value ushr 24).toByte()
        }
        return out
    }

    private fun quarterRound(x: IntArray, a: Int, b: Int, c: Int, d: Int) {
        x[a] += x[b]; x[d] = Integer.rotateLeft(x[d] xor x[a], 16)
        x[c] += x[d]; x[b] = Integer.rotateLeft(x[b] xor x[c], 12)
        x[a] += x[b]; x[d] = Integer.rotateLeft(x[d] xor x[a], 8)
        x[c] += x[d]; x[b] = Integer.rotateLeft(x[b] xor x[c], 7)
    }

    /** 用起始计数 [counter] 的 ChaCha20 密钥流异或 [data]。 */
    private fun xor(key: ByteArray, counter: Int, nonce: ByteArray, data: ByteArray): ByteArray {
        val out = ByteArray(data.size)
        var offset = 0
        var blockCounter = counter
        while (offset < data.size) {
            val stream = block(key, blockCounter, nonce)
            val n = minOf(64, data.size - offset)
            for (i in 0 until n) out[offset + i] = (data[offset + i].toInt() xor stream[i].toInt()).toByte()
            offset += n
            blockCounter++
        }
        return out
    }

    /** Poly1305 MAC（BigInteger 版本；短消息下开销可以忽略）。 */
    private fun poly1305(key: ByteArray, data: ByteArray): ByteArray {
        require(key.size == 32)

        val rBytes = key.copyOfRange(0, 16)
        rBytes[3] = (rBytes[3].toInt() and 15).toByte()
        rBytes[7] = (rBytes[7].toInt() and 15).toByte()
        rBytes[11] = (rBytes[11].toInt() and 15).toByte()
        rBytes[15] = (rBytes[15].toInt() and 15).toByte()
        rBytes[4] = (rBytes[4].toInt() and 252).toByte()
        rBytes[8] = (rBytes[8].toInt() and 252).toByte()
        rBytes[12] = (rBytes[12].toInt() and 252).toByte()

        val r = leToBigInteger(rBytes)
        val s = leToBigInteger(key.copyOfRange(16, 32))
        val p = BigInteger.ONE.shiftLeft(130) - BigInteger.valueOf(5)

        var acc = BigInteger.ZERO
        var offset = 0
        while (offset < data.size) {
            val n = minOf(16, data.size - offset)
            val chunk = ByteArray(n + 1)
            data.copyInto(chunk, 0, offset, offset + n)
            chunk[n] = 0x01 // 追加 0x01 表示「这一块是消息的一部分」
            acc = acc.add(leToBigInteger(chunk)).multiply(r).mod(p)
            offset += n
        }

        val tag = acc.add(s).mod(BigInteger.ONE.shiftLeft(128))
        val out = ByteArray(16)
        val be = tag.toByteArray()
        val beTrimmed = if (be.size > 1 && be[0] == 0.toByte()) be.copyOfRange(1, be.size) else be
        for (i in 0 until minOf(16, beTrimmed.size)) {
            out[i] = beTrimmed[beTrimmed.size - 1 - i]
        }
        return out
    }

    /**
     * RFC 8439 的 MAC 输入：
     * AAD ‖ pad16 ‖ 密文 ‖ pad16 ‖ len(AAD) ‖ len(密文)（两个长度都是小端 64 位）。
     */
    private fun macData(aad: ByteArray, ciphertext: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        out.write(aad)
        out.write(ByteArray(pad16(aad.size)))
        out.write(ciphertext)
        out.write(ByteArray(pad16(ciphertext.size)))
        out.write(le64(aad.size.toLong()))
        out.write(le64(ciphertext.size.toLong()))
        return out.toByteArray()
    }

    private fun pad16(length: Int): Int = (16 - length % 16) % 16

    private fun le64(value: Long): ByteArray {
        val out = ByteArray(8)
        var v = value
        for (i in 0 until 8) {
            out[i] = (v and 0xff).toByte()
            v = v ushr 8
        }
        return out
    }

    private fun leWord(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)

    private fun leToBigInteger(bytes: ByteArray): BigInteger {
        val reversed = ByteArray(bytes.size)
        for (i in bytes.indices) reversed[i] = bytes[bytes.size - 1 - i]
        return BigInteger(1, reversed)
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }
}
