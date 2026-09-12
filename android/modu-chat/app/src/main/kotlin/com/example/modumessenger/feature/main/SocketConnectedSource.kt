package com.example.modumessenger.feature.main

/**
 * 웹소켓이 붙어 있는지. FCM 이 "앱이 앞에 있고 소켓도 붙어 있으면 알림을 띄우지 않는다" 를 판단할 때만 쓴다
 * (스펙 §7).
 *
 * `ChatSocket`(T2) 이 이것을 구현하도록 통합 시 바인딩한다. 기본값은 항상 false 라서,
 * 바인딩 전에는 푸시가 억제되지 않고 그냥 뜬다(안전한 쪽).
 */
interface SocketConnectedSource {
    val isConnected: Boolean
}
