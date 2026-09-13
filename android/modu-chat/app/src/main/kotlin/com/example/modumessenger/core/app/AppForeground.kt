package com.example.modumessenger.core.app

/**
 * 프로세스가 화면에 떠 있는지. [com.example.modumessenger.ModuApp] 이 ProcessLifecycleOwner 로 갱신하고,
 * FCM 수신부가 "앱이 앞에 있고 소켓이 붙어 있으면 알림을 띄우지 않는다" 판단에 쓴다.
 */
object AppForeground {
    @Volatile
    var isForeground: Boolean = false
}
