package com.example.modumessenger.feature.chat

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.modumessenger.R
import com.example.modumessenger.core.model.ChatRoom
import com.example.modumessenger.core.model.Member
import com.example.modumessenger.data.dto.ChatRoomDto
import com.example.modumessenger.data.dto.MemberDto
import com.example.modumessenger.data.repository.ChatRepository
import com.example.modumessenger.data.repository.StorageRepository
import com.example.modumessenger.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatRoomEditUiState(
    val room: ChatRoom? = null,
    val name: String = "",
    /** null 이면 사진을 손대지 않은 것이다. `""` 는 "기본 이미지로" 라는 뜻. */
    val pendingRoomImage: String? = null,
    val isSaving: Boolean = false,
) {
    /** 화면에 그릴 사진 이름. 아직 손대지 않았으면 방의 것. */
    val shownImage: String get() = pendingRoomImage ?: room?.roomImage.orEmpty()
}

/**
 * 채팅방 설정(부록 A §10). 이름과 사진 중 하나라도 바뀌어야 저장한다.
 */
@HiltViewModel
class ChatRoomEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository,
    private val storageRepository: StorageRepository,
) : ViewModel() {

    val roomId: String = savedStateHandle.get<String>(Routes.ARG_ROOM_ID).orEmpty()

    private val _uiState = MutableStateFlow(ChatRoomEditUiState())
    val uiState: StateFlow<ChatRoomEditUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<ChatUiMessage>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages: SharedFlow<ChatUiMessage> = _messages.asSharedFlow()

    private val _saved = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)

    /** 저장이 끝났다. 화면이 뒤로 간다. */
    val saved: SharedFlow<Unit> = _saved.asSharedFlow()

    init {
        viewModelScope.launch {
            chatRepository.getRoom(roomId)
                .onSuccess { room ->
                    _uiState.update { it.copy(room = room, name = room.roomName) }
                }
                .onFailure { emit(R.string.chat_room_edit_load_failed) }
        }
    }

    fun onNameChange(value: String) {
        _uiState.update { it.copy(name = value) }
    }

    /** 갤러리에서 고른 사진을 올려 두고 저장을 기다린다. */
    fun onImagePicked(uri: Uri) {
        viewModelScope.launch {
            storageRepository.upload(uri)
                .onSuccess { fileName ->
                    _uiState.update { it.copy(pendingRoomImage = fileName) }
                    emit(R.string.chat_room_edit_image_picked)
                }
                .onFailure { emit(R.string.chat_room_edit_upload_failed) }
        }
    }

    /** 기본 이미지로. 저장을 눌러야 서버에 반영된다. */
    fun onDefaultImage() {
        _uiState.update { it.copy(pendingRoomImage = "") }
        emit(R.string.chat_room_edit_image_default)
    }

    fun save() {
        val state = _uiState.value
        val room = state.room
        if (room == null) {
            emit(R.string.chat_room_edit_not_loaded)
            return
        }
        if (state.isSaving) return

        val newName = state.name.trim()
        val nameChanged = newName != room.roomName
        val pending = state.pendingRoomImage
        val imageChanged = pending != null && pending != room.roomImage

        if (!nameChanged && !imageChanged) {
            emit(R.string.chat_room_edit_no_change)
            return
        }

        val updated = room.copy(
            roomName = if (nameChanged) newName else room.roomName,
            roomImage = if (imageChanged) pending!! else room.roomImage,
        )

        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            chatRepository.updateRoom(roomId, updated.toDto())
                .onSuccess {
                    emit(R.string.chat_room_edit_save_success)
                    _saved.tryEmit(Unit)
                }
                .onFailure {
                    _uiState.update { it.copy(isSaving = false) }
                    emit(R.string.chat_room_edit_save_failed)
                }
        }
    }

    private fun emit(res: Int) {
        _messages.tryEmit(ChatUiMessage(res))
    }
}

/** 기존 앱과 같이 방 정보를 통째로 보낸다(참여자 목록 포함). */
private fun ChatRoom.toDto(): ChatRoomDto = ChatRoomDto(
    roomId = roomId,
    roomName = roomName,
    roomImage = roomImage,
    lastChatMsg = lastChatMsg,
    lastChatId = lastChatId,
    lastChatTime = lastChatTime,
    members = members.map { it.toDto() },
)

private fun Member.toDto(): MemberDto = MemberDto(
    id = id,
    userId = userId,
    email = email,
    role = if (role.name == "ADMIN") "ROLE_ADMIN" else "ROLE_USER",
    username = username,
    statusMessage = statusMessage,
    profileImage = profileImage,
    wallpaperImage = wallpaperImage,
)
