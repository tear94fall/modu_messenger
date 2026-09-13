package com.example.modumessenger.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 기존 `bottom_gradation_view` 와 같은 높이. */
val BottomGradientHeight: Dp = 55.dp

/**
 * 사진 뷰어 아래쪽에 까는 55dp 그라데이션(투명 → `#111111`). 점 인디케이터는 그 안에 8dp 띄워 앉는다
 * (기존 `bottom_gradation_view` + `profile_image_fading_edge`).
 */
@Composable
fun BottomGradient(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(BottomGradientHeight)
            .background(Brush.verticalGradient(listOf(Color(0x00111111), Color(0xFF111111))))
            .padding(bottom = 8.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        content()
    }
}
