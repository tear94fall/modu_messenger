package com.example.modumessenger.data.dto

import com.google.gson.annotations.SerializedName

data class TokenResponseDto(
    @SerializedName("access_token") val accessToken: String? = null,
    @SerializedName("refresh_token") val refreshToken: String? = null,
    @SerializedName("expires_in") val expiresIn: Long = 0L,
    @SerializedName("token_type") val tokenType: String? = null,
)

data class SsoCodeRequestDto(
    val clientId: String,
    val codeChallenge: String,
    val codeChallengeMethod: String,
)

data class SsoCodeResponseDto(
    val code: String? = null,
    val expiresIn: Long = 0L,
)
