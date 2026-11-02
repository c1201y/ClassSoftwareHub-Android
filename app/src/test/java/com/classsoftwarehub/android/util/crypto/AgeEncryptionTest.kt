package com.classsoftwarehub.android.util.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Base64

/**
 * 密码学原语对照 RFC / 官方向量：
 *   · RFC 8439 §2.8.2 —— ChaCha20-Poly1305 AEAD
 *   · RFC 7748  §5.2   —— X25519
 *   · RFC 5869  A.1    —— HKDF-SHA256
 *   · BIP-173          —— bech32 解码
 *   · CCTV/age testdata/x25519 —— age v1 完整解密（真实 age 实现产物）
 */
class AgeEncryptionTest {

    @Test
    fun `RFC 8439 chacha20-poly1305 aead vector`() {
        val plaintext = ("Ladies and Gentlemen of the class of '99: If I could offer you only " +
            "one tip for the future, sunscreen would be it.").toByteArray(Charsets.US_ASCII)
        val aad = AgeTestSupport.hex("50515253c0c1c2c3c4c5c6c7")
        val key = AgeTestSupport.hex("808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9f")
        val nonce = AgeTestSupport.hex("070000004041424344454647")
        val expectedCiphertext = AgeTestSupport.hex(
            "d31a8d34648e60db7b86afbc53ef7ec2a4aded51296e08fea9e2b5a736ee62d6" +
                "3dbea45e8ca9671282fafb69da92728b1a71de0a9e060b2905d6a5b67ecd3b3" +
                "692ddbd7f2d778b8c9803aee328091b58fab324e4fad675945585808b4831d7b" +
                "c3ff4def08e4b7a9de576d26586cec64b6116",
        )
        val expectedTag = AgeTestSupport.hex("1ae10b594f09e26a7e902ecbd0600691")

        val sealed = ChaCha20Poly1305.seal(key, nonce, plaintext, aad)
        val ciphertext = sealed.copyOfRange(0, sealed.size - 16)
        val tag = sealed.copyOfRange(sealed.size - 16, sealed.size)

        assertArrayEquals(expectedCiphertext, ciphertext)
        assertArrayEquals(expectedTag, tag)

        // 解开也要能对上
        assertArrayEquals(plaintext, ChaCha20Poly1305.open(key, nonce, sealed, aad))
    }

    @Test
    fun `RFC 7748 x25519 vector 1`() {
        val scalar = AgeTestSupport.hex("a546e36bf0527c9d3b16154b82465edd62144c0ac1fc5a18506a2244ba449ac4")
        val u = AgeTestSupport.hex("e6db6867583030db3594c1a424b15f7c726624ec26b3353b10a903a6d0ab1c4c")
        val expected = AgeTestSupport.hex("c3da55379de9c6908e94ea4df28d084f32eccf03491c71f754b4075577a28552")

        assertArrayEquals(expected, AgeEncryption.X25519.scalarMult(scalar, u))
    }

    @Test
    fun `RFC 5869 hkdf-sha256 test case 1`() {
        val ikm = ByteArray(22) { 0x0b }
        val salt = AgeTestSupport.hex("000102030405060708090a0b0c")
        val info = AgeTestSupport.hex("f0f1f2f3f4f5f6f7f8f9")
        val expected = AgeTestSupport.hex(
            "3cb25f25faacd57a90434f64d0362f2a" +
                "2d2d0a90cf1a5a4c5db02d56ecc4c5bf" +
                "34007208d5b887185865",
        )

        assertArrayEquals(expected, AgeEncryption.Hkdf.sha256(ikm, salt, info, 42))
    }

    @Test
    fun `bech32 decodes production recipient key`() {
        val key = AgeEncryption.Bech32.decode(AgeEncryption.RECIPIENT_PUBLIC_KEY)
        assertEquals(32, key.size)
        // 再编码回去必须还原（小写形式），保证位转换双向一致
        assertEquals(AgeEncryption.RECIPIENT_PUBLIC_KEY, AgeEncryption.Bech32.encode("age", key))
    }

    @Test
    fun `bech32 accepts empty payload vector`() {
        // BIP-173 有效向量：hrp "a"、空数据、校验和正确
        assertEquals(0, AgeEncryption.Bech32.decode("A12UEL5L").size)
    }

    @Test
    fun `bech32 roundtrip`() {
        val data = AgeEncryption.randomBytes(32)
        val encoded = AgeEncryption.Bech32.encode("age", data)
        assertArrayEquals(data, AgeEncryption.Bech32.decode(encoded))
    }

    @Test
    fun `decrypts official CCTV age x25519 vector`() {
        // vector 原始文件（含头部的元信息行 + 二进制 age 文件）
        val vector = Base64.getDecoder().decode(VECTOR_B64)
        val text = String(vector, Charsets.ISO_8859_1)

        val expectedPayloadHash = Regex("payload: ([0-9a-f]{64})").find(text)!!.groupValues[1]
        val expectedFileKey = Regex("file key: ([0-9a-f]{32})").find(text)!!.groupValues[1]
        val identity = Regex("identity: (AGE-SECRET-KEY-[A-Z0-9]+)").find(text)!!.groupValues[1]

        // age 二进制部分从第一行 "age-encryption.org/v1" 开始
        val fileStart = text.indexOf("age-encryption.org/v1")
        val file = vector.copyOfRange(fileStart, vector.size)

        val secret = AgeEncryption.Bech32.decode(identity.lowercase())
        val plaintext = AgeTestSupport.decrypt(file, secret)

        assertEquals(expectedPayloadHash, AgeTestSupport.sha256Hex(plaintext))

        // 包装的 file key 也必须与官方向量一致（X25519 + HKDF + ChaCha 包装链路）
        val unwrapped = unwrapOnly(file, secret)
        assertEquals(expectedFileKey, unwrapped.joinToString("") { "%02x".format(it) })
    }

    @Test
    fun `armored roundtrip with fresh keypair`() {
        val secret = AgeEncryption.randomBytes(32)
        val pub = AgeEncryption.X25519.scalarMult(secret, AgeEncryption.X25519.BASEPOINT)
        val recipient = AgeEncryption.Bech32.encode("age", pub)

        val message = "测试联系方式：qq 12345678 / mail test@example.com"
        val armored = AgeEncryption.encryptToArmor(recipient, message)

        val file = AgeTestSupport.stripArmor(armored)
        val plaintext = AgeTestSupport.decrypt(file, secret)
        assertEquals(message, String(plaintext, Charsets.UTF_8))
    }

    private fun unwrapOnly(file: ByteArray, secretScalar: ByteArray): ByteArray {
        val text = String(file, Charsets.ISO_8859_1)
        val line2 = text.lineSequence().elementAt(1)
        val line3 = text.lineSequence().elementAt(2)
        val ephemeralShare = AgeEncryption.Base64NoPad.decode(line2.removePrefix("-> X25519 "))
        val body = AgeEncryption.Base64NoPad.decode(line3)

        val sharedSecret = AgeEncryption.X25519.scalarMult(secretScalar, ephemeralShare)
        val recipientPub = AgeEncryption.X25519.scalarMult(secretScalar, AgeEncryption.X25519.BASEPOINT)
        val salt = ByteArray(64)
        ephemeralShare.copyInto(salt, 0)
        recipientPub.copyInto(salt, 32)
        val wrapKey = AgeEncryption.Hkdf.sha256(
            sharedSecret, salt, "age-encryption.org/v1/X25519".toByteArray(Charsets.US_ASCII), 32,
        )
        return ChaCha20Poly1305.open(wrapKey, ByteArray(12), body)
    }

    companion object {
        /** C2SP/CCTV age/testdata/x25519 原始文件（官方 age 解密向量）。 */
        private const val VECTOR_B64 =
            "ZXhwZWN0OiBzdWNjZXNzCnBheWxvYWQ6IDAxM2Y1NDQwMGM4MmRhMDgwMzc3NTlhZGE5MDdhOGI4NjRlOTdk" +
                "ZTgxYzA4OGExODIwNjJjNGI1NjIyZmQyYWIKZmlsZSBrZXk6IDU5NDU0YzRjNGY1NzIwNTM1NTQyNGQ0MTUy" +
                "NDk0ZTQ1CmlkZW50aXR5OiBBR0UtU0VDUkVULUtFWS0xRUdUWlZGRlYyMDgzNU5XWVY2MjcwTFhZVksyVktO" +
                "WDJNTURLV1lLTE1HUjQ4VUFXWDQwUTJQMkxNMAoKYWdlLWVuY3J5cHRpb24ub3JnL3YxCi0+IFgyNTUxOSBU" +
                "RWlGMHlwcXIrYnB2Y3FYTnlDVkpwTDdPdXdQZFZ3UEw3S1FFYkZET0NjCmhqYWJHWHdTTFE5YzNTNkx3Mmkr" +
                "UzJUdTJmaXdRSEhzbGJCTjZCNDFGTEUKLS0tIFd5SnA5Ri85Rk9aaDdnSmRoZXEyV0lKY3dIZ1ljOE5JVmgz" +
                "ZGR3aHJjTmcK7s9ix86RtDMnTmjU8vkTTLdMW/73vqpSyPC8DpksHoMx+2Y="
    }
}
