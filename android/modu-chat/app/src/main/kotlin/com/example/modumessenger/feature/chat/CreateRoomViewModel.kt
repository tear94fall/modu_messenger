package com.example.modumessenger.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.modumessenger.R
import com.example.modumessenger.core.model.Member
import com.example.modumessenger.core.session.FriendNames
import com.example.modumessenger.core.session.SessionStore
import com.example.modumessenger.core.util.FriendsPager
import com.example.modumessenger.data.repository.ChatRepository
import com.example.modumessenger.data.repository.MemberRepository
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

data class CreateRoomUiState(
    val friends: List<Member> = emptyList(),
    /** 고른 친구들의 숫자 회원 id. 내 id 는 만들 때 서버로 보내며 덧붙인다. */
    val selected: Set<Long> = emptySet(),
    val isCreating: Boolean = false,
)

/**
 * 채팅방 만들기(부록 A §12). 서버는 같은 멤버 구성의 방이 있으면 그 방을 돌려주므로
 * 결과가 새 방일 수도, 이미 있던 방일 수도 있다.
 */
@HiltViewModel
class CreateRoomViewModel @Inject constructor(
    private val memberRepository: MemberRepository,
    private val chatRepository: ChatRepository,
    private val sessionStore: SessionStore,
    friendNames: FriendNames,
) : ViewModel() {

    val names: StateFlow<Map<String, String>> = friendNames.names

    private val _uiState = MutableStateFlow(CreateRoomUiState())
    val uiState: StateFlow<CreateRoomUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<ChatUiMessage>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages: SharedFlow<ChatUiMessage> = _messages.asSharedFlow()

    private val _openRoom = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 1)

    /** 만들어진(또는 이미 있던) 방으로 간다. */
    val openRoom: SharedFlow<String> = _openRoom.asSharedFlow()

    private val pager = FriendsPager()

    init {
        loadNextPage()
    }

    fun onItemAppeared(index: Int) {
        if (index >= _uiState.value.friends.size - FriendsPager.PREFETCH_THRESHOLD) loadNextPage()
    }

    fun toggle(memberId: Long) {
        _uiState.update { state ->
            val selected = state.selected.toMutableSet()
            if (!selected.add(memberId)) selected.remove(memberId)
            state.copy(selected = selected)
        }
    }

    fun loadNextPage() {
        if (!pager.canLoad()) return
        val generation = pager.generation
        val page = pager.beginLoad()
        viewModelScope.launch {
            memberRepository.getFriendsPage(page)
                .onSuccess { response ->
                    if (!pager.onLoaded(generation, response)) return@onSuccess
                    _uiState.update { it.copy(friends = it.friends + response.items) }
                }
                .onFailure {
                    pager.onFailed()
                    emit(R.string.chat_friends_load_failed)
                }
        }
    }

    fun create() {
        val state = _uiState.value
        if (state.isCreating) return
        if (state.selected.isEmpty()) {
            emit(R.string.create_room_empty)
            return
        }

        _uiState.update { it.copy(isCreating = true) }
        emit(R.string.create_room_started)

        viewModelScope.launch {
            val me = sessionStore.memberNow()
            val ids = state.selected.toList() + listOfNotNull(me?.id?.takeIf { it > 0L })
            chatRepository.createRoom(ids)
                .onSuccess { room ->
                    chatRepository.refreshRooms()
                    _openRoom.tryEmit(room.roomId)
                }
                .onFailure {
                    _uiState.update { it.copy(isCreating = false) }
                    emit(R.string.create_room_error)
                }
        }
    }

    private fun emit(res: Int) {
        _messages.tryEmit(ChatUiMessage(res))
    }
}
