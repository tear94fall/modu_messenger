package com.example.modumessenger.data.socket

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class ReconnectPolicyTest {

    /** `nextDouble()` 을 고정해 jitter 를 결정적으로 만든다. 0.5 면 배수가 정확히 1.0 이다. */
    private class FixedRandom(private val value: Double) : Random() {
        override fun nextBits(bitCount: Int): Int = 0
        override fun nextDouble(): Double = value
    }

    @Test
    fun `1초부터 2배씩 늘어난다`() {
        val policy = ReconnectPolicy(FixedRandom(0.5))

        assertEquals(1000L, policy.nextDelayMs())
        assertEquals(2000L, policy.nextDelayMs())
        assertEquals(4000L, policy.nextDelayMs())
        assertEquals(8000L, policy.nextDelayMs())
        assertEquals(16000L, policy.nextDelayMs())
    }

    @Test
    fun `30초에서 상한에 걸린다`() {
        val policy = ReconnectPolicy(FixedRandom(0.5))
        repeat(5) { policy.nextDelayMs() } // 1s, 2s, 4s, 8s, 16s

        assertEquals(30000L, policy.nextDelayMs())
        assertEquals(30000L, policy.nextDelayMs())
    }

    @Test
    fun `jitter 는 20퍼센트 안에 머문다`() {
        assertEquals(800L, ReconnectPolicy(FixedRandom(0.0)).nextDelayMs())
        assertEquals(1200L, ReconnectPolicy(FixedRandom(1.0)).nextDelayMs())
    }

    @Test
    fun `reset 하면 처음 지연으로 돌아간다`() {
        val policy = ReconnectPolicy(FixedRandom(0.5))
        policy.nextDelayMs()
        policy.nextDelayMs()

        policy.reset()

        assertEquals(0, policy.attempt)
        assertEquals(1000L, policy.nextDelayMs())
    }
}
