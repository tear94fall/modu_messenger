package com.example.modumessenger.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SsoRequestTest {

    private val challenge = "a".repeat(43)

    @Test
    fun `허용된 앱만 부를 수 있다`() {
        assertTrue(SsoRequest.isAllowedCaller("com.example.moducommerce"))
        assertFalse(SsoRequest.isAllowedCaller("com.example.evil"))
        assertFalse(SsoRequest.isAllowedCaller(null))
    }

    @Test
    fun `정상 요청`() {
        val request = SsoRequest.from("modu-commerce", challenge, "S256")
        assertNotNull(request)
        assertEquals("modu-commerce", request!!.clientId)
        assertEquals(challenge, request.codeChallenge)
        assertEquals("S256", request.codeChallengeMethod)
    }

    @Test
    fun `허용하지 않은 클라이언트는 거절`() {
        assertNull(SsoRequest.from("modu-admin", challenge, "S256"))
        assertNull(SsoRequest.from(null, challenge, "S256"))
    }

    @Test
    fun `챌린지 길이와 문자를 검사한다`() {
        assertNull(SsoRequest.from("modu-commerce", "a".repeat(42), "S256"))
        assertNull(SsoRequest.from("modu-commerce", "a".repeat(129), "S256"))
        assertNull(SsoRequest.from("modu-commerce", "!".repeat(43), "S256"))
        assertNotNull(SsoRequest.from("modu-commerce", "-_".repeat(30), "S256"))
    }

    @Test
    fun `S256 만 받는다`() {
        assertNull(SsoRequest.from("modu-commerce", challenge, "plain"))
        assertNull(SsoRequest.from("modu-commerce", challenge, "s256"))
        assertNull(SsoRequest.from("modu-commerce", challenge, null))
    }
}
