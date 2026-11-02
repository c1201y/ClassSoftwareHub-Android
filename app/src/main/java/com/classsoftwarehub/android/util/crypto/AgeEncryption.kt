package com.classsoftwarehub.android.util.crypto

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * age v1（X25519）客户端加密 —— 只加密，绝不解密。
 *
 * 与桌面端 `Services/AgeEncryption.cs`、网页端 `age-encryption` 包逐位兼容：
 * 产出标准 age ASCII armor 密文，只能被持有私钥的开发者解开。
 * 私钥绝不在任何客户端代码里；本对象只持有接收方公钥、只做加密。
 *
 * 移植注意：C# 用 .NET 内建 ChaCha20Poly1305 / HKDF / BigInteger X25519；
 * Android 侧 ChaCha 与 X25519 为纯 Kotlin 实现（见 [ChaCha20Poly1305]），
 * HKDF 走 JCA 的 HmacSHA256。
 */
object AgeEncryption {

    /** 「提交软件」「问题反馈」联系方式字段的接收方公钥（与桌面端 / 网页端同一把）。 */
    const val RECIPIENT_PUBLIC_KEY = "age1l9axcy0sxu6eeanapg0maughsv380fh8nhd4unf9p8d7x20rjqfsgz4dxa"

    private const val V1 = "age-encryption.org/v1"
    private const val X25519_LABEL = "age-encryption.org/v1/X25519"

    private val random = SecureRandom()

    /** 用内置接收方公钥加密 UTF-8 明文，返回 ASCII armor 密文。 */
    fun encryptToArmor(plaintext: String): String = encryptToArmor(RECIPIENT_PUBLIC_KEY, plaintext)

    /** 用 age 公钥（age1...）加密 UTF-8 明文，返回 ASCII armor 密文。 */
    fun encryptToArmor(recipient: String, plaintext: String): String {
        val recipientPub = Bech32.decode(recipient)
        val binary = encrypt(recipientPub, plaintext.toByteArray(Charsets.UTF_8))
        return Armor.encode(binary)
    }

    /**
     * age 二进制密文：header ‖ payloadNonce(16) ‖ payload。
     * [fileKey] / [ephemeralSecret] 可注入（单元测试）；生产路径总是随机。
     */
    internal fun encrypt(
        recipientPub: ByteArray,
        plaintext: ByteArray,
        fileKey: ByteArray = randomBytes(16),
        ephemeralSecret: ByteArray = randomBytes(32),
    ): ByteArray {
        // 1. X25519 stanza：包装 file key
        val ephemeralShare = X25519.scalarMult(ephemeralSecret, X25519.BASEPOINT)
        val sharedSecret = X25519.scalarMult(ephemeralSecret, recipientPub)

        val salt = ByteArray(64)
        ephemeralShare.copyInto(salt, 0)
        recipientPub.copyInto(salt, 32)
        val wrapKey = Hkdf.sha256(sharedSecret, salt, X25519_LABEL.toByteArray(Charsets.US_ASCII), 32)

        val body = ChaCha20Poly1305.seal(wrapKey, ByteArray(12), fileKey)

        // 2. header（先算 MAC，再拼回）
        val headerNoMac = "$V1\n-> X25519 ${Base64NoPad.encode(ephemeralShare)}\n" +
            "${Base64NoPad.encode(body)}\n---"
        val hmacKey = Hkdf.sha256(fileKey, null, "header".toByteArray(Charsets.US_ASCII), 32)
        val mac = hmacSha256(hmacKey, headerNoMac.toByteArray(Charsets.US_ASCII))

        val header = "$headerNoMac ${Base64NoPad.encode(mac)}\n".toByteArray(Charsets.US_ASCII)

        // 3. payload：64 KiB 分块流式加密
        val payloadNonce = randomBytes(16)
        val streamKey = Hkdf.sha256(fileKey, payloadNonce, "payload".toByteArray(Charsets.US_ASCII), 32)
        val payload = encryptStream(streamKey, plaintext)

        val output = ByteArray(header.size + payloadNonce.size + payload.size)
        header.copyInto(output, 0)
        payloadNonce.copyInto(output, header.size)
        payload.copyInto(output, header.size + payloadNonce.size)
        return output
    }

    /**
     * age 的 payload 流：64 KiB 明文一块，nonce = 11 字节大端计数器 + 1 字节「是否最后一块」。
     */
    internal fun encryptStream(key: ByteArray, plaintext: ByteArray): ByteArray {
        val chunkSize = 64 * 1024
        val out = ByteArrayOutputStream()
        val nonce = ByteArray(12)
        var offset = 0
        while (offset < plaintext.size) {
            val n = minOf(chunkSize, plaintext.size - offset)
            val isLast = offset + n >= plaintext.size
            nonce[11] = if (isLast) 1 else 0

            out.write(ChaCha20Poly1305.seal(key, nonce, plaintext.copyOfRange(offset, offset + n)))
            offset += n

            // 大端计数器 +1（索引 10 是最低位）
            for (i in 10 downTo 0) {
                nonce[i] = (nonce[i].toInt() + 1).toByte()
                if (nonce[i] != 0.toByte()) break
            }
        }
        return out.toByteArray()
    }

    internal fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    internal fun randomBytes(size: Int): ByteArray = ByteArray(size).also { random.nextBytes(it) }

    // ── X25519（RFC 7748，BigInteger 实现；只用于"公钥加密"，无常数时间要求）──
    internal object X25519 {

        val BASEPOINT = byteArrayOf(
            9, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        )

        private val P = BigInteger.ONE.shiftLeft(255) - BigInteger.valueOf(19)
        private val A24 = BigInteger.valueOf(121665)
        private val TWO = BigInteger.valueOf(2)

        fun scalarMult(scalar: ByteArray, uCoord: ByteArray): ByteArray {
            // clamp 标量
            val k = scalar.copyOf()
            k[0] = (k[0].toInt() and 248).toByte()
            k[31] = (k[31].toInt() and 127).toByte()
            k[31] = (k[31].toInt() or 64).toByte()

            val x1 = leToBigInteger(uCoord).mod(P)
            var x2 = BigInteger.ONE
            var z2 = BigInteger.ZERO
            var x3 = x1
            var z3 = BigInteger.ONE
            var swap = 0

            for (t in 254 downTo 0) {
                val kt = (k[t shr 3].toInt() shr (t and 7)) and 1
                swap = swap xor kt
                if (swap == 1) {
                    var tmp = x2; x2 = x3; x3 = tmp
                    tmp = z2; z2 = z3; z3 = tmp
                }
                swap = kt

                val a = x2.add(z2).mod(P)
                val aa = a.multiply(a).mod(P)
                val b = x2.subtract(z2).mod(P)
                val bb = b.multiply(b).mod(P)
                val e = aa.subtract(bb).mod(P)
                val c = x3.add(z3).mod(P)
                val d = x3.subtract(z3).mod(P)
                val da = d.multiply(a).mod(P)
                val cb = c.multiply(b).mod(P)
                x3 = da.add(cb).mod(P).let { it.multiply(it).mod(P) }
                z3 = x1.multiply(da.subtract(cb).mod(P).let { it.multiply(it).mod(P) }).mod(P)
                x2 = aa.multiply(bb).mod(P)
                z2 = e.multiply(aa.add(A24.multiply(e).mod(P)).mod(P)).mod(P)
            }
            if (swap == 1) {
                var tmp = x2; x2 = x3; x3 = tmp
                tmp = z2; z2 = z3; z3 = tmp
            }

            val result = x2.multiply(z2.modPow(P.subtract(TWO), P)).mod(P)
            return bigIntegerToLe(result, 32)
        }

        private fun leToBigInteger(bytes: ByteArray): BigInteger {
            val reversed = ByteArray(bytes.size)
            for (i in bytes.indices) reversed[i] = bytes[bytes.size - 1 - i]
            return BigInteger(1, reversed)
        }

        private fun bigIntegerToLe(value: BigInteger, length: Int): ByteArray {
            val be = value.toByteArray()
            val trimmed = if (be.size > 1 && be[0] == 0.toByte()) be.copyOfRange(1, be.size) else be
            val out = ByteArray(length)
            for (i in 0 until minOf(length, trimmed.size)) {
                out[i] = trimmed[trimmed.size - 1 - i]
            }
            return out
        }
    }

    // ── HKDF-SHA256（RFC 5869）──
    internal object Hkdf {

        fun sha256(ikm: ByteArray, salt: ByteArray?, info: ByteArray, length: Int): ByteArray {
            // salt 缺省 = HashLen 个零字节
            val effectiveSalt = salt ?: ByteArray(32)

            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(effectiveSalt, "HmacSHA256"))
            val prk = mac.doFinal(ikm)

            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            val out = ByteArrayOutputStream()
            var block = ByteArray(0)
            var counter = 1
            while (out.size() < length) {
                mac.update(block)
                mac.update(info)
                mac.update(counter.toByte())
                block = mac.doFinal()
                out.write(block)
                counter++
            }
            return out.toByteArray().copyOf(length)
        }
    }

    // ── base64 ──
    internal object Base64NoPad {
        fun encode(data: ByteArray): String =
            Base64.getEncoder().withoutPadding().encodeToString(data)

        fun decode(text: String): ByteArray = Base64.getDecoder().decode(text)
    }

    // ── armor（PEM 风格：每 48 字节 raw → 64 字符标准 base64 一行）──
    internal object Armor {
        fun encode(file: ByteArray): String {
            val sb = StringBuilder()
            sb.append("-----BEGIN AGE ENCRYPTED FILE-----\n")
            var i = 0
            while (i < file.size) {
                val n = minOf(48, file.size - i)
                sb.append(Base64.getEncoder().encodeToString(file.copyOfRange(i, i + n))).append('\n')
                i += n
            }
            sb.append("-----END AGE ENCRYPTED FILE-----\n")
            return sb.toString()
        }
    }

    // ── bech32（age1... ↔ 32 字节公钥；encode 仅单元测试用）──
    internal object Bech32 {

        private const val CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"

        fun decode(str: String): ByteArray {
            require(str.length in 8..90) { "invalid bech32 length" }
            val hasLower = str.any { it.isLowerCase() }
            val hasUpper = str.any { it.isUpperCase() }
            require(!(hasLower && hasUpper)) { "mixed case bech32" }
            val lower = str.lowercase()

            val pos = lower.lastIndexOf('1')
            require(pos >= 1 && pos + 7 <= lower.length) { "invalid separator" }
            val hrp = lower.substring(0, pos)
            val data = lower.substring(pos + 1)

            val values = ArrayList<Byte>(hrp.length * 2 + 1 + data.length)
            hrpExpand(hrp).forEach { values.add(it) }
            for (c in data) {
                val idx = CHARSET.indexOf(c)
                require(idx >= 0) { "invalid bech32 char" }
                values.add(idx.toByte())
            }
            require(polymod(values.toByteArray()) == 1L) { "bech32 checksum mismatch" }

            val dataWords = ByteArray(data.length - 6)
            for (i in dataWords.indices) dataWords[i] = CHARSET.indexOf(data[i]).toByte()

            return convertBits(dataWords, 5, 8, pad = false)
        }

        /** 标准 bech32 编码（hrp 通常为 "age"）。仅单元测试构造公钥字符串用。 */
        fun encode(hrp: String, data: ByteArray): String {
            val dataWords = convertBits(data, 8, 5, pad = true)
            val values = ArrayList<Byte>(hrpExpand(hrp).size + dataWords.size + 6)
            hrpExpand(hrp).forEach { values.add(it) }
            dataWords.forEach { values.add(it) }
            repeat(6) { values.add(0) }
            val checksum = polymod(values.toByteArray()) xor 1L
            val sb = StringBuilder(hrp).append('1')
            for (b in dataWords) sb.append(CHARSET[b.toInt()])
            for (i in 0 until 6) {
                sb.append(CHARSET[((checksum shr (5 * (5 - i))) and 31L).toInt()])
            }
            return sb.toString()
        }

        private fun hrpExpand(hrp: String): List<Byte> {
            val result = ArrayList<Byte>(hrp.length * 2 + 1)
            for (c in hrp) result.add((c.code shr 5).toByte())
            result.add(0)
            for (c in hrp) result.add((c.code and 31).toByte())
            return result
        }

        private fun polymod(values: ByteArray): Long {
            val gen = longArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)
            var chk = 1L
            for (v in values) {
                val b = chk shr 25
                chk = ((chk and 0x1ffffff) shl 5) xor v.toLong()
                for (i in 0 until 5) {
                    if (((b shr i) and 1L) == 1L) chk = chk xor gen[i]
                }
            }
            return chk
        }

        private fun convertBits(data: ByteArray, fromBits: Int, toBits: Int, pad: Boolean): ByteArray {
            var acc = 0
            var bits = 0
            val maxv = (1 shl toBits) - 1
            val out = ArrayList<Byte>()
            for (value in data) {
                acc = (acc shl fromBits) or (value.toInt() and 0xff)
                bits += fromBits
                while (bits >= toBits) {
                    bits -= toBits
                    out.add(((acc shr bits) and maxv).toByte())
                }
            }
            if (pad) {
                if (bits > 0) out.add(((acc shl (toBits - bits)) and maxv).toByte())
            } else {
                require(bits < fromBits && ((acc shl (toBits - bits)) and maxv) == 0) { "invalid padding" }
            }
            return out.toByteArray()
        }
    }
}
