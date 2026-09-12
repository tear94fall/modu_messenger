package com.example.modumessenger.feature.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.modumessenger.R
import com.example.modumessenger.core.session.FriendNames
import com.example.modumessenger.core.session.SessionStore
import com.example.modumessenger.core.util.DisplayName
import com.example.modumessenger.data.api.ChatRoomApi
import com.example.modumessenger.data.repository.MemberRepository
import com.example.modumessenger.data.repository.PushRepository
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume

/** 배너 하나를 그릴 재료. 문구 조립은 화면이 한다(문자열 리소스를 쓰려고). */
data class BannerUi(
    val roomId: String,
    val senderName: String,
    val chatType: Int,
    val message: String,
)

/**
 * 메인 화면(부록 A §4). 처음 뜰 때 FCM 토큰 등록 · 방 토픽 구독 · 친구 별칭 적재를 한 번만 한다.
 *
 * 안 읽은 수와 배너는 `ChatRepository`(T2) 대신 [UnreadCountSource]/[BannerSource] 로 받는다.
 * 통합 시 그 두 인터페이스의 바인딩만 갈아 끼우면 이 화면은 그대로 돈다.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val sessionStore: SessionStore,
    private val memberRepository: MemberRepository,
    private val pushRepository: PushRepository,
    private val chatRoomApi: ChatRoomApi,
    private val friendNames: FriendNames,
    unreadCountSource: UnreadCountSource,
    bannerSource: BannerSource,
) : ViewModel() {

    val totalUnread: StateFlow<Int> = unreadCountSource.totalUnread

    val banners: Flow<BannerUi> = bannerSource.banner.map { event ->
        BannerUi(
            roomId = event.roomId,
            // 발신자가 친구면 내가 정한 이름, 아니면 userId 를 그대로 보여 준다.
            senderName = DisplayName.of(event.senderUserId, null, friendNames.names.value)
                .ifBlank { event.senderUserId },
            chatType = event.chatType,
            message = event.message,
        )
    }

    private val _messages = MutableSharedFlow<Int>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** 스낵바로 띄울 문구의 리소스 id. */
    val messages: SharedFlow<Int> = _messages.asSharedFlow()

    private var started = false

    /** 첫 진입에 한 번만. 화면이 여러 번 붙어도 다시 하지 않는다. */
    fun start() {
        if (started) return
        started = true
        viewModelScope.launch {
            val me = sessionStore.memberNow() ?: return@launch

            val token = runCatching { firebaseToken() }.getOrNull()
            if (token.isNullOrBlank()) {
                _messages.tryEmit(R.string.push_token_unstable)
            } else {
                sessionStore.saveFcmToken(token)
                pushRepository.registerToken(token)
                    .onFailure { _messages.tryEmit(R.string.push_token_unstable) }
            }

            // 방마다 토픽 하나. 푸시는 토픽으로 온다.
            runCatching { chatRoomApi.getRooms(me.id.toString()) }
                .getOrNull()
                ?.mapNotNull { it.roomId }
                ?.takeIf { it.isNotEmpty() }
                ?.let(pushRepository::subscribeRooms)

            // 친구 별칭은 알림 제목과 방 이름에 쓰이므로 일찍 채운다.
            memberRepository.loadFriendNames()
        }
    }

    private suspend fun firebaseToken(): String = suspendCancellableCoroutine { cont ->
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token -> cont.resume(token) }
            .addOnFailureListener { error -> cont.resumeWith(Result.failure(error)) }
    }
}
