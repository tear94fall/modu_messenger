package com.example.modumessenger.feature.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.modumessenger.R
import com.example.modumessenger.core.model.Member
import com.example.modumessenger.core.model.Profile
import com.example.modumessenger.core.session.SessionStore
import com.example.modumessenger.data.repository.MemberRepository
import com.example.modumessenger.data.repository.ProfileRepository
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

data class ProfileHistoryUiState(
    val member: Member? = null,
    /** 최신 기록이 위로 온다. */
    val profiles: List<Profile> = emptyList(),
    /** ⋮ 메뉴는 내 기록에만 보인다(부록 A §16). */
    val showMenu: Boolean = false,
    val isLoading: Boolean = true,
    val failed: Boolean = false,
) {
    /** 제목은 별칭이 아니라 서버가 준 이름 그대로다(부록 A §16). */
    val title: String get() = member?.username.orEmpty()
}

/** 프로필 기록(부록 A §16). */
@HiltViewModel
class ProfileHistoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val memberRepository: MemberRepository,
    private val profileRepository: ProfileRepository,
    private val sessionStore: SessionStore,
) : ViewModel() {

    val memberId: Long = savedStateHandle.get<Long>(Routes.ARG_MEMBER_ID) ?: 0L

    private val _uiState = MutableStateFlow(ProfileHistoryUiState())
    val uiState: StateFlow<ProfileHistoryUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<ProfileMessage>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages: SharedFlow<ProfileMessage> = _messages.asSharedFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val isMe = sessionStore.memberNow()?.id == memberId
            memberRepository.getMember(memberId)
                .onSuccess { member ->
                    _uiState.update {
                        it.copy(
                            member = member,
                            profiles = member.profiles.sortedByDescending { profile ->
                                profile.updatedDate
                            },
                            showMenu = isMe,
                            isLoading = false,
                            failed = false,
                        )
                    }
                }
                .onFailure {
                    val hasMember = _uiState.value.member != null
                    _uiState.update { it.copy(isLoading = false, failed = !hasMember) }
                    if (hasMember) _messages.tryEmit(ProfileMessage(R.string.profile_connect_failed))
                }
        }
    }

    /** 기록 삭제. 지워진 줄은 목록에서 바로 뺀다. */
    fun deleteProfile(profile: Profile) {
        viewModelScope.launch {
            profileRepository.deleteProfile(memberId, profile.id)
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(profiles = state.profiles.filterNot { it.id == profile.id })
                    }
                    _messages.tryEmit(ProfileMessage(R.string.profile_history_delete_done))
                }
                .onFailure { _messages.tryEmit(ProfileMessage(R.string.profile_connect_failed)) }
        }
    }
}
