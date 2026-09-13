package com.example.modumessenger.core.util

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** 서버가 주고받는 채팅 시각(`yyyy-MM-dd HH:mm:ss`) 파싱/포맷. */
object ChatTime {

    const val PATTERN = "yyyy-MM-dd HH:mm:ss"

    private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern(PATTERN)

    fun parse(value: String?): LocalDateTime? =
        runCatching { LocalDateTime.parse(value?.trim(), formatter) }.getOrNull()

    fun format(time: LocalDateTime): String = time.format(formatter)

    fun now(): String = format(LocalDateTime.now())

    /**
     * 말풍선·방 목록에 쓰는 짧은 시각(`오전 10:14`). 파싱에 실패하면 빈 문자열.
     * 말풍선 묶음(HEADER/BODY/TAIL)은 "같은 발신자 + 같은 짧은 시각" 으로 판단하므로 여기서 만든 값을 그대로 비교한다.
     */
    fun shortTime(value: String?, locale: Locale = Locale.KOREA): String {
        val time = parse(value) ?: return ""
        return time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
    }
}
