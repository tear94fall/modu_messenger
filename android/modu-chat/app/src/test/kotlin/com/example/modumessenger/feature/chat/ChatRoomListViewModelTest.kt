package com.example.modumessenger.feature.chat

import com.example.modumessenger.R
import com.example.modumessenger.core.model.ChatRoom
import com.example.modumessenger.core.model.Member
import com.example.modumessenger.core.session.FriendNames
import com.example.modumessenger.core.session.SessionStore
import com.example.modumessenger.data.dto.ChatRoomDto
import com.example.modumessenger.data.dto.ChatRoomUnreadDto
import com.example.modumessenger.data.dto.MemberDto
import com.example.modumessenger.data.repository.ChatRepository
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
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** 방 목록 줄 만들기(미리보기 문구·뱃지·아바타)만 본다. 정렬은 리포지토리가 이미 해 준다. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatRoomListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val socket = FakeChatSocket()
    private val chatApi = FakeChatApi()
    private val chatRoomApi = FakeChatRoomApi()
    private val sessionStore: SessionStore = mockk(relaxed = true)

    private val me = Member(id = 7L, userId = ME, username = "나")

    init {
        coEvery { sessionStore.memberNow() } returns me
        coEvery { sessionStore.friendNamesJson() } returns null
    }

    private fun TestScope.viewModel(): ChatRoomListViewModel {
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
        val friendNames = FriendNames(sessionStore, Gson(), scope)
        val viewModel = ChatRoomListViewModel(repository, sessionStore, friendNames)
        viewModel.onResume()
        advanceUntilIdle()
        return viewModel
    }

    @Test
    fun `사진 파일 음성 은 문구로 바뀌고 그 밖의 본문은 그대로다`() = runTest {
        chatRoomApi.rooms = listOf(
            roomDto("r1", "image", "2026-09-12 10:00:00"),
            roomDto("r2", "file", "2026-09-12 09:00:00"),
            roomDto("r3", "audio", "2026-09-12 08:00:00"),
            roomDto("r4", "안녕하세요", "2026-09-12 07:00:00"),
        )

        val rows = viewModel().uiState.value.rooms

        assertEquals(R.string.chat_preview_image, rows[0].previewRes)
        assertEquals(R.string.chat_preview_file, rows[1].previewRes)
        assertEquals(R.string.chat_preview_audio, rows[2].previewRes)
        assertNull(rows[3].previewRes)
        assertEquals("안녕하세요", rows[3].previewText)
    }

    @Test
    fun `뱃지는 0 이하면 감추고 999 를 넘으면 999플러스로 자른다`() = runTest {
        chatRoomApi.rooms = listOf(
            roomDto("r1", "안녕", "2026-09-12 10:00:00"),
            roomDto("r2", "안녕", "2026-09-12 09:00:00"),
            roomDto("r3", "안녕", "2026-09-12 08:00:00"),
        )
        chatRoomApi.unread = listOf(
            ChatRoomUnreadDto(roomId = "r1", unreadChatCount = 0L),
            ChatRoomUnreadDto(roomId = "r2", unreadChatCount = 12L),
            ChatRoomUnreadDto(roomId = "r3", unreadChatCount = 1500L),
        )

        val rows = viewModel().uiState.value.rooms

        assertNull(rows[0].unreadBadge)
        assertEquals("12", rows[1].unreadBadge)
        assertEquals("999+", rows[2].unreadBadge)
    }

    @Test
    fun `방 제목은 나를 뺀 참여자 이름이고 시각은 짧게 보인다`() = runTest {
        chatRoomApi.rooms = listOf(
            ChatRoomDto(
                roomId = "r1",
                roomName = "새로운 채팅방",
                roomImage = "",
                lastChatMsg = "안녕",
                lastChatId = "1",
                lastChatTime = "2026-09-12 10:00:00",
                members = listOf(
                    MemberDto(id = 7L, userId = ME, username = "나"),
                    MemberDto(id = 8L, userId = "friend", username = "친구"),
                ),
            ),
        )

        val row = viewModel().uiState.value.rooms.single()

        assertEquals("친구", row.title)
        assertEquals(RoomAvatar.Single(""), row.avatar)
        assertEquals(true, row.time.isNotEmpty())
    }

    @Test
    fun `마지막 메시지가 없으면 시각도 그리지 않는다`() {
        val room = ChatRoom(roomId = "r1", lastChatMsg = "", lastChatTime = "2026-09-12 10:00:00")
        assertEquals("", ChatRoomListViewModel.lastTimeOf(room))
    }

    @Test
    fun `나를 뺀 참여자가 둘 이상이면 격자 아바타를 최대 넷까지 쓴다`() {
        val members = (1..6).map { Member(id = it.toLong(), userId = "u$it", profileImage = "p$it") }
        val room = ChatRoom(roomId = "r1", roomImage = "", members = members)

        val avatar = ChatRoomListViewModel.avatarOf(room, myUserId = "u1")

        assertEquals(RoomAvatar.Grid(listOf("p2", "p3", "p4", "p5")), avatar)
    }

    @Test
    fun `방 사진이 있으면 참여자 수와 상관없이 그 사진 하나를 쓴다`() {
        val members = (1..6).map { Member(id = it.toLong(), userId = "u$it", profileImage = "p$it") }
        val room = ChatRoom(roomId = "r1", roomImage = "room.png", members = members)

        assertEquals(RoomAvatar.Single("room.png"), ChatRoomListViewModel.avatarOf(room, "u1"))
    }

    private fun roomDto(roomId: String, lastChatMsg: String, lastChatTime: String) = ChatRoomDto(
        roomId = roomId,
        roomName = "방 $roomId",
        roomImage = "",
        lastChatMsg = lastChatMsg,
        lastChatId = "1",
        lastChatTime = lastChatTime,
        members = listOf(MemberDto(id = 7L, userId = ME, username = "나")),
    )

    private companion object {
        const val ME = "me"
    }
}
