package com.example.modumessenger.data.dto

/**
 * FCM data 페이로드. 값이 전부 문자열로 온다(`type` 은 chatType 정수의 문자열, `memberCount` `"2"` 는 1:1 방).
 */
data class FcmMessageDto(
    val type: String? = null,
    val title: String? = null,
    val message: String? = null,
    val roomId: String? = null,
    val sender: String? = null,
    val senderName: String? = null,
    val memberCount: String? = null,
) {
    companion object {
        fun from(data: Map<String, String>): FcmMessageDto = FcmMessageDto(
            type = data["type"],
            title = data["title"],
            message = data["message"],
            roomId = data["roomId"],
            sender = data["sender"],
            senderName = data["senderName"],
            memberCount = data["memberCount"],
        )
    }
}
