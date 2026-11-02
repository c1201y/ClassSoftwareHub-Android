package com.classsoftwarehub.android.data.repository

import com.classsoftwarehub.android.data.local.dao.DraftDao
import com.classsoftwarehub.android.data.local.entity.DraftEntity

/** 提交表单草稿的本地持久化 */
class DraftRepository(private val dao: DraftDao) {

    companion object {
        const val TYPE_SOFTWARE_SUBMIT = "software_submit"
        const val TYPE_FEEDBACK = "feedback"
    }

    suspend fun save(type: String, contentJson: String) {
        dao.upsert(DraftEntity(type = type, contentJson = contentJson, updatedAt = System.currentTimeMillis()))
    }

    suspend fun load(type: String): String? = dao.get(type)?.contentJson

    suspend fun clear(type: String) {
        dao.delete(type)
    }
}
