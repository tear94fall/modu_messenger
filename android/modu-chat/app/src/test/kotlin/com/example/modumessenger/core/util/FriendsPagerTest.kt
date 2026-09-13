package com.example.modumessenger.core.util

import com.example.modumessenger.data.dto.PageResponseDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FriendsPagerTest {

    private fun page(number: Int, last: Boolean, total: Long = 120L) =
        PageResponseDto<String>(content = emptyList(), page = number, size = 50, totalElements = total, last = last)

    @Test
    fun `처음에는 0 페이지를 부른다`() {
        val pager = FriendsPager(50)
        assertTrue(pager.canLoad())
        assertEquals(0, pager.beginLoad())
        assertTrue(pager.isLoading)
        assertFalse(pager.canLoad())
    }

    @Test
    fun `응답을 받으면 다음 페이지로 넘어간다`() {
        val pager = FriendsPager(50)
        pager.beginLoad()
        assertTrue(pager.onLoaded(page(0, last = false)))
        assertEquals(1, pager.nextPage)
        assertEquals(120L, pager.totalElements)
        assertTrue(pager.canLoad())
    }

    @Test
    fun `마지막 페이지면 더 부르지 않는다`() {
        val pager = FriendsPager(50)
        pager.beginLoad()
        pager.onLoaded(page(2, last = true))
        assertTrue(pager.isLast)
        assertFalse(pager.canLoad())
    }

    @Test
    fun `실패하면 같은 페이지를 다시 부를 수 있다`() {
        val pager = FriendsPager(50)
        val requested = pager.beginLoad()
        pager.onFailed()
        assertTrue(pager.canLoad())
        assertEquals(requested, pager.beginLoad())
    }

    @Test
    fun `초기화 전에 보낸 응답은 무시한다`() {
        val pager = FriendsPager(50)
        val generation = pager.generation
        pager.beginLoad()
        pager.reset()
        assertFalse(pager.onLoaded(generation, page(3, last = true)))
        assertEquals(0, pager.nextPage)
        assertFalse(pager.isLast)
        assertTrue(pager.canLoad())
    }

    @Test
    fun `기본 페이지 크기와 미리 당기기 임계값`() {
        assertEquals(50, FriendsPager.DEFAULT_PAGE_SIZE)
        assertEquals(5, FriendsPager.PREFETCH_THRESHOLD)
        assertEquals(50, FriendsPager().pageSize)
    }
}
