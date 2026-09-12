package com.example.modumessenger.core.model

/** 공지사항. [createdDate] 는 `2026-09-06T10:11:12.345` 모양이라 화면에서 `T` 앞만 쓴다. */
data class Notice(
    val id: Long = 0L,
    val title: String = "",
    val content: String = "",
    val writer: String = "",
    val createdDate: String = "",
) {
    companion object {
        /** 작성자가 비어 있을 때 화면에 보여줄 기본값. */
        const val DEFAULT_WRITER = "관리자"
    }
}
