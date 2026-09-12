package com.example.modumessenger.core.model

/** 채팅방. [unreadCount] 는 서버 방 응답에 없고 unread API 와 소켓 수신으로 클라이언트가 채운다. */
data class ChatRoom(
    val roomId: String = "",
    val roomName: String = "",
    val roomImage: String = "",
    val lastChatMsg: String = "",
    val lastChatId: String = "",
    val lastChatTime: String = "",
    val members: List<Member> = emptyList(),
    val unreadCount: Int = 0,
)
