package com.example.modumessenger.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.modumessenger.core.model.Notice
import com.example.modumessenger.data.repository.NoticeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 화면에 바로 그릴 모양. 날짜는 `T` 앞, 작성자가 비면 `"관리자"`(부록 A §23). */
data class NoticeUi(
    val id: Long,
    val title: String,
    val content: String,
    val writer: String,
    val date: String,
)

data class NoticeUiState(
    val notices: List<NoticeUi> = emptyList(),
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
)

@HiltViewModel
class NoticeViewModel @Inject constructor(
    private val noticeRepository: NoticeRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NoticeUiState())
    val uiState: StateFlow<NoticeUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, hasError = false) }
        viewModelScope.launch {
            noticeRepository.getNotices()
                .onSuccess { notices ->
                    _uiState.update {
                        it.copy(notices = notices.map(::toUi), isLoading = false, hasError = false)
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(isLoading = false, hasError = true) }
                }
        }
    }

    private fun toUi(notice: Notice): NoticeUi = NoticeUi(
        id = notice.id,
        title = notice.title,
        content = notice.content,
        writer = notice.writer.ifBlank { Notice.DEFAULT_WRITER },
        date = notice.createdDate.substringBefore('T'),
    )
}
