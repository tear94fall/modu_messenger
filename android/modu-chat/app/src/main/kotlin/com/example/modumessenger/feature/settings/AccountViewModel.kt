package com.example.modumessenger.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.modumessenger.core.session.SessionEvents
import com.example.modumessenger.core.session.SessionStore
import com.example.modumessenger.data.api.ChatRoomApi
import com.example.modumessenger.data.repository.AuthRepository
import com.example.modumessenger.data.repository.PushRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 계정 설정(부록 A §22). 로그아웃 순서가 중요하다:
 * 세션이 살아 있는 동안 방 목록을 받아 FCM 토픽을 끊고, 그다음에 세션을 지운다.
 */
@HiltViewModel
class AccountViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val pushRepository: PushRepository,
    private val chatRoomApi: ChatRoomApi,
    private val sessionStore: SessionStore,
    private val sessionEvents: SessionEvents,
) : ViewModel() {

    private val _isLoggingOut = MutableStateFlow(false)
    val isLoggingOut: StateFlow<Boolean> = _isLoggingOut.asStateFlow()

    private val _loggedOut = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** 화면이 받아 구글 `signOut` 을 하고 `login` 으로 간다. */
    val loggedOut: SharedFlow<Unit> = _loggedOut.asSharedFlow()

    fun logout() {
        if (_isLoggingOut.value) return
        _isLoggingOut.value = true
        viewModelScope.launch {
            // 1) 세션이 지워지기 전에 방 토픽을 끊는다(방 id 가 필요하다).
            val me = sessionStore.memberNow()
            if (me != null) {
                val roomIds = runCatching { chatRoomApi.getRooms(me.id.toString()) }
                    .getOrNull()
                    ?.mapNotNull { it.roomId }
                    .orEmpty()
                if (roomIds.isNotEmpty()) pushRepository.unsubscribeRooms(roomIds)
            }
            // 2) revoke 를 시도하고, 성공 여부와 상관없이 세션을 지운다.
            authRepository.logout()
            // 3) 앱 전체에 알린다(소켓 종료 등은 구독자가 한다).
            sessionEvents.notifyLoggedOut()
            _isLoggingOut.value = false
            _loggedOut.tryEmit(Unit)
        }
    }
}
