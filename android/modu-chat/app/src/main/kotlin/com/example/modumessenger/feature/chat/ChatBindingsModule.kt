package com.example.modumessenger.feature.chat

import com.example.modumessenger.core.chat.RoomCreator
import com.example.modumessenger.data.repository.ChatRepository
import com.example.modumessenger.data.socket.ChatSocket
import com.example.modumessenger.data.socket.ConnectionState
import com.example.modumessenger.feature.main.BannerEvent
import com.example.modumessenger.feature.main.BannerSource
import com.example.modumessenger.feature.main.SocketConnectedSource
import com.example.modumessenger.feature.main.UnreadCountSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Singleton

/**
 * 채팅 데이터 계층을 앱 껍데기(T3)가 쓰는 인터페이스에 붙인다.
 * `MainDefaultsModule` 의 기본 구현을 대신한다.
 */
@Module
@InstallIn(SingletonComponent::class)
object ChatBindingsModule {

    @Provides
    @Singleton
    fun provideUnreadCountSource(chatRepository: ChatRepository): UnreadCountSource =
        object : UnreadCountSource {
            override val totalUnread: StateFlow<Int> = chatRepository.totalUnread
        }

    /** `data.repository.BannerEvent` 를 화면이 쓰는 `feature.main.BannerEvent` 로 옮긴다. */
    @Provides
    @Singleton
    fun provideBannerSource(chatRepository: ChatRepository): BannerSource =
        object : BannerSource {
            override val banner: Flow<BannerEvent> = chatRepository.banner.map { event ->
                BannerEvent(
                    roomId = event.roomId,
                    senderUserId = event.senderUserId,
                    message = event.message,
                    chatType = event.chatType,
                )
            }
        }

    @Provides
    @Singleton
    fun provideSocketConnectedSource(chatSocket: ChatSocket): SocketConnectedSource =
        object : SocketConnectedSource {
            override val isConnected: Boolean
                get() = chatSocket.state.value == ConnectionState.CONNECTED
        }

    @Provides
    @Singleton
    fun provideRoomCreator(chatRepository: ChatRepository): RoomCreator = object : RoomCreator {
        override suspend fun createRoom(memberIds: List<Long>): Result<String> =
            chatRepository.createRoom(memberIds).map { it.roomId }
    }
}
