package com.example.modumessenger.core.network

/** HTTP 오류. 게이트웨이 인증 실패는 본문이 비어 있으므로 [body] 가 null 일 수 있다. */
class ApiException(
    val code: Int,
    val body: String? = null,
) : RuntimeException("HTTP $code" + if (body.isNullOrBlank()) "" else ": $body")
