package com.example.modumessenger.data.repository

import app.cash.turbine.test
import com.example.modumessenger.core.model.SendStatus
import com.example.modumessenger.core.session.SessionStore
import com.example.modumessenger.data.api.ChatApi
import com.example.modumessenger.data.api.ChatRoomApi
import com.example.modumessenger.data.dto.ChatDto
import com.example.modumessenger.data.dto.ChatReadCursorDto
import com.example.modumessenger.data.dto.ChatRoomDto
import com.example.modumessenger.data.dto.ChatRoomUnreadDto
import com.example.modumessenger.data.socket.ChatSocket
import com.example.modumessenger.data.socket.ConnectionState
import com.example.modumessenger.data.socket.SocketEvent
import com.google.gson.Gson
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 소켓과 REST 를 전부 가짜로 바꾸고 라우팅 규칙만 본다. */
class ChatRepositoryTest {

    // ---------- 가짜들 ----------

    private class FakeChatSocket : ChatSocket {
        val incoming = MutableSharedFlow<SocketEvent>(extraBufferCapacity = 64)
        val sent = mutableListOf<String>()
        var sendResult = true

        override val state = MutableStateFlow(ConnectionState.CONNECTED)
        override val events: SharedFlow<SocketEvent> = incoming

        override fun connect() = Unit
        override fun disconnect() = Unit

        override fun send(text: String): Boolean {
            if (sendResult) sent += text
            return sendResult
        }

        fun readFrames(roomId: String): Int =
            sent.count { it.contains("\"type\":\"READ\"") && it.contains("\"roomId\":\"$roomId\"") }
    }

    private class FakeChatApi : ChatApi {
        var recent: List<ChatDto> = emptyList()
        var before: List<ChatDto> = emptyList()
        var images: List<ChatDto> = emptyList()
        var byIds: List<ChatDto> = emptyList()
        var recentCalls = 0

        override suspend fun getChats(ids: List<String>): List<ChatDto> = byIds
        override suspend fun getRecent(roomId: String, size: Int): List<ChatDto> {
            recentCalls++
            return recent
        }

        override suspend fun getBefore(roomId: String, chatId: Long, size: Int): List<ChatDto> = before
        override suspend fun getImages(roomId: String, size: Int): List<ChatDto> = images
    }

    private class FakeChatRoomApi : ChatRoomApi {
        var rooms: List<ChatRoomDto> = emptyList()
        var unread: List<ChatRoomUnreadDto> = emptyList()
        var cursors: List<ChatReadCursorDto> = emptyList()
        var roomsCalls = 0
        val lastReadCalls = mutableListOf<Pair<String, String>>()

        override suspend fun getRooms(memberId: String): List<ChatRoomDto> {
            roomsCalls++
            return rooms
        }

        override suspend fun getRoom(roomId: String): ChatRoomDto =
            rooms.first { it.roomId == roomId }

        override suspend fun createRoom(ids: List<Long>): ChatRoomDto = rooms.first()
        override suspend fun leaveRoom(roomId: String, userId: String): ChatRoomDto =
            rooms.first { it.roomId == roomId }

        override suspend fun updateRoom(roomId: String, room: ChatRoomDto): ChatRoomDto = room
        override suspend fun inviteMembers(roomId: String, userIds: List<String>): ChatRoomDto =
            rooms.first { it.roomId == roomId }

        override suspend fun getUnreadCounts(memberId: String): List<ChatRoomUnreadDto> = unread
        override suspend fun updateLastRead(roomId: String, memberId: String) {
            lastReadCalls += roomId to memberId
        }

        override suspend fun getReadCursors(roomId: String): List<ChatReadCursorDto> = cursors
    }

    // ---------- 고정물 ----------

    private val socket = FakeChatSocket()
    private val chatApi = FakeChatApi()
    private val chatRoomApi = FakeChatRoomApi()

    private val sessionStore: SessionStore = mockk(relaxed = true)

    init {
        // 신원은 setIdentity 로 넣는다. 세션에서 다시 읽을 일이 없어야 한다.
        coEvery { sessionStore.memberNow() } returns null
    }

    private fun roomDto(roomId: String, lastChatTime: String) = ChatRoomDto(
        roomId = roomId,
        roomName = roomId,
        lastChatMsg = "",
        lastChatId = "0",
        lastChatTime = lastChatTime,
        members = emptyList(),
    )

    private fun chat(
        id: Long,
        roomId: String,
        sender: String,
        message: String = "메시지",
        chatTime: String = "2026-08-26 10:00:00",
    ) = ChatDto(
        id = id,
        chatType = 1,
        roomId = roomId,
        sender = sender,
        message = message,
        chatTime = chatTime,
    )

    /** 방 두 개를 받아 둔 뒤 [ACTIVE_ROOM] 을 연 상태에서 시작한다. */
    private suspend fun TestScope.openedRepository(): ChatRepository {
        chatRoomApi.rooms = listOf(
            roomDto(OTHER_ROOM, "2026-08-26 09:00:00"),
            roomDto(ACTIVE_ROOM, "2026-08-26 10:00:00"),
        )

        val repository = ChatRepository(
            socket = socket,
            chatApi = chatApi,
            chatRoomApi = chatRoomApi,
            sessionStore = sessionStore,
            gson = Gson(),
            // Unconfined: 리포지토리가 launch 한 일이 곧바로 돌아 테스트가 결정적이 된다.
            // (StandardTestDispatcher 면 소켓 구독이 붙기 전에 emit 한 사건이 버려진다.)
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
            scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
        )

        repository.setIdentity(ME, MEMBER_ID)
        repository.refreshRooms()
        repository.openRoom(ACTIVE_ROOM)
        advanceUntilIdle()

        socket.sent.clear()
        return repository
    }

    // ---------- 수신 라우팅 ----------

    @Test
    fun `활성 방 메시지는 목록에 붙고 READ 를 보낸다`() = runTest {
        val repository = openedRepository()

        socket.incoming.emit(SocketEvent.Chat(chat(5L, ACTIVE_ROOM, OTHER, "안녕")))
        advanceUntilIdle()

        assertEquals(listOf(5L), repository.activeMessages.value.map { it.id })
        assertEquals("안녕", repository.activeMessages.value.single().message)
        assertEquals(1, socket.readFrames(ACTIVE_ROOM))
        assertEquals(0, repository.totalUnread.value)
    }

    @Test
    fun `다른 방 메시지는 미읽음을 올리고 배너를 띄운다`() = runTest {
        val repository = openedRepository()

        repository.banner.test {
            socket.incoming.emit(SocketEvent.Chat(chat(7L, OTHER_ROOM, OTHER, "다른 방")))
            advanceUntilIdle()

            val banner = awaitItem()
            assertEquals(OTHER_ROOM, banner.roomId)
            assertEquals(OTHER, banner.senderUserId)
            assertEquals("다른 방", banner.message)
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(repository.activeMessages.value.isEmpty())
        assertEquals(1, repository.rooms.value.first { it.roomId == OTHER_ROOM }.unreadCount)
        assertEquals(1, repository.totalUnread.value)
        // 다른 방 메시지에는 READ 를 보내지 않는다.
        assertEquals(0, socket.readFrames(OTHER_ROOM))
    }

    @Test
    fun `내 메시지는 배너도 미읽음도 없다`() = runTest {
        val repository = openedRepository()

        repository.banner.test {
            socket.incoming.emit(SocketEvent.Chat(chat(7L, OTHER_ROOM, ME, "내가 다른 기기에서")))
            advanceUntilIdle()

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(0, repository.totalUnread.value)
    }

    @Test
    fun `로그아웃 후 도착한 메시지는 통째로 버린다`() = runTest {
        val repository = openedRepository()

        repository.onLoggedOut()
        socket.incoming.emit(SocketEvent.Chat(chat(5L, ACTIVE_ROOM, OTHER, "잔여")))
        advanceUntilIdle()

        assertTrue(repository.activeMessages.value.isEmpty())
        assertTrue(repository.rooms.value.isEmpty())
        assertEquals(0, repository.totalUnread.value)
    }

    @Test
    fun `방 목록은 마지막 대화 시각 내림차순이다`() = runTest {
        val repository = openedRepository()

        assertEquals(listOf(ACTIVE_ROOM, OTHER_ROOM), repository.rooms.value.map { it.roomId })

        socket.incoming.emit(
            SocketEvent.Chat(chat(9L, OTHER_ROOM, OTHER, "최신", chatTime = "2026-08-26 11:00:00")),
        )
        advanceUntilIdle()

        assertEquals(listOf(OTHER_ROOM, ACTIVE_ROOM), repository.rooms.value.map { it.roomId })
    }

    @Test
    fun `미읽음은 서버 응답으로 덮어쓴다`() = runTest {
        val repository = openedRepository()
        chatRoomApi.unread = listOf(
            ChatRoomUnreadDto(roomId = OTHER_ROOM, unreadChatCount = 4L),
        )

        repository.refreshRooms()
        advanceUntilIdle()

        assertEquals(4, repository.rooms.value.first { it.roomId == OTHER_ROOM }.unreadCount)
        assertEquals(0, repository.rooms.value.first { it.roomId == ACTIVE_ROOM }.unreadCount)
        assertEquals(4, repository.totalUnread.value)
    }

    // ---------- 낙관적 에코 ----------

    @Test
    fun `내 에코는 서버 브로드캐스트가 오면 실제 메시지로 바뀐다`() = runTest {
        val repository = openedRepository()

        assertTrue(repository.sendText(ACTIVE_ROOM, "안녕"))
        advanceUntilIdle()

        val echo = repository.activeMessages.value.single()
        assertTrue("임시 id 는 음수", echo.id < 0L)
        assertEquals(SendStatus.SENDING, echo.status)
        assertEquals("안녕", echo.message)

        socket.incoming.emit(SocketEvent.Chat(chat(11L, ACTIVE_ROOM, ME, "안녕")))
        advanceUntilIdle()

        val settled = repository.activeMessages.value.single()
        assertEquals(11L, settled.id)
        assertEquals(SendStatus.SENT, settled.status)
    }

    @Test
    fun `전송 실패는 FAILED 로 남고 재전송이 되살린다`() = runTest {
        val repository = openedRepository()
        socket.sendResult = false

        assertFalse(repository.sendText(ACTIVE_ROOM, "안녕"))
        advanceUntilIdle()

        val failed = repository.activeMessages.value.single()
        assertEquals(SendStatus.FAILED, failed.status)

        socket.sendResult = true
        assertTrue(repository.resendFailed(failed.id))
        advanceUntilIdle()

        assertEquals(SendStatus.SENDING, repository.activeMessages.value.single().status)

        // 실패했던 에코는 큐에 없었으므로 재전송분 하나만 대체된다.
        socket.incoming.emit(SocketEvent.Chat(chat(11L, ACTIVE_ROOM, ME, "안녕")))
        advanceUntilIdle()

        assertEquals(listOf(11L), repository.activeMessages.value.map { it.id })
    }

    @Test
    fun `실패한 말풍선을 지운다`() = runTest {
        val repository = openedRepository()
        socket.sendResult = false

        repository.sendText(ACTIVE_ROOM, "안녕")
        advanceUntilIdle()

        repository.deleteFailed(repository.activeMessages.value.single().id)
        advanceUntilIdle()

        assertTrue(repository.activeMessages.value.isEmpty())
    }

    // ---------- 읽음 ----------

    @Test
    fun `READ 프레임이 오면 안 읽음 숫자가 줄어든다`() = runTest {
        chatRoomApi.cursors = listOf(
            ChatReadCursorDto(userId = ME, lastReadChatId = 0L),
            ChatReadCursorDto(userId = OTHER, lastReadChatId = 0L),
        )
        val repository = openedRepository()

        socket.incoming.emit(SocketEvent.Chat(chat(5L, ACTIVE_ROOM, ME, "내 메시지")))
        advanceUntilIdle()
        assertEquals(1, repository.activeMessages.value.single().unreadCount)

        socket.incoming.emit(SocketEvent.Read(ACTIVE_ROOM, OTHER, 5L))
        advanceUntilIdle()

        assertEquals(0, repository.activeMessages.value.single().unreadCount)
    }

    @Test
    fun `커서 맵에 없는 사람의 READ 는 분모를 늘리지 않는다`() = runTest {
        val repository = openedRepository() // 커서 응답이 비어 있다

        socket.incoming.emit(SocketEvent.Chat(chat(5L, ACTIVE_ROOM, ME, "내 메시지")))
        socket.incoming.emit(SocketEvent.Read(ACTIVE_ROOM, "stranger", 5L))
        advanceUntilIdle()

        assertEquals(0, repository.activeMessages.value.single().unreadCount)
    }

    @Test
    fun `방을 닫으면 서버에 마지막 읽음을 알린다`() = runTest {
        val repository = openedRepository()

        repository.closeRoom(ACTIVE_ROOM)
        advanceUntilIdle()

        assertTrue(repository.activeMessages.value.isEmpty())
        assertEquals(1, socket.readFrames(ACTIVE_ROOM))
        assertEquals(listOf(ACTIVE_ROOM to MEMBER_ID), chatRoomApi.lastReadCalls)
    }

    // ---------- 페이지 ----------

    @Test
    fun `loadPrev 는 앞에 붙인 개수를 돌려준다`() = runTest {
        val repository = openedRepository()
        chatApi.recent = listOf(chat(5L, ACTIVE_ROOM, OTHER), chat(6L, ACTIVE_ROOM, OTHER))
        repository.loadInitial(ACTIVE_ROOM)
        advanceUntilIdle()

        // 5 는 이미 들고 있으므로 새로 붙는 건 3, 4 두 개다.
        chatApi.before = listOf(chat(3L, ACTIVE_ROOM, OTHER), chat(4L, ACTIVE_ROOM, OTHER), chat(5L, ACTIVE_ROOM, OTHER))
        val added = repository.loadPrev(ACTIVE_ROOM, oldestChatId = 5L)
        advanceUntilIdle()

        assertEquals(2, added)
        assertEquals(listOf(3L, 4L, 5L, 6L), repository.activeMessages.value.map { it.id })
    }

    // ---------- 재연결 ----------

    @Test
    fun `재연결하면 보류 READ 를 다시 보내고 목록과 갭을 복구한다`() = runTest {
        socket.sendResult = false
        val repository = openedRepository() // openRoom 의 READ 가 전달되지 못한다

        socket.sendResult = true
        chatApi.recent = listOf(chat(5L, ACTIVE_ROOM, OTHER), chat(6L, ACTIVE_ROOM, OTHER))
        repository.loadInitial(ACTIVE_ROOM)
        advanceUntilIdle()

        val roomsCallsBefore = chatRoomApi.roomsCalls
        val recentCallsBefore = chatApi.recentCalls
        socket.sent.clear()

        repository.banner.test {
            socket.incoming.emit(SocketEvent.Reconnected)
            advanceUntilIdle()
            expectNoEvents() // 갭 복구분에는 배너가 없다
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals("끊긴 동안 못 보낸 READ 하나만 다시 나간다", 1, socket.readFrames(ACTIVE_ROOM))
        assertEquals(roomsCallsBefore + 1, chatRoomApi.roomsCalls)
        assertEquals(recentCallsBefore + 1, chatApi.recentCalls)
        assertEquals("갭 복구가 메시지를 복제하면 안 된다", listOf(5L, 6L), repository.activeMessages.value.map { it.id })
        assertEquals(0, repository.totalUnread.value)
    }

    @Test
    fun `전달된 READ 는 재연결 때 다시 보내지 않는다`() = runTest {
        val repository = openedRepository() // READ 가 정상 전달됐다
        socket.sent.clear()

        socket.incoming.emit(SocketEvent.Reconnected)
        advanceUntilIdle()

        assertEquals(0, socket.readFrames(ACTIVE_ROOM))
        assertTrue(repository.activeMessages.value.isEmpty())
    }

    @Test
    fun `ROOM_CREATED 는 방 목록을 다시 받는다`() = runTest {
        val repository = openedRepository()
        val before = chatRoomApi.roomsCalls

        socket.incoming.emit(SocketEvent.RoomCreated("room-9"))
        advanceUntilIdle()

        assertEquals(before + 1, chatRoomApi.roomsCalls)
        assertEquals(ConnectionState.CONNECTED, repository.connectionState.value)
    }

    @Test
    fun `AuthFailure 도 REST 를 한 번 태워 토큰을 갱신시킨다`() = runTest {
        openedRepository()
        val before = chatRoomApi.roomsCalls

        socket.incoming.emit(SocketEvent.AuthFailure)
        advanceUntilIdle()

        assertEquals(before + 1, chatRoomApi.roomsCalls)
    }

    private companion object {
        const val ME = "me"
        const val OTHER = "other"
        const val MEMBER_ID = "1"
        const val ACTIVE_ROOM = "room-active"
        const val OTHER_ROOM = "room-other"
    }
}
