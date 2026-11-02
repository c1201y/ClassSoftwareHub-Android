package com.classsoftwarehub.android.data.remote

import com.classsoftwarehub.android.util.KeyValueStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 教室喊话**发送端**（手机 → 教室电脑）。
 *
 * 与桌面端 / 网页端共用自建 Worker 的中继通道（设计稿 docs/教室喊话功能设计.md）：
 *   · POST {入口}/api/announce/send  {pairCode, text, from, duration} → 2xx {ok, devices}
 *   · GET  {入口}/api/announce/list?pairCode=… → 200 {devices:[{name, ts}]}
 *
 * 入口按序回退（classhub 主域 → submit），上次成功的记在本地下次优先；单入口 10 秒超时。
 * ⚠️ 手机只发送、不轮询接收 —— 接收与全屏播报在电脑端完成。
 */
class AnnounceClient(
    private val client: OkHttpClient,
    private val json: Json,
    private val prefs: KeyValueStore,
) {

    companion object {
        private const val USER_AGENT = "ClassSoftwareHub-Android/announce"
        private const val KEY_LAST_ENDPOINT = "announce_endpoint"

        /**
         * 自建 Worker 入口（.workers.dev 域名国内打不开、不列入）。
         * 2026-10-30 起 classhub 全面换到直连主域 api.classsoftwarehub.cn（已实测 announce 接口 200）；
         * cshapi.132614.xyz 的第三方中转已 512 下线剔除，submit.132614.xyz 留作回退。
         */
        val ENDPOINTS = listOf(
            "https://api.classsoftwarehub.cn",
            "https://submit.132614.xyz",
        )
    }

    /** 单个目标码的发送结果 */
    sealed interface SendOutcome {

        /** 服务端已受理；devices = 预计送达设备数（0 = 目标设备当前离线，消息会过期） */
        data class Sent(val devices: Int) : SendOutcome

        /** 服务端明确拒绝：rate_limited / no_device / unknown_pair / bad_request / … */
        data class Rejected(val error: String) : SendOutcome

        /** 所有入口都连不上 */
        object Unreachable : SendOutcome
    }

    /** 一台电脑的登记信息（name 空 = 该码从未有设备上线） */
    data class DeviceInfo(val name: String, val lastSeenMs: Long)

    /** 按序返回入口：上次成功的排最前 */
    private fun orderedEndpoints(): List<String> {
        val remembered = prefs.getString(KEY_LAST_ENDPOINT)
        if (remembered.isNullOrBlank() || remembered !in ENDPOINTS) return ENDPOINTS
        return listOf(remembered) + ENDPOINTS.filter { it != remembered }
    }

    /** 向一个配对码发送一句话 */
    suspend fun send(code: String, text: String, from: String, duration: Int): SendOutcome =
        withContext(Dispatchers.IO) {
            val payload = json.encodeToString(
                AnnounceSendBody.serializer(),
                AnnounceSendBody(pairCode = code, text = text, from = from, duration = duration),
            )
            val body = payload.toRequestBody("application/json; charset=utf-8".toMediaType())

            for (endpoint in orderedEndpoints()) {
                val reply = try {
                    exchange(Request.Builder().url("$endpoint/api/announce/send").post(body))
                } catch (_: Exception) {
                    continue
                }

                if (reply.code in 200..299) {
                    prefs.putString(KEY_LAST_ENDPOINT, endpoint)
                    val devices = runCatching {
                        json.decodeFromString<AnnounceSendResponse>(reply.body).devices
                    }.getOrDefault(0)
                    return@withContext SendOutcome.Sent(devices)
                }

                val error = runCatching {
                    json.decodeFromString<AnnounceErrorResponse>(reply.body).error
                }.getOrDefault("")

                // 限流 / 配对码被拒这类确定性失败不换入口重试；5xx 换个入口再试
                if (reply.code >= 500) continue
                return@withContext SendOutcome.Rejected(error)
            }
            SendOutcome.Unreachable
        }

    /** 查询一个配对码的设备登记信息；网络不可达返回 null */
    suspend fun queryDevice(code: String): DeviceInfo? = withContext(Dispatchers.IO) {
        for (endpoint in orderedEndpoints()) {
            val reply = try {
                exchange(Request.Builder().url("$endpoint/api/announce/list?pairCode=$code").get())
            } catch (_: Exception) {
                continue
            }
            if (reply.code !in 200..299) continue
            val parsed = runCatching {
                json.decodeFromString<AnnounceListResponse>(reply.body)
            }.getOrNull() ?: continue
            prefs.putString(KEY_LAST_ENDPOINT, endpoint)
            val first = parsed.devices.firstOrNull()
            return@withContext DeviceInfo(first?.name.orEmpty(), first?.ts ?: 0L)
        }
        null
    }

    private data class HttpReply(val code: Int, val body: String)

    private fun exchange(builder: Request.Builder): HttpReply {
        val request = builder.header("User-Agent", USER_AGENT).build()
        client.newCall(request).execute().use { response ->
            return HttpReply(response.code, response.body?.string().orEmpty())
        }
    }
}

@Serializable
private data class AnnounceSendBody(
    val pairCode: String,
    val text: String,
    val from: String,
    val duration: Int,
)

@Serializable
private data class AnnounceSendResponse(
    val ok: Boolean = false,
    val id: String? = null,
    val devices: Int = 0,
)

@Serializable
private data class AnnounceErrorResponse(val error: String = "")

@Serializable
private data class AnnounceListResponse(val devices: List<AnnounceListDevice> = emptyList())

@Serializable
private data class AnnounceListDevice(val name: String = "", val ts: Long = 0L)
