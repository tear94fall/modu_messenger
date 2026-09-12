package com.example.modumessenger.data.api

import com.example.modumessenger.data.dto.ChatReadCursorDto
import com.example.modumessenger.data.dto.ChatRoomDto
import com.example.modumessenger.data.dto.ChatRoomUnreadDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ChatRoomApi {

    /** 경로 이름은 `memberId` 지만 숫자 회원 id 를 문자열로 넣는다. */
    @GET("chat-service/api-public/chat/{memberId}/rooms")
    suspend fun getRooms(@Path("memberId") memberId: String): List<ChatRoomDto>

    @GET("chat-service/api-public/chat/{roomId}/room")
    suspend fun getRoom(@Path("roomId") roomId: String): ChatRoomDto

    /** 같은 멤버 구성의 방이 있으면 서버가 그 방을 돌려준다. */
    @POST("chat-service/api-public/chat/chat/room")
    suspend fun createRoom(@Body ids: List<Long>): ChatRoomDto

    @DELETE("chat-service/api-public/chat/{roomId}/member/{userId}")
    suspend fun leaveRoom(
        @Path("roomId") roomId: String,
        @Path("userId") userId: String,
    ): ChatRoomDto

    @POST("chat-service/api-public/chat/{roomId}/room")
    suspend fun updateRoom(
        @Path("roomId") roomId: String,
        @Body room: ChatRoomDto,
    ): ChatRoomDto

    @POST("chat-service/api-public/chat/{roomId}/member")
    suspend fun inviteMembers(
        @Path("roomId") roomId: String,
        @Body userIds: List<String>,
    ): ChatRoomDto

    /** 경로 이름은 `userId` 지만 숫자 회원 id 를 넣는다(기존 앱과 같은 호출). */
    @GET("chat-service/api-public/chat/unread/{userId}")
    suspend fun getUnreadCounts(@Path("userId") memberId: String): List<ChatRoomUnreadDto>

    @POST("chat-service/api-public/chat/read/{roomId}/{userId}")
    suspend fun updateLastRead(
        @Path("roomId") roomId: String,
        @Path("userId") memberId: String,
    )

    @GET("chat-service/api-public/chat/read/{roomId}")
    suspend fun getReadCursors(@Path("roomId") roomId: String): List<ChatReadCursorDto>
}
