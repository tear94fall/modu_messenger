package com.example.modumessenger.data.api

import com.example.modumessenger.data.dto.CommonDataDto
import retrofit2.http.GET
import retrofit2.http.Path

interface CommonApi {

    /** 키가 없으면 404 다. */
    @GET("member-service/api-public/common/{key}")
    suspend fun getCommonData(@Path("key") key: String): CommonDataDto
}
