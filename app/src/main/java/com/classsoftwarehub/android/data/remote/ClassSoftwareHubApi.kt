package com.classsoftwarehub.android.data.remote

import com.classsoftwarehub.android.data.remote.dto.SoftwareDto
import retrofit2.http.GET

interface ClassSoftwareHubApi {

    /** 拉取软件清单（远程站点 API，失败时回退到内置目录） */
    @GET("api/software")
    suspend fun getCatalog(): List<SoftwareDto>
}
