package com.example.modumessenger.data.dto

data class NoticeDto(
    val id: Long? = null,
    val title: String? = null,
    val content: String? = null,
    val writer: String? = null,
    /** `2026-09-06T10:11:12.345` 모양. */
    val createdDate: String? = null,
)
