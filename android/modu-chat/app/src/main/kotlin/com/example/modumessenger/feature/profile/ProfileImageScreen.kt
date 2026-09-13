package com.example.modumessenger.feature.profile

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.modumessenger.R
import com.example.modumessenger.core.network.ApiConfig
import com.example.modumessenger.core.ui.components.BottomGradient
import com.example.modumessenger.core.ui.components.LoadingBox
import kotlinx.coroutines.launch

private const val WRITE_STORAGE = Manifest.permission.WRITE_EXTERNAL_STORAGE

/** 전체화면 사진 보기(부록 A §15). 검은 바탕에 페이저 + 아래쪽 그라데이션 위의 점 인디케이터. */
@Composable
fun ProfileImageScreen(
    onClose: () -> Unit,
    viewModel: ProfileImageViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { uiState.images.size })

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                context.getString(message.res, *message.args.toTypedArray()),
            )
        }
    }

    // API 28 이하에서는 사진을 저장하려면 쓰기 권한이 필요하다(API 29 부터는 필요 없다).
    val cancelledMessage = stringResource(R.string.profile_image_download_cancelled)
    val announceCancelled: () -> Unit = {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(cancelledMessage)
        }
        Unit
    }

    var pendingPage by remember { mutableStateOf<Int?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val page = pendingPage
        pendingPage = null
        if (granted && page != null) viewModel.download(page) else announceCancelled()
    }

    val onDownload: () -> Unit = onDownload@{
        val page = pagerState.currentPage
        if (!needsLegacyStoragePermission() || context.hasWriteStoragePermission()) {
            viewModel.download(page)
            return@onDownload
        }
        val activity = context.findActivity()
        // 시스템이 "왜 필요한지 설명하라"고 하면 기존 앱은 그대로 내려받기를 접었다.
        if (activity != null &&
            ActivityCompat.shouldShowRequestPermissionRationale(activity, WRITE_STORAGE)
        ) {
            announceCancelled()
            return@onDownload
        }
        pendingPage = page
        permissionLauncher.launch(WRITE_STORAGE)
    }

    Scaffold(
        containerColor = Color.Black,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().background(Color.Black).padding(padding)) {
            when {
                uiState.isLoading -> LoadingBox()
                uiState.images.isEmpty() -> Unit
                else -> HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    AsyncImage(
                        model = ApiConfig.imageUrl(uiState.images[page].fileName),
                        contentDescription = stringResource(R.string.profile_image_title),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            // 기존 레이아웃과 같은 자리: 내려받기·삭제는 왼쪽 위, 닫기는 오른쪽 위.
            Row(modifier = Modifier.align(Alignment.TopStart)) {
                IconButton(onClick = onDownload, modifier = Modifier.size(TOP_BUTTON_SIZE)) {
                    Icon(
                        Icons.Filled.Download,
                        contentDescription = stringResource(R.string.profile_image_download),
                        tint = Color.White,
                    )
                }
                if (uiState.canDelete) {
                    IconButton(
                        onClick = { viewModel.delete(pagerState.currentPage) },
                        modifier = Modifier.size(TOP_BUTTON_SIZE),
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.profile_image_delete),
                            tint = Color.White,
                        )
                    }
                }
            }

            TextButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 4.dp),
            ) {
                Text(
                    text = stringResource(R.string.profile_image_close),
                    color = Color.White,
                    fontSize = 18.sp,
                )
            }

            BottomGradient(modifier = Modifier.align(Alignment.BottomCenter)) {
                if (uiState.images.size > 1) {
                    PageIndicator(count = uiState.images.size, current = pagerState.currentPage)
                }
            }
        }
    }
}

/** 기존 앱의 `profile_image_indicator_active`/`_inactive` 를 그대로 옮긴 점. */
@Composable
private fun PageIndicator(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (index == current) Color.White else Color(0x66FFFFFF)),
            )
        }
    }
}

private fun needsLegacyStoragePermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

private fun Context.hasWriteStoragePermission(): Boolean =
    ContextCompat.checkSelfPermission(this, WRITE_STORAGE) == PackageManager.PERMISSION_GRANTED

/** `shouldShowRequestPermissionRationale` 은 액티비티가 있어야 물어볼 수 있다. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private val TOP_BUTTON_SIZE = 50.dp
