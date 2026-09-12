package com.example.modumessenger.feature.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.modumessenger.R
import com.example.modumessenger.core.network.ApiConfig
import com.example.modumessenger.core.ui.components.ProfileImage
import com.example.modumessenger.core.ui.theme.BrandViolet
import com.example.modumessenger.core.ui.theme.ProfileSurface

private val AvatarSize = 112.dp
private val ScrimHeight = 96.dp

/** 프로필 편집(부록 A §14). */
@Composable
fun ProfileEditScreen(
    onClose: () -> Unit,
    viewModel: ProfileEditViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var pendingTarget by remember { mutableStateOf<ProfileEditTarget?>(null) }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val target = pendingTarget
        pendingTarget = null
        if (target != null) viewModel.onImagePicked(target, uri)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                context.getString(message.res, *message.args.toTypedArray()),
            )
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ProfileEditEvent.Saved -> onClose()
                is ProfileEditEvent.PickImage -> {
                    pendingTarget = event.target
                    pickImage.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                }
            }
        }
    }

    Scaffold(
        containerColor = ProfileSurface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    EditableWallpaper(
                        fileName = uiState.member?.wallpaperImage.orEmpty(),
                        onEdit = { viewModel.openSheet(ProfileEditTarget.PROFILE_WALLPAPER) },
                    )
                    Box(
                        modifier = Modifier.offset(y = AvatarSize / 2),
                        contentAlignment = Alignment.BottomEnd,
                    ) {
                        ProfileImage(
                            fileName = uiState.member?.profileImage,
                            size = AvatarSize - 8.dp,
                            modifier = Modifier
                                .background(Color.White, CircleShape)
                                .padding(4.dp),
                        )
                        IconButton(
                            onClick = { viewModel.openSheet(ProfileEditTarget.PROFILE_IMAGE) },
                            modifier = Modifier.size(36.dp).background(Color.White, CircleShape),
                        ) {
                            Icon(
                                Icons.Filled.PhotoCamera,
                                contentDescription = stringResource(R.string.profile_edit_image_button),
                                tint = BrandViolet,
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = uiState.name,
                    onValueChange = viewModel::onNameChange,
                    label = { Text(stringResource(R.string.profile_edit_name_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, top = AvatarSize / 2 + 16.dp),
                )
                OutlinedTextField(
                    value = uiState.statusMessage,
                    onValueChange = viewModel::onStatusMessageChange,
                    label = { Text(stringResource(R.string.profile_edit_status_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, top = 12.dp),
                )
                Button(
                    onClick = viewModel::save,
                    enabled = !uiState.isBusy,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandViolet),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 24.dp)
                        .height(52.dp),
                ) {
                    Text(text = stringResource(R.string.profile_edit_save), fontSize = 16.sp)
                }
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(40.dp)
                    .background(Color(0x59000000), CircleShape),
            ) {
                Icon(
                    Icons.Filled.Clear,
                    contentDescription = stringResource(R.string.profile_close_button),
                    tint = Color.White,
                )
            }
        }
    }

    uiState.sheetTarget?.let { target ->
        ProfileEditSheet(
            target = target,
            onAlbum = viewModel::onAlbumSelected,
            onDefault = viewModel::onDefaultSelected,
            onDismiss = viewModel::dismissSheet,
        )
    }
}

@Composable
private fun EditableWallpaper(fileName: String, onEdit: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(BrandViolet),
    ) {
        if (fileName.isNotBlank()) {
            AsyncImage(
                model = ApiConfig.imageUrl(fileName),
                contentDescription = stringResource(R.string.profile_wallpaper_description),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ScrimHeight)
                .background(Brush.verticalGradient(listOf(Color(0x59000000), Color(0x00000000)))),
        )
        IconButton(
            onClick = onEdit,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .size(40.dp)
                .background(Color(0x59000000), CircleShape),
        ) {
            Icon(
                Icons.Filled.PhotoCamera,
                contentDescription = stringResource(R.string.profile_edit_wallpaper_button),
                tint = Color.White,
            )
        }
    }
}

/** 프로필 편집 바텀시트(부록 A §14a). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileEditSheet(
    target: ProfileEditTarget,
    onAlbum: () -> Unit,
    onDefault: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        val bannerRes = when (target) {
            ProfileEditTarget.PROFILE_IMAGE -> R.string.profile_edit_sheet_profile
            ProfileEditTarget.PROFILE_WALLPAPER -> R.string.profile_edit_sheet_wallpaper
        }
        Text(
            text = stringResource(bannerRes),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
        HorizontalDivider()
        SheetRow(text = stringResource(R.string.profile_edit_sheet_album), onClick = onAlbum)
        SheetRow(text = stringResource(R.string.profile_edit_sheet_default), onClick = onDefault)
    }
}

@Composable
private fun SheetRow(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}
