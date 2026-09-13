package com.example.modumessenger.feature.chat

import androidx.lifecycle.SavedStateHandle
import com.example.modumessenger.core.model.ChatType
import com.example.modumessenger.core.model.Member
import com.example.modumessenger.core.model.SendStatus
import com.example.modumessenger.core.session.FriendNames
import com.example.modumessenger.core.session.SessionStore
import com.example.modumessenger.data.dto.ChatDto
import com.example.modumessenger.data.dto.ChatRoomDto
import com.example.modumessenger.data.dto.MemberDto
import com.example.modumessenger.data.repository.ChatRepository
import com.example.modumessenger.navigation.Routes
import com.example.modumessenger.testing.MainDispatcherRule
import com.google.gson.Gson
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val socket = FakeChatSocket()
    private val chatApi = FakeChatApi()
    private val chatRoomApi = FakeChatRoomApi()
    private val storage = FakeStorageRepository()
    private val sessionStore: SessionStore = mockk(relaxed = true)

    init {
        coEvery { sessionStore.memberNow() } returns Member(id = 7L, userId = ME, username = "나")
        coEvery { sessionStore.friendNamesJson() } returns null
        chatRoomApi.rooms = listOf(
            ChatRoomDto(
                roomId = ROOM,
                roomName = "새로운 채팅방",
                roomImage = "",
                lastChatMsg = "",
                lastChatId = "0",
                lastChatTime = "2026-09-12 10:00:00",
                members = listOf(
                    MemberDto(id = 7L, userId = ME, username = "나"),
                    MemberDto(id = 8L, userId = OTHER, username = "친구"),
                ),
            ),
        )
    }

    private fun TestScope.viewModel(): ChatViewModel {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(backgroundScope.coroutineContext + dispatcher)
        val repository = ChatRepository(
            socket = socket,
            chatApi = chatApi,
            chatRoomApi = chatRoomApi,
            sessionStore = sessionStore,
            gson = Gson(),
            ioDispatcher = dispatcher,
            scope = scope,
        )
        repository.setIdentity(ME, "7")
        val viewModel = ChatViewModel(
            savedStateHandle = SavedStateHandle(mapOf(Routes.ARG_ROOM_ID to ROOM)),
            chatRepository = repository,
            storageRepository = storage,
            sessionStore = sessionStore,
            friendNames = FriendNames(sessionStore, Gson(), scope),
            appScope = scope,
        )
        advanceUntilIdle()
        socket.sent.clear()
        return viewModel
    }

    // ---------- "아래로" 배지 규칙 ----------

    @Test
    fun `맨 아래에 있으면 배지를 띄우지 않는다`() {
        assertFalse(ChatViewModel.shouldShowJumpToBottom(wasAtBottom = true, lastSender = OTHER, myUserId = ME))
    }

    @Test
    fun `맨 아래가 아니고 남이 보냈으면 배지를 띄운다`() {
        assertTrue(ChatViewModel.shouldShowJumpToBottom(wasAtBottom = false, lastSender = OTHER, myUserId = ME))
    }

    @Test
    fun `내가 보낸 메시지로는 배지를 띄우지 않는다`() {
        assertFalse(ChatViewModel.shouldShowJumpToBottom(wasAtBottom = false, lastSender = ME, myUserId = ME))
    }

    @Test
    fun `발신자가 비어 있으면 배지를 띄우지 않는다`() {
        assertFalse(ChatViewModel.shouldShowJumpToBottom(wasAtBottom = false, lastSender = "", myUserId = ME))
    }

    @Test
    fun `위로 올라간 뒤 남의 메시지가 오면 배지 수가 쌓이고 맨 아래로 가면 지워진다`() = runTest {
        val viewModel = viewModel()
        viewModel.onAtBottomChanged(false)

        socket.incoming.emit(
            com.example.modumessenger.data.socket.SocketEvent.Chat(
                chat(id = 1L, sender = OTHER, message = "안녕"),
            ),
        )
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.jumpToBottomCount)

        socket.incoming.emit(
            com.example.modumessenger.data.socket.SocketEvent.Chat(
                chat(id = 2L, sender = OTHER, message = "또 안녕"),
            ),
        )
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.jumpToBottomCount)

        viewModel.onAtBottomChanged(true)
        assertEquals(0, viewModel.uiState.value.jumpToBottomCount)
    }

    // ---------- 전송 ----------

    @Test
    fun `전송하면 입력칸이 비고 소켓으로 나간다`() = runTest {
        val viewModel = viewModel()

        viewModel.onInputChange("안녕하세요")
        viewModel.send()
        advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.input)
        assertEquals(1, socket.sent.size)
        assertTrue(socket.sent.single().contains("안녕하세요"))
        assertEquals("안녕하세요", viewModel.uiState.value.bubbles.single().message.message)
    }

    @Test
    fun `빈 입력은 보내지 않는다`() = runTest {
        val viewModel = viewModel()

        viewModel.send()
        advanceUntilIdle()

        assertTrue(socket.sent.isEmpty())
        assertTrue(viewModel.uiState.value.bubbles.isEmpty())
    }

    // ---------- 실패·재전송 ----------

    @Test
    fun `못 보낸 말풍선은 실패로 남고 재전송하면 다시 나간다`() = runTest {
        val viewModel = viewModel()

        socket.sendResult = false
        viewModel.onInputChange("안 갔다")
        viewModel.send()
        advanceUntilIdle()

        val failed = viewModel.uiState.value.bubbles.single()
        assertEquals(SendStatus.FAILED, failed.message.status)
        assertTrue(failed.isFailed)
        assertTrue(socket.sent.isEmpty())

        socket.sendResult = true
        viewModel.resend(failed.message.id)
        advanceUntilIdle()

        assertEquals(1, socket.sent.size)
        assertTrue(socket.sent.single().contains("안 갔다"))
        assertEquals(SendStatus.SENDING, viewModel.uiState.value.bubbles.single().message.status)
    }

    @Test
    fun `실패한 말풍선을 지우면 목록에서 사라진다`() = runTest {
        val viewModel = viewModel()

        socket.sendResult = false
        viewModel.onInputChange("지울 것")
        viewModel.send()
        advanceUntilIdle()

        val failed = viewModel.uiState.value.bubbles.single()
        viewModel.deleteFailed(failed.message.id)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.bubbles.isEmpty())
    }

    // ---------- 말풍선 묶음 ----------

    @Test
    fun `같은 발신자와 같은 시각이면 처음은 헤더 가운데는 본문 끝은 꼬리다`() {
        val messages = listOf(
            message(1L, OTHER, "2026-09-12 10:00:00"),
            message(2L, OTHER, "2026-09-12 10:00:10"),
            message(3L, OTHER, "2026-09-12 10:00:20"),
            message(4L, OTHER, "2026-09-12 10:05:00"),
        )
        val members = listOf(Member(id = 8L, userId = OTHER, username = "친구"))

        val bubbles = ChatViewModel.buildBubbles(messages, members, ME, emptyMap())

        assertEquals(BubbleGroup.HEADER, bubbles[0].group)
        assertEquals(BubbleGroup.BODY, bubbles[1].group)
        assertEquals(BubbleGroup.TAIL, bubbles[2].group)
        assertEquals(BubbleGroup.SINGLE, bubbles[3].group)

        assertTrue(bubbles[0].showSender)
        assertFalse(bubbles[1].showSender)
        assertFalse(bubbles[0].showTime)
        assertTrue(bubbles[2].showTime)
        assertEquals("친구", bubbles[0].senderName)
        assertEquals(8L, bubbles[0].senderMemberId)
        assertFalse(bubbles[0].isMine)
    }

    @Test
    fun `방에 없는 발신자는 이름과 회원 id 가 비어 프로필로 갈 수 없다`() {
        val bubbles = ChatViewModel.buildBubbles(
            listOf(message(1L, "ghost", "2026-09-12 10:00:00")),
            emptyList(),
            ME,
            emptyMap(),
        )

        assertEquals(null, bubbles.single().senderName)
        assertEquals(null, bubbles.single().senderMemberId)
    }

    @Test
    fun `내가 보낸 말풍선은 오른쪽이다`() {
        val bubbles = ChatViewModel.buildBubbles(
            listOf(message(1L, ME, "2026-09-12 10:00:00")),
            listOf(Member(id = 7L, userId = ME, username = "나")),
            ME,
            emptyMap(),
        )

        assertTrue(bubbles.single().isMine)
    }

    private fun message(id: Long, sender: String, time: String) =
        com.example.modumessenger.core.model.ChatMessage(
            id = id,
            chatType = ChatType.TEXT,
            roomId = ROOM,
            sender = sender,
            message = "메시지 $id",
            chatTime = time,
        )

    private fun chat(id: Long, sender: String, message: String) = ChatDto(
        id = id,
        chatType = ChatType.TEXT,
        roomId = ROOM,
        sender = sender,
        message = message,
        chatTime = "2026-09-12 10:00:00",
    )

    private companion object {
        const val ROOM = "room-1"
        const val ME = "me"
        const val OTHER = "other"
    }
}
