package com.example.modumessenger.data.repository

import com.example.modumessenger.core.model.ChatMessage

/**
 * 안 읽음 계산. 부작용이 없는 순수 함수라 리포지토리 밖으로 빼서 단위 테스트한다.
 *
 * 커서 맵의 **키 집합이 곧 방 인원**이고, 그게 안 읽음 수의 분모다.
 * 그래서 여기 어떤 함수도 맵에 키를 새로 추가하지 않는다 — 키를 하나 늘리면 방 인원을 잘못 세게 된다.
 */
object ChatUnread {

    /**
     * [chatId] 를 아직 안 읽은 방 인원 수.
     * 발신자 본인은 언제나 읽은 것으로 본다(보냈다는 것이 읽었다는 뜻이다).
     * 커서가 없는(null) 멤버는 한 번도 안 읽은 것으로 센다.
     */
    fun unreadCountFor(chatId: Long, senderUserId: String, cursors: Map<String, Long?>): Int =
        cursors.count { (userId, cursor) ->
            userId != senderUserId && (cursor == null || cursor < chatId)
        }

    /**
     * 메시지 자체에서 유도한 읽음 하한을 커서 맵에 얹는다.
     *
     * 누군가 id N 을 보냈다는 건 그 방을 보고 있었다는 뜻이므로 N 까지는 읽은 것이다.
     * READ 프레임이 유실되거나 커서가 NULL 로 남은 방에서도 숫자가 맞게 된다.
     *
     * 나는 예외다. 이 계산은 내가 지금 보고 있는 활성 방에서만 도는데, `openRoom` 이 이미
     * 서버에 READ 를 보냈다. 그러니 내 커서는 내가 보낸 마지막 메시지가 아니라 **화면의 마지막
     * 메시지**까지다 — 방에 막 들어간 순간 GET 스냅샷이 옛 커서를 들고 와도 상대 메시지에
     * 숫자가 남지 않는다.
     *
     * 커서는 **올리기만** 하고, 맵에 없는 발신자는 건너뛴다.
     */
    fun withImpliedCursors(
        cursors: Map<String, Long?>,
        messagesById: Map<Long, ChatMessage>,
        myUserId: String,
    ): Map<String, Long?> {
        val merged = cursors.toMutableMap()

        messagesById.forEach { (chatId, message) ->
            raise(merged, message.sender, chatId)
            raise(merged, myUserId, chatId)
        }
        return merged
    }

    /** 맵에 이미 있는 사용자의 커서만, 더 큰 값으로만 올린다. */
    private fun raise(cursors: MutableMap<String, Long?>, userId: String, chatId: Long) {
        if (!cursors.containsKey(userId)) return

        val current = cursors[userId]
        if (current == null || current < chatId) cursors[userId] = chatId
    }
}
