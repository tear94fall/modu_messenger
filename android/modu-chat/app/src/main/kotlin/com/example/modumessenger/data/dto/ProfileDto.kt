package com.example.modumessenger.data.dto

data class ProfileDto(
    val id: Long? = null,
    val memberId: Long? = null,
    /** `PROFILE_STATUS_MESSAGE` / `PROFILE_WALLPAPER` / `PROFILE_IMAGE`. */
    val profileType: String? = null,
    val value: String? = null,
    val createdDate: String? = null,
    val updatedDate: String? = null,
)

data class CreateProfileDto(
    val memberId: Long,
    val profileType: String,
    val value: String,
)
