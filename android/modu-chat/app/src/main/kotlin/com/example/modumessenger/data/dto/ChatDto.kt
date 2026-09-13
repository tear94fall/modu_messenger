package com.example.modumessenger.data.dto

import com.google.gson.annotations.SerializedName

/** 소켓으로도 이 모양 그대로 오간다(보낼 때 `id` 는 null, 서버가 붙인다). */
data class ChatDto(
    val id: Long? = null,
    val chatType: Int = 0,
    val roomId: String? = null,
    val sender: String? = null,
    val message: String? = null,
    val chatTime: String? = null,
)

data class ChatRoomDto(
    val roomId: String? = null,
    val roomName: String? = null,
    val roomImage: String? = null,
    val lastChatMsg: String? = null,
    val lastChatId: String? = null,
    val lastChatTime: String? = null,
    val members: List<MemberDto>? = null,
)

data class ChatRoomUnreadDto(
    @SerializedName("roomId") val roomId: String? = null,
    @SerializedName("lastSendChatId") val lastSendChatId: Long = 0L,
    @SerializedName("lastReadChatId") val lastReadChatId: Long = 0L,
    @SerializedName("unreadChatCount") val unreadChatCount: Long = 0L,
)

data class ChatReadCursorDto(
    @SerializedName("userId") val userId: String? = null,
    @SerializedName("lastReadChatId") val lastReadChatId: Long = 0L,
)
