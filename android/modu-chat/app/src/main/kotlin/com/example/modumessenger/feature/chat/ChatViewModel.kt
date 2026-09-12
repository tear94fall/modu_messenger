package com.example.modumessenger.feature.chat

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.modumessenger.R
import com.example.modumessenger.core.di.ApplicationScope
import com.example.modumessenger.core.model.ChatMessage
import com.example.modumessenger.core.model.ChatRoom
import com.example.modumessenger.core.model.ChatType
import com.example.modumessenger.core.model.Member
import com.example.modumessenger.core.model.SendStatus
import com.example.modumessenger.core.session.FriendNames
import com.example.modumessenger.core.session.SessionStore
import com.example.modumessenger.core.util.ChatRoomNameUtil
import com.example.modumessenger.core.util.ChatTime
import com.example.modumessenger.core.util.DisplayName
import com.example.modumessenger.data.repository.ChatRepository
import com.example.modumessenger.data.repository.StorageRepository
import com.example.modumessenger.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
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

/** 말풍선 묶음 위치(부록 A §8a). 아바타·이름은 HEADER/SINGLE 에만, 시각은 TAIL/SINGLE 에만 그린다. */
enum class BubbleGroup { SINGLE, HEADER, BODY, TAIL }

/**
 * 말풍선 하나를 그릴 재료. [senderMemberId] 가 null 이면 발신자를 모르는 것이라 프로필로 갈 수 없고,
 * [senderName] 도 null 이라 화면이 `알 수 없음` 을 그린다(부록 A §0.3).
 */
data class ChatBubble(
    val message: ChatMessage,
    val isMine: Boolean,
    val group: BubbleGroup,
    val senderName: String?,
    val senderImage: String,
    val senderMemberId: Long?,
    val shortTime: String,
) {
    val showSender: Boolean get() = group == BubbleGroup.HEADER || group == BubbleGroup.SINGLE
    val showTime: Boolean get() = group == BubbleGroup.TAIL || group == BubbleGroup.SINGLE
    val isFailed: Boolean get() = message.status == SendStatus.FAILED
}

data class ChatUiState(
    val roomId: String = "",
    val title: String = "",
    val room: ChatRoom? = null,
    val bubbles: List<ChatBubble> = emptyList(),
    val input: String = "",
    /** 드로어의 최근 사진. 행 클릭은 이 전체를, 격자는 앞 3개를 쓴다. */
    val recentImages: List<ChatMessage> = emptyList(),
    /** 0 이면 "아래로" 배지를 감춘다. */
    val jumpToBottomCount: Int = 0,
    val isLeaving: Boolean = false,
) {
    val memberCount: Int get() = room?.members?.size ?: 0
    val memberUserIds: List<String> get() = room?.members?.map { it.userId }.orEmpty()
}

/** 첨부 시트가 고른 것의 종류. 업로드한 파일 이름을 어떤 chatType 으로 보낼지 정한다. */
enum class AttachKind { IMAGE, FILE, AUDIO }

/**
 * 채팅방(부록 A §8). 들어올 때 [ChatRepository.openRoom] + `loadInitial` + `getRoom`,
 * 나갈 때 [onCleared] 에서 `closeRoom` 한다 — 화면이 사라진 뒤에도 끝나야 하므로 앱 스코프에서 돈다.
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository,
    private val storageRepository: StorageRepository,
    private val sessionStore: SessionStore,
    private val friendNames: FriendNames,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    val roomId: String = savedStateHandle.get<String>(Routes.ARG_ROOM_ID).orEmpty()

    /** 친구 별칭. 드로어 멤버 줄이 이름을 풀 때 쓴다. */
    val names: StateFlow<Map<String, String>> = friendNames.names

    private val _uiState = MutableStateFlow(ChatUiState(roomId = roomId))
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<ChatUiMessage>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** 스낵바로 띄울 문구. */
    val messages: SharedFlow<ChatUiMessage> = _messages.asSharedFlow()

    private val _leftRoom = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)

    /** 방을 나갔다. 화면이 뒤로 간다. */
    val leftRoom: SharedFlow<Unit> = _leftRoom.asSharedFlow()

    private var me: Member? = null

    /** 화면이 맨 아래에 붙어 있는지. "아래로" 배지 규칙의 입력이다. */
    private var atBottom: Boolean = true

    private var lastKnownId: Long? = null

    /** 첫 방출(빈 목록)을 지나기 전까지는 배지를 세지 않는다. */
    private var initialized = false

    private var loadingPrev = false

    init {
        viewModelScope.launch {
            me = sessionStore.memberNow()
            chatRepository.openRoom(roomId)
            chatRepository.loadInitial(roomId, PAGE_SIZE)
            loadRoom()
            loadRecentImages()
        }

        viewModelScope.launch {
            chatRepository.activeMessages.collect { onMessages(it) }
        }

        // 친구 별칭이 바뀌면 보낸 사람 이름과 방 제목을 다시 그린다.
        viewModelScope.launch {
            friendNames.names.collect { rebuild() }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // viewModelScope 는 이미 취소됐다. 읽음 처리는 끝까지 가야 하므로 앱 스코프에서 돈다.
        appScope.launch { chatRepository.closeRoom(roomId) }
    }

    // ---------- 방 정보 ----------

    /** 화면이 다시 보일 때마다. 방 이름·참여자·최근 사진을 다시 읽는다(기존 앱의 `onResume`). */
    fun onResume() {
        viewModelScope.launch {
            loadRoom()
            loadRecentImages()
        }
    }

    private suspend fun loadRoom() {
        chatRepository.getRoom(roomId)
            .onSuccess { room ->
                _uiState.update { it.copy(room = room) }
                rebuild()
            }
            .onFailure { emit(R.string.chat_room_edit_load_failed) }
    }

    private suspend fun loadRecentImages() {
        chatRepository.getRoomImages(roomId, PAGE_SIZE)
            .onSuccess { images -> _uiState.update { it.copy(recentImages = images) } }
    }

    // ---------- 메시지 ----------

    private fun onMessages(messages: List<ChatMessage>) {
        val last = messages.lastOrNull()
        if (last != null && last.id != lastKnownId) {
            val wasInitialized = initialized
            lastKnownId = last.id
            if (wasInitialized && shouldShowJumpToBottom(atBottom, last.sender, myUserId())) {
                _uiState.update { it.copy(jumpToBottomCount = it.jumpToBottomCount + 1) }
            }
        }
        initialized = true
        rebuild(messages)
    }

    private fun rebuild(messages: List<ChatMessage> = chatRepository.activeMessages.value) {
        val names = friendNames.names.value
        val room = _uiState.value.room
        _uiState.update { state ->
            state.copy(
                title = ChatRoomNameUtil.resolve(
                    roomName = room?.roomName,
                    members = room?.members,
                    myUserId = myUserId(),
                    myUsername = me?.username,
                    names = names,
                    maxLength = 0,
                ),
                bubbles = buildBubbles(messages, room?.members.orEmpty(), myUserId(), names),
            )
        }
    }

    fun onInputChange(value: String) {
        _uiState.update { it.copy(input = value) }
    }

    /** 빈 문자열은 무시한다. 보냈든 못 보냈든 입력칸은 항상 비운다(기존 앱과 같다). */
    fun send() {
        val text = _uiState.value.input
        if (text.isEmpty()) return
        _uiState.update { it.copy(input = "") }
        chatRepository.sendText(roomId, text)
    }

    fun resend(tempId: Long) {
        if (!chatRepository.resendFailed(tempId)) emit(R.string.chat_send_failed)
    }

    fun deleteFailed(tempId: Long) {
        chatRepository.deleteFailed(tempId)
    }

    // ---------- 페이징·스크롤 ----------

    /** 맨 위에 닿았다. 실제로 앞에 붙은 개수를 돌려준다(스크롤 위치 보정용). */
    suspend fun loadPrev(): Int {
        if (loadingPrev) return 0
        val oldest = chatRepository.activeMessages.value.firstOrNull { it.id > 0L } ?: return 0
        loadingPrev = true
        return try {
            chatRepository.loadPrev(roomId, oldest.id, PAGE_SIZE)
        } finally {
            loadingPrev = false
        }
    }

    /** 화면이 맨 아래에 닿았는지 알려 준다. 닿으면 "아래로" 배지를 지운다. */
    fun onAtBottomChanged(isAtBottom: Boolean) {
        atBottom = isAtBottom
        if (isAtBottom && _uiState.value.jumpToBottomCount > 0) {
            _uiState.update { it.copy(jumpToBottomCount = 0) }
        }
    }

    // ---------- 첨부 ----------

    fun takePictureUri(): Uri = storageRepository.takePictureUri()

    /** 고른 파일을 올리고 종류에 맞는 chatType 으로 보낸다(부록 A §9). */
    fun onAttachmentPicked(uri: Uri, kind: AttachKind) {
        viewModelScope.launch {
            storageRepository.upload(uri)
                .onSuccess { fileName ->
                    val sent = when (kind) {
                        AttachKind.IMAGE -> chatRepository.sendImage(roomId, fileName)
                        AttachKind.FILE -> chatRepository.sendFile(roomId, fileName)
                        AttachKind.AUDIO -> chatRepository.sendAudio(roomId, fileName)
                    }
                    if (!sent) emit(R.string.chat_send_failed)
                }
                .onFailure { emit(R.string.attach_upload_failed) }
        }
    }

    // ---------- 드로어 ----------

    fun leaveRoom() {
        if (_uiState.value.isLeaving) return
        _uiState.update { it.copy(isLeaving = true) }
        viewModelScope.launch {
            chatRepository.leaveRoom(roomId)
                .onSuccess {
                    emit(R.string.chat_exit_done)
                    _leftRoom.tryEmit(Unit)
                }
                .onFailure {
                    _uiState.update { state -> state.copy(isLeaving = false) }
                    emit(R.string.chat_exit_failed)
                }
        }
    }

    /** 드로어의 `사진, 동영상` 행. 사진이 없으면 안내만 하고 null 을 돌려준다. */
    fun recentImageIds(): List<String>? {
        val images = _uiState.value.recentImages
        if (images.isEmpty()) {
            emit(R.string.chat_drawer_no_photos)
            return null
        }
        return images.map { it.id.toString() }
    }

    private fun myUserId(): String = me?.userId.orEmpty()

    private fun emit(res: Int, vararg args: String) {
        _messages.tryEmit(ChatUiMessage(res, args.toList()))
    }

    companion object {

        const val PAGE_SIZE = 20

        /**
         * "아래로" 배지는 **남이 보낸** 메시지가 **맨 아래가 아닐 때** 도착했을 때만 뜬다.
         * 내가 보낸 메시지는 화면이 따라 내려가므로 배지를 띄우지 않는다.
         */
        fun shouldShowJumpToBottom(wasAtBottom: Boolean, lastSender: String, myUserId: String): Boolean =
            !wasAtBottom && lastSender.isNotEmpty() && lastSender != myUserId

        /**
         * 메시지 목록을 말풍선으로 바꾼다. 묶음 판단은 "같은 발신자 + 같은 짧은 시각" 이다(부록 A §8a).
         */
        fun buildBubbles(
            messages: List<ChatMessage>,
            members: List<Member>,
            myUserId: String,
            names: Map<String, String>,
        ): List<ChatBubble> {
            val byUserId = members.associateBy { it.userId }
            val keys = messages.map { it.sender to ChatTime.shortTime(it.chatTime) }

            return messages.mapIndexed { index, message ->
                val sameAsPrev = index > 0 && keys[index - 1] == keys[index]
                val sameAsNext = index < messages.lastIndex && keys[index + 1] == keys[index]
                val group = when {
                    !sameAsPrev && !sameAsNext -> BubbleGroup.SINGLE
                    !sameAsPrev -> BubbleGroup.HEADER
                    !sameAsNext -> BubbleGroup.TAIL
                    else -> BubbleGroup.BODY
                }
                val member = byUserId[message.sender]
                ChatBubble(
                    message = message,
                    isMine = message.sender == myUserId && myUserId.isNotEmpty(),
                    group = group,
                    senderName = member
                        ?.let { DisplayName.of(it.userId, it.username, names) }
                        ?.takeIf { it.isNotBlank() },
                    senderImage = member?.profileImage.orEmpty(),
                    senderMemberId = member?.id?.takeIf { it > 0L },
                    shortTime = keys[index].second,
                )
            }
        }

        /** 이미지 말풍선인지. 파일·음성은 본문(파일 이름)을 글자로 보여 준다. */
        fun isImage(message: ChatMessage): Boolean = message.chatType == ChatType.IMAGE
    }
}
