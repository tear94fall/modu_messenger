package com.example.modumessenger.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.modumessenger.R
import com.example.modumessenger.core.model.Profile
import com.example.modumessenger.core.model.ProfileType
import com.example.modumessenger.core.network.ApiConfig
import com.example.modumessenger.core.ui.components.ErrorBox
import com.example.modumessenger.core.ui.components.LoadingBox
import com.example.modumessenger.core.ui.components.ModuTopBar
import com.example.modumessenger.core.ui.components.ProfileImage
import com.example.modumessenger.core.ui.components.rememberComingSoon
import com.example.modumessenger.core.ui.theme.ModuGrey
import com.example.modumessenger.core.ui.theme.ProfileSurface

/** 상태메시지가 짧으면 크게 보여 준다(부록 A §16). */
private const val LONG_STATUS_LENGTH = 15

/** 프로필 기록(부록 A §16). */
@Composable
fun ProfileHistoryScreen(
    onBack: () -> Unit,
    onOpenProfile: (Long) -> Unit,
    onOpenImage: (Long, String, Long) -> Unit,
    viewModel: ProfileHistoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val comingSoon = rememberComingSoon(snackbarHostState)
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                context.getString(message.res, *message.args.toTypedArray()),
            )
        }
    }

    Scaffold(
        containerColor = ProfileSurface,
        topBar = { ModuTopBar(title = uiState.title, onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading && uiState.member == null -> LoadingBox()
                uiState.failed -> ErrorBox(
                    message = stringResource(R.string.profile_connect_failed),
                    onRetry = viewModel::load,
                )

                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.profiles, key = { it.id }) { profile ->
                        HistoryRow(
                            profile = profile,
                            username = uiState.member?.username.orEmpty(),
                            profileImage = uiState.member?.profileImage,
                            showMenu = uiState.showMenu,
                            onAvatarClick = { onOpenProfile(viewModel.memberId) },
                            onImageClick = {
                                onOpenImage(viewModel.memberId, profile.type.name, profile.id)
                            },
                            onComingSoon = comingSoon,
                            onDelete = { viewModel.deleteProfile(profile) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    profile: Profile,
    username: String,
    profileImage: String?,
    showMenu: Boolean,
    onAvatarClick: () -> Unit,
    onImageClick: () -> Unit,
    onComingSoon: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 30.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProfileImage(
                fileName = profileImage,
                size = 40.dp,
                modifier = Modifier.clickable(onClick = onAvatarClick),
            )
            Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                Text(
                    text = stringResource(titleResOf(profile.type), username),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = formatHistoryDate(profile),
                    style = MaterialTheme.typography.bodySmall,
                    color = ModuGrey,
                )
            }
            if (showMenu) {
                HistoryMenu(onComingSoon = onComingSoon, onDelete = onDelete)
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)

        if (profile.type == ProfileType.PROFILE_STATUS_MESSAGE) {
            Text(
                text = profile.value,
                fontSize = if (profile.value.length < LONG_STATUS_LENGTH) 25.sp else 20.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onImageClick),
            ) {
                if (profile.value.isNotBlank()) {
                    AsyncImage(
                        model = ApiConfig.imageUrl(profile.value),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryMenu(onComingSoon: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.profile_history_menu),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.profile_history_set_profile)) },
                onClick = {
                    expanded = false
                    onComingSoon()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.profile_history_save_profile)) },
                onClick = {
                    expanded = false
                    onComingSoon()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.profile_history_delete_profile)) },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

private fun titleResOf(type: ProfileType): Int = when (type) {
    ProfileType.PROFILE_IMAGE -> R.string.profile_history_image_name
    ProfileType.PROFILE_WALLPAPER -> R.string.profile_history_wallpaper_name
    ProfileType.PROFILE_STATUS_MESSAGE -> R.string.profile_history_status_name
}

/** `2024-05-01 12:00:00` → `2024년 05월 01일`. 모양이 다르면 원문을 그대로 보여 준다. */
@Composable
private fun formatHistoryDate(profile: Profile): String {
    val raw = profile.updatedDate.ifBlank { profile.createdDate }
    val parts = raw.take(10).split("-")
    return if (parts.size == 3 && parts.all { it.isNotBlank() }) {
        stringResource(R.string.profile_history_date, parts[0], parts[1], parts[2])
    } else {
        raw
    }
}
