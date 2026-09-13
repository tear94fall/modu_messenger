package com.example.modumessenger.feature.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.modumessenger.R
import com.example.modumessenger.core.model.Member
import com.example.modumessenger.core.session.FriendNames
import com.example.modumessenger.core.util.DisplayName
import com.example.modumessenger.core.util.FriendsPager
import com.example.modumessenger.data.repository.ChatRepository
import com.example.modumessenger.data.repository.MemberRepository
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

data class InviteUiState(
    val friends: List<Member> = emptyList(),
    /** 고른 사람들의 `userId`. */
    val selected: Set<String> = emptySet(),
    val isInviting: Boolean = false,
)

/**
 * 친구 초대(부록 A §11). 이미 방에 있는 사람은 페이지마다 걸러내고,
 * 한 페이지가 통째로 걸러지면 다음 페이지를 곧바로 당긴다(빈 화면이 페이징을 막지 않게).
 */
@HiltViewModel
class InviteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val memberRepository: MemberRepository,
    private val chatRepository: ChatRepository,
    friendNames: FriendNames,
) : ViewModel() {

    val roomId: String = savedStateHandle.get<String>(Routes.ARG_ROOM_ID).orEmpty()

    /** 이미 방에 있는 사람들의 `userId`. */
    private val currentMembers: Set<String> =
        Routes.splitList(savedStateHandle.get<String>(Routes.ARG_MEMBERS)).toSet()

    val names: StateFlow<Map<String, String>> = friendNames.names

    private val _uiState = MutableStateFlow(InviteUiState())
    val uiState: StateFlow<InviteUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<ChatUiMessage>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages: SharedFlow<ChatUiMessage> = _messages.asSharedFlow()

    private val _finished = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)

    /** 초대가 끝났다. 화면이 뒤로 간다. */
    val finished: SharedFlow<Unit> = _finished.asSharedFlow()

    private val pager = FriendsPager()

    init {
        loadNextPage()
    }

    fun onItemAppeared(index: Int) {
        if (index >= _uiState.value.friends.size - FriendsPager.PREFETCH_THRESHOLD) loadNextPage()
    }

    fun toggle(userId: String) {
        _uiState.update { state ->
            val selected = state.selected.toMutableSet()
            if (!selected.add(userId)) selected.remove(userId)
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
                    val filtered = response.items.filter { it.userId !in currentMembers }
                    if (filtered.isNotEmpty()) {
                        _uiState.update { it.copy(friends = it.friends + filtered) }
                    } else if (!pager.isLast) {
                        // 한 페이지가 통째로 걸러졌다. 스크롤로는 다음 페이지를 부를 수 없으니 곧바로 당긴다.
                        loadNextPage()
                    }
                }
                .onFailure {
                    pager.onFailed()
                    emit(R.string.chat_friends_load_failed)
                }
        }
    }

    /**
     * 고른 친구들을 초대한다. 응답 방의 멤버에 없는 사람은 실패로 본다
     * (기존 앱은 조건이 뒤집혀 있었다 — 스펙대로 바로잡았다).
     */
    fun invite() {
        val state = _uiState.value
        if (state.isInviting) return
        val userIds = state.selected.toList()
        if (userIds.isEmpty()) {
            emit(R.string.invite_empty)
            return
        }

        _uiState.update { it.copy(isInviting = true) }
        emit(R.string.invite_started)

        viewModelScope.launch {
            chatRepository.invite(roomId, userIds)
                .onSuccess { room ->
                    val joined = room.members.map { it.userId }.toSet()
                    val names = names.value
                    userIds.filterNot { it in joined }.forEach { userId ->
                        val member = state.friends.firstOrNull { it.userId == userId }
                        val name = DisplayName.of(userId, member?.username, names).ifBlank { userId }
                        emit(R.string.invite_member_failed, name)
                    }
                    _finished.tryEmit(Unit)
                }
                .onFailure {
                    _uiState.update { it.copy(isInviting = false) }
                    emit(R.string.invite_failed)
                }
        }
    }

    private fun emit(res: Int, vararg args: String) {
        _messages.tryEmit(ChatUiMessage(res, args.toList()))
    }
}
