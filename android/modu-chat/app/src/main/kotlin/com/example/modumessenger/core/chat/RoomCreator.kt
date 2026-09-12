package com.example.modumessenger.core.chat

/**
 * "이 사람들과 채팅방을 만들어 줘". 친구 찾기와 프로필 화면이 쓴다.
 *
 * 진짜 구현은 `ChatRepository`(T2) 가 들고 있고 T5/통합자가 Hilt 로 바인딩한다.
 * T3 혼자 빌드할 수 있게 [com.example.modumessenger.feature.main.MainDefaultsModule] 에
 * 임시 구현을 두었다 — 통합할 때 그 `@Provides` 를 지우고 `ChatRepository` 를 바인딩한다.
 */
interface RoomCreator {

    /** 서버는 같은 멤버 구성의 방이 이미 있으면 그 방을 돌려준다. @return roomId */
    suspend fun createRoom(memberIds: List<Long>): Result<String>
}
