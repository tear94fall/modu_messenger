package com.example.modumessenger.data.api

import com.example.modumessenger.data.dto.ChatDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface ChatApi {

    /** `ids=` 파라미터가 여러 번 붙는다. */
    @GET("chat-service/api-public/chat")
    suspend fun getChats(@Query("ids") ids: List<String>): List<ChatDto>

    /** 최근 [size] 개. 첫 진입과 재접속 갭 복구에 쓴다. */
    @GET("chat-service/api-public/chat/{roomId}/page/{size}")
    suspend fun getRecent(
        @Path("roomId") roomId: String,
        @Path("size") size: Int,
    ): List<ChatDto>

    /** [chatId] 보다 앞쪽 [size] 개(커서 페이징). */
    @GET("chat-service/api-public/chat/{roomId}/{chatId}/{size}")
    suspend fun getBefore(
        @Path("roomId") roomId: String,
        @Path("chatId") chatId: Long,
        @Path("size") size: Int,
    ): List<ChatDto>

    @GET("chat-service/api-public/chat/{roomId}/images/{size}")
    suspend fun getImages(
        @Path("roomId") roomId: String,
        @Path("size") size: Int,
    ): List<ChatDto>
}
