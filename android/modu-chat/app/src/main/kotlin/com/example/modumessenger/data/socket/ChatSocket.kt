package com.example.modumessenger.data.socket

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 채팅 웹소켓. 연결·재연결은 구현이 알아서 하고, 바깥에는 상태와 사건만 보여 준다.
 * `ChatRepository` 하나만 이걸 구독한다.
 */
interface ChatSocket {

    val state: StateFlow<ConnectionState>

    /** 버퍼가 있는 hot flow. 구독자가 없어도 emit 이 막히지 않는다. */
    val events: SharedFlow<SocketEvent>

    fun connect()

    fun disconnect()

    /** 연결돼 있지 않으면 큐에 쌓지 않고 곧바로 false. 호출자가 실패를 처리한다. */
    fun send(text: String): Boolean
}

/**
 * 핸드셰이크 자격증명 공급자. 값이 아니라 공급자를 받는 이유는 매 연결 시도마다
 * 최신 토큰을 다시 읽기 위해서다 — 401 로 끊긴 뒤 예약된 재연결이 새 토큰을 자동으로 쓴다.
 *
 * 돌려주는 값은 `(userId, 순수 JWT)`. `Bearer ` 접두사는 소켓이 붙인다.
 * 아직 로그인 전이면 null.
 */
fun interface SocketCredentials {
    suspend fun get(): Pair<String, String>?
}
