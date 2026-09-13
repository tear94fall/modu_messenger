package com.example.modumessenger.data.socket

import android.util.Log
import com.example.modumessenger.core.di.ApplicationScope
import com.example.modumessenger.core.network.NetworkMonitor
import com.example.modumessenger.data.dto.ChatDto
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 자바 `OkHttpWebSocketManager` 의 코루틴 포팅.
 *
 * 그대로 옮긴 규칙:
 * - generation 카운터로 옛 소켓의 늦은 콜백을 전부 버린다.
 * - `onClosing` 에 1000 으로 응답해야 `onClosed` 가 와서 재연결이 예약된다.
 * - 401 핸드셰이크 실패는 [SocketEvent.AuthFailure] 를 올리고 **그래도** 재연결을 예약한다.
 * - 네트워크가 돌아오면 대기 시간을 기다리지 않고 즉시 재접속한다.
 * - [SocketEvent.Reconnected] 는 최초 연결이 아닐 때만 나간다(갭 복구 트리거).
 * - 수신 파싱은 어떤 쓰레기 프레임에도 리더가 죽지 않는다.
 */
@Singleton
class OkHttpChatSocket @Inject constructor(
    @WsClient private val client: OkHttpClient,
    @WsUrl private val url: String,
    private val credentials: SocketCredentials,
    private val policy: ReconnectPolicy,
    private val networkMonitor: NetworkMonitor,
    private val gson: Gson,
    @ApplicationScope private val scope: CoroutineScope,
) : ChatSocket {

    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<SocketEvent>(
        replay = 0,
        extraBufferCapacity = EVENT_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val events: SharedFlow<SocketEvent> = _events.asSharedFlow()

    @Volatile
    private var intentionallyClosed = true

    @Volatile
    private var everConnected = false

    @Volatile
    private var webSocket: WebSocket? = null

    /** `openSocket`/`disconnect` 마다 증가. 옛 리스너 콜백을 구분한다. */
    @Volatile
    private var generation = 0

    private var reconnectJob: Job? = null
    private var networkJob: Job? = null

    @Synchronized
    override fun connect() {
        if (_state.value == ConnectionState.CONNECTING || _state.value == ConnectionState.CONNECTED) return

        intentionallyClosed = false
        cancelReconnect()
        updateState(ConnectionState.CONNECTING)
        startNetworkMonitor()
        openSocket()
    }

    @Synchronized
    override fun disconnect() {
        intentionallyClosed = true
        generation++ // 이후 도착하는 옛 소켓 콜백은 전부 stale 이 된다
        cancelReconnect()
        networkJob?.cancel()
        networkJob = null
        policy.reset()

        webSocket?.close(NORMAL_CLOSURE, null)
        webSocket = null
        updateState(ConnectionState.DISCONNECTED)
    }

    override fun send(text: String): Boolean {
        val socket = webSocket ?: return false
        if (_state.value != ConnectionState.CONNECTED) return false
        return socket.send(text)
    }

    // ---------- 연결 ----------

    /**
     * 자격증명 조회가 suspend 라 실제 open 은 코루틴으로 넘어간다.
     * 그 사이에 `disconnect()` 가 끼어들 수 있으므로 세대 번호를 들고 가서 다시 확인한다.
     */
    @Synchronized
    private fun openSocket() {
        if (intentionallyClosed) return

        val myGeneration = ++generation
        scope.launch {
            val creds = runCatching { credentials.get() }.getOrNull()
            openWith(myGeneration, creds)
        }
    }

    @Synchronized
    private fun openWith(requestGeneration: Int, creds: Pair<String, String>?) {
        if (intentionallyClosed || requestGeneration != generation) return

        val request = Request.Builder()
            .url(url)
            .addHeader(HEADER_USER_ID, creds?.first.orEmpty())
            .addHeader(HEADER_AUTHORIZATION, creds?.second?.let { "$BEARER_PREFIX$it" }.orEmpty())
            .build()

        webSocket = client.newWebSocket(request, SocketListener(requestGeneration))
    }

    @Synchronized
    private fun onNetworkAvailable() {
        if (intentionallyClosed) return
        if (_state.value == ConnectionState.CONNECTED || _state.value == ConnectionState.CONNECTING) return

        cancelReconnect()
        policy.reset()
        openSocket()
    }

    @Synchronized
    private fun startNetworkMonitor() {
        if (networkJob != null) return
        networkJob = scope.launch {
            networkMonitor.available.collect { onNetworkAvailable() }
        }
    }

    @Synchronized
    private fun scheduleReconnect() {
        if (intentionallyClosed) return

        updateState(ConnectionState.RECONNECTING)
        val delayMs = policy.nextDelayMs()
        cancelReconnect()
        reconnectJob = scope.launch {
            delay(delayMs)
            openSocket()
        }
    }

    @Synchronized
    private fun cancelReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
    }

    private fun updateState(next: ConnectionState) {
        _state.value = next
    }

    /**
     * 소켓 콜백 스레드에서 부르므로 절대 막히면 안 된다.
     * 버퍼가 넘치면 가장 오래된 사건을 버린다(재접속 갭 복구가 메꾼다).
     */
    private fun emit(event: SocketEvent) {
        if (!_events.tryEmit(event)) {
            // DROP_OLDEST 라 사실상 실패하지 않지만, 구독자가 전혀 없는 순간을 대비한 폴백.
            scope.launch(start = CoroutineStart.UNDISPATCHED) { _events.emit(event) }
        }
    }

    private inner class SocketListener(private val listenerGeneration: Int) : WebSocketListener() {

        private fun isStale(): Boolean = listenerGeneration != generation

        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (isStale()) return

            // 옛 콜백이 예약해 둔 재연결이 남아 있을 수 있다. 붙었으니 취소한다.
            cancelReconnect()

            val isReconnect = everConnected
            everConnected = true
            policy.reset()
            updateState(ConnectionState.CONNECTED)

            if (isReconnect) emit(SocketEvent.Reconnected)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (isStale()) return

            try {
                val json = JsonParser.parseString(text).asJsonObject
                val type = json.get(FIELD_TYPE)?.takeUnless { it.isJsonNull }?.asString

                when (type) {
                    TYPE_READ -> emit(
                        SocketEvent.Read(
                            roomId = json.get(FIELD_ROOM_ID).asString,
                            userId = json.get(FIELD_USER_ID).asString,
                            lastReadChatId = parseCursor(json.get(FIELD_LAST_READ_CHAT_ID)),
                        ),
                    )

                    TYPE_ROOM_CREATED -> emit(SocketEvent.RoomCreated(json.get(FIELD_ROOM_ID).asString))

                    else -> {
                        val dto = gson.fromJson(text, ChatDto::class.java)
                        if (dto != null) emit(SocketEvent.Chat(dto))
                    }
                }
            } catch (e: RuntimeException) {
                // JsonSyntaxException / IllegalStateException / NullPointerException /
                // NumberFormatException 을 모두 삼킨다. 프레임 하나 때문에 리더가 죽으면
                // 소켓이 통째로 멎는다.
                Log.e(TAG, "malformed socket payload: $text", e)
            }
        }

        /**
         * 읽음 커서. 서버가 String 으로 내려주고, 메시지가 없는 방은 빈 문자열이 온다.
         * 숫자로 못 읽으면 0 으로 본다 — 프레임을 통째로 버리면 그 방의 커서 사건을 놓친다.
         */
        private fun parseCursor(cursor: JsonElement?): Long {
            if (cursor == null || cursor.isJsonNull) return 0L
            return runCatching { cursor.asString.trim().toLong() }.getOrDefault(0L)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            if (isStale()) return

            // OkHttp 계약: 상대 close 프레임에 응답해야 핸드셰이크가 끝나고 onClosed 가 온다.
            // 응답하지 않으면 서버가 먼저 끊었을 때 ping 타임아웃까지 방치된다.
            webSocket.close(NORMAL_CLOSURE, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (isStale()) return

            if (intentionallyClosed) {
                updateState(ConnectionState.DISCONNECTED)
            } else {
                scheduleReconnect()
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (isStale()) return

            Log.w(TAG, "socket failure", t)
            if (response?.code == UNAUTHORIZED) emit(SocketEvent.AuthFailure)
            scheduleReconnect()
        }
    }

    companion object {
        private const val TAG = "ChatSocket"

        const val PING_INTERVAL_SECONDS = 20L
        const val NORMAL_CLOSURE = 1000
        const val UNAUTHORIZED = 401

        const val HEADER_USER_ID = "userId"
        const val HEADER_AUTHORIZATION = "Authorization"
        const val BEARER_PREFIX = "Bearer "

        private const val EVENT_BUFFER = 64

        private const val FIELD_TYPE = "type"
        private const val FIELD_ROOM_ID = "roomId"
        private const val FIELD_USER_ID = "userId"
        private const val FIELD_LAST_READ_CHAT_ID = "lastReadChatId"
        private const val TYPE_READ = "READ"
        private const val TYPE_ROOM_CREATED = "ROOM_CREATED"
    }
}
