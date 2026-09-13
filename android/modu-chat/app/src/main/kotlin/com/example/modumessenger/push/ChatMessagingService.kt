package com.example.modumessenger.push

import android.widget.Toast
import com.example.modumessenger.R
import com.example.modumessenger.core.app.AppForeground
import com.example.modumessenger.core.session.SessionStore
import com.example.modumessenger.data.dto.FcmMessageDto
import com.example.modumessenger.data.repository.PushRepository
import com.example.modumessenger.feature.main.SocketConnectedSource
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * FCM 수신(스펙 §7). 데이터 전용 메시지만 다룬다.
 *
 * 알림을 띄우지 않는 경우 두 가지:
 * 1. 발신자가 나 자신일 때(다른 기기에서 내가 보낸 메시지의 에코).
 * 2. 앱이 앞에 떠 있고 웹소켓도 붙어 있을 때 — 그때는 인앱 배너가 대신 알린다.
 *
 * 알림을 띄우는 경우에는 기존 앱처럼 토스트도 같이 띄운다.
 */
@AndroidEntryPoint
class ChatMessagingService : FirebaseMessagingService() {

    @Inject lateinit var sessionStore: SessionStore

    @Inject lateinit var pushRepository: PushRepository

    @Inject lateinit var notifier: ChatNotifier

    @Inject lateinit var socketConnected: SocketConnectedSource

    /** 서비스는 메시지 하나 처리하고 바로 죽을 수 있어 자기 스코프를 쓴다. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        scope.launch {
            sessionStore.saveFcmToken(token)
            // 로그인 전이면 서버에 올릴 수 없다. 다음 로그인 때 MainViewModel 이 올린다.
            if (sessionStore.memberNow() != null) pushRepository.registerToken(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data.isEmpty()) return
        val dto = FcmMessageDto.from(data)

        scope.launch {
            val myUserId = sessionStore.memberNow()?.userId
            if (!dto.sender.isNullOrBlank() && dto.sender == myUserId) return@launch
            if (AppForeground.isForeground && socketConnected.isConnected) return@launch
            notifier.notify(dto)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    applicationContext,
                    getString(R.string.push_new_message),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
