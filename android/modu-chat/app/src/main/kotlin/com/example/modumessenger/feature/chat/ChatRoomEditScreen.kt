package com.example.modumessenger.feature.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.modumessenger.R
import com.example.modumessenger.core.ui.components.ProfileImage
import com.example.modumessenger.core.ui.components.swipeDownToClose
import com.example.modumessenger.core.ui.theme.BrandViolet
import com.example.modumessenger.core.ui.theme.ModuGrey
import com.example.modumessenger.core.ui.theme.OnChatBubble
import com.example.modumessenger.core.ui.theme.ProfileSurface

/** 방에는 프로필 같은 배경사진이 없어 브랜드색으로 깐다(기존 `chat_room_banner`). */
private val BannerHeight = 200.dp
private val BannerScrimHeight = 96.dp
private val RoomImageSize = 112.dp
private val ImageButtonSize = 36.dp
private val CloseButtonSize = 40.dp
private val SaveButtonHeight = 52.dp

/**
 * 채팅방 설정(부록 A §10). 프로필 보기·수정과 같은 뼈대다:
 * 배너 → 배너 아래 선에 반씩 걸친 원형 방 사진 → 이름 칸 → 화면 아래에 붙은 저장 버튼.
 *
 * 상단바는 없고 배너 위 오른쪽 닫기 버튼으로 나간다. 배너·방 사진을 아래로 쓸어내려도 닫힌다.
 */
@Composable
fun ChatRoomEditScreen(
    onBack: () -> Unit,
    viewModel: ChatRoomEditViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        viewModel.onImagePicked(uri)
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(context.getString(message.res, *message.args.toTypedArray()))
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.saved.collect { onBack() }
    }

    Scaffold(
        containerColor = ProfileSurface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 24.dp),
                ) {
                    RoomBanner(
                        image = uiState.shownImage,
                        onClose = onBack,
                        onChangeImage = { menuOpen = true },
                        menuOpen = menuOpen,
                        onMenuDismiss = { menuOpen = false },
                        onPickGallery = {
                            menuOpen = false
                            pickImage.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        onDefaultImage = {
                            menuOpen = false
                            viewModel.onDefaultImage()
                        },
                    )

                    OutlinedTextField(
                        value = uiState.name,
                        onValueChange = viewModel::onNameChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 24.dp),
                        singleLine = true,
                        label = { Text(stringResource(R.string.chat_room_edit_name_hint)) },
                        shape = RoundedCornerShape(4.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrandViolet,
                            focusedLabelColor = BrandViolet,
                            cursorColor = BrandViolet,
                            focusedTextColor = OnChatBubble,
                            unfocusedTextColor = OnChatBubble,
                        ),
                    )
                    Text(
                        text = stringResource(R.string.chat_room_edit_name_help),
                        fontSize = 12.sp,
                        color = ModuGrey,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, top = 8.dp),
                    )
                }

                // 기존 앱과 같이 화면 아래에 붙여 둔다.
                Button(
                    onClick = viewModel::save,
                    enabled = !uiState.isSaving,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandViolet,
                        contentColor = Color.White,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                        .height(SaveButtonHeight),
                ) {
                    Text(text = stringResource(R.string.chat_room_edit_save), fontSize = 16.sp)
                }
            }
        }
    }
}

/**
 * 배너 + 위쪽 스크림 + 배너 아래 선에 반씩 걸친 방 사진, 그리고 배너 위 오른쪽 닫기 버튼.
 * 이 덩어리를 아래로 쓸어내리면 화면이 닫힌다(기존 앱 `OnSwipeListener`).
 */
@Composable
private fun RoomBanner(
    image: String,
    onClose: () -> Unit,
    onChangeImage: () -> Unit,
    menuOpen: Boolean,
    onMenuDismiss: () -> Unit,
    onPickGallery: () -> Unit,
    onDefaultImage: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(BannerHeight + RoomImageSize / 2)
            .swipeDownToClose(onClose = onClose),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(BannerHeight)
                .background(BrandViolet),
        ) {
            // profile_top_scrim: 위쪽만 어둡게 깔아 흰 아이콘이 읽히게 한다.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(BannerScrimHeight)
                    .background(
                        Brush.verticalGradient(listOf(Color(0x59000000), Color(0x00000000))),
                    ),
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = BannerHeight - RoomImageSize / 2)
                .size(RoomImageSize),
        ) {
            // 흰 테두리 4dp 는 바깥 배경 + 안쪽 여백으로 만든다(전체 112dp).
            ProfileImage(
                fileName = image,
                size = RoomImageSize - 8.dp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.White, CircleShape)
                    .padding(4.dp),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(ImageButtonSize)
                    .background(Color.White, CircleShape)
                    .clickable(onClick = onChangeImage),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_baseline_photo_camera_24),
                    contentDescription = stringResource(R.string.chat_room_edit_change_image),
                    tint = BrandViolet,
                    modifier = Modifier.size(ImageButtonSize - 14.dp),
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = onMenuDismiss) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.chat_room_edit_menu_gallery)) },
                    onClick = onPickGallery,
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.chat_room_edit_menu_default)) },
                    onClick = onDefaultImage,
                )
            }
        }

        // circle_icon_button_scrim: 반투명 검정 원 + 흰 X.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .size(CloseButtonSize)
                .background(Color(0x66000000), CircleShape)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_baseline_clear_24),
                contentDescription = stringResource(R.string.chat_room_edit_close),
                tint = Color.White,
                modifier = Modifier.size(CloseButtonSize - 16.dp),
            )
        }
    }
}
