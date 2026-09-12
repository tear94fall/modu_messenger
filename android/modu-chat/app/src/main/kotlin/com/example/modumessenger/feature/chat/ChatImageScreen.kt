package com.example.modumessenger.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.modumessenger.R
import com.example.modumessenger.core.ui.components.BottomGradient

/**
 * 채팅 사진 전체 보기(부록 A §17). 아래쪽 55dp 그라데이션 안에 점 인디케이터가 앉는다
 * (기존 `bottom_gradation_view`). 점 자체는 기존 앱에서 비어 있던 것을 채운 것이다.
 */
@Composable
fun ChatImageScreen(
    onBack: () -> Unit,
    viewModel: ChatImageViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val pageCount = uiState.fileNames.size
    val pagerState = rememberPagerState(pageCount = { pageCount })

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(context.getString(message.res))
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Black,
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                ChatImage(
                    fileName = uiState.fileNames[page],
                    modifier = Modifier.fillMaxWidth().align(Alignment.Center),
                )
            }

            BottomGradient(modifier = Modifier.align(Alignment.BottomCenter)) {
                if (pageCount > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        repeat(pageCount) { index ->
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 3.dp)
                                    .size(if (index == pagerState.currentPage) 9.dp else 6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (index == pagerState.currentPage) {
                                            Color.White
                                        } else {
                                            colorResource(R.color.grey)
                                        },
                                    ),
                            )
                        }
                    }
                }
            }

            // 기존 레이아웃과 같은 자리: 오른쪽 위 X.
            TextButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 4.dp),
            ) {
                Text(text = stringResource(R.string.chat_images_close), color = Color.White)
            }
        }
    }
}
