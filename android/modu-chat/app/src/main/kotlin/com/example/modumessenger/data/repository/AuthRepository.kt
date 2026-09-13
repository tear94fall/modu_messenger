package com.example.modumessenger.data.repository

import com.example.modumessenger.core.model.Member
import com.example.modumessenger.core.network.ApiException
import com.example.modumessenger.core.network.safeCall
import com.example.modumessenger.core.session.SessionStore
import com.example.modumessenger.core.util.OAuthClient
import com.example.modumessenger.data.api.AuthApi
import com.example.modumessenger.data.api.MemberApi
import com.example.modumessenger.data.dto.SsoCodeRequestDto
import com.example.modumessenger.data.dto.SsoCodeResponseDto
import com.example.modumessenger.data.dto.toModel
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 토큰은 받았는데 회원 정보를 못 받았다(연결 문제). 로그인 자체는 된 셈이라 화면은
 * `"오프라인 모드 입니다."` 만 띄우고 메인으로 들어간다(부록 A §2).
 */
class OfflineLoginException(cause: Throwable) : RuntimeException("오프라인 모드", cause)

interface AuthRepository {

    /**
     * 구글 id 토큰을 앱 토큰으로 바꾸고 회원 정보까지 받아 세션에 저장한다.
     * 이메일은 토큰 응답에 없으므로 `GoogleSignInAccount.email` 을 받아 쓴다.
     *
     * 토큰까지는 받았는데 회원 정보를 못 받은 경우(네트워크)에는 [OfflineLoginException] 으로 실패한다.
     */
    suspend fun loginWithGoogle(idToken: String, email: String): Result<Member>

    /** revoke 를 시도하되, 성공하든 실패하든 세션은 반드시 지운다. */
    suspend fun logout(): Result<Unit>

    suspend fun issueSsoCode(request: SsoCodeRequestDto): Result<SsoCodeResponseDto>
}

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val authApi: AuthApi,
    private val memberApi: MemberApi,
    private val sessionStore: SessionStore,
) : AuthRepository {

    override suspend fun loginWithGoogle(idToken: String, email: String): Result<Member> {
        val granted = safeCall {
            val tokens = authApi.token(OAuthClient.googleForm(idToken))
            val access = tokens.accessToken
            if (access.isNullOrBlank()) throw ApiException(200, "access_token 이 비어 있다")
            sessionStore.saveTokens(access, tokens.refreshToken.orEmpty())
        }
        granted.exceptionOrNull()?.let { return Result.failure(it) }

        return safeCall {
            val member = memberApi.getMemberByEmail(email).toModel()
            sessionStore.saveMember(member)
            member
        }.recoverCatching { error ->
            // 서버가 답을 했다면(4xx/5xx) 진짜 실패다. 아예 닿지 못한 것만 오프라인으로 본다.
            throw if (error is IOException) OfflineLoginException(error) else error
        }
    }

    override suspend fun logout(): Result<Unit> {
        val refresh = sessionStore.refreshToken()
        if (!refresh.isNullOrBlank()) {
            // 서버가 안 받아도(네트워크 단절 포함) 로그아웃은 계속한다. 기존 앱은 여기서 화면이 멈췄다.
            safeCall { authApi.revoke(refresh, OAuthClient.CLIENT_ID) }
        }
        sessionStore.clearSession()
        return Result.success(Unit)
    }

    override suspend fun issueSsoCode(request: SsoCodeRequestDto): Result<SsoCodeResponseDto> =
        safeCall { authApi.ssoCode(request) }
}
