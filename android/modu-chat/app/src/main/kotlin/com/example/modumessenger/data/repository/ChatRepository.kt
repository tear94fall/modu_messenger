package com.example.modumessenger.data.repository

import com.example.modumessenger.core.di.ApplicationScope
import com.example.modumessenger.core.di.IoDispatcher
import com.example.modumessenger.core.model.ChatMessage
import com.example.modumessenger.core.model.ChatRoom
import com.example.modumessenger.core.model.ChatType
import com.example.modumessenger.core.model.SendStatus
import com.example.modumessenger.core.network.safeCall
import com.example.modumessenger.core.session.SessionStore
import com.example.modumessenger.core.util.ChatTime
import com.example.modumessenger.data.api.ChatApi
import com.example.modumessenger.data.api.ChatRoomApi
import com.example.modumessenger.data.dto.ChatDto
import com.example.modumessenger.data.dto.ChatRoomDto
import com.example.modumessenger.data.dto.toDto
import com.example.modumessenger.data.dto.toModel
import com.example.modumessenger.data.socket.ChatSocket
import com.example.modumessenger.data.socket.ConnectionState
import com.example.modumessenger.data.socket.SocketEvent
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** 다른 방에 메시지가 왔을 때 띄우는 인앱 배너 한 건. */
data class BannerEvent(
    val roomId: String,
    val senderUserId: String,
    val message: String,
    val chatType: Int,
)

/**
 * 소켓과 REST 를 하나의 Flow 로 합치는 단일 진실 원천. 자바 `Repository/ChatRepository` 의 포팅이다.
 * 메시지가 소켓에서 왔는지 REST 에서 왔는지 UI 가 알 필요 없게 만드는 것이 목적이다.
 *
 * 자바는 `activeRoomIndex` / `roomCache` 두 모니터를 락 순서까지 신경 쓰며 썼다. 여기서는 [mutex]
 * 하나로 모든 상태를 감싼다 — kotlinx [Mutex] 는 재진입이 되지 않으므로, 락을 잡은 채로 다시 락을
 * 잡는 함수를 부르지 않는다는 규칙을 지킨다(`…Locked` 로 끝나는 private 함수는 락 안에서만 부른다).
 */
@Singleton
class ChatRepository @Inject constructor(
    private val socket: ChatSocket,
    private val chatApi: ChatApi,
    private val chatRoomApi: ChatRoomApi,
    private val sessionStore: SessionStore,
    private val gson: Gson,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @ApplicationScope private val scope: CoroutineScope,
) {

    private val mutex = Mutex()

    /** chatId -> 메시지. 활성 방만. 삽입 순서를 유지하면서 chatId 로 중복을 제거한다. */
    private val activeRoomIndex = LinkedHashMap<Long, ChatMessage>()

    /** 방 목록 미러. StateFlow 만으로는 "읽고-고쳐-쓰기" 를 원자적으로 못 한다. */
    private val roomCache = mutableListOf<ChatRoom>()

    /** 활성 방의 멤버별 읽음 커서. 키 집합이 곧 방 인원이자 안 읽음의 분모다. */
    private val readCursors = mutableMapOf<String, Long?>()

    /** 낙관적 에코의 임시 id FIFO. 내 브로드캐스트가 전송 순서대로 돌아온다는 전제. */
    private val pendingEchoes = ArrayDeque<Long>()

    /** 소켓이 끊겨 있어 못 보낸 READ 의 방 목록. 재연결 때 다시 보낸다. */
    private val pendingReadRooms = LinkedHashSet<String>()

    private var nextTempId = -1L

    @Volatile
    private var activeRoomId: String? = null

    @Volatile
    private var myUserId: String = ""

    @Volatile
    private var myMemberId: String = ""

    private val _rooms = MutableStateFlow<List<ChatRoom>>(emptyList())
    val rooms: StateFlow<List<ChatRoom>> = _rooms.asStateFlow()

    private val _totalUnread = MutableStateFlow(0)
    val totalUnread: StateFlow<Int> = _totalUnread.asStateFlow()

    private val _activeMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val activeMessages: StateFlow<List<ChatMessage>> = _activeMessages.asStateFlow()

    val connectionState: StateFlow<ConnectionState> get() = socket.state

    private val _banner = MutableSharedFlow<BannerEvent>(
        replay = 0,
        extraBufferCapacity = BANNER_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val banner: SharedFlow<BannerEvent> = _banner.asSharedFlow()

    init {
        scope.launch { socket.events.collect { handleEvent(it) } }
    }

    // ---------- 신원 ----------

    /** 로그인 직후/부팅 시. 신원이 없으면 수신 메시지를 전부 버린다(로그아웃 후 잔여 프레임 차단). */
    fun setIdentity(userId: String, memberId: String) {
        myUserId = userId
        myMemberId = memberId
    }

    /** 로그아웃. 신원을 **먼저** 지워 in-flight 프레임이 정리를 되돌리지 못하게 한다. */
    fun onLoggedOut() {
        myUserId = ""
        myMemberId = ""
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            mutex.withLock {
                activeRoomId = null
                activeRoomIndex.clear()
                readCursors.clear()
                pendingEchoes.clear()
                pendingReadRooms.clear()
                roomCache.clear()
                _activeMessages.value = emptyList()
                publishRoomsLocked()
            }
        }
    }

    /**
     * FCM 탭으로 채팅방에 바로 들어오는 경로가 있어 로그인 화면을 한 번도 안 거칠 수 있다.
     * 그때 신원이 비어 있으면 수신 메시지를 전부 버리므로, 세션에서 한 번 더 채워 준다.
     */
    private suspend fun currentMemberId(): String? {
        if (myMemberId.isNotBlank()) return myMemberId

        val member = sessionStore.memberNow() ?: return null
        setIdentity(member.userId, member.id.toString())
        return myMemberId.takeIf { it.isNotBlank() }
    }

    // ---------- 소켓 사건 ----------

    private suspend fun handleEvent(event: SocketEvent) {
        when (event) {
            is SocketEvent.Chat -> handleChat(event.dto, isGapRecovery = false)
            is SocketEvent.Read -> onReadCursorAdvanced(event.roomId, event.userId, event.lastReadChatId)

            // 방 이름·멤버는 프레임에 없다. 목록을 통째로 다시 받아 채운다.
            is SocketEvent.RoomCreated -> scope.launch { refreshRooms() }

            SocketEvent.Reconnected -> scope.launch { onReconnected() }

            // 기존 재발급 경로(Authenticator)를 태워 새 토큰을 저장시킨다. 자격증명은 매 연결마다
            // 다시 읽히므로 예약된 재연결이 자동으로 새 토큰을 쓴다.
            SocketEvent.AuthFailure -> scope.launch { refreshRooms() }
        }
    }

    /** 끊겨 있던 동안의 공백을 메꾼다. 갭 복구분에는 배너도 READ 도 없다. */
    private suspend fun onReconnected() {
        flushPendingReadReceipts()
        refreshRooms()

        val roomId = activeRoomId ?: return
        safeCall { chatApi.getRecent(roomId, GAP_RECOVERY_SIZE) }
            .onSuccess { dtos -> dtos.forEach { handleChat(it, isGapRecovery = true) } }
    }

    /** 패키지 가시성. 테스트가 소켓 없이 라우팅 규칙을 검증한다. */
    internal suspend fun handleChat(dto: ChatDto, isGapRecovery: Boolean) {
        val roomId = dto.roomId ?: return
        val chatId = dto.id ?: return

        val me = myUserId
        // 로그아웃 이후 도착한 잔여 메시지. disconnect() 는 비동기라 이미 wire 에 실린 프레임까지
        // 막지 못하므로 여기서 버린다. 목록/말풍선/배너 어디에도 반영하지 않는다.
        if (me.isEmpty()) return

        updateRoomOrder(dto, roomId, chatId)

        val mine = me == dto.sender

        // 활성 방 판정과 추가는 같은 락 안에서 원자적으로 해야 한다. 락 밖에서 판정하면
        // 방을 전환하는 순간 이전 방의 메시지가 새 방에 섞인다.
        val isActiveRoom = mutex.withLock {
            val active = roomId == activeRoomId
            if (active) {
                // 내가 보낸 메시지의 브로드캐스트라면 먼저 올려 둔 에코를 걷어낸다. 전송 순서대로
                // 돌아오므로 가장 오래된 에코를 대체한다. 갭 복구분은 실시간 전송이 아니므로
                // 에코를 소비하지 않는다.
                if (mine && !isGapRecovery && pendingEchoes.isNotEmpty()) {
                    activeRoomIndex.remove(pendingEchoes.removeFirst())
                }
                activeRoomIndex[chatId] = dto.toModel()
                publishActiveLocked()
            }
            active
        }

        // 보고 있는 방에 온 메시지는 곧바로 읽은 것이다. 갭 복구분은 제외한다 —
        // 재연결 직후 밀린 메시지마다 READ 를 쏘면 프레임만 늘고 결과는 같다.
        if (isActiveRoom && !isGapRecovery) sendReadReceipt(roomId)

        if (!mine && !isActiveRoom && !isGapRecovery) {
            incrementUnread(roomId)
            _banner.tryEmit(
                BannerEvent(roomId, dto.sender.orEmpty(), dto.message.orEmpty(), dto.chatType),
            )
        }
    }

    /** 한 사람의 커서만 올린다. 소켓 READ 프레임이 도착했을 때 쓴다. */
    internal suspend fun onReadCursorAdvanced(roomId: String, userId: String, lastReadChatId: Long) {
        mutex.withLock {
            if (roomId != activeRoomId) return@withLock

            // 커서 맵에 없는 사용자는 새로 추가하지 않는다. 빈 맵은 "아직 못 받아왔다" 는 뜻이지
            // "멤버가 한 명" 이라는 뜻이 아니다 — 여기서 키를 만들면 분모가 줄어든다.
            if (!readCursors.containsKey(userId)) return@withLock

            // 커서는 단조 증가한다. 늦게 도착한 옛 사건이 되돌리지 못하게 한다.
            val current = readCursors[userId]
            if (current != null && current >= lastReadChatId) return@withLock

            readCursors[userId] = lastReadChatId
            publishActiveLocked()
        }
    }

    // ---------- 방 열기/닫기 ----------

    /**
     * 부록 C §3.5: 로컬 미읽음 0 → READ 전송 → 커서 조회.
     * 서버 반영(`updateLastRead`)은 [closeRoom] 에서 한다.
     */
    suspend fun openRoom(roomId: String) {
        mutex.withLock {
            activeRoomId = roomId
            activeRoomIndex.clear()
            readCursors.clear()
            pendingEchoes.clear()
            _activeMessages.value = emptyList()
        }

        clearUnread(roomId)
        sendReadReceipt(roomId)
        scope.launch { refreshReadCursors(roomId) }
    }

    suspend fun closeRoom(roomId: String) {
        val memberId = myMemberId

        val wasActive = mutex.withLock {
            if (roomId != activeRoomId) return@withLock false

            activeRoomId = null
            activeRoomIndex.clear()
            readCursors.clear()
            pendingEchoes.clear()
            _activeMessages.value = emptyList()
            true
        }
        if (!wasActive) return

        clearUnread(roomId)
        sendReadReceipt(roomId)

        if (memberId.isBlank()) return
        // 화면이 사라진 뒤에도 끝나야 하므로 앱 스코프에서 돌린다. 실패해도 앱 동작을 막지 않는다 —
        // 다음 목록 갱신에서 서버의 옛 값이 돌아와 배지가 되살아날 뿐이다.
        scope.launch { safeCall { chatRoomApi.updateLastRead(roomId, memberId) } }
    }

    // ---------- 읽음 ----------

    /**
     * "여기까지 읽었다" 를 소켓으로 알린다. 서버가 커서를 lastChatId 로 점프시키고 방 인원에게
     * 브로드캐스트한다. 못 보냈으면 방을 적어 두고 재연결 때 다시 보낸다.
     */
    private suspend fun sendReadReceipt(roomId: String) {
        val me = myUserId
        if (me.isEmpty()) return

        val frame = mapOf("type" to "READ", "roomId" to roomId, "sender" to me)
        val delivered = socket.send(gson.toJson(frame))

        mutex.withLock {
            if (delivered) pendingReadRooms.remove(roomId) else pendingReadRooms.add(roomId)
        }
    }

    /**
     * 끊긴 동안 못 보낸 READ 를 다시 보낸다. 서버는 커서를 그 시점의 lastChatId 로 점프시키므로
     * 끊긴 동안 쌓인 메시지까지 읽은 것이 된다.
     */
    private suspend fun flushPendingReadReceipts() {
        val rooms = mutex.withLock { pendingReadRooms.toList() }
        rooms.forEach { sendReadReceipt(it) }
    }

    /** 방에 들어갈 때 서버에서 커서를 받아온다. 실패하면 숫자가 안 뜰 뿐 대화는 정상이다. */
    private suspend fun refreshReadCursors(roomId: String) {
        safeCall { chatRoomApi.getReadCursors(roomId) }.onSuccess { dtos ->
            val cursors = dtos.mapNotNull { dto -> dto.userId?.let { it to dto.lastReadChatId } }.toMap()
            applyReadCursors(roomId, cursors)
        }
    }

    /** 커서를 병합하고 활성 방 말풍선을 다시 계산한다. */
    internal suspend fun applyReadCursors(roomId: String, cursors: Map<String, Long>) {
        mutex.withLock {
            if (roomId != activeRoomId) return@withLock

            // openRoom 이 이 GET 과 READ 프레임을 연달아 보낸다. 소켓 브로드캐스트(최신)가 GET
            // 응답(스냅샷)보다 먼저 도착하는 경우가 흔해서, 통째로 갈아끼우면 방금 반영된 최신
            // 커서가 옛 값으로 되돌아간다. 키별로 큰 쪽을 쓰되, 응답에 없는 멤버(나간 사람)는
            // 제거한다 — 멤버 목록은 응답이 진실이다.
            val merged = cursors.mapValues { (userId, incoming) ->
                val existing = readCursors[userId]
                if (existing == null) incoming else maxOf(existing, incoming)
            }

            readCursors.clear()
            readCursors.putAll(merged)
            publishActiveLocked()
        }
    }

    // ---------- 방 목록 ----------

    suspend fun refreshRooms(): Result<Unit> = withContext(ioDispatcher) {
        val memberId = currentMemberId()
            ?: return@withContext Result.failure(IllegalStateException("신원이 없어 방 목록을 갱신할 수 없다"))

        safeCall { chatRoomApi.getRooms(memberId) }
            .onSuccess { dtos ->
                applyRooms(dtos.map { it.toModel() })
                // 방 목록이 채워진 뒤에 병합해야 캐시에 반영된다.
                refreshUnreadCounts(memberId)
            }
            .map { }
    }

    /** 안 읽은 개수는 방 목록과 별개의 호출이라, 실패해도 방 목록 자체는 그대로 남는다. */
    private suspend fun refreshUnreadCounts(memberId: String) {
        safeCall { chatRoomApi.getUnreadCounts(memberId) }.onSuccess { dtos ->
            val byRoom = dtos.mapNotNull { dto -> dto.roomId?.let { it to dto.unreadChatCount.toInt() } }.toMap()
            mutex.withLock {
                // 응답에 없는 방은 0 으로 둔다. 서버가 진실이다.
                roomCache.indices.forEach { i ->
                    roomCache[i] = roomCache[i].copy(unreadCount = byRoom[roomCache[i].roomId] ?: 0)
                }
                publishRoomsLocked()
            }
        }
    }

    private suspend fun applyRooms(rooms: List<ChatRoom>) {
        mutex.withLock {
            // 안 읽음은 방 응답에 없다. 바로 뒤따르는 unread 호출이 서버 값으로 덮어쓰지만,
            // 그 호출이 실패하는 구간에도 배지가 깜빡이지 않게 기존 값을 들고 간다.
            val previous = roomCache.associate { it.roomId to it.unreadCount }
            roomCache.clear()
            roomCache.addAll(rooms.map { it.copy(unreadCount = previous[it.roomId] ?: it.unreadCount) })
            publishRoomsLocked()
        }
    }

    /** 로컬에서만 +1 한다. 서버 재조회 없이 배지가 즉시 반응하게 하기 위해서다. */
    private suspend fun incrementUnread(roomId: String) {
        mutex.withLock {
            val index = roomCache.indexOfFirst { it.roomId == roomId }
            if (index < 0) return@withLock

            roomCache[index] = roomCache[index].copy(unreadCount = roomCache[index].unreadCount + 1)
            publishRoomsLocked()
        }
    }

    /** 로컬에서만 0 으로 만든다. 서버 반영은 [closeRoom] 에서 한다. */
    private suspend fun clearUnread(roomId: String) {
        mutex.withLock {
            val index = roomCache.indexOfFirst { it.roomId == roomId }
            if (index < 0 || roomCache[index].unreadCount == 0) return@withLock

            roomCache[index] = roomCache[index].copy(unreadCount = 0)
            publishRoomsLocked()
        }
    }

    private suspend fun updateRoomOrder(dto: ChatDto, roomId: String, chatId: Long) {
        mutex.withLock {
            val index = roomCache.indexOfFirst { it.roomId == roomId }
            if (index < 0) return@withLock

            roomCache[index] = roomCache[index].copy(
                lastChatMsg = dto.message.orEmpty(),
                lastChatId = chatId.toString(),
                lastChatTime = dto.chatTime.orEmpty(),
            )
            publishRoomsLocked()
        }
    }

    /** 방 목록은 항상 마지막 대화 시각 내림차순. `yyyy-MM-dd HH:mm:ss` 는 문자열 정렬 = 시간 정렬. */
    private fun publishRoomsLocked() {
        val sorted = roomCache.sortedByDescending { it.lastChatTime }
        _rooms.value = sorted
        _totalUnread.value = sorted.sumOf { maxOf(0, it.unreadCount) }
    }

    // ---------- 메시지 읽기 ----------

    suspend fun loadInitial(roomId: String, size: Int = PAGE_SIZE): Result<Unit> =
        safeCall { chatApi.getRecent(roomId, size) }
            .onSuccess { mergeChats(roomId, it, prepend = false) }
            .map { }

    /** 위로 더 불러온다. 실제로 **앞에 붙은 개수**를 돌려준다(스크롤 위치 보정용). */
    suspend fun loadPrev(roomId: String, oldestChatId: Long, size: Int = PAGE_SIZE): Int {
        val chats = safeCall { chatApi.getBefore(roomId, oldestChatId, size) }.getOrNull() ?: return 0
        return mergeChats(roomId, chats, prepend = true)
    }

    private suspend fun mergeChats(roomId: String, chats: List<ChatDto>, prepend: Boolean): Int =
        mutex.withLock {
            if (roomId != activeRoomId) return@withLock 0

            var added = 0
            if (prepend) {
                val merged = LinkedHashMap<Long, ChatMessage>()
                chats.forEach { dto ->
                    val id = dto.id ?: return@forEach
                    if (!activeRoomIndex.containsKey(id)) added++
                    merged[id] = dto.toModel()
                }
                // 기존 항목을 나중에 넣어 중복 시 기존 값(안 읽음 수 포함)이 살아남게 한다.
                merged.putAll(activeRoomIndex)
                activeRoomIndex.clear()
                activeRoomIndex.putAll(merged)
            } else {
                chats.forEach { dto ->
                    val id = dto.id ?: return@forEach
                    if (!activeRoomIndex.containsKey(id)) added++
                    activeRoomIndex[id] = dto.toModel()
                }
            }

            publishActiveLocked()
            added
        }

    /**
     * 활성 방 말풍선 전체의 숫자를 다시 계산하고 내보낸다.
     * 커서 하나만 바뀌어도 화면의 모든 말풍선이 영향을 받으므로 전체를 훑는다.
     */
    private fun publishActiveLocked() {
        recomputeUnreadLocked()
        _activeMessages.value = sortedActiveLocked()
    }

    private fun recomputeUnreadLocked() {
        if (readCursors.isEmpty()) return

        val effective = ChatUnread.withImpliedCursors(readCursors, activeRoomIndex, myUserId)
        activeRoomIndex.entries.forEach { entry ->
            val message = entry.value
            val count = ChatUnread.unreadCountFor(entry.key, message.sender, effective)
            if (message.unreadCount != count) entry.setValue(message.copy(unreadCount = count))
        }
    }

    /**
     * 서버 메시지는 chatId 오름차순, 아직 에코가 돌아오지 않은 임시 메시지(음수 id)는 맨 뒤에
     * 보낸 순서대로. 임시 id 는 -1 부터 줄어들므로 내림차순이 곧 보낸 순서다.
     */
    private fun sortedActiveLocked(): List<ChatMessage> {
        val (temp, real) = activeRoomIndex.values.partition { it.id < 0L }
        return real.sortedBy { it.id } + temp.sortedByDescending { it.id }
    }

    // ---------- 보내기 ----------

    fun sendText(roomId: String, text: String): Boolean = send(roomId, text, ChatType.TEXT)

    /** 이미지·파일·음성은 본문에 저장소 파일명을 싣는다(서버 계약 그대로). */
    fun sendImage(roomId: String, fileName: String): Boolean = send(roomId, fileName, ChatType.IMAGE)

    fun sendFile(roomId: String, fileName: String): Boolean = send(roomId, fileName, ChatType.FILE)

    fun sendAudio(roomId: String, fileName: String): Boolean = send(roomId, fileName, ChatType.AUDIO)

    /**
     * 보낸 메시지는 서버 왕복(socket → chat-service → broadcast → socket)을 기다리지 않고
     * 즉시 화면에 올린다. 브로드캐스트가 실제 id 를 달고 돌아오면 그때 에코를 대체한다.
     * 소켓이 [ChatSocket.send] 에서 false 를 돌려주면 그 말풍선은 FAILED 로 남는다.
     */
    fun send(roomId: String, message: String, chatType: Int): Boolean {
        val sender = myUserId
        if (sender.isEmpty()) return false

        val dto = ChatDto(
            id = null,
            chatType = chatType,
            roomId = roomId,
            sender = sender,
            message = message,
            chatTime = ChatTime.now(),
        )

        val sent = socket.send(gson.toJson(dto))
        // UNDISPATCHED: 락이 비어 있으면 곧바로 돌아 에코가 이 호출 안에서 화면에 올라간다.
        // 서버 에코가 로컬 에코보다 먼저 등록되는 역전을 막는다.
        scope.launch(start = CoroutineStart.UNDISPATCHED) { addOptimisticEcho(dto, roomId, sent) }
        return sent
    }

    private suspend fun addOptimisticEcho(dto: ChatDto, roomId: String, sent: Boolean) {
        mutex.withLock {
            if (roomId != activeRoomId) return@withLock

            val tempId = nextTempId--
            activeRoomIndex[tempId] = ChatMessage(
                id = tempId,
                chatType = dto.chatType,
                roomId = roomId,
                sender = dto.sender.orEmpty(),
                message = dto.message.orEmpty(),
                chatTime = dto.chatTime.orEmpty(),
                status = if (sent) SendStatus.SENDING else SendStatus.FAILED,
            )
            // 실패한 에코는 돌아올 브로드캐스트가 없으므로 큐에 넣지 않는다(엉뚱한 대체 방지).
            if (sent) pendingEchoes.addLast(tempId)

            publishActiveLocked()
        }
    }

    /**
     * 실패한 말풍선을 다시 보낸다. 성공하면 실패 표시를 지우고 큐에 넣어 브로드캐스트가 돌아올 때
     * 실제 메시지로 대체되게 한다. 또 실패하면 그대로 둔다.
     */
    fun resendFailed(tempId: Long): Boolean {
        val message = _activeMessages.value
            .firstOrNull { it.id == tempId && it.status == SendStatus.FAILED } ?: return false

        val sent = socket.send(gson.toJson(message.toDto()))
        if (!sent) return false

        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            mutex.withLock {
                val current = activeRoomIndex[tempId] ?: return@withLock // 그새 삭제됨
                activeRoomIndex[tempId] = current.copy(status = SendStatus.SENDING)
                pendingEchoes.addLast(tempId)
                publishActiveLocked()
            }
        }
        return true
    }

    /** 실패한 말풍선을 목록에서 지운다. */
    fun deleteFailed(tempId: Long) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            mutex.withLock {
                if (activeRoomIndex.remove(tempId) == null) return@withLock
                pendingEchoes.remove(tempId)
                publishActiveLocked()
            }
        }
    }

    // ---------- 방 관리 REST ----------

    /** 같은 멤버 구성의 방이 이미 있으면 서버가 그 방을 돌려준다. */
    suspend fun createRoom(memberIds: List<Long>): Result<ChatRoom> = withContext(ioDispatcher) {
        safeCall { chatRoomApi.createRoom(memberIds).toModel() }
            .onSuccess { scope.launch { refreshRooms() } }
    }

    suspend fun leaveRoom(roomId: String): Result<Unit> = withContext(ioDispatcher) {
        val userId = myUserId
        if (userId.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("신원이 없어 방을 나갈 수 없다"))
        }

        safeCall { chatRoomApi.leaveRoom(roomId, userId) }
            .onSuccess { scope.launch { refreshRooms() } }
            .map { }
    }

    suspend fun invite(roomId: String, userIds: List<String>): Result<ChatRoom> = withContext(ioDispatcher) {
        safeCall { chatRoomApi.inviteMembers(roomId, userIds).toModel() }
            .onSuccess { scope.launch { refreshRooms() } }
    }

    suspend fun updateRoom(roomId: String, dto: ChatRoomDto): Result<ChatRoom> = withContext(ioDispatcher) {
        safeCall { chatRoomApi.updateRoom(roomId, dto).toModel() }
            .onSuccess { scope.launch { refreshRooms() } }
    }

    suspend fun getRoom(roomId: String): Result<ChatRoom> = withContext(ioDispatcher) {
        safeCall { chatRoomApi.getRoom(roomId).toModel() }
    }

    suspend fun getRoomImages(roomId: String, size: Int = PAGE_SIZE): Result<List<ChatMessage>> =
        withContext(ioDispatcher) {
            safeCall { chatApi.getImages(roomId, size).map { it.toModel() } }
        }

    suspend fun getChats(ids: List<String>): Result<List<ChatMessage>> = withContext(ioDispatcher) {
        if (ids.isEmpty()) return@withContext Result.success(emptyList())
        safeCall { chatApi.getChats(ids).map { it.toModel() } }
    }

    companion object {
        /** 재연결 후 갭 복구로 다시 받아올 최신 채팅 개수. */
        const val GAP_RECOVERY_SIZE = 30

        const val PAGE_SIZE = 20

        private const val BANNER_BUFFER = 16
    }
}
