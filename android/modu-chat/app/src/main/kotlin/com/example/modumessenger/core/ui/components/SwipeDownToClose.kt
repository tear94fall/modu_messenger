package com.example.modumessenger.core.ui.components

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 기존 앱 `OnSwipeListener` 의 세로 문턱값(120px 남짓)에 해당한다. */
val SwipeDownCloseThreshold: Dp = 100.dp

/**
 * 한 번의 세로 드래그 동안 아래로 끌린 총합이 문턱을 넘었는지. 순수 함수라 단위 테스트로 잰다.
 * 위로 끌면 총합이 음수가 되므로 절대 닫히지 않는다.
 */
internal fun shouldCloseOnSwipeDown(totalDragPx: Float, thresholdPx: Float): Boolean =
    totalDragPx > thresholdPx

/**
 * 아래로 쓸어내리면 [onClose]. 프로필 보기(배경사진·프로필 사진)와 채팅방 설정(배너·방 사진)이 쓴다
 * (부록 A §13 · §10).
 *
 * 드래그를 소비하므로 이 영역에서는 바깥 스크롤이 움직이지 않는다 — 기존 앱도 같은 자리에
 * 터치 리스너를 얹어 같게 동작했다.
 */
@Composable
fun Modifier.swipeDownToClose(
    threshold: Dp = SwipeDownCloseThreshold,
    onClose: () -> Unit,
): Modifier {
    val thresholdPx = with(LocalDensity.current) { threshold.toPx() }
    return this.pointerInput(thresholdPx, onClose) {
        var total = 0f
        detectVerticalDragGestures(
            onDragStart = { total = 0f },
            onDragCancel = { total = 0f },
            onDragEnd = {
                val dragged = total
                total = 0f
                if (shouldCloseOnSwipeDown(dragged, thresholdPx)) onClose()
            },
        ) { change, dragAmount ->
            total += dragAmount
            change.consume()
        }
    }
}
