package com.example.modumessenger.core.util

/**
 * 화면에 보여줄 사람 이름. 내가 정한 별칭이 있으면 별칭, 없으면 상대가 정한 이름, 그것도 없으면 빈 문자열.
 * 앱의 모든 이름 표시는 이 함수를 거친다.
 */
object DisplayName {

    fun of(userId: String?, username: String?, names: Map<String, String>): String {
        val alias = userId?.let { names[it] }
        if (alias != null && alias.trim().isNotEmpty()) return alias
        return username ?: ""
    }
}
