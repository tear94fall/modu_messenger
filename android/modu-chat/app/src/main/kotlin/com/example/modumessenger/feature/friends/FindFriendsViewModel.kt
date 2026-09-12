package com.example.modumessenger.feature.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.modumessenger.R
import com.example.modumessenger.core.chat.RoomCreator
import com.example.modumessenger.core.model.Member
import com.example.modumessenger.core.session.FriendNames
import com.example.modumessenger.core.session.SessionStore
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

data class FindFriendsUiState(
    val query: String = "",
    val results: List<Member> = emptyList(),
    val myUserId: String = "",
    val isLoading: Boolean = false,
)

/**
 * 친구 찾기(부록 A §19). 기존 앱은 검색 결과를 실패 분기에서만 그렸고 `채팅 하기` 도 빈 껍데기였다.
 * 여기서는 성공 응답을 그리고, `채팅 하기` 는 [RoomCreator] 로 방을 만들어 방으로 간다.
 */
@HiltViewModel
class FindFriendsViewModel @Inject constructor(
    private val memberRepository: MemberRepository,
    private val sessionStore: SessionStore,
    private val roomCreator: RoomCreator,
    friendNames: FriendNames,
) : ViewModel() {

    val names: StateFlow<Map<String, String>> = friendNames.names

    private val _uiState = MutableStateFlow(FindFriendsUiState())
    val uiState: StateFlow<FindFriendsUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<FriendsMessage>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages: SharedFlow<FriendsMessage> = _messages.asSharedFlow()

    /** 방이 준비되면 roomId 를 흘린다. 화면이 받아 `chat/{roomId}` 로 간다. */
    private val _openRoom = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val openRoom: SharedFlow<String> = _openRoom.asSharedFlow()

    init {
        viewModelScope.launch {
            sessionStore.memberNow()?.let { me -> _uiState.update { it.copy(myUserId = me.userId) } }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        if (query.isBlank()) _uiState.update { it.copy(results = emptyList()) }
    }

    fun search() {
        val email = _uiState.value.query.trim()
        if (email.isEmpty()) {
            _messages.tryEmit(FriendsMessage(R.string.friends_search_empty))
            _uiState.update { it.copy(results = emptyList()) }
            return
        }
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            memberRepository.searchByEmail(email)
                .onSuccess { members ->
                    _uiState.update { it.copy(results = members, isLoading = false) }
                    _messages.tryEmit(FriendsMessage(R.string.friends_find_done, email))
                }
                .onFailure {
                    _uiState.update { it.copy(isLoading = false) }
                    _messages.tryEmit(FriendsMessage(R.string.friends_load_failed))
                }
        }
    }

    /** 나와 상대만 있는 방. 같은 구성의 방이 이미 있으면 서버가 그 방을 돌려준다. */
    fun startChat(member: Member) {
        viewModelScope.launch {
            val me = sessionStore.memberNow()
            if (me == null) {
                _messages.tryEmit(FriendsMessage(R.string.create_room_failed))
                return@launch
            }
            roomCreator.createRoom(listOf(me.id, member.id))
                .onSuccess { roomId -> _openRoom.tryEmit(roomId) }
                .onFailure { _messages.tryEmit(FriendsMessage(R.string.create_room_failed)) }
        }
    }
}
