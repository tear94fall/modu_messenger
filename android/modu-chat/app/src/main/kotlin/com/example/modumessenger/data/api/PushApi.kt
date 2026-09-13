package com.example.modumessenger.data.api

import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.PUT
import retrofit2.http.Path

interface PushApi {

    /**
     * 서버가 `@RequestBody String` 으로 받으므로 본문은 JSON 문자열 리터럴(`"abc..."`)이어야 한다.
     * Gson 컨버터가 `String` 을 그렇게 직렬화한다 — 서버 계약이라 그대로 둔다.
     */
    @PUT("push-service/api-public/push/{userId}/token")
    suspend fun registerToken(
        @Path("userId") userId: String,
        @Body token: String,
    ): ResponseBody
}
