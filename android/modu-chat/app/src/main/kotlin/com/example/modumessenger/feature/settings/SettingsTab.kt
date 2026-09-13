package com.example.modumessenger.feature.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.modumessenger.R
import com.example.modumessenger.core.model.Member
import com.example.modumessenger.core.ui.components.ProfileImage
import com.example.modumessenger.core.ui.components.rememberComingSoon
import com.example.modumessenger.core.ui.components.rememberSnackbarThen

/** 설정 탭 격자 한 칸. [onClick] 이 null 이면 `"준비 중입니다"`. */
private data class SettingGridItem(
    @DrawableRes val icon: Int,
    @StringRes val label: Int,
    val key: String,
)

@Composable
fun SettingsTab(
    onOpenProfile: (Long) -> Unit,
    onSetup: () -> Unit,
    onNotice: () -> Unit,
    onAppInfo: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val me by viewModel.me.collectAsStateWithLifecycle()
    val comingSoon = rememberComingSoon(snackbarHostState)
    // 기존 앱은 격자 한 칸을 누르면 그 이름을 토스트로 띄웠다.
    val announce = rememberSnackbarThen(snackbarHostState)

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }

    // 부록 A §7 의 8칸을 순서 그대로.
    val items = listOf(
        SettingGridItem(R.drawable.ic_baseline_info_24, R.string.setting_grid_app_info, "appInfo"),
        SettingGridItem(R.drawable.ic_baseline_celebration_24, R.string.setting_grid_notice, "notice"),
        SettingGridItem(R.drawable.ic_baseline_cloud_download_24, R.string.setting_grid_backup, "backup"),
        SettingGridItem(R.drawable.ic_baseline_settings_24_grey, R.string.setting_grid_setup, "setup"),
        SettingGridItem(R.drawable.ic_baseline_color_lens_24, R.string.setting_grid_theme, "theme"),
        SettingGridItem(R.drawable.ic_baseline_person_24, R.string.setting_grid_favorite, "favorite"),
        SettingGridItem(R.drawable.ic_baseline_person_outline_24, R.string.setting_grid_hidden, "hidden"),
        SettingGridItem(R.drawable.ic_baseline_person_off_24, R.string.setting_grid_blocked, "blocked"),
    )

    Column(modifier = modifier.fillMaxSize()) {
        MySettingCard(me = me, onClick = { me?.let { onOpenProfile(it.id) } })
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
        ) {
            items(items, key = { it.key }) { item ->
                val label = stringResource(item.label)
                SettingGridCell(item = item) {
                    announce(label) {
                        when (item.key) {
                            "appInfo" -> onAppInfo()
                            "notice" -> onNotice()
                            "setup" -> onSetup()
                            else -> comingSoon()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MySettingCard(me: Member?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ProfileImage(fileName = me?.profileImage, size = 56.dp)
        Column {
            Text(
                text = me?.username?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.setting_name_placeholder),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = me?.email?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.setting_email_placeholder),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SettingGridCell(item: SettingGridItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painter = painterResource(item.icon),
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(item.label),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
        )
    }
}
