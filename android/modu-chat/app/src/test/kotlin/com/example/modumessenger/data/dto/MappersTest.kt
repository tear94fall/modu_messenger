package com.example.modumessenger.data.dto

import com.example.modumessenger.core.model.ChatType
import com.example.modumessenger.core.model.ProfileType
import com.example.modumessenger.core.model.Role
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test

class MappersTest {

    private val gson = Gson()

    @Test
    fun `역할 문자열을 enum 으로 바꾼다`() {
        assertEquals(Role.USER, roleOf("ROLE_USER"))
        assertEquals(Role.ADMIN, roleOf("ROLE_ADMIN"))
        assertEquals(Role.USER, roleOf(null))
        assertEquals(Role.USER, roleOf("뭔가이상한값"))
    }

    @Test
    fun `서버가 보내는 profiles 이름으로 읽는다`() {
        val json = """
            {"id":1,"userId":"u1","email":"a@b.c","role":"ROLE_USER","username":"나",
             "profiles":[{"id":9,"memberId":1,"profileType":"PROFILE_IMAGE","value":"a.jpg",
                          "createdDate":"2026-09-12 10:14:00","updatedDate":"2026-09-12 10:14:00"}]}
        """.trimIndent()

        val member = gson.fromJson(json, MemberDto::class.java).toModel()

        assertEquals(1L, member.id)
        assertEquals(Role.USER, member.role)
        assertEquals(1, member.profiles.size)
        assertEquals(ProfileType.PROFILE_IMAGE, member.profiles[0].type)
        assertEquals("a.jpg", member.profiles[0].value)
    }

    @Test
    fun `널 필드는 빈 문자열로 채운다`() {
        val member = MemberDto(id = null, userId = null, username = null).toModel()
        assertEquals(0L, member.id)
        assertEquals("", member.userId)
        assertEquals("", member.username)
        assertEquals(emptyList<Any>(), member.profiles)
    }

    @Test
    fun `모르는 프로필 종류는 상태메시지로 본다`() {
        assertEquals(ProfileType.PROFILE_WALLPAPER, profileTypeOf("PROFILE_WALLPAPER"))
        assertEquals(ProfileType.PROFILE_STATUS_MESSAGE, profileTypeOf("???"))
    }

    @Test
    fun `채팅 타입이 비어 있으면 텍스트로 본다`() {
        assertEquals(ChatType.TEXT, ChatDto(id = 1L).toModel().chatType)
        assertEquals(ChatType.IMAGE, ChatDto(id = 1L, chatType = ChatType.IMAGE).toModel().chatType)
    }

    @Test
    fun `방 DTO 는 멤버까지 모델로 바꾼다`() {
        val room = ChatRoomDto(
            roomId = "r1",
            roomName = "방",
            members = listOf(MemberDto(id = 1L, userId = "u1", username = "나")),
        ).toModel()

        assertEquals("r1", room.roomId)
        assertEquals(1, room.members.size)
        assertEquals("u1", room.members[0].userId)
        assertEquals(0, room.unreadCount)
    }

    @Test
    fun `페이지 내용만 바꾼다`() {
        val page = PageResponseDto(
            content = listOf(MemberDto(id = 1L, userId = "u1")),
            page = 2, size = 50, totalElements = 120L, totalPages = 3, last = false,
        )
        val mapped = page.mapContent { it.toModel() }
        assertEquals(2, mapped.page)
        assertEquals(120L, mapped.totalElements)
        assertEquals("u1", mapped.items[0].userId)
    }
}
