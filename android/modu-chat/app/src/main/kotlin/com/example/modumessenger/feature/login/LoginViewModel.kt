package com.example.modumessenger.feature.login

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.modumessenger.R
import com.example.modumessenger.core.network.ApiException
import com.example.modumessenger.data.repository.AuthRepository
import com.example.modumessenger.data.repository.OfflineLoginException
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

/**
 * 로그인 화면 상태. 문구는 리소스 id 로만 들고 다니고, 실제 한국어는 화면이 [R.string] 에서 읽는다
 * (부록 A §2 의 문구 그대로).
 */
sealed interface LoginUiState {

    /** 버튼을 보여 주고 기다린다. */
    data object Idle : LoginUiState

    /** 무음 로그인 중이거나 토큰을 교환하는 중. 버튼을 숨긴다. */
    data object Signing : LoginUiState

    /** 세션이 저장됐다. 화면은 `main` 으로 간다. */
    data object Success : LoginUiState

    /** 버튼을 다시 보여 주고 스낵바로 [messageRes] 를 띄운다. [code] 가 있으면 포맷 인자로 넣는다. */
    data class Error(@StringRes val messageRes: Int, val code: Int? = null) : LoginUiState
}

/** 한 번만 띄우는 안내 문구(기존 앱의 토스트 자리). */
data class LoginMessage(@StringRes val messageRes: Int, val code: Int? = null)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<LoginMessage>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages: SharedFlow<LoginMessage> = _messages.asSharedFlow()

    private val _signOutRequests = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /**
     * 로그인이 진짜 실패했다 → 구글에서도 로그아웃해야 다음 실행 때 같은 계정으로 조용히 다시 시도하지
     * 않는다(기존 앱의 `mGoogleSignInClient.signOut()`). 오프라인 모드는 실패가 아니므로 보내지 않는다.
     */
    val signOutRequests: SharedFlow<Unit> = _signOutRequests.asSharedFlow()

    /** 화면 진입 시 마지막 계정이 있어 무음 로그인을 시작했다. */
    fun onSilentSignInStarted() {
        _uiState.value = LoginUiState.Signing
        _messages.tryEmit(LoginMessage(R.string.login_signing_in))
    }

    /** 무음 로그인이 안 됐다. 조용히 버튼을 되돌린다(기존 앱과 같음). */
    fun onSilentSignInFailed() {
        _uiState.value = LoginUiState.Idle
    }

    /** 버튼을 눌렀다. */
    fun onSignInClicked() {
        _messages.tryEmit(LoginMessage(R.string.login_sign_up_start))
    }

    /** 구글 쪽에서 실패했다(사용자 취소 포함). */
    fun onGoogleSignInFailed(statusCode: Int?) {
        _uiState.value = LoginUiState.Error(R.string.login_google_failed, statusCode ?: 0)
    }

    /** 구글 계정을 받았다. id 토큰을 앱 토큰으로 바꾸고 회원 정보까지 저장한다. */
    fun onGoogleAccount(idToken: String?, email: String?) {
        if (idToken.isNullOrBlank() || email.isNullOrBlank()) {
            _uiState.value = LoginUiState.Error(R.string.login_no_id_token)
            return
        }
        _uiState.value = LoginUiState.Signing
        viewModelScope.launch {
            authRepository.loginWithGoogle(idToken, email)
                .onSuccess {
                    _messages.tryEmit(LoginMessage(R.string.login_success))
                    _uiState.value = LoginUiState.Success
                }
                .onFailure { error ->
                    // 토큰은 받았는데 회원 정보만 못 받았다 → 오프라인 모드로 메인까지 들어간다.
                    if (error is OfflineLoginException) {
                        _messages.tryEmit(LoginMessage(R.string.login_offline_mode))
                        _uiState.value = LoginUiState.Success
                        return@onFailure
                    }
                    _signOutRequests.tryEmit(Unit)
                    _uiState.value = when (error) {
                        // 서버가 답은 했는데 거절했다 → 코드를 그대로 보여 준다.
                        is ApiException -> LoginUiState.Error(R.string.login_failed, error.code)
                        // 연결 자체가 안 됐다.
                        else -> LoginUiState.Error(R.string.login_connection_unstable)
                    }
                }
        }
    }
}
