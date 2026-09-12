package com.example.modumessenger.core.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeDownToCloseTest {

    @Test
    fun `문턱을 넘게 아래로 끌면 닫는다`() {
        assertTrue(shouldCloseOnSwipeDown(totalDragPx = 260f, thresholdPx = 250f))
    }

    @Test
    fun `문턱과 같으면 아직 닫지 않는다`() {
        assertFalse(shouldCloseOnSwipeDown(totalDragPx = 250f, thresholdPx = 250f))
    }

    @Test
    fun `조금만 끌면 닫지 않는다`() {
        assertFalse(shouldCloseOnSwipeDown(totalDragPx = 40f, thresholdPx = 250f))
    }

    @Test
    fun `위로 끌면 아무리 많이 끌어도 닫지 않는다`() {
        assertFalse(shouldCloseOnSwipeDown(totalDragPx = -900f, thresholdPx = 250f))
    }
}
