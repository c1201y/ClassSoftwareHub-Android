package com.classsoftwarehub.android.data.remote

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object NetworkModule {

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    fun createOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(
            HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        )
        .build()

    /**
     * 目录镜像等"多入口逐个试"的场景：单个入口短超时，连不上尽快换下一条。
     * （一次刷新最坏会串行试好几个入口，超时太长会让人干等）
     */
    fun createShortTimeoutClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    /** 喊话发送：单入口 10 秒内没结果就换下一个（与桌面端 TimeoutMs = 10000 对齐） */
    fun createAnnounceClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * 提交服务（/api/submit、/api/feedback、/api/oss-sign）：单入口 10 秒预算，
     * 与桌面端 SubmitEndpoint.TimeoutMs 一致 —— 连不上尽快换下一个入口。
     */
    fun createSubmitClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * OSS 直传专用：软件包可能几百 MB，绝不能让总超时把上传掐死。
     * 只保留连接超时与响应读取超时，写方向不设限。
     */
    fun createUploadClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(0, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun createApi(baseUrl: String, client: OkHttpClient = createOkHttpClient()): ClassSoftwareHubApi =
        Retrofit.Builder()
            .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ClassSoftwareHubApi::class.java)
}
