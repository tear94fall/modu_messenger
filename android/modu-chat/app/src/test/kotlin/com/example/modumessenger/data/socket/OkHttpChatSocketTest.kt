package com.example.modumessenger.data.socket

import app.cash.turbine.test
import com.example.modumessenger.core.network.NetworkMonitor
import com.google.gson.Gson
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

/**
 * MockWebServer 의 웹소켓 업그레이드로 실제 핸드셰이크·프레임을 태운다.
 * 가상 시간이 아니라 실제 IO 스레드를 쓰므로 `runBlocking` + 넉넉한 타임아웃으로 돌린다.
 */
class OkHttpChatSocketTest {

    private lateinit var server: MockWebServer
    private lateinit var scope: CoroutineScope
    private lateinit var socket: OkHttpChatSocket

    /** 서버 쪽 소켓. 여기로 프레임을 밀어 넣는다. */
    private val serverSocket = CompletableDeferred<WebSocket>()

    private val networkAvailable = MutableSharedFlow<Unit>()

    private val fakeNetworkMonitor = object : NetworkMonitor {
        override val available: Flow<Unit> = networkAvailable
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @After
    fun tearDown() {
        if (::socket.isInitialized) socket.disconnect()
        scope.cancel()
        server.shutdown()
    }

    private fun enqueueUpgrade() {
        server.enqueue(
            MockResponse().withWebSocketUpgrade(
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        serverSocket.complete(webSocket)
                    }

                    // 클라이언트의 close 프레임에 응답해야 연결이 실제로 닫히고
                    // MockWebServer 가 셧다운할 수 있다.
                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        webSocket.close(code, reason)
                    }
                },
            ),
        )
    }

    private fun newSocket(
        credentials: SocketCredentials = SocketCredentials { USER_ID to JWT },
    ): OkHttpChatSocket = OkHttpChatSocket(
        client = OkHttpClient.Builder()
            .pingInterval(OkHttpChatSocket.PING_INTERVAL_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build(),
        url = server.url("/ws-service/modu-chat").toString(),
        credentials = credentials,
        policy = ReconnectPolicy(),
        networkMonitor = fakeNetworkMonitor,
        gson = Gson(),
        scope = scope,
    ).also { socket = it }

    @Test
    fun `핸드셰이크에 userId 와 Bearer 토큰을 싣는다`() = runBlocking {
        enqueueUpgrade()
        newSocket().connect()

        serverSocket.await()
        val request = server.takeRequest()

        assertEquals(USER_ID, request.getHeader("userId"))
        assertEquals("Bearer $JWT", request.getHeader("Authorization"))
    }

    @Test
    fun `채팅 프레임을 ChatDto 로 올린다`() = runBlocking {
        enqueueUpgrade()
        val socket = newSocket()

        socket.events.test(timeout = TIMEOUT) {
            socket.connect()
            serverSocket.await().send(
                """{"id":42,"chatType":1,"roomId":"room-1","sender":"user-b",""" +
                    """"message":"hello","chatTime":"2026-08-26 10:00:00"}""",
            )

            val event = awaitItem() as SocketEvent.Chat
            assertEquals(42L, event.dto.id)
            assertEquals("room-1", event.dto.roomId)
            assertEquals("user-b", event.dto.sender)
            assertEquals("hello", event.dto.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `READ 프레임의 문자열 커서를 숫자로 읽는다`() = runBlocking {
        enqueueUpgrade()
        val socket = newSocket()

        socket.events.test(timeout = TIMEOUT) {
            socket.connect()
            serverSocket.await().send(
                """{"type":"READ","roomId":"room-1","userId":"user-b","lastReadChatId":"315"}""",
            )

            assertEquals(SocketEvent.Read("room-1", "user-b", 315L), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `빈 커서와 깨진 JSON 이 와도 리더가 살아 있다`() = runBlocking {
        enqueueUpgrade()
        val socket = newSocket()

        socket.events.test(timeout = TIMEOUT) {
            socket.connect()
            val ws = serverSocket.await()

            // 메시지가 하나도 없는 방은 빈 문자열이 온다 -> 0.
            ws.send("""{"type":"READ","roomId":"room-1","userId":"user-b","lastReadChatId":""}""")
            assertEquals(SocketEvent.Read("room-1", "user-b", 0L), awaitItem())

            // 숫자로 못 읽는 커서도 0. 프레임을 통째로 버리지 않는다.
            ws.send("""{"type":"READ","roomId":"room-1","userId":"user-b","lastReadChatId":"abc"}""")
            assertEquals(SocketEvent.Read("room-1", "user-b", 0L), awaitItem())

            // 완전히 깨진 프레임은 조용히 버리고 다음 프레임은 정상 처리한다.
            ws.send("not json at all")
            ws.send("""{"type":"ROOM_CREATED","roomId":"room-9"}""")
            assertEquals(SocketEvent.RoomCreated("room-9"), awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `401 핸드셰이크는 AuthFailure 를 올린다`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        val socket = newSocket()

        socket.events.test(timeout = TIMEOUT) {
            socket.connect()

            assertEquals(SocketEvent.AuthFailure, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        // 401 이어도 재연결은 예약된다(토큰이 갱신되면 다음 시도에서 붙는다).
        withTimeout(TIMEOUT) { socket.state.first { it == ConnectionState.RECONNECTING } }
        Unit
    }

    @Test
    fun `연결 전에는 send 가 false 를 돌려준다`() {
        newSocket()

        assertEquals(false, socket.send("{}"))
    }

    @Test
    fun `자격증명이 없어도 빈 헤더로 연결을 시도한다`() = runBlocking {
        enqueueUpgrade()
        newSocket(credentials = SocketCredentials { null }).connect()

        serverSocket.await()
        val request = server.takeRequest()

        assertEquals("", request.getHeader("userId"))
        assertEquals("", request.getHeader("Authorization"))
    }

    private companion object {
        const val USER_ID = "user-a"
        const val JWT = "jwt-123"
        val TIMEOUT = 10.seconds
    }
}
