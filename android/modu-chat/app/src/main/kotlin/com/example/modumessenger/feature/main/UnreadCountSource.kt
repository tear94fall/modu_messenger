package com.example.modumessenger.feature.main

import kotlinx.coroutines.flow.StateFlow

/**
 * 채팅 탭 뱃지에 쓸 "안 읽은 메시지 총합".
 *
 * `ChatRepository`(T2) 가 이것을 구현하도록 통합 시 바인딩한다.
 * 그때까지는 [MainDefaultsModule] 의 항상 0 인 기본 구현을 쓴다.
 */
interface UnreadCountSource {
    val totalUnread: StateFlow<Int>
}
