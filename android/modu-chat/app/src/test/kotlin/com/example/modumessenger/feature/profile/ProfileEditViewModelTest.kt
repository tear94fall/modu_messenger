package com.example.modumessenger.feature.profile

import com.example.modumessenger.core.model.Member
import com.example.modumessenger.core.model.ProfileType
import com.example.modumessenger.core.session.SessionStore
import com.example.modumessenger.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileEditViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val me = Member(
        id = 1L,
        userId = "me",
        username = "나",
        statusMessage = "안녕",
        profileImage = "p.jpg",
        wallpaperImage = "w.jpg",
    )

    private class Fixture {
        val log = CallLog()
        val memberRepository = FakeMemberRepository(log)
        val profileRepository = FakeProfileRepository(log)
        val storageRepository = FakeStorageRepository()
    }

    private fun viewModel(fixture: Fixture): ProfileEditViewModel {
        val sessionStore = mockk<SessionStore>()
        coEvery { sessionStore.memberNow() } returns me
        return ProfileEditViewModel(
            memberRepository = fixture.memberRepository,
            profileRepository = fixture.profileRepository,
            storageRepository = fixture.storageRepository,
            sessionStore = sessionStore,
        )
    }

    private fun fixture(): Fixture = Fixture().apply {
        memberRepository.me = me
        memberRepository.member = me
    }

    @Test
    fun `바뀐 게 없으면 저장해도 아무것도 부르지 않는다`() = runTest {
        val fixture = fixture()
        val vm = viewModel(fixture)
        advanceUntilIdle()
        // 화면을 열면 내 정보를 한 번 읽는다.
        assertEquals(listOf("getMe"), fixture.log.calls)

        vm.save()
        advanceUntilIdle()

        assertEquals(listOf("getMe"), fixture.log.calls)
        assertTrue(fixture.memberRepository.updatedProfiles.isEmpty())
        assertTrue(fixture.profileRepository.added.isEmpty())
    }

    @Test
    fun `상태메시지가 바뀌면 기록을 먼저 남기고 회원 정보를 갱신한다`() = runTest {
        val fixture = fixture()
        val vm = viewModel(fixture)
        advanceUntilIdle()

        vm.onStatusMessageChange("새 상태")
        vm.save()
        advanceUntilIdle()

        assertEquals(listOf("getMe", "addProfile", "updateProfile"), fixture.log.calls)
        assertEquals(
            listOf(Triple(1L, ProfileType.PROFILE_STATUS_MESSAGE, "새 상태")),
            fixture.profileRepository.added,
        )
        val dto = fixture.memberRepository.updatedProfiles.single()
        assertEquals("나", dto.username)
        assertEquals("새 상태", dto.statusMessage)
        // 사진은 건드리지 않는다.
        assertEquals("p.jpg", dto.profileImage)
        assertEquals("w.jpg", dto.wallpaperImage)
    }

    @Test
    fun `상태메시지를 비우면 기록은 남기지 않고 갱신만 한다`() = runTest {
        val fixture = fixture()
        val vm = viewModel(fixture)
        advanceUntilIdle()

        vm.onStatusMessageChange("")
        vm.save()
        advanceUntilIdle()

        assertEquals(listOf("getMe", "updateProfile"), fixture.log.calls)
        assertTrue(fixture.profileRepository.added.isEmpty())
        assertEquals("", fixture.memberRepository.updatedProfiles.single().statusMessage)
    }

    @Test
    fun `이름만 바뀌면 기록 없이 갱신한다`() = runTest {
        val fixture = fixture()
        val vm = viewModel(fixture)
        advanceUntilIdle()

        vm.onNameChange("새 이름")
        vm.save()
        advanceUntilIdle()

        assertEquals(listOf("getMe", "updateProfile"), fixture.log.calls)
        assertTrue(fixture.profileRepository.added.isEmpty())
        assertEquals("새 이름", fixture.memberRepository.updatedProfiles.single().username)
    }

    @Test
    fun `기본 이미지로 바꾸면 기록 없이 빈 값으로 갱신한다`() = runTest {
        val fixture = fixture()
        val vm = viewModel(fixture)
        advanceUntilIdle()

        vm.openSheet(ProfileEditTarget.PROFILE_WALLPAPER)
        vm.onDefaultSelected()
        advanceUntilIdle()

        assertEquals(listOf("getMe", "updateProfile"), fixture.log.calls)
        val dto = fixture.memberRepository.updatedProfiles.single()
        assertEquals("", dto.wallpaperImage)
        assertEquals("p.jpg", dto.profileImage)
    }
}
