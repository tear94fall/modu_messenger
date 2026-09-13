package com.example.modumessenger.feature.chat

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.modumessenger.R
import com.example.modumessenger.core.model.ChatRoom
import com.example.modumessenger.core.model.Member
import com.example.modumessenger.core.session.FriendNames
import com.example.modumessenger.core.session.SessionStore
import com.example.modumessenger.core.util.ChatRoomNameUtil
import com.example.modumessenger.core.util.ChatTime
import com.example.modumessenger.data.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 방 목록 한 줄의 아바타. 방 사진이 있으면 하나, 없으면 참여자 사진을 최대 4개 격자로 그린다. */
sealed interface RoomAvatar {

    /** 방 사진 또는 참여자가 한 명일 때. 파일 이름이 비면 기본 이미지. */
    data class Single(val fileName: String) : RoomAvatar

    /** 나를 뺀 참여자가 둘 이상일 때. 최대 4개. */
    data class Grid(val fileNames: List<String>) : RoomAvatar
}

/**
 * 방 목록 한 줄(부록 A §6). [previewRes] 가 있으면 그 문구를, 없으면 [previewText] 를 그린다.
 * [unreadBadge] 가 null 이면 뱃지를 감춘다.
 */
data class ChatRoomRowUi(
    val roomId: String,
    val title: String,
    @StringRes val previewRes: Int?,
    val previewText: String,
    val time: String,
    val unreadBadge: String?,
    val avatar: RoomAvatar,
)

data class ChatRoomListUiState(
    val rooms: List<ChatRoomRowUi> = emptyList(),
)

/**
 * 채팅 탭(부록 A §6). 정렬은 `ChatRepository.rooms` 가 이미 마지막 대화 시각 내림차순으로 준다.
 * 친구 별칭이 바뀌면 [FriendNames.names] 가 바뀌므로 제목이 저절로 다시 그려진다.
 */
@HiltViewModel
class ChatRoomListViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val sessionStore: SessionStore,
    friendNames: FriendNames,
) : ViewModel() {

    private val me = MutableStateFlow<Member?>(null)

    val uiState: StateFlow<ChatRoomListUiState> =
        combine(chatRepository.rooms, friendNames.names, me) { rooms, names, member ->
            ChatRoomListUiState(rooms.map { room -> toRow(room, names, member) })
        }.stateIn(viewModelScope, SharingStarted.Eagerly, ChatRoomListUiState())

    init {
        viewModelScope.launch { me.value = sessionStore.memberNow() }
    }

    /** 화면이 다시 보일 때마다 방 목록을 새로 읽는다(기존 앱의 `onResume` 과 같다). */
    fun onResume() {
        viewModelScope.launch {
            me.value = sessionStore.memberNow()
            chatRepository.refreshRooms()
        }
    }

    private fun toRow(room: ChatRoom, names: Map<String, String>, member: Member?): ChatRoomRowUi {
        val myUserId = member?.userId.orEmpty()
        return ChatRoomRowUi(
            roomId = room.roomId,
            title = ChatRoomNameUtil.resolve(
                roomName = room.roomName,
                members = room.members,
                myUserId = myUserId,
                myUsername = member?.username,
                names = names,
                maxLength = TITLE_MAX_LENGTH,
            ),
            previewRes = previewResOf(room.lastChatMsg),
            previewText = room.lastChatMsg,
            time = lastTimeOf(room),
            unreadBadge = unreadBadgeOf(room.unreadCount),
            avatar = avatarOf(room, myUserId),
        )
    }

    companion object {

        /** 목록 제목은 25자에서 자른다(채팅방 제목은 자르지 않는다). */
        const val TITLE_MAX_LENGTH = 25

        /** 999 를 넘으면 더 세지 않는다. */
        const val BADGE_MAX = 999

        /** 격자 아바타는 최대 4개. */
        const val AVATAR_GRID_MAX = 4

        /** 본문이 이 값들이면 문구로 바꿔 보여 준다(기존 앱과 같은 문자열 비교). */
        fun previewResOf(lastChatMsg: String): Int? = when (lastChatMsg) {
            "image" -> R.string.chat_preview_image
            "file" -> R.string.chat_preview_file
            "audio" -> R.string.chat_preview_audio
            else -> null
        }

        /** 마지막 메시지나 시각이 비어 있으면 시각을 그리지 않는다. */
        fun lastTimeOf(room: ChatRoom): String =
            if (room.lastChatMsg.isEmpty() || room.lastChatTime.isEmpty()) {
                ""
            } else {
                ChatTime.shortTime(room.lastChatTime)
            }

        /** 0 이하면 뱃지를 감춘다(null). */
        fun unreadBadgeOf(count: Int): String? = when {
            count <= 0 -> null
            count > BADGE_MAX -> "$BADGE_MAX+"
            else -> count.toString()
        }

        /**
         * 방 사진이 있으면 그것 하나. 없으면 나를 뺀 참여자로 정한다 —
         * 0명이면 첫 참여자(=나), 1명이면 그 사람, 2명 이상이면 최대 4개 격자.
         */
        fun avatarOf(room: ChatRoom, myUserId: String): RoomAvatar {
            if (ChatRoomNameUtil.hasCustomImage(room.roomImage)) return RoomAvatar.Single(room.roomImage)

            val others = room.members.filter { it.userId.isNotEmpty() && it.userId != myUserId }
            return when {
                others.isEmpty() -> RoomAvatar.Single(room.members.firstOrNull()?.profileImage.orEmpty())
                others.size == 1 -> RoomAvatar.Single(others.first().profileImage)
                else -> RoomAvatar.Grid(others.take(AVATAR_GRID_MAX).map { it.profileImage })
            }
        }
    }
}
