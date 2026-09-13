package com.example.modumessenger.feature.friends

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.modumessenger.R
import com.example.modumessenger.core.model.Member
import com.example.modumessenger.core.ui.components.ProfileImage

/**
 * 친구 탭(부록 A §5). 상단바 액션(검색·친구 추가·친구 설정)은 `MainScreen` 이 그린다.
 */
@Composable
fun FriendsTab(
    onOpenProfile: (Long) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: FriendsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val names by viewModel.names.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 기존 앱과 같이 화면이 다시 보일 때마다 목록을 통째로 다시 읽는다.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }

    LaunchedEffect(viewModel) {
        viewModel.errors.collect { res ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(context.getString(res))
        }
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            MyProfileCard(
                me = uiState.me,
                onClick = { uiState.me?.let { onOpenProfile(it.id) } },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Text(
                text = stringResource(R.string.friends_count, uiState.totalCount.toInt()),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        itemsIndexed(uiState.friends, key = { _, member -> member.id }) { index, member ->
            LaunchedEffect(index, uiState.friends.size) { viewModel.onItemAppeared(index) }
            FriendRow(
                member = member,
                names = names,
                onClick = { onOpenProfile(member.id) },
            )
        }
    }
}

@Composable
private fun MyProfileCard(me: Member?, onClick: () -> Unit) {
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
                    ?: stringResource(R.string.friends_name_placeholder),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = me?.statusMessage?.takeIf { it.isNotBlank() }?.let(::shortenStatusMessage)
                    ?: stringResource(R.string.friends_status_placeholder),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
