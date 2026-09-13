package com.example.modumessenger.feature.profile

import android.net.Uri
import com.example.modumessenger.core.chat.RoomCreator
import com.example.modumessenger.core.model.Member
import com.example.modumessenger.core.model.Profile
import com.example.modumessenger.core.model.ProfileType
import com.example.modumessenger.data.dto.PageResponseDto
import com.example.modumessenger.data.dto.UpdateProfileDto
import com.example.modumessenger.data.repository.MemberRepository
import com.example.modumessenger.data.repository.ProfileRepository
import com.example.modumessenger.data.repository.StorageRepository

/** 두 리포지토리가 어떤 순서로 불렸는지 보려고 한 곳에 적는다. */
class CallLog {
    val calls = mutableListOf<String>()
    fun record(call: String) = calls.add(call)
}

class FakeMemberRepository(
    private val log: CallLog = CallLog(),
    var member: Member = Member(id = 2L, userId = "friend", username = "친구"),
    var me: Member = Member(id = 1L, userId = "me", username = "나"),
) : MemberRepository {

    var getMemberResult: Result<Member>? = null
    var updateProfileResult: Result<Member>? = null
    var renameResult: Result<Member>? = null

    val updatedProfiles = mutableListOf<UpdateProfileDto>()
    val renamedTo = mutableListOf<Pair<Long, String>>()
    val deletedImages = mutableListOf<String>()

    override suspend fun getMe(): Result<Member> {
        log.record("getMe")
        return Result.success(me)
    }

    override suspend fun getMember(id: Long): Result<Member> {
        log.record("getMember")
        return getMemberResult ?: Result.success(member)
    }

    override suspend fun updateProfile(dto: UpdateProfileDto): Result<Member> {
        log.record("updateProfile")
        updatedProfiles += dto
        return updateProfileResult ?: Result.success(
            me.copy(
                username = dto.username.orEmpty(),
                statusMessage = dto.statusMessage.orEmpty(),
                profileImage = dto.profileImage.orEmpty(),
                wallpaperImage = dto.wallpaperImage.orEmpty(),
            ),
        )
    }

    override suspend fun getFriendsPage(
        page: Int,
        size: Int,
        sort: String,
    ): Result<PageResponseDto<Member>> = Result.success(PageResponseDto(content = emptyList()))

    override suspend fun addFriend(email: String): Result<Member> = Result.success(Member())

    override suspend fun loadFriendNames(): Result<Map<String, String>> =
        Result.success(emptyMap())

    override suspend fun renameFriend(friendMemberId: Long, name: String): Result<Member> {
        log.record("renameFriend")
        renamedTo += friendMemberId to name
        return renameResult ?: Result.success(member.copy(friendName = name))
    }

    override suspend fun searchByEmail(email: String): Result<List<Member>> =
        Result.success(emptyList())

    override suspend fun deleteProfileImage(image: String): Result<Member> {
        log.record("deleteProfileImage")
        deletedImages += image
        return Result.success(me)
    }
}

class FakeProfileRepository(private val log: CallLog = CallLog()) : ProfileRepository {

    val added = mutableListOf<Triple<Long, ProfileType, String>>()
    val deleted = mutableListOf<Pair<Long, Long>>()
    var profiles: List<Profile> = emptyList()

    override suspend fun getProfiles(memberId: Long): Result<List<Profile>> =
        Result.success(profiles)

    override suspend fun getProfile(memberId: Long, id: Long): Result<Profile> =
        profiles.firstOrNull { it.id == id }
            ?.let { Result.success(it) }
            ?: Result.failure(NoSuchElementException("없는 기록: $id"))

    override suspend fun addProfile(
        memberId: Long,
        type: ProfileType,
        value: String,
    ): Result<Profile> {
        log.record("addProfile")
        added += Triple(memberId, type, value)
        return Result.success(Profile(id = 1L, memberId = memberId, type = type, value = value))
    }

    override suspend fun deleteProfile(memberId: Long, id: Long): Result<Long> {
        log.record("deleteProfile")
        deleted += memberId to id
        return Result.success(id)
    }
}

class FakeStorageRepository : StorageRepository {

    var uploaded = mutableListOf<Uri>()
    var uploadResult: Result<String> = Result.success("uploaded.jpg")

    override suspend fun upload(uri: Uri): Result<String> {
        uploaded += uri
        return uploadResult
    }

    override fun takePictureUri(): Uri = throw UnsupportedOperationException()
}

class FakeRoomCreator(private val roomId: String = "room-1") : RoomCreator {

    val requested = mutableListOf<List<Long>>()

    override suspend fun createRoom(memberIds: List<Long>): Result<String> {
        requested += memberIds
        return Result.success(roomId)
    }
}
