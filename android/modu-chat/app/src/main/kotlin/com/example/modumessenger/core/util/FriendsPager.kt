package com.example.modumessenger.core.util

import com.example.modumessenger.data.dto.PageResponseDto

/**
 * 친구 목록 페이지 상태. 화면은 [canLoad] 를 보고 [beginLoad] 로 다음 페이지 번호를 받아 요청하고,
 * 응답이 오면 [onLoaded], 실패하면 [onFailed] 를 부른다.
 * [reset] 은 세대(generation)를 올려서, 초기화 전에 보낸 요청의 늦은 응답이 목록을 덮어쓰지 못하게 한다.
 */
class FriendsPager(val pageSize: Int = DEFAULT_PAGE_SIZE) {

    var nextPage: Int = 0
        private set
    var isLast: Boolean = false
        private set
    var isLoading: Boolean = false
        private set
    var totalElements: Long = 0L
        private set

    /** 현재 세대. 요청을 보낼 때 같이 잡아 두었다가 [onLoaded] 에 넘긴다. */
    var generation: Int = 0
        private set

    fun canLoad(): Boolean = !isLoading && !isLast

    /** 요청할 페이지 번호를 돌려주고 읽는 중으로 표시한다. */
    fun beginLoad(): Int {
        isLoading = true
        return nextPage
    }

    fun onLoaded(page: PageResponseDto<*>): Boolean = onLoaded(generation, page)

    /** 응답을 반영한다. 세대가 다르면(초기화 이후 도착한 옛 응답) 무시하고 false 를 돌려준다. */
    fun onLoaded(requestGeneration: Int, page: PageResponseDto<*>): Boolean {
        if (requestGeneration != generation) return false
        isLoading = false
        isLast = page.last
        nextPage = page.page + 1
        totalElements = page.totalElements
        return true
    }

    fun onFailed() {
        isLoading = false
    }

    fun reset() {
        generation++
        nextPage = 0
        isLast = false
        isLoading = false
        totalElements = 0L
    }

    companion object {
        /** 서버 기본값과 같다(최대 100 까지 서버가 잘라낸다). */
        const val DEFAULT_PAGE_SIZE = 50

        /** 목록 끝에서 이만큼 남으면 다음 페이지를 미리 당긴다. */
        const val PREFETCH_THRESHOLD = 5
    }
}
