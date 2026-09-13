package com.example.modumessenger.data.repository

import com.example.modumessenger.core.network.safeCall
import com.example.modumessenger.core.session.SessionStore
import com.example.modumessenger.data.api.PushApi
import com.google.firebase.messaging.FirebaseMessaging
import javax.inject.Inject
import javax.inject.Singleton

interface PushRepository {

    /** FCM 토큰을 세션에 저장하고 서버에 등록한다. */
    suspend fun registerToken(token: String): Result<Unit>

    /** 방마다 토픽을 하나씩 쓴다. */
    fun subscribeRooms(roomIds: List<String>)

    fun unsubscribeRooms(roomIds: List<String>)
}

@Singleton
class PushRepositoryImpl @Inject constructor(
    private val pushApi: PushApi,
    private val sessionStore: SessionStore,
) : PushRepository {

    override suspend fun registerToken(token: String): Result<Unit> = safeCall {
        sessionStore.saveFcmToken(token)
        val userId = sessionStore.memberNow()?.userId
            ?: throw IllegalStateException("로그인 정보가 없다")
        pushApi.registerToken(userId, token).close()
    }

    override fun subscribeRooms(roomIds: List<String>) {
        val messaging = FirebaseMessaging.getInstance()
        roomIds.filter { it.isNotBlank() }.forEach { messaging.subscribeToTopic(it) }
    }

    override fun unsubscribeRooms(roomIds: List<String>) {
        val messaging = FirebaseMessaging.getInstance()
        roomIds.filter { it.isNotBlank() }.forEach { messaging.unsubscribeFromTopic(it) }
    }
}
