package com.example.modumessenger.feature.profile

import androidx.lifecycle.SavedStateHandle
import com.example.modumessenger.core.model.Member
import com.example.modumessenger.core.model.Profile
import com.example.modumessenger.core.model.ProfileType
import com.example.modumessenger.core.session.FriendNames
import com.example.modumessenger.core.session.SessionStore
import com.example.modumessenger.navigation.Routes
import com.example.modumessenger.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val me = Member(id = 1L, userId = "me", username = "나")

    private fun viewModel(
        memberId: Long,
        repository: FakeMemberRepository,
        roomCreator: FakeRoomCreator = FakeRoomCreator(),
    ): ProfileViewModel {
        val sessionStore = mockk<SessionStore>()
        coEvery { sessionStore.memberNow() } returns me
        val friendNames = mockk<FriendNames>()
        every { friendNames.names } returns MutableStateFlow(emptyMap())
        return ProfileViewModel(
            savedStateHandle = SavedStateHandle(mapOf(Routes.ARG_MEMBER_ID to memberId)),
            memberRepository = repository,
            sessionStore = sessionStore,
            friendNames = friendNames,
            roomCreator = roomCreator,
        )
    }

    @Test
    fun `내 프로필이면 편집 버튼이 보이고 이름 변경은 숨긴다`() = runTest {
        val repository = FakeMemberRepository(member = me, me = me)
        val vm = viewModel(memberId = me.id, repository = repository)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.isMe)
        assertTrue(state.showEditButton)
        assertFalse(state.showRenameButton)
    }

    @Test
    fun `남의 프로필이면 이름 변경이 보이고 편집은 숨긴다`() = runTest {
        val friend = Member(id = 2L, userId = "friend", username = "친구")
        val repository = FakeMemberRepository(member = friend, me = me)
        val vm = viewModel(memberId = friend.id, repository = repository)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isMe)
        assertFalse(state.showEditButton)
        assertTrue(state.showRenameButton)
    }

    @Test
    fun `기록이 없으면 기록 버튼을 숨기고 있으면 보여 준다`() = runTest {
        val friend = Member(id = 2L, userId = "friend", username = "친구")
        val empty = FakeMemberRepository(member = friend, me = me)
        val emptyVm = viewModel(memberId = friend.id, repository = empty)
        advanceUntilIdle()
        assertFalse(emptyVm.uiState.value.showHistoryButton)

        val withHistory = FakeMemberRepository(
            member = friend.copy(
                profiles = listOf(
                    Profile(id = 9L, memberId = 2L, type = ProfileType.PROFILE_IMAGE, value = "a.jpg"),
                ),
            ),
            me = me,
        )
        val historyVm = viewModel(memberId = friend.id, repository = withHistory)
        advanceUntilIdle()
        assertTrue(historyVm.uiState.value.showHistoryButton)
    }

    @Test
    fun `이름이 비어 있으면 저장할 수 없다`() = runTest {
        val friend = Member(id = 2L, userId = "friend", username = "친구")
        val repository = FakeMemberRepository(member = friend, me = me)
        val vm = viewModel(memberId = friend.id, repository = repository)
        advanceUntilIdle()

        vm.showRenameDialog()
        assertEquals("친구", vm.uiState.value.renameInput)
        assertTrue(vm.uiState.value.canSaveRename)

        vm.onRenameInputChange("   ")
        assertFalse(vm.uiState.value.canSaveRename)

        vm.saveRename()
        advanceUntilIdle()
        assertTrue(repository.renamedTo.isEmpty())

        vm.onRenameInputChange(" 단짝 ")
        assertTrue(vm.uiState.value.canSaveRename)
        vm.saveRename()
        advanceUntilIdle()

        // 앞뒤 공백은 떼고 보낸다.
        assertEquals(listOf(2L to "단짝"), repository.renamedTo)
        assertFalse(vm.uiState.value.renameDialogVisible)
    }

    @Test
    fun `나와 채팅 하기는 내 id 하나만 보낸다`() = runTest {
        val repository = FakeMemberRepository(member = me, me = me)
        val roomCreator = FakeRoomCreator()
        val vm = viewModel(memberId = me.id, repository = repository, roomCreator = roomCreator)
        advanceUntilIdle()

        vm.startChat()
        advanceUntilIdle()

        assertEquals(listOf(listOf(1L)), roomCreator.requested)
    }

    @Test
    fun `친구와 채팅 하기는 내 id 와 상대 id 를 보낸다`() = runTest {
        val friend = Member(id = 2L, userId = "friend", username = "친구")
        val repository = FakeMemberRepository(member = friend, me = me)
        val roomCreator = FakeRoomCreator()
        val vm = viewModel(memberId = friend.id, repository = repository, roomCreator = roomCreator)
        advanceUntilIdle()

        vm.startChat()
        advanceUntilIdle()

        assertEquals(listOf(listOf(1L, 2L)), roomCreator.requested)
    }
}
