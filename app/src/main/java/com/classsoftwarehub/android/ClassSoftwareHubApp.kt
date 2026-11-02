package com.classsoftwarehub.android

import android.app.Application
import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.classsoftwarehub.android.data.local.AppDatabase
import com.classsoftwarehub.android.data.remote.AnnounceClient
import com.classsoftwarehub.android.data.remote.NetworkModule
import com.classsoftwarehub.android.data.remote.RepoCatalogSource
import com.classsoftwarehub.android.data.remote.SubmitClient
import com.classsoftwarehub.android.data.repository.AnnounceRepository
import com.classsoftwarehub.android.data.repository.DraftRepository
import com.classsoftwarehub.android.data.repository.DownloadRepository
import com.classsoftwarehub.android.data.repository.SettingsRepository
import com.classsoftwarehub.android.data.repository.SoftwareRepository
import com.classsoftwarehub.android.download.DownloadNotifications
import com.classsoftwarehub.android.download.DownloadRecoveryWorker
import com.classsoftwarehub.android.util.KeyValueStore
import okhttp3.OkHttpClient

/** 轻量依赖容器（替代 Hilt，手工装配） */
class AppContainer(private val app: Application) {

    val application: Application get() = app

    val json = NetworkModule.json

    val okHttpClient: OkHttpClient by lazy { NetworkModule.createOkHttpClient() }

    val database: AppDatabase by lazy { AppDatabase.create(app) }

    /** 少量元数据（镜像入口记忆、仓库同步标记、喊话入口记忆） */
    val keyValueStore: KeyValueStore by lazy { KeyValueStore(app, "csh_meta") }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(app) }

    /** 目录镜像类请求：单入口短超时，连不上尽快换下一条 */
    private val shortTimeoutClient: OkHttpClient by lazy { NetworkModule.createShortTimeoutClient() }

    /** 站点仓库镜像源（软件数据走自建 gh 反代 / jsDelivr / gh-proxy，不直连 GitHub） */
    val repoCatalogSource: RepoCatalogSource by lazy {
        RepoCatalogSource(
            context = app,
            client = shortTimeoutClient,
            json = json,
            prefs = keyValueStore,
        )
    }

    val softwareRepository: SoftwareRepository by lazy {
        SoftwareRepository(
            context = app,
            dao = database.softwareDao(),
            api = NetworkModule.createApi(BuildConfig.DEFAULT_API_BASE_URL, shortTimeoutClient),
            repoSource = repoCatalogSource,
            json = json,
            prefs = keyValueStore,
        )
    }

    val draftRepository: DraftRepository by lazy { DraftRepository(database.draftDao()) }

    val announceClient: AnnounceClient by lazy {
        AnnounceClient(
            client = NetworkModule.createAnnounceClient(),
            json = json,
            prefs = keyValueStore,
        )
    }

    val announceRepository: AnnounceRepository by lazy {
        AnnounceRepository(database.announceDeviceDao(), announceClient)
    }

    /** 提交软件 / 问题反馈 / OSS 直传（自建 Worker classhub） */
    val submitClient: SubmitClient by lazy {
        SubmitClient(
            appContext = app,
            client = NetworkModule.createSubmitClient(),
            uploadClient = NetworkModule.createUploadClient(),
            json = json,
            prefs = keyValueStore,
        )
    }

    val downloadNotifications: DownloadNotifications by lazy { DownloadNotifications(app) }

    val downloadRepository: DownloadRepository by lazy {
        DownloadRepository(
            context = app,
            dao = database.downloadTaskDao(),
            settingsRepository = settingsRepository,
            notifications = downloadNotifications,
        )
    }
}

class ClassSoftwareHubApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.downloadNotifications.ensureChannels()
        container.downloadRepository.start()

        // 进程被杀后，把残留的 DOWNLOADING 任务重置为 PENDING 并尝试恢复下载
        WorkManager.getInstance(this).enqueueUniqueWork(
            DownloadRecoveryWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<DownloadRecoveryWorker>().build(),
        )
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as ClassSoftwareHubApp).container
