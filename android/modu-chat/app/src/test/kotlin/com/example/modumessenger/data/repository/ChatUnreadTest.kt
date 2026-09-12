package com.example.modumessenger.data.repository

import com.example.modumessenger.core.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Test

/** 자바 `ChatRepositoryTest` 의 안 읽음 계산 케이스를 그대로 옮겼다. */
class ChatUnreadTest {

    private companion object {
        const val ME = "me"
        const val OTHER = "other"
        const val THIRD = "third-person"
    }

    private fun messages(vararg pairs: Pair<Long, String>): Map<Long, ChatMessage> =
        pairs.associate { (id, sender) ->
            id to ChatMessage(id = id, roomId = "room-1", sender = sender, message = "메시지")
        }

    // ---------- unreadCountFor ----------

    @Test
    fun `커서가 뒤처진 멤버를 센다`() {
        val cursors = mapOf<String, Long?>(ME to 10L, OTHER to 4L)

        assertEquals(1, ChatUnread.unreadCountFor(7L, ME, cursors))
    }

    @Test
    fun `발신자 본인은 언제나 읽은 것으로 본다`() {
        val cursors = mapOf<String, Long?>(ME to 0L, OTHER to 99L)

        assertEquals(0, ChatUnread.unreadCountFor(7L, ME, cursors))
    }

    @Test
    fun `커서가 없는 멤버는 한 번도 안 읽은 것이다`() {
        val cursors = mapOf(ME to 10L, OTHER to null)

        assertEquals(1, ChatUnread.unreadCountFor(7L, ME, cursors))
    }

    @Test
    fun `모두 읽었으면 0`() {
        val cursors = mapOf<String, Long?>(ME to 10L, OTHER to 10L)

        assertEquals(0, ChatUnread.unreadCountFor(7L, ME, cursors))
    }

    @Test
    fun `단체방에서는 뒤처진 인원을 모두 센다`() {
        val cursors = mapOf<String, Long?>(ME to 10L, OTHER to 3L, THIRD to 3L)

        assertEquals(2, ChatUnread.unreadCountFor(7L, ME, cursors))
    }

    @Test
    fun `커서가 비어 있으면 0`() {
        assertEquals(0, ChatUnread.unreadCountFor(7L, ME, emptyMap()))
    }

    // ---------- withImpliedCursors ----------

    @Test
    fun `보낸 메시지는 읽음의 증거다`() {
        val merged = ChatUnread.withImpliedCursors(
            mapOf(ME to 10L, OTHER to 0L),
            messages(8L to OTHER),
            ME,
        )

        assertEquals(8L, merged[OTHER])
    }

    @Test
    fun `그 사람이 보낸 것 중 가장 큰 id 를 쓴다`() {
        val merged = ChatUnread.withImpliedCursors(
            mapOf(ME to 10L, OTHER to 0L),
            messages(3L to OTHER, 8L to OTHER, 5L to OTHER),
            ME,
        )

        assertEquals(8L, merged[OTHER])
    }

    @Test
    fun `추론은 커서를 올리기만 한다`() {
        val merged = ChatUnread.withImpliedCursors(
            mapOf(ME to 10L, OTHER to 20L),
            messages(8L to OTHER),
            ME,
        )

        assertEquals(20L, merged[OTHER])
    }

    @Test
    fun `커서가 null 이어도 보낸 메시지로 채운다`() {
        val merged = ChatUnread.withImpliedCursors(
            mapOf(ME to 10L, OTHER to null),
            messages(8L to OTHER),
            ME,
        )

        assertEquals(8L, merged[OTHER])
    }

    @Test
    fun `커서 맵에 없는 발신자는 키를 늘리지 않는다`() {
        val merged = ChatUnread.withImpliedCursors(
            mapOf(ME to 10L, OTHER to 0L),
            messages(8L to "stranger"),
            ME,
        )

        assertEquals("방 멤버가 아닌 발신자는 분모를 늘리면 안 된다", 2, merged.size)
        assertEquals(0L, merged[OTHER])
    }

    @Test
    fun `내 커서는 화면의 마지막 메시지까지다`() {
        // 내가 보고 있는 방이다. openRoom 이 이미 READ 를 보냈으므로 내 커서는 방 끝까지다.
        val merged = ChatUnread.withImpliedCursors(
            mapOf(ME to 5L, OTHER to 0L),
            messages(5L to ME, 9L to OTHER),
            ME,
        )

        assertEquals(9L, merged[ME])
    }

    @Test
    fun `상대는 자기가 보낸 것까지만 읽은 것으로 본다`() {
        val merged = ChatUnread.withImpliedCursors(
            mapOf(ME to 0L, OTHER to 0L),
            messages(5L to OTHER, 9L to ME),
            ME,
        )

        assertEquals(5L, merged[OTHER])
    }

    @Test
    fun `상대가 답장했으면 내 이전 메시지는 읽힌 것이다`() {
        val effective = ChatUnread.withImpliedCursors(
            mapOf(ME to 5L, OTHER to 0L),
            messages(5L to ME, 9L to OTHER),
            ME,
        )

        assertEquals(0, ChatUnread.unreadCountFor(5L, ME, effective))
    }

    @Test
    fun `커서가 전부 null 인 방도 보낸 메시지로 숫자가 지워진다`() {
        val effective = ChatUnread.withImpliedCursors(
            mapOf(ME to null, OTHER to null),
            messages(5L to ME, 9L to OTHER),
            ME,
        )

        assertEquals(0, ChatUnread.unreadCountFor(5L, ME, effective))
    }

    @Test
    fun `임시 음수 id 는 커서를 내리지 않는다`() {
        // 아직 서버 에코가 안 온 낙관적 메시지가 섞여 있어도 커서는 그대로여야 한다.
        val merged = ChatUnread.withImpliedCursors(
            mapOf(ME to 10L, OTHER to 4L),
            messages(-1L to ME),
            ME,
        )

        assertEquals(10L, merged[ME])
        assertEquals(4L, merged[OTHER])
    }
}
