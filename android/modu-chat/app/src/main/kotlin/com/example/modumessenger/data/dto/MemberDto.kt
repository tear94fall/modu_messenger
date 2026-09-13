package com.example.modumessenger.data.dto

/** 서버 `ResponseMemberDto`/`ResponseFriendDto`. Gson 은 이름 정책이 없으므로 필드 이름 = JSON 이름. */
data class MemberDto(
    val id: Long? = null,
    val userId: String? = null,
    val email: String? = null,
    val auth: String? = null,
    /** `"ROLE_ADMIN"` / `"ROLE_USER"`. 문자열로 받아 [roleOf] 가 enum 으로 바꾼다. */
    val role: String? = null,
    val username: String? = null,
    val statusMessage: String? = null,
    val profileImage: String? = null,
    val wallpaperImage: String? = null,
    /** 서버가 보내는 이름은 `profiles` 다(기존 앱은 `profile` 로 잘못 맞춰 두어 항상 비어 있었다). */
    val profiles: List<ProfileDto>? = null,
    /** 친구 목록 응답에만 들어온다. */
    val friendName: String? = null,
)

data class UpdateProfileDto(
    val username: String? = null,
    val statusMessage: String? = null,
    val profileImage: String? = null,
    val wallpaperImage: String? = null,
)

data class AddFriendDto(val email: String)

data class RenameFriendDto(val name: String)

/** 서버 `PageResponse` 레코드와 모양이 같다. */
data class PageResponseDto<T>(
    val content: List<T>? = null,
    val page: Int = 0,
    val size: Int = 0,
    val totalElements: Long = 0L,
    val totalPages: Int = 0,
    val last: Boolean = false,
) {
    val items: List<T> get() = content ?: emptyList()
}

/** 페이지 껍데기는 그대로 두고 내용만 바꾼다(DTO → 모델). */
fun <T, R> PageResponseDto<T>.mapContent(transform: (T) -> R): PageResponseDto<R> =
    PageResponseDto(
        content = items.map(transform),
        page = page,
        size = size,
        totalElements = totalElements,
        totalPages = totalPages,
        last = last,
    )
