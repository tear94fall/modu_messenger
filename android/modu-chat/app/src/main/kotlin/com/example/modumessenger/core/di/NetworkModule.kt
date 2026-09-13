package com.example.modumessenger.core.di

import android.content.Context
import coil.ImageLoader
import com.example.modumessenger.BuildConfig
import com.example.modumessenger.core.network.ApiConfig
import com.example.modumessenger.core.network.AuthInterceptor
import com.example.modumessenger.core.network.TokenAuthenticator
import com.example.modumessenger.data.api.AuthApi
import com.example.modumessenger.data.api.ChatApi
import com.example.modumessenger.data.api.ChatRoomApi
import com.example.modumessenger.data.api.CommonApi
import com.example.modumessenger.data.api.MemberApi
import com.example.modumessenger.data.api.NoticeApi
import com.example.modumessenger.data.api.ProfileApi
import com.example.modumessenger.data.api.PushApi
import com.example.modumessenger.data.api.StorageApi
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val TIMEOUT_SECONDS = 10L

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder().setLenient().create()

    /** 토큰 갱신 전용. 인터셉터도 Authenticator 도 없다(갱신 요청이 자기 자신을 부르면 안 된다). */
    @Provides
    @Singleton
    @Named("plain")
    fun providePlainClient(): OkHttpClient = baseClientBuilder().build()

    @Provides
    @Singleton
    @Named("api")
    fun provideApiClient(
        authInterceptor: AuthInterceptor,
        tokenAuthenticator: TokenAuthenticator,
    ): OkHttpClient = baseClientBuilder()
        .addInterceptor(authInterceptor)
        .authenticator(tokenAuthenticator)
        .build()

    @Provides
    @Singleton
    @Named("plain")
    fun providePlainRetrofit(@Named("plain") client: OkHttpClient, gson: Gson): Retrofit =
        retrofit(client, gson)

    @Provides
    @Singleton
    @Named("api")
    fun provideRetrofit(@Named("api") client: OkHttpClient, gson: Gson): Retrofit =
        retrofit(client, gson)

    /** [TokenAuthenticator] 가 쓰는 갱신 전용 AuthApi. */
    @Provides
    @Singleton
    @Named("plain")
    fun providePlainAuthApi(@Named("plain") retrofit: Retrofit): AuthApi =
        retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideAuthApi(@Named("api") retrofit: Retrofit): AuthApi =
        retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideMemberApi(@Named("api") retrofit: Retrofit): MemberApi =
        retrofit.create(MemberApi::class.java)

    @Provides
    @Singleton
    fun provideChatApi(@Named("api") retrofit: Retrofit): ChatApi =
        retrofit.create(ChatApi::class.java)

    @Provides
    @Singleton
    fun provideChatRoomApi(@Named("api") retrofit: Retrofit): ChatRoomApi =
        retrofit.create(ChatRoomApi::class.java)

    @Provides
    @Singleton
    fun provideProfileApi(@Named("api") retrofit: Retrofit): ProfileApi =
        retrofit.create(ProfileApi::class.java)

    @Provides
    @Singleton
    fun provideStorageApi(@Named("api") retrofit: Retrofit): StorageApi =
        retrofit.create(StorageApi::class.java)

    @Provides
    @Singleton
    fun providePushApi(@Named("api") retrofit: Retrofit): PushApi =
        retrofit.create(PushApi::class.java)

    @Provides
    @Singleton
    fun provideNoticeApi(@Named("api") retrofit: Retrofit): NoticeApi =
        retrofit.create(NoticeApi::class.java)

    @Provides
    @Singleton
    fun provideCommonApi(@Named("api") retrofit: Retrofit): CommonApi =
        retrofit.create(CommonApi::class.java)

    /** 저장소 이미지는 인증이 필요하다. 인증 클라이언트를 그대로 쓴다. */
    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        @Named("api") client: OkHttpClient,
    ): ImageLoader = ImageLoader.Builder(context)
        .okHttpClient(client)
        .crossfade(true)
        .build()

    private fun baseClientBuilder(): OkHttpClient.Builder {
        val builder = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)

        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC },
            )
        }
        return builder
    }

    private fun retrofit(client: OkHttpClient, gson: Gson): Retrofit = Retrofit.Builder()
        .baseUrl(ApiConfig.BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
}
