package com.example.modumessenger.core.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

/**
 * 무엇을 눌렀는지 스낵바로 알리고 이어서 하던 일을 한다(기존 앱이 메뉴·격자·첨부에서 띄우던 토스트 자리).
 *
 * 화면에서 `val announce = rememberSnackbarThen(snackbarHostState)` 로 받아
 * `onClick = { announce(label) { 이동() } }` 처럼 쓴다.
 */
@Composable
fun rememberSnackbarThen(snackbarHostState: SnackbarHostState): (String, () -> Unit) -> Unit {
    val scope = rememberCoroutineScope()
    return remember(snackbarHostState, scope) {
        { message, action ->
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Short)
            }
            action()
        }
    }
}
