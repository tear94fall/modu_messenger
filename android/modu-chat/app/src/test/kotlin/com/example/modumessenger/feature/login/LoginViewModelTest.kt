package com.example.modumessenger.feature.login

import com.example.modumessenger.R
import com.example.modumessenger.core.model.Member
import com.example.modumessenger.core.network.ApiException
import com.example.modumessenger.data.dto.SsoCodeRequestDto
import com.example.modumessenger.data.dto.SsoCodeResponseDto
import com.example.modumessenger.data.repository.AuthRepository
import com.example.modumessenger.data.repository.OfflineLoginException
import com.example.modumessenger.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import app.cash.turbine.test
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeAuthRepository(
        var result: Result<Member> = Result.success(Member(id = 1L, userId = "u1")),
    ) : AuthRepository {
        var calls = 0
            private set
        var lastIdToken: String? = null
        var lastEmail: String? = null

        override suspend fun loginWithGoogle(idToken: String, email: String): Result<Member> {
            calls++
            lastIdToken = idToken
            lastEmail = email
            return result
        }

        override suspend fun logout(): Result<Unit> = Result.success(Unit)

        override suspend fun issueSsoCode(request: SsoCodeRequestDto): Result<SsoCodeResponseDto> =
            Result.success(SsoCodeResponseDto())
    }

    @Test
    fun `id 토큰이 없으면 서버를 부르지 않고 문구만 띄운다`() = runTest {
        val repository = FakeAuthRepository()
        val viewModel = LoginViewModel(repository)

        viewModel.onGoogleAccount(idToken = null, email = "a@b.c")
        advanceUntilIdle()

        assertEquals(0, repository.calls)
        assertEquals(
            LoginUiState.Error(R.string.login_no_id_token),
            viewModel.uiState.value,
        )
    }

    @Test
    fun `로그인에 성공하면 성공 상태가 된다`() = runTest {
        val repository = FakeAuthRepository()
        val viewModel = LoginViewModel(repository)

        viewModel.onGoogleAccount(idToken = "token", email = "a@b.c")
        advanceUntilIdle()

        assertEquals(LoginUiState.Success, viewModel.uiState.value)
        assertEquals("token", repository.lastIdToken)
        assertEquals("a@b.c", repository.lastEmail)
    }

    @Test
    fun `서버가 거절하면 코드를 담은 오류가 된다`() = runTest {
        val repository = FakeAuthRepository(result = Result.failure(ApiException(401, null)))
        val viewModel = LoginViewModel(repository)

        viewModel.onGoogleAccount(idToken = "token", email = "a@b.c")
        advanceUntilIdle()

        assertEquals(
            LoginUiState.Error(R.string.login_failed, 401),
            viewModel.uiState.value,
        )
    }

    @Test
    fun `연결 자체가 안 되면 연결 문구가 된다`() = runTest {
        val repository = FakeAuthRepository(result = Result.failure(IOException("offline")))
        val viewModel = LoginViewModel(repository)

        viewModel.onGoogleAccount(idToken = "token", email = "a@b.c")
        advanceUntilIdle()

        assertEquals(
            LoginUiState.Error(R.string.login_connection_unstable),
            viewModel.uiState.value,
        )
    }

    @Test
    fun `구글 쪽 실패는 상태 코드를 그대로 보여 준다`() = runTest {
        val viewModel = LoginViewModel(FakeAuthRepository())

        viewModel.onGoogleSignInFailed(12501)

        assertEquals(
            LoginUiState.Error(R.string.login_google_failed, 12501),
            viewModel.uiState.value,
        )
    }

    @Test
    fun `오프라인 예외면 안내만 띄우고 메인으로 들어간다`() = runTest {
        val repository = FakeAuthRepository(
            result = Result.failure(OfflineLoginException(IOException("offline"))),
        )
        val viewModel = LoginViewModel(repository)

        viewModel.messages.test {
            viewModel.onGoogleAccount(idToken = "token", email = "a@b.c")
            advanceUntilIdle()

            assertEquals(LoginMessage(R.string.login_offline_mode), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        // 실패가 아니므로 메인으로 간다.
        assertEquals(LoginUiState.Success, viewModel.uiState.value)
    }

    @Test
    fun `오프라인 모드에서는 구글 로그아웃을 시키지 않는다`() = runTest {
        val repository = FakeAuthRepository(
            result = Result.failure(OfflineLoginException(IOException("offline"))),
        )
        val viewModel = LoginViewModel(repository)

        viewModel.signOutRequests.test {
            viewModel.onGoogleAccount(idToken = "token", email = "a@b.c")
            advanceUntilIdle()

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `서버가 거절하면 구글 로그아웃을 시킨다`() = runTest {
        val repository = FakeAuthRepository(result = Result.failure(ApiException(401, null)))
        val viewModel = LoginViewModel(repository)

        viewModel.signOutRequests.test {
            viewModel.onGoogleAccount(idToken = "token", email = "a@b.c")
            advanceUntilIdle()

            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `토큰 교환이 연결 실패로 끝나도 구글 로그아웃을 시킨다`() = runTest {
        val repository = FakeAuthRepository(result = Result.failure(IOException("offline")))
        val viewModel = LoginViewModel(repository)

        viewModel.signOutRequests.test {
            viewModel.onGoogleAccount(idToken = "token", email = "a@b.c")
            advanceUntilIdle()

            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(
            LoginUiState.Error(R.string.login_connection_unstable),
            viewModel.uiState.value,
        )
    }

    @Test
    fun `무음 로그인이 시작되면 버튼을 숨기고 실패하면 되돌린다`() = runTest {
        val viewModel = LoginViewModel(FakeAuthRepository())

        viewModel.onSilentSignInStarted()
        assertTrue(viewModel.uiState.value is LoginUiState.Signing)

        viewModel.onSilentSignInFailed()
        assertTrue(viewModel.uiState.value is LoginUiState.Idle)
    }
}
