package com.example.modumessenger.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationTextTest {

    private val names = mapOf("s1" to "별칭")

    @Test
    fun `1대1 방은 제목이 발신자다`() {
        val (title, body) = NotificationText.build(names, "방이름", "s1", "본명", "2", "안녕", false)
        assertEquals("별칭", title)
        assertEquals("안녕", body)
    }

    @Test
    fun `단체방은 제목이 방 이름이고 본문에 발신자가 붙는다`() {
        val (title, body) = NotificationText.build(names, "방이름", "s1", "본명", "5", "안녕", false)
        assertEquals("방이름", title)
        assertEquals("별칭: 안녕", body)
    }

    @Test
    fun `이미지면 본문이 고정 문구다`() {
        val (_, body) = NotificationText.build(names, "방이름", "s1", "본명", "2", "a.jpg", true)
        assertEquals(NotificationText.IMAGE_BODY, body)
        assertEquals("새로운 사진", body)
    }

    @Test
    fun `발신자 이름을 못 찾으면 제목은 방 이름이고 본문만 남는다`() {
        val (title, body) = NotificationText.build(emptyMap(), "방이름", "s9", null, "2", "안녕", false)
        assertEquals("방이름", title)
        assertEquals("안녕", body)
    }

    @Test
    fun `memberCount 가 숫자가 아니면 1대1 이 아니다`() {
        val (title, body) = NotificationText.build(names, "방이름", "s1", "본명", "많음", "안녕", false)
        assertEquals("방이름", title)
        assertEquals("별칭: 안녕", body)
    }

    @Test
    fun `메시지가 널이면 빈 본문`() {
        val (_, body) = NotificationText.build(names, "방이름", "s1", "본명", "2", null, false)
        assertEquals("", body)
    }
}
