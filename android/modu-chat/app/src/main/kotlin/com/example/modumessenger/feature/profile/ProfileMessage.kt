package com.example.modumessenger.feature.profile

import androidx.annotation.StringRes

/**
 * 한 번만 띄우는 스낵바 문구. [args] 는 포맷 인자(이름·오류 코드 등)다.
 * 프로필 계열 화면은 모두 이 모양으로 문구를 흘려보낸다.
 */
data class ProfileMessage(@StringRes val res: Int, val args: List<Any> = emptyList()) {

    constructor(@StringRes res: Int, arg: Any) : this(res, listOf(arg))
}
