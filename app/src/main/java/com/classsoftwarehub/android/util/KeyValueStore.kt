package com.classsoftwarehub.android.util

import android.content.Context

/**
 * 轻量键值存储（SharedPreferences）。
 * 只承载少量元数据（镜像入口记忆、仓库同步标记等），业务数据一律走 Room / DataStore。
 */
class KeyValueStore(context: Context, name: String) {

    private val prefs = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    fun getString(key: String): String? = prefs.getString(key, null)

    fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun getInt(key: String, default: Int): Int = prefs.getInt(key, default)

    fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }
}
