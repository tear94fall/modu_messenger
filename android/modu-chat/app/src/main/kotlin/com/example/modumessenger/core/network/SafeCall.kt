package com.example.modumessenger.core.network

import retrofit2.HttpException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * 네트워크 호출을 [Result] 로 감싼다. HTTP 오류는 [ApiException], 전송 오류는 [IOException] 그대로 넘어온다.
 * 코루틴 취소는 절대 삼키지 않는다.
 */
suspend fun <T> safeCall(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: HttpException) {
    val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
    Result.failure(ApiException(e.code(), body))
} catch (e: IOException) {
    Result.failure(e)
} catch (e: Exception) {
    Result.failure(e)
}
