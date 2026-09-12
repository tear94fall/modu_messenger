package com.example.modumessenger.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.modumessenger.data.repository.CommonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 버전 정보(부록 A §24)의 "서버 버전" 줄. 못 가져오면 빈 문자열로 남는다
 * (기존 앱도 실패하면 값을 채우지 않았다).
 */
@HiltViewModel
class AppInfoViewModel @Inject constructor(
    private val commonRepository: CommonRepository,
) : ViewModel() {

    private val _serverVersion = MutableStateFlow("")
    val serverVersion: StateFlow<String> = _serverVersion.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            commonRepository.getServerVersion()
                .onSuccess { _serverVersion.value = it }
        }
    }
}
