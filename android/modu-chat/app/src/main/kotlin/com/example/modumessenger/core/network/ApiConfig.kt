package com.example.modumessenger.core.network

import com.example.modumessenger.BuildConfig

/** 서버 주소 한 곳. 이미지 URL 을 네 군데서 따로 만들던 것을 여기로 모았다. */
object ApiConfig {

    /** 끝에 `/` 가 붙어 있다(Retrofit base url 규칙). */
    val BASE_URL: String = BuildConfig.API_BASE_URL

    /** 채팅 웹소켓. T2 의 소켓이 쓴다. */
    val WS_URL: String = BuildConfig.WS_BASE_URL + "ws-service/modu-chat"

    private const val VIEW_PATH = "storage-service/api-public/view/"

    /** 저장소 이미지 주소. 인증이 필요하므로 Coil ImageLoader 에 AuthInterceptor 를 달아 쓴다. */
    fun imageUrl(fileName: String): String = BASE_URL + VIEW_PATH + fileName
}
