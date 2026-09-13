package com.example.modumessenger.data.repository

import com.example.modumessenger.data.api.CommonApi
import com.example.modumessenger.data.dto.CommonDataDto
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/** 서버 버전 조회. 키가 없는 404 는 오류가 아니라 "값 없음" 이다(부록 A §24). */
class CommonRepositoryTest {

    private class FakeCommonApi(
        private val response: (String) -> CommonDataDto,
    ) : CommonApi {
        var lastKey: String? = null
            private set

        override suspend fun getCommonData(key: String): CommonDataDto {
            lastKey = key
            return response(key)
        }
    }

    private fun httpError(code: Int) =
        HttpException(Response.error<Unit>(code, "".toResponseBody("text/plain".toMediaType())))

    @Test
    fun `값이 있으면 그대로 돌려주고 version 키로 묻는다`() = runTest {
        val api = FakeCommonApi { CommonDataDto(key = it, value = "1.2.3") }

        val result = CommonRepositoryImpl(api).getServerVersion()

        assertEquals("1.2.3", result.getOrNull())
        assertEquals("version", api.lastKey)
    }

    @Test
    fun `키가 없어 404 면 실패가 아니라 빈 값이다`() = runTest {
        val api = FakeCommonApi { throw httpError(404) }

        val result = CommonRepositoryImpl(api).getServerVersion()

        assertTrue(result.isSuccess)
        assertEquals("", result.getOrNull())
    }

    @Test
    fun `연결이 끊기면 실패로 남는다`() = runTest {
        val api = FakeCommonApi { throw IOException("offline") }

        val result = CommonRepositoryImpl(api).getServerVersion()

        assertTrue(result.exceptionOrNull() is IOException)
    }
}
