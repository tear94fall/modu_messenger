package com.example.modumessenger.data.socket

import com.example.modumessenger.data.dto.ChatDto

/**
 * 소켓이 올려 보내는 사건. 자바의 `ChatSocketListener` 콜백 다섯 개를 그대로 옮겼다.
 * 상태 변화는 [ChatSocket.state] 로 따로 나가므로 여기에는 없다.
 */
sealed interface SocketEvent {

    /** 채팅 프레임. `type` 필드가 없는 모든 프레임이 여기로 온다. */
    data class Chat(val dto: ChatDto) : SocketEvent

    /** 방 멤버 한 명의 읽음 커서가 올라갔다. */
    data class Read(
        val roomId: String,
        val userId: String,
        val lastReadChatId: Long,
    ) : SocketEvent

    /** 내가 멤버로 들어간 방이 새로 만들어졌다. 프레임에는 roomId 뿐이다. */
    data class RoomCreated(val roomId: String) : SocketEvent

    /** 최초 연결이 아니라 끊겼다 다시 붙었다. 갭 복구 트리거. */
    data object Reconnected : SocketEvent

    /** 핸드셰이크가 401 로 거절됐다. 토큰을 새로 받아야 한다. */
    data object AuthFailure : SocketEvent
}
