package com.example.modumessenger.data.socket

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.modumessenger.core.di.ApplicationScope
import com.example.modumessenger.core.session.SessionEvents
import com.example.modumessenger.core.session.SessionStore
import com.example.modumessenger.data.repository.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 소켓을 액티비티가 아니라 **프로세스** 생명주기에 묶는다. 자바 `AppLifecycleObserver` 의 포팅.
 *
 * `ModuApp.onCreate` 에서 [start] 를 한 번 부르면 된다:
 * ```
 * @Inject lateinit var socketLifecycle: SocketLifecycle
 * override fun onCreate() { super.onCreate(); socketLifecycle.start() }
 * ```
 *
 * ProcessLifecycleOwner 의 700ms 디바운스 덕분에 화면 회전이나 액티비티 이동으로는 소켓이 끊기지 않는다.
 */
@Singleton
class SocketLifecycle @Inject constructor(
    private val socket: ChatSocket,
    private val chatRepository: ChatRepository,
    private val sessionStore: SessionStore,
    private val sessionEvents: SessionEvents,
    @ApplicationScope private val scope: CoroutineScope,
) {

    private var started = false

    /** 메인 스레드에서 부른다(ProcessLifecycleOwner 요구). 두 번 불러도 안전하다. */
    fun start() {
        if (started) return
        started = true

        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    scope.launch { connectIfLoggedIn() }
                }

                override fun onStop(owner: LifecycleOwner) {
                    socket.disconnect()
                }
            },
        )

        scope.launch {
            sessionEvents.loggedOut.collect {
                chatRepository.onLoggedOut()
                socket.disconnect()
            }
        }
    }

    /**
     * 로그인 직후에 부른다. 포그라운드 진입은 이미 지나갔으므로 [start] 의 관찰만으로는
     * 다음 백그라운드 왕복 전까지 소켓이 붙지 않는다. 세션 저장이 비동기라 값을 직접 받는다.
     */
    fun onLoggedIn(userId: String, memberId: String) {
        chatRepository.setIdentity(userId, memberId)
        socket.connect()
    }

    /** 로그인 화면이 세션 저장을 마친 뒤 부른다. 저장된 회원·토큰으로 identity 를 잡고 연결한다. */
    fun connectFromSession() {
        scope.launch { connectIfLoggedIn() }
    }

    /**
     * 토큰과 회원이 **둘 다** 있어야 붙는다. 로그인 도중에 붙으면 `userId` 헤더가 비어 서버가 거절한다.
     */
    private suspend fun connectIfLoggedIn() {
        val member = sessionStore.memberNow() ?: return
        if (member.userId.isBlank()) return
        if (sessionStore.accessToken().isNullOrBlank()) return

        chatRepository.setIdentity(member.userId, member.id.toString())
        socket.connect()
    }
}
