package com.classsoftwarehub.android.util

import android.os.Build
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * 设备指纹：给 /api/oss-sign 报一个「同一台机器长期不变、换机器就变」的短标识。
 *
 * 与桌面端同思路：本机持久随机 ID（存 SharedPreferences，清数据就换一个新的）
 * ＋ 设备特征，取 SHA-256 前 16 位十六进制。服务端只把它当不透明标识，不跨端比对。
 */
class DeviceFingerprint(private val prefs: KeyValueStore) {

    private val cached: String by lazy { compute() }

    val value: String get() = cached

    private fun compute(): String {
        val id = prefs.getString(KEY_ID) ?: randomHex32().also { prefs.putString(KEY_ID, it) }
        val parts = listOf(
            id,
            Build.BRAND,
            Build.MODEL,
            Build.DEVICE,
            Build.VERSION.RELEASE,
            Build.VERSION.SDK_INT.toString(),
        ).joinToString("|")

        val digest = MessageDigest.getInstance("SHA-256").digest(parts.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }

    private fun randomHex32(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val KEY_ID = "device_fp_id"
    }
}
