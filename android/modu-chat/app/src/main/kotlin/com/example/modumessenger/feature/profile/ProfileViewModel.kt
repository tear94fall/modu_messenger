package com.example.modumessenger.feature.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.modumessenger.R
import com.example.modumessenger.core.chat.RoomCreator
import com.example.modumessenger.core.model.Member
import com.example.modumessenger.core.network.ApiException
import com.example.modumessenger.core.session.FriendNames
import com.example.modumessenger.core.session.SessionStore
import com.example.modumessenger.core.util.DisplayName
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

/**
 * 프로필 화면 상태(부록 A §13). 버튼 노출 규칙을 전부 여기서 계산해 화면은 그리기만 한다.
 */
data class ProfileUiState(
    val member: Member? = null,
    /** 지금 보고 있는 사람이 나인가. */
    val isMe: Boolean = false,
    val isLoading: Boolean = true,
    /** 한 번도 못 불러왔다. 부분 실패(새로고침 실패)는 스낵바로만 알린다. */
    val failed: Boolean = false,
    val renameDialogVisible: Boolean = false,
    val renameInput: String = "",
    val startingChat: Boolean = false,
) {

    /** `프로필 편집` 은 내 프로필에서만 보인다. */
    val showEditButton: Boolean get() = isMe

    /** `이름 변경` 은 남의 프로필에서만 보인다. */
    val showRenameButton: Boolean get() = !isMe

    /** 기록 아이콘은 쌓인 기록이 있을 때만 보인다. */
    val showHistoryButton: Boolean get() = member?.profiles?.isNotEmpty() == true

    /** `채팅 하기` 버튼은 항상 보이고 문구만 바뀐다. */
    val startChatTextRes: Int
        get() = if (isMe) R.string.profile_start_chat_self else R.string.profile_start_chat_friend

    /** 빈 이름으로는 저장할 수 없다. */
    val canSaveRename: Boolean get() = renameInput.trim().isNotEmpty()
}

/** 화면 밖으로 나가는 일(방 열기)은 상태가 아니라 사건으로 흘린다. */
sealed interface ProfileEvent {
    data class OpenRoom(val roomId: String) : ProfileEvent
}

/**
 * 프로필 보기(부록 A §13). `profile/{memberId}` 의 `memberId` 를 [SavedStateHandle] 로 받는다.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val memberRepository: MemberRepository,
    private val sessionStore: SessionStore,
    private val friendNames: FriendNames,
    private val roomCreator: RoomCreator,
) : ViewModel() {

    val memberId: Long = savedStateHandle.get<Long>(Routes.ARG_MEMBER_ID) ?: 0L

    val names: StateFlow<Map<String, String>> = friendNames.names

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<ProfileMessage>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages: SharedFlow<ProfileMessage> = _messages.asSharedFlow()

    private val _events = MutableSharedFlow<ProfileEvent>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<ProfileEvent> = _events.asSharedFlow()

    private var myMember: Member? = null

    init {
        load()
    }

    /** 기존 앱과 같이 화면이 다시 보일 때마다 서버에서 다시 읽는다. */
    fun onResume() = load()

    private fun load() {
        viewModelScope.launch {
            val me = sessionStore.memberNow()
            myMember = me
            val isMe = me != null && me.id == memberId
            _uiState.update { it.copy(isMe = isMe, isLoading = it.member == null) }

            memberRepository.getMember(memberId)
                .onSuccess { member ->
                    _uiState.update {
                        it.copy(member = member, isMe = isMe, isLoading = false, failed = false)
                    }
                }
                .onFailure {
                    // 이미 보여 주고 있는 내용이 있으면 지우지 않는다.
                    val hasMember = _uiState.value.member != null
                    _uiState.update { it.copy(isLoading = false, failed = !hasMember) }
                    if (hasMember) _messages.tryEmit(ProfileMessage(R.string.profile_connect_failed))
                }
        }
    }

    fun showRenameDialog() {
        val member = _uiState.value.member ?: return
        val current = DisplayName.of(member.userId, member.username, friendNames.names.value)
        _uiState.update { it.copy(renameDialogVisible = true, renameInput = current) }
    }

    fun onRenameInputChange(value: String) {
        _uiState.update { it.copy(renameInput = value) }
    }

    fun dismissRenameDialog() {
        _uiState.update { it.copy(renameDialogVisible = false) }
    }

    /** 저장은 이름이 비어 있지 않을 때만 동작한다(버튼도 같은 규칙으로 비활성). */
    fun saveRename() {
        val state = _uiState.value
        if (!state.canSaveRename) return
        val name = state.renameInput.trim()
        _uiState.update { it.copy(renameDialogVisible = false) }

        viewModelScope.launch {
            // 별칭 캐시(FriendNames) 갱신은 리포지토리가 한다.
            memberRepository.renameFriend(memberId, name)
                .onSuccess { _messages.tryEmit(ProfileMessage(R.string.profile_rename_success)) }
                .onFailure { error -> _messages.tryEmit(renameError(error)) }
        }
    }

    /** 방 멤버는 나 + 상대. 나와의 채팅이면 나 혼자다(부록 A §13). */
    fun startChat() {
        if (_uiState.value.startingChat) return
        _uiState.update { it.copy(startingChat = true) }

        viewModelScope.launch {
            val myId = myMember?.id ?: sessionStore.memberNow()?.id
            if (myId == null) {
                _uiState.update { it.copy(startingChat = false) }
                _messages.tryEmit(ProfileMessage(R.string.profile_connect_failed))
                return@launch
            }
            val ids = if (myId == memberId) listOf(myId) else listOf(myId, memberId)
            roomCreator.createRoom(ids)
                .onSuccess { roomId ->
                    _uiState.update { it.copy(startingChat = false) }
                    _events.tryEmit(ProfileEvent.OpenRoom(roomId))
                }
                .onFailure {
                    _uiState.update { it.copy(startingChat = false) }
                    _messages.tryEmit(ProfileMessage(R.string.profile_connect_failed))
                }
        }
    }

    private fun renameError(error: Throwable): ProfileMessage = when (error) {
        is ApiException -> ProfileMessage(R.string.profile_rename_failed, error.code)
        else -> ProfileMessage(R.string.profile_connect_failed)
    }
}
