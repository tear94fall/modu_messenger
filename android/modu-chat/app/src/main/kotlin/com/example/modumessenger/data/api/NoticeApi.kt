package com.example.modumessenger.data.api

import com.example.modumessenger.data.dto.NoticeDto
import retrofit2.http.GET

interface NoticeApi {

    @GET("member-service/api-public/notice")
    suspend fun getNotices(): List<NoticeDto>
}
