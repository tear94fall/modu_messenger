package com.example.modumessenger.core.model

/**
 * 채팅 한 줄. [id] 가 음수면 아직 서버 에코가 돌아오지 않은 낙관적 임시 메시지다.
 * [unreadCount] 는 읽음 커서로 계산한 "아직 안 읽은 사람 수"다.
 */
data class ChatMessage(
    val id: Long = 0L,
    val chatType: Int = ChatType.TEXT,
    val roomId: String = "",
    val sender: String = "",
    val message: String = "",
    val chatTime: String = "",
    val unreadCount: Int = 0,
    val status: SendStatus = SendStatus.SENT,
)

enum class SendStatus { SENDING, SENT, FAILED }

/** 서버와 주고받는 chatType 정수. */
object ChatType {
    const val INVALID = 0
    const val TEXT = 1
    const val IMAGE = 2
    const val FILE = 3
    const val AUDIO = 4
}
