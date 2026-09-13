package com.example.modumessenger.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.modumessenger.R
import com.example.modumessenger.core.network.ApiConfig
import com.example.modumessenger.core.ui.components.ProfileImage

/**
 * 말풍선 한 줄(부록 A §8a). 왼쪽/오른쪽 구분과 묶음 위치에 따라 아바타·이름·시각이 나타났다 사라진다.
 */
@Composable
fun ChatBubbleRow(
    bubble: ChatBubble,
    onOpenProfile: (Long) -> Unit,
    onOpenImage: (Long) -> Unit,
    onResend: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (bubble.isMine) {
        RightBubble(bubble, onOpenImage, onResend, onDelete, modifier)
    } else {
        LeftBubble(bubble, onOpenProfile, onOpenImage, modifier)
    }
}

@Composable
private fun LeftBubble(
    bubble: ChatBubble,
    onOpenProfile: (Long) -> Unit,
    onOpenImage: (Long) -> Unit,
    modifier: Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (bubble.showSender) {
            val memberId = bubble.senderMemberId
            ProfileImage(
                fileName = bubble.senderImage,
                size = AVATAR_SIZE,
                modifier = if (memberId != null) {
                    Modifier.clickable { onOpenProfile(memberId) }
                } else {
                    Modifier
                },
            )
        } else {
            Spacer(modifier = Modifier.size(AVATAR_SIZE))
        }

        Column(horizontalAlignment = Alignment.Start) {
            if (bubble.showSender) {
                Text(
                    text = bubble.senderName ?: stringResource(R.string.chat_unknown_sender),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                BubbleBody(bubble = bubble, mine = false, onOpenImage = onOpenImage)
                MetaColumn(bubble = bubble, alignment = Alignment.Start)
            }
        }
    }
}

@Composable
private fun RightBubble(
    bubble: ChatBubble,
    onOpenImage: (Long) -> Unit,
    onResend: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (bubble.isFailed) {
            FailedActions(
                onResend = { onResend(bubble.message.id) },
                onDelete = { onDelete(bubble.message.id) },
            )
        } else {
            MetaColumn(bubble = bubble, alignment = Alignment.End)
        }
        Spacer(modifier = Modifier.width(4.dp))
        BubbleBody(bubble = bubble, mine = true, onOpenImage = onOpenImage)
    }
}

/** 실패한 말풍선은 시각·미읽음 대신 재전송/삭제와 `!` 를 보여 준다(부록 A §8a). */
@Composable
private fun FailedActions(onResend: () -> Unit, onDelete: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.chat_failed_mark),
            color = colorResource(R.color.red),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        IconButton(onClick = onResend, modifier = Modifier.size(ACTION_SIZE)) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = stringResource(R.string.chat_resend),
                tint = colorResource(R.color.red),
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(ACTION_SIZE)) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(R.string.chat_delete),
                tint = colorResource(R.color.red),
            )
        }
    }
}

/** 시각과 미읽음 수. 미읽음은 0 이하면 감춘다. 실패 말풍선에는 둘 다 그리지 않는다. */
@Composable
private fun MetaColumn(bubble: ChatBubble, alignment: Alignment.Horizontal) {
    if (bubble.isFailed) return
    val unread = bubble.message.unreadCount
    if (unread <= 0 && !bubble.showTime) return

    Column(horizontalAlignment = alignment) {
        if (unread > 0) {
            Text(
                text = unread.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = colorResource(R.color.unread_marker),
            )
        }
        if (bubble.showTime) {
            Text(
                text = bubble.shortTime,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 본문. 이미지면 200dp 폭 사진, 아니면 글자다(파일·음성은 저장 파일 이름이 본문이다). */
@Composable
private fun BubbleBody(bubble: ChatBubble, mine: Boolean, onOpenImage: (Long) -> Unit) {
    if (ChatViewModel.isImage(bubble.message)) {
        // 세로로 긴 사진(스크린샷 등)이 화면을 다 덮지 않게 높이를 제한한다. 전체는 눌러서 뷰어로 본다.
        ChatImage(
            fileName = bubble.message.message,
            modifier = Modifier
                .width(IMAGE_WIDTH)
                .heightIn(min = IMAGE_MIN_HEIGHT, max = IMAGE_MAX_HEIGHT)
                .clip(RoundedCornerShape(BUBBLE_RADIUS))
                .clickable { onOpenImage(bubble.message.id) },
        )
        return
    }

    // 기존 앱 말풍선의 maxWidth="250sp" 와 같게 글자 크기 배율을 따라 넓어진다.
    val maxBubbleWidth = with(LocalDensity.current) { TEXT_MAX_WIDTH_SP.toDp() }
    Text(
        text = bubble.message.message,
        style = MaterialTheme.typography.bodyMedium,
        color = if (mine) colorResource(R.color.on_chat_bubble) else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .widthIn(max = maxBubbleWidth)
            .clip(RoundedCornerShape(BUBBLE_RADIUS))
            .background(
                if (mine) colorResource(R.color.chat_bubble) else MaterialTheme.colorScheme.surfaceVariant,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/** 채팅 사진. [ProfileImage] 와 같은 주소 규칙을 쓰되 동그랗게 자르지 않는다. */
@Composable
fun ChatImage(fileName: String, modifier: Modifier = Modifier) {
    val placeholder = painterResource(R.drawable.basic_profile_image)
    AsyncImage(
        model = fileName.takeIf { it.isNotBlank() }?.let { ApiConfig.imageUrl(it) },
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Crop,
        placeholder = placeholder,
        error = placeholder,
        fallback = placeholder,
    )
}

private val AVATAR_SIZE = 40.dp
private val IMAGE_WIDTH = 200.dp
private val IMAGE_MIN_HEIGHT = 120.dp
private val IMAGE_MAX_HEIGHT = 260.dp
private val ACTION_SIZE = 32.dp
private val BUBBLE_RADIUS = 12.dp
private val TEXT_MAX_WIDTH_SP = 250.sp
