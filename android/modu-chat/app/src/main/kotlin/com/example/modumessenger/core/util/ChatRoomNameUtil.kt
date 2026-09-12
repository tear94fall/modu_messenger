package com.example.modumessenger.core.util

import com.example.modumessenger.core.model.Member

/**
 * 방 이름 규칙을 한 곳에 모은다. 목록과 채팅방 화면이 서로 다른 규칙을 쓰면 같은 방이 다른 이름으로 보인다.
 */
object ChatRoomNameUtil {

    /** 방을 만들 때 서버가 넣는 기본 이름. 이 값은 "직접 지은 이름" 으로 보지 않는다. */
    const val DEFAULT_ROOM_NAME = "새로운 채팅방"

    fun hasCustomName(roomName: String?): Boolean =
        roomName != null && roomName.trim().isNotEmpty() && roomName != DEFAULT_ROOM_NAME

    /** 직접 넣은 방 사진이 있는지. 있으면 참여자 사진 대신 이것을 쓴다. */
    fun hasCustomImage(roomImage: String?): Boolean =
        roomImage != null && roomImage.trim().isNotEmpty()

    /**
     * 화면에 보일 방 이름. 직접 지은 이름이 있으면 그것, 없으면 나를 뺀 참여자 이름을 `", "` 로 잇는다.
     * 나 말고 아무도 없으면 `나와의 채팅 (내이름)`.
     *
     * @param maxLength 0 이하면 자르지 않는다. 목록은 25, 채팅방 제목은 0 을 쓴다.
     */
    fun resolve(
        roomName: String?,
        members: List<Member>?,
        myUserId: String?,
        myUsername: String?,
        names: Map<String, String>,
        maxLength: Int,
    ): String {
        if (hasCustomName(roomName)) return truncate(roomName!!, maxLength)

        val others = (members ?: emptyList())
            .filter { it.userId.isNotEmpty() && it.userId != myUserId }
            .map { DisplayName.of(it.userId, it.username, names) }

        if (others.isEmpty()) return String.format("나와의 채팅 (%s)", myUsername ?: "")

        return truncate(others.joinToString(", "), maxLength)
    }

    private fun truncate(value: String, maxLength: Int): String {
        if (maxLength <= 0 || value.length <= maxLength) return value
        val cut = value.substring(0, maxLength).trim()
        return if (cut.endsWith(",")) cut.substring(0, cut.length - 1) else cut
    }
}
