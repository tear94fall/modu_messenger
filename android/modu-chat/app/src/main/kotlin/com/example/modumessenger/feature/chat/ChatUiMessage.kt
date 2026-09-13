package com.example.modumessenger.feature.chat

import androidx.annotation.StringRes

/**
 * 스낵바 한 건. 화면이 `context.getString(res, *args)` 로 푼다.
 * ViewModel 이 `Context` 를 들지 않게 하려고 문자열 대신 리소스 id 로 주고받는다.
 */
data class ChatUiMessage(
    @StringRes val res: Int,
    val args: List<String> = emptyList(),
)
