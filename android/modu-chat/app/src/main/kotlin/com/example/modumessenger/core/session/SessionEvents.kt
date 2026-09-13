package com.example.modumessenger.core.session

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/** 토큰 갱신이 끝내 실패했을 때처럼 "이제 로그인 화면으로 가야 한다" 를 앱 전체에 알린다. */
@Singleton
class SessionEvents @Inject constructor() {

    private val _loggedOut = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val loggedOut: SharedFlow<Unit> = _loggedOut.asSharedFlow()

    fun notifyLoggedOut() {
        _loggedOut.tryEmit(Unit)
    }
}
