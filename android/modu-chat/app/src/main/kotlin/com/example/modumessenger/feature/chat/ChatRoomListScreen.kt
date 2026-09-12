package com.example.modumessenger.feature.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.modumessenger.R
import com.example.modumessenger.core.ui.components.ProfileImage
import com.example.modumessenger.core.ui.components.rememberComingSoon

/**
 * 채팅 탭 본문(부록 A §6).
 *
 * 상단바는 `MainScreen` 이 그리지만 그 액션(검색·설정)은 친구 탭 것만 있다 — 그 파일은 T3 소유라
 * 손대지 않고, 채팅 탭 전용 액션을 목록 위 얇은 줄에 둔다. 둘 다 기존 앱과 같이 "준비 중" 이다.
 */
@Composable
fun ChatRoomListScreen(
    onOpenRoom: (String) -> Unit,
    onCreateRoom: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatRoomListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val comingSoon = rememberComingSoon(snackbarHostState)

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }

    // 바깥 MainScreen 의 Scaffold 가 시스템 바 인셋을 이미 처리한다. 안쪽 Scaffold 가 다시 넣으면
    // 위·아래에 상태바/내비게이션 바 높이만큼 빈 공간이 생긴다(targetSdk 35 는 edge-to-edge 강제).
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0.dp),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            // 기존 앱의 FAB 처럼 동그랗게
            FloatingActionButton(onClick = onCreateRoom, shape = CircleShape) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.chat_list_create_room))
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(uiState.rooms, key = { it.roomId }) { room ->
                    ChatRoomRow(
                        room = room,
                        onClick = { onOpenRoom(room.roomId) },
                        onLongClick = comingSoon,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatRoomRow(
    room: ChatRoomRowUi,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RoomAvatarImage(room.avatar)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = room.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val preview = room.previewRes?.let { stringResource(it) } ?: room.previewText
            if (preview.isNotEmpty()) {
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (room.time.isNotEmpty()) {
                Text(
                    text = room.time,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            room.unreadBadge?.let { Badge { Text(it) } }
        }
    }
}

/** 방 아바타. 격자는 52dp 안에 24dp 짜리 네 칸(부록 A §6). */
@Composable
private fun RoomAvatarImage(avatar: RoomAvatar) {
    when (avatar) {
        is RoomAvatar.Single -> ProfileImage(fileName = avatar.fileName, size = AVATAR_SIZE)

        is RoomAvatar.Grid -> Box(modifier = Modifier.size(AVATAR_SIZE)) {
            Column(verticalArrangement = Arrangement.spacedBy(GRID_GAP)) {
                avatar.fileNames.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(GRID_GAP)) {
                        row.forEach { fileName ->
                            ProfileImage(
                                fileName = fileName,
                                size = GRID_CELL,
                                modifier = Modifier.clip(CircleShape),
                            )
                        }
                        if (row.size == 1) Spacer(modifier = Modifier.size(GRID_CELL))
                    }
                }
                if (avatar.fileNames.size <= 2) Spacer(modifier = Modifier.height(GRID_CELL))
            }
        }
    }
}

private val AVATAR_SIZE = 52.dp
private val GRID_CELL = 24.dp
private val GRID_GAP = 4.dp
