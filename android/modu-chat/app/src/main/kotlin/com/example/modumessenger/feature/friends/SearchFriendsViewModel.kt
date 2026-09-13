package com.example.modumessenger.feature.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.modumessenger.R
import com.example.modumessenger.core.model.Member
import com.example.modumessenger.core.session.FriendNames
import com.example.modumessenger.core.session.SessionStore
import com.example.modumessenger.core.util.DisplayName
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

data class SearchFriendsUiState(
    val query: String = "",
    val results: List<Member> = emptyList(),
    /** 내 계정 행에는 `친구 추가` 버튼을 숨긴다. */
    val myUserId: String = "",
    /** 이미 추가한 사람. 버튼을 숨긴다. */
    val addedMemberIds: Set<Long> = emptySet(),
    val isLoading: Boolean = false,
)

/** 친구 추가(부록 A §18). 이메일로 찾아 `친구 추가` 를 누르면 친구가 된다. */
@HiltViewModel
class SearchFriendsViewModel @Inject constructor(
    private val memberRepository: MemberRepository,
    private val sessionStore: SessionStore,
    private val friendNames: FriendNames,
) : ViewModel() {

    val names: StateFlow<Map<String, String>> = friendNames.names

    private val _uiState = MutableStateFlow(SearchFriendsUiState())
    val uiState: StateFlow<SearchFriendsUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<FriendsMessage>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages: SharedFlow<FriendsMessage> = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            sessionStore.memberNow()?.let { me -> _uiState.update { it.copy(myUserId = me.userId) } }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        // 기존 앱은 검색어를 지우면 빈 질의를 다시 제출해 결과를 비웠다.
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
                    _messages.tryEmit(FriendsMessage(R.string.friends_search_done, email))
                }
                .onFailure {
                    _uiState.update { it.copy(isLoading = false) }
                    _messages.tryEmit(FriendsMessage(R.string.friends_load_failed))
                }
        }
    }

    fun addFriend(member: Member) {
        viewModelScope.launch {
            memberRepository.addFriend(member.email)
                .onSuccess { friend ->
                    _uiState.update { it.copy(addedMemberIds = it.addedMemberIds + member.id) }
                    val shown = DisplayName.of(friend.userId, friend.username, friendNames.names.value)
                    _messages.tryEmit(FriendsMessage(R.string.add_friend_success, shown))
                }
                .onFailure { _messages.tryEmit(FriendsMessage(R.string.add_friend_failed)) }
        }
    }
}
