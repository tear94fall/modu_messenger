package com.example.modumessenger.feature.main

import kotlinx.coroutines.flow.Flow

/**
 * 앱이 떠 있는 동안 다른 방에서 메시지가 오면 띄우는 배너(부록 A §0.7).
 *
 * `ChatRepository`(T2) 가 이것을 구현하도록 통합 시 바인딩한다.
 * T2 도 `data.repository` 에 같은 이름의 `BannerEvent` 를 두므로, 통합자가 둘 중 하나로 맞춘다
 * (이 인터페이스는 `feature.main.BannerEvent` 를 쓴다).
 */
interface BannerSource {
    val banner: Flow<BannerEvent>
}

/**
 * [chatType] 은 [com.example.modumessenger.core.model.ChatType] 상수.
 * 발신자 표시 이름은 화면이 [com.example.modumessenger.core.session.FriendNames] 로 푼다.
 */
data class BannerEvent(
    val roomId: String,
    val senderUserId: String,
    val message: String,
    val chatType: Int,
)
