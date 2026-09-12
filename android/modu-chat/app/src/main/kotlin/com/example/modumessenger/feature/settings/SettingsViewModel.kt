package com.example.modumessenger.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.modumessenger.core.model.Member
import com.example.modumessenger.core.session.SessionStore
import com.example.modumessenger.data.repository.MemberRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 설정 탭(부록 A §7). 내 카드만 서버에서 다시 읽는다. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val memberRepository: MemberRepository,
    private val sessionStore: SessionStore,
) : ViewModel() {

    private val _me = MutableStateFlow<Member?>(null)
    val me: StateFlow<Member?> = _me.asStateFlow()

    init {
        viewModelScope.launch { _me.value = sessionStore.memberNow() }
        refresh()
    }

    private var firstResumeHandled = false

    /** 화면이 다시 보였다. 처음 뜬 직후는 [init] 이 이미 읽었다. */
    fun onResume() {
        if (!firstResumeHandled) {
            firstResumeHandled = true
            return
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            memberRepository.getMe().onSuccess { _me.value = it }
        }
    }
}
