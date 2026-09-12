package com.example.modumessenger.data.repository

import com.example.modumessenger.core.network.ApiException
import com.example.modumessenger.core.session.SessionStore
import com.example.modumessenger.data.api.AuthApi
import com.example.modumessenger.data.api.MemberApi
import com.example.modumessenger.data.dto.MemberDto
import com.example.modumessenger.data.dto.SsoCodeRequestDto
import com.example.modumessenger.data.dto.SsoCodeResponseDto
import com.example.modumessenger.data.dto.TokenResponseDto
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * 로그인 경로 중 "토큰은 받았는데 회원 정보를 못 받은" 갈래를 잰다(부록 A §2 오프라인 모드).
 */
class AuthRepositoryTest {

    /** 필요한 것만 답하는 가짜 api. 나머지는 부르면 안 되므로 터뜨린다. */
    private class FakeAuthApi(
        private val token: () -> TokenResponseDto,
    ) : AuthApi {
        override suspend fun token(form: Map<String, String>): TokenResponseDto = token.invoke()

        override suspend fun revoke(token: String, clientId: String) = Unit

        override suspend fun ssoCode(body: SsoCodeRequestDto): SsoCodeResponseDto =
            throw UnsupportedOperationException()
    }

    private class FakeMemberApi(
        private val member: () -> MemberDto,
    ) : MemberApi by mockk(relaxed = true) {
        override suspend fun getMemberByEmail(email: String): MemberDto = member.invoke()
    }

    private fun repository(
        token: () -> TokenResponseDto = { TokenResponseDto(accessToken = "a", refreshToken = "r") },
        member: () -> MemberDto = { MemberDto(id = 1L, userId = "u1") },
        sessionStore: SessionStore = mockk(relaxed = true),
    ) = AuthRepositoryImpl(FakeAuthApi(token), FakeMemberApi(member), sessionStore)

    private fun httpError(code: Int) =
        HttpException(Response.error<Unit>(code, "".toResponseBody("text/plain".toMediaType())))

    @Test
    fun `회원 정보를 네트워크 때문에 못 받으면 오프라인 예외가 된다`() = runTest {
        val store = mockk<SessionStore>(relaxed = true)
        val result = repository(member = { throw IOException("offline") }, sessionStore = store)
            .loginWithGoogle("id-token", "a@b.c")

        assertTrue(result.exceptionOrNull() is OfflineLoginException)
        // 토큰까지는 받았으므로 세션에 저장되어 있어야 한다.
        coVerify { store.saveTokens("a", "r") }
    }

    @Test
    fun `회원 조회가 4xx 면 오프라인이 아니라 그냥 실패다`() = runTest {
        val result = repository(member = { throw httpError(404) })
            .loginWithGoogle("id-token", "a@b.c")

        val error = result.exceptionOrNull()
        assertTrue(error is ApiException)
        assertEquals(404, (error as ApiException).code)
    }

    @Test
    fun `토큰 교환이 실패하면 회원 조회까지 가지 않는다`() = runTest {
        var memberCalls = 0
        val result = repository(
            token = { throw httpError(401) },
            member = { memberCalls++; MemberDto(id = 1L, userId = "u1") },
        ).loginWithGoogle("id-token", "a@b.c")

        assertEquals(401, (result.exceptionOrNull() as ApiException).code)
        assertEquals(0, memberCalls)
    }

    @Test
    fun `access_token 이 비어 있으면 실패다`() = runTest {
        val result = repository(token = { TokenResponseDto(accessToken = " ") })
            .loginWithGoogle("id-token", "a@b.c")

        assertTrue(result.exceptionOrNull() is ApiException)
    }

    @Test
    fun `다 되면 회원을 세션에 저장하고 돌려준다`() = runTest {
        val store = mockk<SessionStore>(relaxed = true)
        val result = repository(sessionStore = store).loginWithGoogle("id-token", "a@b.c")

        assertEquals("u1", result.getOrNull()?.userId)
        coVerify { store.saveMember(match { it.userId == "u1" }) }
    }
}
