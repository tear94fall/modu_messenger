package com.example.modumessenger.feature.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.modumessenger.R
import com.example.modumessenger.core.model.Member
import com.example.modumessenger.core.model.ProfileType
import com.example.modumessenger.core.session.SessionStore
import com.example.modumessenger.data.dto.UpdateProfileDto
import com.example.modumessenger.data.repository.MemberRepository
import com.example.modumessenger.data.repository.ProfileRepository
import com.example.modumessenger.data.repository.StorageRepository
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

/** 바텀시트가 어느 사진을 바꾸는지(부록 A §14a). */
enum class ProfileEditTarget(val profileType: ProfileType) {
    PROFILE_IMAGE(ProfileType.PROFILE_IMAGE),
    PROFILE_WALLPAPER(ProfileType.PROFILE_WALLPAPER),
}

data class ProfileEditUiState(
    val member: Member? = null,
    val name: String = "",
    val statusMessage: String = "",
    /** null 이면 바텀시트가 닫혀 있다. */
    val sheetTarget: ProfileEditTarget? = null,
    val isBusy: Boolean = false,
)

sealed interface ProfileEditEvent {
    /** 저장이 끝났다. 화면은 뒤로 간다. */
    data object Saved : ProfileEditEvent

    /** 앨범을 열어 달라(화면이 `PickVisualMedia` 를 띄운다). */
    data class PickImage(val target: ProfileEditTarget) : ProfileEditEvent
}

/**
 * 프로필 편집(부록 A §14). 언제나 로그인한 사람 자신을 편집한다.
 */
@HiltViewModel
class ProfileEditViewModel @Inject constructor(
    private val memberRepository: MemberRepository,
    private val profileRepository: ProfileRepository,
    private val storageRepository: StorageRepository,
    private val sessionStore: SessionStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileEditUiState())
    val uiState: StateFlow<ProfileEditUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<ProfileMessage>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages: SharedFlow<ProfileMessage> = _messages.asSharedFlow()

    private val _events = MutableSharedFlow<ProfileEditEvent>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<ProfileEditEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            sessionStore.memberNow()?.let(::applyMember)
            refresh()
        }
    }

    /** 기존 앱과 같이 화면이 다시 보일 때마다 내 정보를 다시 읽는다. */
    fun onResume() {
        viewModelScope.launch { refresh() }
    }

    fun onNameChange(value: String) = _uiState.update { it.copy(name = value) }

    fun onStatusMessageChange(value: String) = _uiState.update { it.copy(statusMessage = value) }

    fun openSheet(target: ProfileEditTarget) = _uiState.update { it.copy(sheetTarget = target) }

    fun dismissSheet() = _uiState.update { it.copy(sheetTarget = null) }

    /** `앨범에서 사진 선택`. 화면이 사진 고르기를 띄운다. */
    fun onAlbumSelected() {
        val target = _uiState.value.sheetTarget ?: return
        _uiState.update { it.copy(sheetTarget = null) }
        _messages.tryEmit(ProfileMessage(R.string.profile_edit_to_gallery))
        _events.tryEmit(ProfileEditEvent.PickImage(target))
    }

    /** `기본 이미지로 변경`. 빈 값으로 바로 갱신한다. */
    fun onDefaultSelected() {
        val target = _uiState.value.sheetTarget ?: return
        _uiState.update { it.copy(sheetTarget = null) }
        _messages.tryEmit(ProfileMessage(R.string.profile_edit_to_default))
        updateImage(target, fileName = "", addHistory = false)
    }

    /** 앨범에서 고른 사진을 올리고 기록을 남긴 뒤 회원 정보를 갱신한다. */
    fun onImagePicked(target: ProfileEditTarget, uri: Uri?) {
        if (uri == null) return
        _uiState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            storageRepository.upload(uri)
                .onSuccess { fileName -> updateImage(target, fileName, addHistory = true) }
                .onFailure {
                    _uiState.update { state -> state.copy(isBusy = false) }
                    _messages.tryEmit(ProfileMessage(R.string.profile_connect_failed))
                }
        }
    }

    /**
     * 저장(부록 A §14). 이름·상태메시지가 그대로면 아무 일도 하지 않는다.
     * 상태메시지가 바뀌고 비어 있지 않으면 기록을 먼저 남기고 회원 정보를 갱신한다.
     */
    fun save() {
        val state = _uiState.value
        val member = state.member ?: return
        val username = state.name
        val statusMessage = state.statusMessage

        val nameChanged = member.username != username
        val statusChanged = member.statusMessage != statusMessage
        if (!nameChanged && !statusChanged) return

        _uiState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            if (statusChanged && statusMessage.isNotEmpty()) {
                profileRepository.addProfile(
                    memberId = member.id,
                    type = ProfileType.PROFILE_STATUS_MESSAGE,
                    value = statusMessage,
                )
            }
            val dto = UpdateProfileDto(
                username = username,
                statusMessage = statusMessage,
                profileImage = member.profileImage,
                wallpaperImage = member.wallpaperImage,
            )
            memberRepository.updateProfile(dto)
                .onSuccess { updated ->
                    applyMember(updated)
                    _uiState.update { it.copy(isBusy = false) }
                    _messages.tryEmit(ProfileMessage(R.string.profile_edit_success))
                    _events.tryEmit(ProfileEditEvent.Saved)
                }
                .onFailure {
                    _uiState.update { it.copy(isBusy = false) }
                    _messages.tryEmit(ProfileMessage(R.string.profile_connect_failed))
                }
        }
    }

    /** 사진 한 장만 바꾼다. 화면은 그대로 두고 미리보기만 갱신한다. */
    private fun updateImage(target: ProfileEditTarget, fileName: String, addHistory: Boolean) {
        val member = _uiState.value.member ?: return
        _uiState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            if (addHistory && fileName.isNotEmpty()) {
                profileRepository.addProfile(member.id, target.profileType, fileName)
            }
            val dto = UpdateProfileDto(
                username = _uiState.value.name,
                statusMessage = _uiState.value.statusMessage,
                profileImage = if (target == ProfileEditTarget.PROFILE_IMAGE) {
                    fileName
                } else {
                    member.profileImage
                },
                wallpaperImage = if (target == ProfileEditTarget.PROFILE_WALLPAPER) {
                    fileName
                } else {
                    member.wallpaperImage
                },
            )
            memberRepository.updateProfile(dto)
                .onSuccess { updated ->
                    applyMember(updated)
                    _uiState.update { it.copy(isBusy = false) }
                }
                .onFailure {
                    _uiState.update { it.copy(isBusy = false) }
                    _messages.tryEmit(ProfileMessage(R.string.profile_connect_failed))
                }
        }
    }

    private suspend fun refresh() {
        memberRepository.getMe().onSuccess(::applyMember)
    }

    /** 서버에서 새로 읽은 값은, 사용자가 아직 건드리지 않은 칸에만 반영한다. */
    private fun applyMember(member: Member) {
        _uiState.update { state ->
            val old = state.member
            state.copy(
                member = member,
                name = if (old == null || state.name == old.username) member.username else state.name,
                statusMessage = if (old == null || state.statusMessage == old.statusMessage) {
                    member.statusMessage
                } else {
                    state.statusMessage
                },
            )
        }
    }
}
