package com.example.modumessenger.core.model

/** 프로필 기록 한 줄. 상태메시지·배경·프로필 사진이 바뀔 때마다 서버가 한 건씩 쌓는다. */
data class Profile(
    val id: Long = 0L,
    val memberId: Long = 0L,
    val type: ProfileType = ProfileType.PROFILE_STATUS_MESSAGE,
    val value: String = "",
    val createdDate: String = "",
    val updatedDate: String = "",
)

enum class ProfileType { PROFILE_STATUS_MESSAGE, PROFILE_WALLPAPER, PROFILE_IMAGE }
