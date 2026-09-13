package com.example.modumessenger.data.api

import com.example.modumessenger.data.dto.SsoCodeRequestDto
import com.example.modumessenger.data.dto.SsoCodeResponseDto
import com.example.modumessenger.data.dto.TokenResponseDto
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface AuthApi {

    /** `oauth2/token` 은 게이트웨이의 no-auth 경로다. 폼은 [com.example.modumessenger.core.util.OAuthClient] 가 만든다. */
    @FormUrlEncoded
    @POST("auth-service/oauth2/token")
    suspend fun token(@FieldMap form: Map<String, String>): TokenResponseDto

    @FormUrlEncoded
    @POST("auth-service/oauth2/revoke")
    suspend fun revoke(
        @Field("token") token: String,
        @Field("client_id") clientId: String,
    )

    /** 커머스 앱에 넘길 SSO 코드. 이 경로는 로그인 토큰이 필요하다. */
    @POST("auth-service/api-public/auth/sso-code")
    suspend fun ssoCode(@Body body: SsoCodeRequestDto): SsoCodeResponseDto
}
