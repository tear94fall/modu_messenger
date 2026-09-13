package com.example.modumessenger.data.socket

import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * 재연결 지연 계산기. 1s 부터 2배씩 올라가 30s 에서 상한에 걸린다.
 * 서버가 재시작할 때 모든 클라이언트가 같은 순간에 몰리지 않도록 ±20% jitter 를 섞는다.
 *
 * [random] 을 주입받아 테스트에서 jitter 를 고정한다.
 */
class ReconnectPolicy(private val random: Random = Random.Default) {

    @Volatile
    var attempt: Int = 0
        private set

    @Synchronized
    fun nextDelayMs(): Long {
        var base = INITIAL_DELAY_MS
        var i = 0
        while (i < attempt && base < MAX_DELAY_MS) {
            base *= 2
            i++
        }
        if (base > MAX_DELAY_MS) base = MAX_DELAY_MS
        attempt++

        val factor = (1.0 - JITTER_RATIO) + (2.0 * JITTER_RATIO * random.nextDouble())
        return (base * factor).roundToLong()
    }

    @Synchronized
    fun reset() {
        attempt = 0
    }

    companion object {
        const val INITIAL_DELAY_MS = 1000L
        const val MAX_DELAY_MS = 30000L
        private const val JITTER_RATIO = 0.2
    }
}
