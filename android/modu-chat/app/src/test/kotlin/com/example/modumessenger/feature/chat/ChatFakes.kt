package com.example.modumessenger.feature.chat

import android.net.Uri
import com.example.modumessenger.data.api.ChatApi
import com.example.modumessenger.data.api.ChatRoomApi
import com.example.modumessenger.data.dto.ChatDto
import com.example.modumessenger.data.dto.ChatReadCursorDto
import com.example.modumessenger.data.dto.ChatRoomDto
import com.example.modumessenger.data.dto.ChatRoomUnreadDto
import com.example.modumessenger.data.repository.StorageRepository
import com.example.modumessenger.data.socket.ChatSocket
import com.example.modumessenger.data.socket.ConnectionState
import com.example.modumessenger.data.socket.SocketEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow

/** 보낸 프레임만 모아 두는 가짜 소켓. [sendResult] 로 전송 실패를 흉내 낸다. */
class FakeChatSocket : ChatSocket {

    val incoming = MutableSharedFlow<SocketEvent>(extraBufferCapacity = 64)
    val sent = mutableListOf<String>()
    var sendResult = true

    override val state = MutableStateFlow(ConnectionState.CONNECTED)
    override val events: SharedFlow<SocketEvent> = incoming

    override fun connect() = Unit
    override fun disconnect() = Unit

    override fun send(text: String): Boolean {
        if (sendResult) sent += text
        return sendResult
    }
}

class FakeChatApi : ChatApi {
    var recent: List<ChatDto> = emptyList()
    var before: List<ChatDto> = emptyList()
    var images: List<ChatDto> = emptyList()
    var byIds: List<ChatDto> = emptyList()

    override suspend fun getChats(ids: List<String>): List<ChatDto> = byIds
    override suspend fun getRecent(roomId: String, size: Int): List<ChatDto> = recent
    override suspend fun getBefore(roomId: String, chatId: Long, size: Int): List<ChatDto> = before
    override suspend fun getImages(roomId: String, size: Int): List<ChatDto> = images
}

class FakeChatRoomApi : ChatRoomApi {
    var rooms: List<ChatRoomDto> = emptyList()
    var unread: List<ChatRoomUnreadDto> = emptyList()
    var cursors: List<ChatReadCursorDto> = emptyList()

    override suspend fun getRooms(memberId: String): List<ChatRoomDto> = rooms
    override suspend fun getRoom(roomId: String): ChatRoomDto = rooms.first { it.roomId == roomId }
    override suspend fun createRoom(ids: List<Long>): ChatRoomDto = rooms.first()
    override suspend fun leaveRoom(roomId: String, userId: String): ChatRoomDto =
        rooms.first { it.roomId == roomId }

    override suspend fun updateRoom(roomId: String, room: ChatRoomDto): ChatRoomDto = room
    override suspend fun inviteMembers(roomId: String, userIds: List<String>): ChatRoomDto =
        rooms.first { it.roomId == roomId }

    override suspend fun getUnreadCounts(memberId: String): List<ChatRoomUnreadDto> = unread
    override suspend fun updateLastRead(roomId: String, memberId: String) = Unit
    override suspend fun getReadCursors(roomId: String): List<ChatReadCursorDto> = cursors
}

/** 업로드는 언제나 같은 파일 이름을 돌려준다. */
class FakeStorageRepository : StorageRepository {
    var uploadResult: Result<String> = Result.success("uploaded.jpg")

    override suspend fun upload(uri: Uri): Result<String> = uploadResult
    override fun takePictureUri(): Uri = Uri.EMPTY
}
