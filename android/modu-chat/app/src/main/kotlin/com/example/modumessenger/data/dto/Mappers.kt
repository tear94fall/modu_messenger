package com.example.modumessenger.data.dto

import com.example.modumessenger.core.model.ChatMessage
import com.example.modumessenger.core.model.ChatRoom
import com.example.modumessenger.core.model.ChatType
import com.example.modumessenger.core.model.Member
import com.example.modumessenger.core.model.Notice
import com.example.modumessenger.core.model.Profile
import com.example.modumessenger.core.model.ProfileType
import com.example.modumessenger.core.model.Role
import com.example.modumessenger.core.model.SendStatus

/** 서버 값은 `"ROLE_ADMIN"`/`"ROLE_USER"`. 그 밖의 값(널 포함)은 일반 사용자로 본다. */
fun roleOf(value: String?): Role = if (value == "ROLE_ADMIN") Role.ADMIN else Role.USER

/** 모르는 값이면 상태메시지 기록으로 본다(화면이 깨지는 것보다 낫다). */
fun profileTypeOf(value: String?): ProfileType =
    ProfileType.entries.firstOrNull { it.name == value } ?: ProfileType.PROFILE_STATUS_MESSAGE

fun MemberDto.toModel(): Member = Member(
    id = id ?: 0L,
    userId = userId.orEmpty(),
    email = email.orEmpty(),
    username = username.orEmpty(),
    statusMessage = statusMessage.orEmpty(),
    profileImage = profileImage.orEmpty(),
    wallpaperImage = wallpaperImage.orEmpty(),
    role = roleOf(role),
    profiles = profiles?.map { it.toModel() } ?: emptyList(),
    friendName = friendName,
)

fun ProfileDto.toModel(): Profile = Profile(
    id = id ?: 0L,
    memberId = memberId ?: 0L,
    type = profileTypeOf(profileType),
    value = value.orEmpty(),
    createdDate = createdDate.orEmpty(),
    updatedDate = updatedDate.orEmpty(),
)

fun ChatDto.toModel(): ChatMessage = ChatMessage(
    id = id ?: 0L,
    chatType = if (chatType == 0) ChatType.TEXT else chatType,
    roomId = roomId.orEmpty(),
    sender = sender.orEmpty(),
    message = message.orEmpty(),
    chatTime = chatTime.orEmpty(),
    unreadCount = 0,
    status = SendStatus.SENT,
)

fun ChatRoomDto.toModel(): ChatRoom = ChatRoom(
    roomId = roomId.orEmpty(),
    roomName = roomName.orEmpty(),
    roomImage = roomImage.orEmpty(),
    lastChatMsg = lastChatMsg.orEmpty(),
    lastChatId = lastChatId.orEmpty(),
    lastChatTime = lastChatTime.orEmpty(),
    members = members?.map { it.toModel() } ?: emptyList(),
)

fun NoticeDto.toModel(): Notice = Notice(
    id = id ?: 0L,
    title = title.orEmpty(),
    content = content.orEmpty(),
    writer = writer.orEmpty(),
    createdDate = createdDate.orEmpty(),
)

/** 낙관적 전송/소켓 송신용. 서버가 id 를 붙이므로 보낼 때는 비운다. */
fun ChatMessage.toDto(): ChatDto = ChatDto(
    id = if (id > 0L) id else null,
    chatType = chatType,
    roomId = roomId,
    sender = sender,
    message = message,
    chatTime = chatTime,
)

fun Member.toUpdateProfileDto(): UpdateProfileDto = UpdateProfileDto(
    username = username,
    statusMessage = statusMessage,
    profileImage = profileImage,
    wallpaperImage = wallpaperImage,
)
