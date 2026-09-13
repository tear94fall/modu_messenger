package com.example.modumessenger.feature.friends

import com.example.modumessenger.core.model.Member
import com.example.modumessenger.core.session.FriendNames
import com.example.modumessenger.core.session.SessionStore
import com.example.modumessenger.core.util.FriendsPager
import com.example.modumessenger.data.dto.PageResponseDto
import com.example.modumessenger.data.dto.UpdateProfileDto
import com.example.modumessenger.data.repository.MemberRepository
import com.example.modumessenger.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class FriendsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val pageSize = FriendsPager.DEFAULT_PAGE_SIZE

    /** 서버가 [totalElements] 명을 [pageSize] 씩 나눠 준다고 가정한 가짜. */
    private class FakeMemberRepository(
        private val totalElements: Long,
        private val pageSize: Int,
    ) : MemberRepository {

        val requestedPages = mutableListOf<Int>()
        var failNextPage = false

        override suspend fun getMe(): Result<Member> = Result.success(Member(id = 1L, userId = "me"))

        override suspend fun getMember(id: Long): Result<Member> = Result.success(Member(id = id))

        override suspend fun updateProfile(dto: UpdateProfileDto): Result<Member> =
            Result.success(Member())

        override suspend fun getFriendsPage(
            page: Int,
            size: Int,
            sort: String,
        ): Result<PageResponseDto<Member>> {
            requestedPages += page
            if (failNextPage) {
                failNextPage = false
                return Result.failure(IOException("offline"))
            }
            val from = page.toLong() * pageSize
            val count = (totalElements - from).coerceIn(0L, pageSize.toLong()).toInt()
            val content = (0 until count).map { index ->
                val id = from + index
                Member(id = id, userId = "u$id", username = "친구$id")
            }
            val totalPages = ((totalElements + pageSize - 1) / pageSize).toInt()
            return Result.success(
                PageResponseDto(
                    content = content,
                    page = page,
                    size = pageSize,
                    totalElements = totalElements,
                    totalPages = totalPages,
                    last = page >= totalPages - 1,
                ),
            )
        }

        override suspend fun addFriend(email: String): Result<Member> = Result.success(Member())

        override suspend fun loadFriendNames(): Result<Map<String, String>> =
            Result.success(emptyMap())

        override suspend fun renameFriend(friendMemberId: Long, name: String): Result<Member> =
            Result.success(Member())

        override suspend fun searchByEmail(email: String): Result<List<Member>> =
            Result.success(emptyList())

        override suspend fun deleteProfileImage(image: String): Result<Member> =
            Result.success(Member())
    }

    private fun viewModel(repository: MemberRepository): FriendsViewModel {
        val sessionStore = mockk<SessionStore>()
        coEvery { sessionStore.memberNow() } returns Member(id = 1L, userId = "me")
        val friendNames = mockk<FriendNames>()
        every { friendNames.names } returns MutableStateFlow(emptyMap())
        return FriendsViewModel(repository, sessionStore, friendNames)
    }

    @Test
    fun `처음 뜨면 첫 페이지만 읽는다`() = runTest {
        val repository = FakeMemberRepository(totalElements = 120L, pageSize = pageSize)
        val vm = viewModel(repository)
        advanceUntilIdle()

        assertEquals(listOf(0), repository.requestedPages)
        assertEquals(pageSize, vm.uiState.value.friends.size)
        assertEquals(120L, vm.uiState.value.totalCount)
    }

    @Test
    fun `끝에서 다섯 개 남으면 다음 페이지를 당긴다`() = runTest {
        val repository = FakeMemberRepository(totalElements = 120L, pageSize = pageSize)
        val vm = viewModel(repository)
        advanceUntilIdle()

        // 임계값보다 앞이면 당기지 않는다.
        vm.onItemAppeared(pageSize - FriendsPager.PREFETCH_THRESHOLD - 1)
        advanceUntilIdle()
        assertEquals(listOf(0), repository.requestedPages)

        vm.onItemAppeared(pageSize - FriendsPager.PREFETCH_THRESHOLD)
        advanceUntilIdle()
        assertEquals(listOf(0, 1), repository.requestedPages)
        assertEquals(pageSize * 2, vm.uiState.value.friends.size)
    }

    @Test
    fun `마지막 페이지까지 읽으면 더 요청하지 않는다`() = runTest {
        val repository = FakeMemberRepository(totalElements = 30L, pageSize = pageSize)
        val vm = viewModel(repository)
        advanceUntilIdle()

        assertEquals(30, vm.uiState.value.friends.size)

        vm.onItemAppeared(29)
        advanceUntilIdle()

        assertEquals(listOf(0), repository.requestedPages)
    }

    @Test
    fun `refresh 는 목록을 비우고 처음부터 다시 읽는다`() = runTest {
        val repository = FakeMemberRepository(totalElements = 120L, pageSize = pageSize)
        val vm = viewModel(repository)
        advanceUntilIdle()
        vm.onItemAppeared(pageSize - 1)
        advanceUntilIdle()
        assertEquals(pageSize * 2, vm.uiState.value.friends.size)

        vm.refresh()
        advanceUntilIdle()

        assertEquals(listOf(0, 1, 0), repository.requestedPages)
        assertEquals(pageSize, vm.uiState.value.friends.size)
    }

    @Test
    fun `페이지 읽기에 실패하면 다시 읽을 수 있다`() = runTest {
        val repository = FakeMemberRepository(totalElements = 120L, pageSize = pageSize)
        repository.failNextPage = true
        val vm = viewModel(repository)
        advanceUntilIdle()

        assertEquals(0, vm.uiState.value.friends.size)
        assertEquals(false, vm.uiState.value.isLoading)

        vm.loadNextPage()
        advanceUntilIdle()

        assertEquals(listOf(0, 0), repository.requestedPages)
        assertEquals(pageSize, vm.uiState.value.friends.size)
    }

}
