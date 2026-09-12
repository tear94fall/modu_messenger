package com.example.modumessenger.feature.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.modumessenger.R
import com.example.modumessenger.data.repository.ChatRepository
import com.example.modumessenger.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 보여 줄 사진의 저장 파일 이름들(메시지 본문). */
data class ChatImageUiState(val fileNames: List<String> = emptyList())

/** 사진 보기(부록 A §17). 채팅 id 목록을 받아 본문(파일 이름)만 뽑아 페이저에 싣는다. */
@HiltViewModel
class ChatImageViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository,
) : ViewModel() {

    private val ids: List<String> =
        Routes.splitList(savedStateHandle.get<String>(Routes.ARG_IDS))

    private val _uiState = MutableStateFlow(ChatImageUiState())
    val uiState: StateFlow<ChatImageUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<ChatUiMessage>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages: SharedFlow<ChatUiMessage> = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            chatRepository.getChats(ids)
                .onSuccess { chats ->
                    _uiState.value = ChatImageUiState(chats.map { it.message }.filter { it.isNotBlank() })
                }
                .onFailure { _messages.tryEmit(ChatUiMessage(R.string.chat_images_load_failed)) }
        }
    }
}
