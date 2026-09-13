package com.example.modumessenger.feature.friends

import androidx.annotation.StringRes

/** 한 번만 띄우는 문구. [arg] 는 이메일이나 이름 같은 포맷 인자. */
data class FriendsMessage(@StringRes val res: Int, val arg: String? = null)
