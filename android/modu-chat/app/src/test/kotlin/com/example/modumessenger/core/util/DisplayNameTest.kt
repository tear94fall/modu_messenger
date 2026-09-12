package com.example.modumessenger.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayNameTest {

    @Test
    fun `별칭이 있으면 별칭을 쓴다`() {
        val names = mapOf("u1" to "친구")
        assertEquals("친구", DisplayName.of("u1", "본명", names))
    }

    @Test
    fun `별칭이 공백뿐이면 상대가 정한 이름을 쓴다`() {
        val names = mapOf("u1" to "   ")
        assertEquals("본명", DisplayName.of("u1", "본명", names))
    }

    @Test
    fun `별칭을 지운 친구도 상대가 정한 이름을 쓴다`() {
        val names = mapOf("u1" to "")
        assertEquals("본명", DisplayName.of("u1", "본명", names))
    }

    @Test
    fun `친구가 아니면 상대가 정한 이름을 쓴다`() {
        assertEquals("본명", DisplayName.of("u2", "본명", mapOf("u1" to "친구")))
    }

    @Test
    fun `이름이 아예 없으면 빈 문자열`() {
        assertEquals("", DisplayName.of(null, null, emptyMap()))
    }
}
