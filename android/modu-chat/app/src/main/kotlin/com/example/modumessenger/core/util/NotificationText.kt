package com.example.modumessenger.core.util

/**
 * 채팅 푸시의 제목/본문. 서버는 발신자 userId 와 그 사람이 정한 이름만 주고,
 * "내가 정한 이름" 은 이 기기에 저장된 별칭 맵에서 찾는다.
 * 1:1 방(참여자 2명)은 제목 = 발신자, 그 외에는 제목 = 방 이름, 본문 = `"발신자: 메시지"`.
 */
object NotificationText {

    const val IMAGE_BODY = "새로운 사진"

    /** @return (title, body) */
    fun build(
        names: Map<String, String>,
        roomName: String?,
        senderUserId: String?,
        senderName: String?,
        memberCount: String?,
        message: String?,
        isImage: Boolean,
    ): Pair<String, String> {
        val who = DisplayName.of(senderUserId, senderName, names)
        val text = if (isImage) IMAGE_BODY else (message ?: "")
        val room = roomName ?: ""

        if (who.isEmpty()) return room to text
        if (isOneOnOne(memberCount)) return who to text
        return room to "$who: $text"
    }

    private fun isOneOnOne(memberCount: String?): Boolean =
        memberCount?.trim()?.toIntOrNull() == 2
}
