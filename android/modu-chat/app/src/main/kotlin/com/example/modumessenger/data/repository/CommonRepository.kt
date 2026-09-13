package com.example.modumessenger.data.repository

import com.example.modumessenger.core.network.ApiException
import com.example.modumessenger.core.network.safeCall
import com.example.modumessenger.data.api.CommonApi
import javax.inject.Inject
import javax.inject.Singleton

interface CommonRepository {
    /** 서버가 알려 주는 버전. 키가 없으면 빈 문자열이다(기존 앱도 빈 줄을 그렸다). */
    suspend fun getServerVersion(): Result<String>
}

@Singleton
class CommonRepositoryImpl @Inject constructor(
    private val commonApi: CommonApi,
) : CommonRepository {

    override suspend fun getServerVersion(): Result<String> =
        safeCall { commonApi.getCommonData(VERSION_KEY).value.orEmpty() }
            .recoverCatching { error ->
                // 키가 아직 없을 수 있다. 그건 오류가 아니라 "값 없음" 이다.
                if (error is ApiException && error.code == HTTP_NOT_FOUND) "" else throw error
            }

    private companion object {
        const val VERSION_KEY = "version"
        const val HTTP_NOT_FOUND = 404
    }
}
