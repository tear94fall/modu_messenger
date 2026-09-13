package com.example.modumessenger.data.dto

/** `member-service/api-public/common/{key}` 응답. `{"key":"version","value":"1.2.3"}` 모양. */
data class CommonDataDto(
    val key: String? = null,
    val value: String? = null,
)
