package com.example.modumessenger.data.repository

import com.example.modumessenger.core.model.Notice
import com.example.modumessenger.core.network.safeCall
import com.example.modumessenger.data.api.NoticeApi
import com.example.modumessenger.data.dto.toModel
import javax.inject.Inject
import javax.inject.Singleton

interface NoticeRepository {
    suspend fun getNotices(): Result<List<Notice>>
}

@Singleton
class NoticeRepositoryImpl @Inject constructor(
    private val noticeApi: NoticeApi,
) : NoticeRepository {

    override suspend fun getNotices(): Result<List<Notice>> = safeCall {
        noticeApi.getNotices().map { it.toModel() }
    }
}
