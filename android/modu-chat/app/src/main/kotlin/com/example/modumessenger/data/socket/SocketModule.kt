package com.example.modumessenger.data.socket

import com.example.modumessenger.core.network.ApiConfig
import com.example.modumessenger.core.session.SessionStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/** 채팅 웹소켓 전용 OkHttp 클라이언트(ping 20s, 자동 재시도 없음). */
@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class WsClient

/** `ws://…/ws-service/modu-chat`. */
@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class WsUrl

@Module
@InstallIn(SingletonComponent::class)
abstract class SocketModule {

    @Binds
    @Singleton
    abstract fun bindChatSocket(impl: OkHttpChatSocket): ChatSocket

    companion object {

        @Provides
        @Singleton
        @WsUrl
        fun provideWsUrl(): String = ApiConfig.WS_URL

        /**
         * 재시도는 OkHttp 가 아니라 [ReconnectPolicy] 가 맡는다. OkHttp 가 자기 마음대로 다시
         * 붙으면 백오프와 세대 카운터가 어긋난다.
         */
        @Provides
        @Singleton
        @WsClient
        fun provideWsClient(): OkHttpClient = OkHttpClient.Builder()
            .pingInterval(OkHttpChatSocket.PING_INTERVAL_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()

        @Provides
        @Singleton
        fun provideReconnectPolicy(): ReconnectPolicy = ReconnectPolicy()

        /**
         * 매 연결 시도마다 DataStore 를 다시 읽는다. [SessionStore.accessToken] 은 순수 JWT 를
         * 돌려주므로 `Bearer ` 는 소켓이 붙인다. 로그인 전이면 null 이라 헤더가 비어 나가고,
         * 서버는 401 로 끊는다 — 재연결이 예약되고 로그인 후 자동으로 붙는다.
         */
        @Provides
        @Singleton
        fun provideSocketCredentials(sessionStore: SessionStore): SocketCredentials =
            SocketCredentials {
                val userId = sessionStore.memberNow()?.userId.orEmpty()
                val token = sessionStore.accessToken().orEmpty()
                if (userId.isEmpty() || token.isEmpty()) null else userId to token
            }
    }
}
