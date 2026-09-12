package com.example.modumessenger.feature.friends

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.modumessenger.R
import com.example.modumessenger.core.model.Member
import com.example.modumessenger.core.session.FriendNames
import com.example.modumessenger.core.session.SessionStore
import com.example.modumessenger.core.util.FriendsPager
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

data class FriendsUiState(
    val me: Member? = null,
    val friends: List<Member> = emptyList(),
    /** 서버가 알려 준 전체 친구 수. `"친구 N 명"` 에 쓴다. */
    val totalCount: Long = 0L,
    val isLoading: Boolean = false,
)

/**
 * 친구 탭(부록 A §5). 페이지 크기 50, 끝에서 5개 남으면 다음 페이지를 미리 당긴다.
 * 화면이 다시 보일 때마다 [refresh] 로 목록을 통째로 다시 읽는다(기존 앱의 `onResume` 과 같다).
 */
@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val memberRepository: MemberRepository,
    private val sessionStore: SessionStore,
    friendNames: FriendNames,
) : ViewModel() {

    val names: StateFlow<Map<String, String>> = friendNames.names

    private val _uiState = MutableStateFlow(FriendsUiState())
    val uiState: StateFlow<FriendsUiState> = _uiState.asStateFlow()

    private val _errors = MutableSharedFlow<Int>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** 스낵바로 띄울 문구의 리소스 id. */
    val errors: SharedFlow<Int> = _errors.asSharedFlow()

    private val pager = FriendsPager()

    private var firstResumeHandled = false

    init {
        viewModelScope.launch {
            // 서버 응답을 기다리는 동안 저장해 둔 내 정보로 카드를 먼저 채운다.
            sessionStore.memberNow()?.let { me -> _uiState.update { it.copy(me = me) } }
        }
        refresh()
    }

    /**
     * 화면이 다시 보였다. 처음 뜬 직후의 `ON_RESUME` 은 [init] 이 이미 읽었으므로 건너뛴다.
     */
    fun onResume() {
        if (!firstResumeHandled) {
            firstResumeHandled = true
            return
        }
        refresh()
    }

    /** 목록을 처음부터 다시 읽는다. [FriendsPager.reset] 이 세대를 올려 늦게 온 옛 응답을 버린다. */
    fun refresh() {
        pager.reset()
        _uiState.update { it.copy(friends = emptyList(), totalCount = 0L, isLoading = false) }
        loadMe()
        loadNextPage()
    }

    /** 목록에서 [index] 번째가 보였다. 끝에 가까우면 다음 페이지를 당긴다. */
    fun onItemAppeared(index: Int) {
        val loaded = _uiState.value.friends.size
        if (index >= loaded - FriendsPager.PREFETCH_THRESHOLD) loadNextPage()
    }

    fun loadNextPage() {
        if (!pager.canLoad()) return
        val generation = pager.generation
        val page = pager.beginLoad()
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            memberRepository.getFriendsPage(page)
                .onSuccess { response ->
                    // 세대가 다르면 초기화 이후 도착한 옛 응답이므로 목록에 섞지 않는다.
                    if (pager.onLoaded(generation, response)) {
                        _uiState.update {
                            it.copy(
                                friends = it.friends + response.items,
                                totalCount = pager.totalElements,
                                isLoading = false,
                            )
                        }
                    }
                }
                .onFailure {
                    pager.onFailed()
                    _uiState.update { it.copy(isLoading = false) }
                    emitError(R.string.friends_load_failed)
                }
        }
    }

    private fun loadMe() {
        viewModelScope.launch {
            memberRepository.getMe().onSuccess { me -> _uiState.update { it.copy(me = me) } }
        }
    }

    private fun emitError(@StringRes res: Int) {
        _errors.tryEmit(res)
    }
}
