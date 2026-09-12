package com.example.modumessenger.core.util

import com.example.modumessenger.core.model.Member
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRoomNameUtilTest {

    private fun member(userId: String, username: String) = Member(userId = userId, username = username)

    private val me = member("me", "나")
    private val a = member("a", "가나다")
    private val b = member("b", "라마바")

    @Test
    fun `서버 기본 이름은 직접 지은 이름이 아니다`() {
        assertFalse(ChatRoomNameUtil.hasCustomName(ChatRoomNameUtil.DEFAULT_ROOM_NAME))
        assertFalse(ChatRoomNameUtil.hasCustomName("  "))
        assertFalse(ChatRoomNameUtil.hasCustomName(null))
        assertTrue(ChatRoomNameUtil.hasCustomName("우리방"))
    }

    @Test
    fun `방 이미지 유무`() {
        assertFalse(ChatRoomNameUtil.hasCustomImage(""))
        assertFalse(ChatRoomNameUtil.hasCustomImage(null))
        assertTrue(ChatRoomNameUtil.hasCustomImage("a.jpg"))
    }

    @Test
    fun `직접 지은 이름이 있으면 그것을 쓴다`() {
        val name = ChatRoomNameUtil.resolve("우리방", listOf(me, a), "me", "나", emptyMap(), 25)
        assertEquals("우리방", name)
    }

    @Test
    fun `이름이 없으면 나를 뺀 참여자 이름을 잇는다`() {
        val name = ChatRoomNameUtil.resolve(
            ChatRoomNameUtil.DEFAULT_ROOM_NAME, listOf(me, a, b), "me", "나", emptyMap(), 0,
        )
        assertEquals("가나다, 라마바", name)
    }

    @Test
    fun `참여자 이름에도 별칭을 쓴다`() {
        val name = ChatRoomNameUtil.resolve(null, listOf(me, a), "me", "나", mapOf("a" to "별칭"), 0)
        assertEquals("별칭", name)
    }

    @Test
    fun `나 말고 아무도 없으면 나와의 채팅`() {
        val name = ChatRoomNameUtil.resolve(null, listOf(me), "me", "나", emptyMap(), 0)
        assertEquals("나와의 채팅 (나)", name)
    }

    @Test
    fun `maxLength 로 자르고 끝의 쉼표를 뗀다`() {
        val long = ChatRoomNameUtil.resolve(
            null,
            listOf(me, member("a", "일이삼사오"), member("b", "육칠팔구십")),
            "me", "나", emptyMap(), 6,
        )
        // "일이삼사오, 육칠팔구십" 을 6 자로 자르면 "일이삼사오," 이고 끝 쉼표를 뗀다.
        assertEquals("일이삼사오", long)
    }

    @Test
    fun `maxLength 가 0 이하면 자르지 않는다`() {
        val name = ChatRoomNameUtil.resolve("아주아주 긴 방 이름", emptyList(), "me", "나", emptyMap(), 0)
        assertEquals("아주아주 긴 방 이름", name)
    }
}
