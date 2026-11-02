package com.classsoftwarehub.android.data.repository

import com.classsoftwarehub.android.data.local.dao.AnnounceDeviceDao
import com.classsoftwarehub.android.data.local.entity.AnnounceDeviceEntity
import com.classsoftwarehub.android.data.model.AnnounceDevice
import com.classsoftwarehub.android.data.remote.AnnounceClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 一次喊话的聚合结果 */
data class AnnounceSendSummary(
    /** 是否至少成功发往一台（部分成功也是 true） */
    val anySent: Boolean,
    /** 服务端合计预计送达设备数 */
    val devices: Int,
    /** 失败的目标数 */
    val failed: Int,
    /** 首个失败原因（rate_limited / no_device / unreachable / …） */
    val firstError: String,
)

/** 教室喊话（发送端）：已配对设备存 Room，发送走 AnnounceClient 多入口回退 */
class AnnounceRepository(
    private val dao: AnnounceDeviceDao,
    private val client: AnnounceClient,
) {

    val devices: Flow<List<AnnounceDevice>> = dao.observeAll().map { rows ->
        rows.map { AnnounceDevice(code = it.code, name = it.name, selected = it.selected) }
    }

    suspend fun addDevice(code: String, name: String) {
        dao.upsert(AnnounceDeviceEntity(code = code, name = name, selected = true))
    }

    suspend fun removeDevice(code: String) = dao.delete(code)

    suspend fun setSelected(code: String, selected: Boolean) = dao.setSelected(code, selected)

    suspend fun setAllSelected(selected: Boolean) = dao.setAllSelected(selected)

    suspend fun updateName(code: String, name: String) = dao.updateName(code, name)

    suspend fun isKnown(code: String): Boolean = dao.count(code) > 0

    suspend fun queryDevice(code: String): AnnounceClient.DeviceInfo? = client.queryDevice(code)

    /** 逐码顺序发送（服务端按码限流，逐条发口径最清楚），整批汇总 */
    suspend fun send(text: String, from: String, duration: Int, codes: List<String>): AnnounceSendSummary {
        val targets = codes.distinct()
        var devices = 0
        var failed = 0
        var firstError = ""
        for (code in targets) {
            when (val outcome = client.send(code, text, from, duration)) {
                is AnnounceClient.SendOutcome.Sent -> devices += outcome.devices
                is AnnounceClient.SendOutcome.Rejected -> {
                    failed++
                    if (firstError.isEmpty()) firstError = outcome.error
                }
                AnnounceClient.SendOutcome.Unreachable -> {
                    failed++
                    if (firstError.isEmpty()) firstError = "unreachable"
                }
            }
        }
        return AnnounceSendSummary(
            anySent = failed < targets.size,
            devices = devices,
            failed = failed,
            firstError = firstError,
        )
    }
}
