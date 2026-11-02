package com.classsoftwarehub.android.data.remote

import android.content.Context
import com.classsoftwarehub.android.util.DeviceFingerprint
import com.classsoftwarehub.android.util.KeyValueStore
import com.classsoftwarehub.android.util.PickedUpload
import com.classsoftwarehub.android.util.Uploads
import com.classsoftwarehub.android.util.UriRequestBody
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.URLEncoder

/**
 * 自建提交服务（Cloudflare Worker `classhub`）的客户端，对齐桌面端 SubmitClient / OssUpload：
 *
 *   · 提交软件   → POST {入口}/api/submit    （令牌在服务端，客户端无需登录 GitHub）
 *   · 问题反馈   → POST {入口}/api/feedback
 *   · 直传签名   → POST {入口}/api/oss-sign  → 再 PUT 到 OSS（软件包 / 图标两种用途）
 *
 * 入口按序回退（classhub 主域 → submit），上次成功的记在本地下次优先；单入口 10 秒预算；
 * 只有拿到本服务约定的 JSON（success / error）才算"这个入口可用"——
 * 回源还没生效时 nginx 会吐 HTML 错误页，那不是接口响应，换下一个。
 */
class SubmitClient(
    private val appContext: Context,
    private val client: OkHttpClient,
    private val uploadClient: OkHttpClient,
    private val json: Json,
    private val prefs: KeyValueStore,
) {

    companion object {
        private const val USER_AGENT = "ClassSoftwareHub-Android/submit"
        private const val KEY_LAST_ENDPOINT = "submit_endpoint"

        /**
         * 与喊话同一套入口（.workers.dev 域名国内打不开、不列入）。
         * 2026-10-30 起 classhub 全面换到直连主域 api.classsoftwarehub.cn；
         * cshapi.132614.xyz 的第三方中转已 512 下线剔除，submit.132614.xyz 留作回退。
         */
        val ENDPOINTS = listOf(
            "https://api.classsoftwarehub.cn",
            "https://submit.132614.xyz",
        )

        /** 单文件上限 2.5 GB（与网页端 UPLOAD_MAX_BYTES、Worker OSS_MAX_BYTES 对齐） */
        const val UPLOAD_MAX_BYTES = 2_500_000_000L

        /** 超过 1 GB 提示很慢但不阻止 */
        const val UPLOAD_WARN_BYTES = 1_000_000_000L

        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val fingerprint = DeviceFingerprint(prefs)

    /** 通用 POST 结果；null 表示所有入口都不通（连同 HTML 错误页、超时） */
    sealed interface PostOutcome {
        data class Accepted(val message: String, val url: String?) : PostOutcome
        data class Rejected(val error: String) : PostOutcome
    }

    /** OSS 直传结果 */
    sealed interface UploadOutcome {
        data class Done(val url: String, val key: String, val orphanMinutes: Int) : UploadOutcome
        data class Failed(val message: String) : UploadOutcome
    }

    private data class SignTarget(
        val base: String,
        val url: String,
        val key: String,
        val contentType: String,
        val orphanMinutes: Int,
    )

    /** 按序返回入口：上次成功的排最前 */
    private fun orderedEndpoints(): List<String> {
        val remembered = prefs.getString(KEY_LAST_ENDPOINT)
        if (remembered.isNullOrBlank() || remembered !in ENDPOINTS) return ENDPOINTS
        return listOf(remembered) + ENDPOINTS.filter { it != remembered }
    }

    /** 提交 /api/submit 或 /api/feedback；返回 null = 所有入口都不通 */
    suspend fun post(route: String, payload: JsonObject): PostOutcome? = withContext(Dispatchers.IO) {
        val body = payload.toString().toRequestBody(JSON_TYPE)
        for (base in orderedEndpoints()) {
            val reply = try {
                exchange(
                    client,
                    Request.Builder().url("$base$route").post(body).header("User-Agent", USER_AGENT).build(),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                continue
            }

            val parsed = runCatching { json.parseToJsonElement(reply.body).jsonObject }.getOrNull()
            val success = parsed?.get("success")?.jsonPrimitive?.booleanOrNull == true
            val error = parsed?.get("error")?.jsonPrimitive?.contentOrNull
            if (parsed != null && (success || error != null)) {
                prefs.putString(KEY_LAST_ENDPOINT, base)
                return@withContext if (success) {
                    PostOutcome.Accepted(
                        message = parsed["message"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        url = parsed["url"]?.jsonPrimitive?.contentOrNull,
                    )
                } else {
                    PostOutcome.Rejected(error.orEmpty())
                }
            }
            // 不是提交服务的响应（回源未生效 / HTML 错误页）→ 换下一个入口
        }
        null
    }

    /**
     * 把文件直传 OSS：
     *   ① /api/oss-sign 换一次性预签名 PUT 地址（多入口回退，服务端明确拒绝则不再换）；
     *   ② PUT 到 OSS（上传进度即这一步的）；
     *   ③ 返回可写入数据的地址：软件包 `oss://键`、图标 `{入口}/api/icon?k=键`。
     */
    suspend fun upload(
        picked: PickedUpload,
        purpose: String,
        onProgress: ((sent: Long, total: Long) -> Unit)? = null,
    ): UploadOutcome = withContext(Dispatchers.IO) {
        if (picked.size > UPLOAD_MAX_BYTES) {
            return@withContext UploadOutcome.Failed(
                "文件超过上限（${Uploads.humanSize(UPLOAD_MAX_BYTES)}），请改用官网或 GitHub Releases 直链",
            )
        }

        // ① 要预签名地址
        var target: SignTarget? = null
        var lastError: String? = null
        for (base in orderedEndpoints()) {
            val signJson = buildJsonObject {
                put("name", picked.name)
                put("size", picked.size)
                put("contentType", picked.contentType)
                put("purpose", purpose)
                put("fp", fingerprint.value)
            }.toString().toRequestBody(JSON_TYPE)

            val reply = try {
                exchange(
                    client,
                    Request.Builder().url("$base/api/oss-sign").post(signJson).header("User-Agent", USER_AGENT).build(),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                lastError = "连不上提交服务，请检查网络后重试"
                continue
            }

            val parsed = runCatching { json.parseToJsonElement(reply.body).jsonObject }.getOrNull()
            if (parsed == null) {
                lastError = "提交服务响应异常，请稍后重试"
                continue
            }
            val success = parsed["success"]?.jsonPrimitive?.booleanOrNull == true
            if (!success) {
                // 服务端明确拒绝（文件过大 / 未配置）：换入口也没用
                return@withContext UploadOutcome.Failed(
                    parsed["error"]?.jsonPrimitive?.contentOrNull ?: "站点拒绝了这次上传（HTTP ${reply.code}）",
                )
            }
            val url = parsed["url"]?.jsonPrimitive?.contentOrNull
            val key = parsed["key"]?.jsonPrimitive?.contentOrNull
            if (url.isNullOrBlank() || key.isNullOrBlank()) {
                lastError = "提交服务响应缺少上传地址"
                continue
            }
            target = SignTarget(
                base = base,
                url = url,
                key = key,
                contentType = parsed["contentType"]?.jsonPrimitive?.contentOrNull ?: picked.contentType,
                orphanMinutes = parsed["orphanMinutes"]?.jsonPrimitive?.intOrNull ?: 0,
            )
            break
        }
        val signed = target ?: return@withContext UploadOutcome.Failed(lastError ?: "连不上提交服务，请检查网络后重试")

        // ② PUT 到 OSS（Content-Type 必须与签发时锁定的值完全一致）
        try {
            val putRequest = Request.Builder()
                .url(signed.url)
                .put(UriRequestBody(appContext.contentResolver, picked, onProgress))
                .header("Content-Type", signed.contentType)
                .header("User-Agent", USER_AGENT)
                .build()
            exchange(uploadClient, putRequest).let { reply ->
                if (reply.code !in 200..299) {
                    val code = Regex("<Code>([^<]+)</Code>").find(reply.body)?.groupValues?.get(1)
                    return@withContext UploadOutcome.Failed(
                        "上传被存储服务拒绝（HTTP ${reply.code}${if (code != null) " $code" else ""}）",
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return@withContext UploadOutcome.Failed("传输中断，请检查网络后重试")
        }

        // ③ 上传成功后才记住该入口；返回写入数据的地址
        prefs.putString(KEY_LAST_ENDPOINT, signed.base)
        val dataUrl = if (purpose == "icon") {
            "${signed.base}/api/icon?k=${URLEncoder.encode(signed.key, "UTF-8").replace("+", "%20")}"
        } else {
            "oss://${signed.key}"
        }
        UploadOutcome.Done(dataUrl, signed.key, signed.orphanMinutes)
    }

    // ── HTTP 底座 ──

    private data class HttpReply(val code: Int, val body: String)

    private suspend fun exchange(client: OkHttpClient, request: Request): HttpReply {
        val response = executeAsync(client, request)
        response.use { return HttpReply(it.code, it.body?.string().orEmpty()) }
    }

    /** 异步执行 + 协程取消时顺带取消底层 Call */
    private suspend fun executeAsync(client: OkHttpClient, request: Request): Response =
        suspendCancellableCoroutine { cont ->
            val call = client.newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (cont.isActive) cont.resumeWith(Result.failure(e))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        if (cont.isActive) {
                            cont.resumeWith(Result.success(response))
                        } else {
                            response.close()
                        }
                    }
                },
            )
        }
}
