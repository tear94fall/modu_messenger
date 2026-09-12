package com.example.modumessenger.core.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import com.example.modumessenger.R
import kotlinx.coroutines.launch

/**
 * 아직 만들지 않은 항목을 눌렀을 때 스낵바 `"준비 중입니다"` 를 띄우는 람다.
 * 화면에서 `val comingSoon = rememberComingSoon(snackbarHostState)` 로 받아 onClick 에 그대로 넘긴다.
 */
@Composable
fun rememberComingSoon(snackbarHostState: SnackbarHostState): () -> Unit {
    val scope = rememberCoroutineScope()
    val message = stringResource(R.string.coming_soon)
    return remember(snackbarHostState, message, scope) {
        {
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Short)
            }
            Unit
        }
    }
}
