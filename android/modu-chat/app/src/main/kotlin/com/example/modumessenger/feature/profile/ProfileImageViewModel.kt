package com.example.modumessenger.feature.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.modumessenger.R
import com.example.modumessenger.core.model.Profile
import com.example.modumessenger.core.model.ProfileType
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

/** 페이저 한 장. [profileId] 가 null 이면 기록이 아니라 지금 쓰고 있는 사진이다. */
data class ProfileImageItem(val profileId: Long?, val fileName: String)

data class ProfileImageUiState(
    val images: List<ProfileImageItem> = emptyList(),
    /** 삭제 버튼은 내 사진일 때만 보인다(부록 A §15). */
    val canDelete: Boolean = false,
    val isLoading: Boolean = true,
)

/** 프로필 이미지 보기(부록 A §15). */
@HiltViewModel
class ProfileImageViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val memberRepository: MemberRepository,
    private val profileRepository: ProfileRepository,
    private val sessionStore: SessionStore,
    private val downloader: ProfileImageDownloader,
) : ViewModel() {

    val memberId: Long = savedStateHandle.get<Long>(Routes.ARG_MEMBER_ID) ?: 0L

    /** 라우트에 실려 온 `ProfileType` 이름. 모르는 값이면 프로필 사진으로 본다. */
    val type: ProfileType = ProfileType.entries
        .firstOrNull { it.name == savedStateHandle.get<String>(Routes.ARG_TYPE) }
        ?: ProfileType.PROFILE_IMAGE

    /** 한 장만 볼 때 쓰는 기록 id. 없으면 그 종류의 기록을 전부 본다. */
    private val profileId: Long? = savedStateHandle.get<String>(Routes.ARG_PROFILE_ID)?.toLongOrNull()

    private val _uiState = MutableStateFlow(ProfileImageUiState())
    val uiState: StateFlow<ProfileImageUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<ProfileMessage>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages: SharedFlow<ProfileMessage> = _messages.asSharedFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val myUserId = sessionStore.memberNow()?.userId
            val member = memberRepository.getMember(memberId).getOrNull()
            val canDelete = member != null && myUserId != null && member.userId == myUserId

            val profiles = if (profileId != null) {
                profileRepository.getProfile(memberId, profileId).getOrNull()?.let(::listOf)
                    ?: emptyList()
            } else {
                profileRepository.getProfiles(memberId).getOrElse { emptyList() }
                    .filter { it.type == type }
                    .sortedByDescending(Profile::updatedDate)
            }

            val images = profiles
                .filter { it.value.isNotBlank() }
                .map { ProfileImageItem(profileId = it.id, fileName = it.value) }
                .ifEmpty {
                    // 기록이 없으면 지금 쓰고 있는 사진 한 장을 보여 준다(부록 A §15).
                    val current = when (type) {
                        ProfileType.PROFILE_WALLPAPER -> member?.wallpaperImage
                        else -> member?.profileImage
                    }
                    current?.takeIf { it.isNotBlank() }
                        ?.let { listOf(ProfileImageItem(profileId = null, fileName = it)) }
                        ?: emptyList()
                }

            _uiState.update {
                it.copy(images = images, canDelete = canDelete, isLoading = false)
            }
        }
    }

    fun download(index: Int) {
        val item = _uiState.value.images.getOrNull(index) ?: return
        viewModelScope.launch {
            downloader.download(item.fileName)
                .onSuccess { _messages.tryEmit(ProfileMessage(R.string.profile_image_saved)) }
                .onFailure { _messages.tryEmit(ProfileMessage(R.string.profile_image_save_failed)) }
        }
    }

    fun delete(index: Int) {
        val item = _uiState.value.images.getOrNull(index) ?: return
        viewModelScope.launch {
            memberRepository.deleteProfileImage(item.fileName)
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(images = state.images.filterNot { it.fileName == item.fileName })
                    }
                }
                .onFailure { _messages.tryEmit(ProfileMessage(R.string.profile_connect_failed)) }
        }
    }
}
