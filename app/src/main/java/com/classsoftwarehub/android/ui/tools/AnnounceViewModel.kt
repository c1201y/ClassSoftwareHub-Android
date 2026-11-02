package com.classsoftwarehub.android.ui.tools

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.classsoftwarehub.android.data.model.AnnounceDevice
import com.classsoftwarehub.android.data.repository.AnnounceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 教室喊话（发送端）：
 *   · 设备名单存 Room（勾选即定向，可多选批量）；
 *   · 内容 ≤100 字、署名 ≤20 字、时长 15/30/60 秒，与桌面端/网页端口径一致；
 *   · 发送走 AnnounceClient 多入口回退，聚合结果里把"受理"与"送达"分开说。
 */
class AnnounceViewModel(private val repository: AnnounceRepository) : ViewModel() {

    companion object {
        /** 在线判定窗口：略大于最大轮询间隔（60 秒档 + 抖动） */
        const val ONLINE_WINDOW_MS = 90_000L

        /** 展示时长档位（与桌面端一致） */
        val DURATION_OPTIONS = listOf(15, 30, 60)

        fun isValidPairCode(code: String): Boolean =
            code.length == 6 && code.all { it in '0'..'9' }
    }

    val devices: StateFlow<List<AnnounceDevice>> =
        repository.devices.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 配对码 → 最近一次登记时刻（毫秒）；仅会话内暂存，不落库 */
    val lastSeen = mutableStateMapOf<String, Long>()

    var content by mutableStateOf("")
        private set

    var from by mutableStateOf("")
        private set

    var duration by mutableStateOf(30)
        private set

    var sending by mutableStateOf(false)
        private set

    /** 查询 / 刷新中（添加设备、刷新状态共用） */
    var busy by mutableStateOf(false)
        private set

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun consumeMessage() {
        _message.value = null
    }

    fun onContentChange(value: String) {
        content = value.take(100)
    }

    fun onFromChange(value: String) {
        from = value.take(20)
    }

    fun onDurationChange(value: Int) {
        duration = value
    }

    /** 设备状态：在线 / 离线 / 待确认（该码还没有任何登记） */
    fun statusOf(code: String): String {
        val seen = lastSeen[code] ?: return "待确认"
        if (seen <= 0L) return "待确认"
        return if (System.currentTimeMillis() - seen < ONLINE_WINDOW_MS) "在线" else "离线"
    }

    fun toggle(code: String, selected: Boolean) {
        viewModelScope.launch { repository.setSelected(code, selected) }
    }

    fun setAllSelected(selected: Boolean) {
        viewModelScope.launch { repository.setAllSelected(selected) }
    }

    fun remove(code: String) {
        lastSeen.remove(code)
        viewModelScope.launch { repository.removeDevice(code) }
    }

    /** 添加配对码：先问服务端要机器名与在线时间，再落库 */
    fun addDevice(input: String) {
        val code = input.trim()
        if (!isValidPairCode(code)) {
            _message.value = "配对码格式不对：应为 6 位数字。"
            return
        }
        if (busy) return
        viewModelScope.launch {
            if (repository.isKnown(code)) {
                _message.value = "该设备已在名单中。"
                return@launch
            }
            busy = true
            val info = repository.queryDevice(code)
            busy = false
            if (info == null) {
                _message.value = "查询失败：网络不可达，请稍后重试。"
                return@launch
            }
            repository.addDevice(code, info.name)
            if (info.lastSeenMs > 0) lastSeen[code] = info.lastSeenMs
            _message.value = if (info.lastSeenMs > 0) {
                if (info.name.isBlank()) "已添加。" else "已添加：${info.name}。"
            } else {
                "已添加。该电脑尚未开启「接收喊话」，开启后再点刷新即可显示机器名。"
            }
        }
    }

    /** 刷新全部设备的名称与在线状态 */
    fun refreshStatuses() {
        if (busy) return
        viewModelScope.launch {
            val list = devices.value
            if (list.isEmpty()) return@launch
            busy = true
            var reachable = false
            for (device in list) {
                val info = repository.queryDevice(device.code) ?: continue
                reachable = true
                if (info.lastSeenMs > 0) lastSeen[device.code] = info.lastSeenMs
                if (info.name.isNotBlank() && info.name != device.name) {
                    repository.updateName(device.code, info.name)
                }
            }
            busy = false
            _message.value = if (reachable) "已刷新设备状态。" else "刷新失败：网络不可达。"
        }
    }

    fun send() {
        val text = content.trim()
        if (text.isEmpty()) {
            _message.value = "请先输入喊话内容。"
            return
        }
        val targets = devices.value.filter { it.selected }.map { it.code }
        if (targets.isEmpty()) {
            _message.value = "请先在「目标设备」里勾选要接收的电脑。"
            return
        }
        if (sending) return
        viewModelScope.launch {
            sending = true
            val result = repository.send(text, from.trim(), duration, targets)
            sending = false
            _message.value = when {
                result.anySent -> {
                    val part = if (result.failed > 0) "，${result.failed} 台失败" else ""
                    if (result.devices > 0) {
                        "已发送，${result.devices} 台电脑将在一个轮询间隔内收到$part。"
                    } else {
                        "已发送，但目标电脑当前离线$part —— 电脑开启「接收喊话」后才会收到。"
                    }
                }
                result.firstError == "unreachable" -> "网络不可达：请检查手机网络后重试。"
                result.firstError == "rate_limited" -> "发送过快：同一配对码 10 秒内限发一条。"
                result.firstError == "no_device" -> "目标电脑离线：须开启「接收喊话」并保持联网。"
                result.firstError.isNotEmpty() -> "发送失败（${result.firstError}），请稍后重试。"
                else -> "发送失败，请稍后重试。"
            }
        }
    }
}
