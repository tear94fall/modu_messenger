package com.example.modumessenger.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 다크 테마는 범위 밖이라 라이트 팔레트 하나로 고정한다. */
private val ModuLightColors = lightColorScheme(
    primary = BrandViolet,
    onPrimary = Color.White,
    primaryContainer = ChatBubble,
    onPrimaryContainer = OnChatBubble,
    secondary = BrandBlue,
    onSecondary = Color.Black,
    secondaryContainer = ChatInputFill,
    onSecondaryContainer = OnChatBubble,
    background = Color.White,
    onBackground = OnChatBubble,
    surface = ProfileSurface,
    onSurface = OnChatBubble,
    surfaceVariant = ChatInputFill,
    onSurfaceVariant = ModuGrey,
    outline = ModuDivider,
    error = ModuRed,
    onError = Color.White,
)

/** [darkTheme] 은 받되 쓰지 않는다(스펙 §0). */
@Composable
fun ModuTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = ModuLightColors,
        typography = ModuTypography,
        content = content,
    )
}
