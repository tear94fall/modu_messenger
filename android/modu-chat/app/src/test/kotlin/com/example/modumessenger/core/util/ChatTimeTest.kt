package com.example.modumessenger.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.util.Locale

class ChatTimeTest {

    @Test
    fun `서버 포맷을 파싱한다`() {
        val parsed = ChatTime.parse("2026-09-12 10:14:00")
        assertEquals(LocalDateTime.of(2026, 9, 12, 10, 14, 0), parsed)
    }

    @Test
    fun `엉뚱한 값은 널`() {
        assertNull(ChatTime.parse("어제"))
        assertNull(ChatTime.parse(null))
        assertNull(ChatTime.parse(""))
    }

    @Test
    fun `포맷은 파싱의 역이다`() {
        val value = "2026-09-12 10:14:00"
        assertEquals(value, ChatTime.format(ChatTime.parse(value)!!))
    }

    @Test
    fun `짧은 시각은 한국어 오전 오후를 쓴다`() {
        assertEquals("오전 10:14", ChatTime.shortTime("2026-09-12 10:14:00", Locale.KOREA))
        assertEquals("오후 10:14", ChatTime.shortTime("2026-09-12 22:14:00", Locale.KOREA))
    }

    @Test
    fun `짧은 시각은 파싱 실패 시 빈 문자열`() {
        assertEquals("", ChatTime.shortTime("", Locale.KOREA))
    }
}
