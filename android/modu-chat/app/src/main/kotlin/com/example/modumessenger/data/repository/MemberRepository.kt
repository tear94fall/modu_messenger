package com.example.modumessenger.data.repository

import com.example.modumessenger.core.model.Member
import com.example.modumessenger.core.network.safeCall
import com.example.modumessenger.core.session.FriendNames
import com.example.modumessenger.core.session.SessionStore
import com.example.modumessenger.core.util.FriendSort
import com.example.modumessenger.core.util.FriendsPager
import com.example.modumessenger.data.api.MemberApi
import com.example.modumessenger.data.dto.AddFriendDto
import com.example.modumessenger.data.dto.PageResponseDto
import com.example.modumessenger.data.dto.RenameFriendDto
import com.example.modumessenger.data.dto.UpdateProfileDto
import com.example.modumessenger.data.dto.mapContent
import com.example.modumessenger.data.dto.toModel
import javax.inject.Inject
import javax.inject.Singleton

interface MemberRepository {

    /** 세션에 저장된 내 이메일로 서버의 최신 내 정보를 받아 세션을 갱신한다. */
    suspend fun getMe(): Result<Member>

    suspend fun getMember(id: Long): Result<Member>

    /** 내 프로필 수정. 성공하면 세션의 `member` 도 갱신한다. */
    suspend fun updateProfile(dto: UpdateProfileDto): Result<Member>

    suspend fun getFriendsPage(
        page: Int,
        size: Int = FriendsPager.DEFAULT_PAGE_SIZE,
        sort: String = FriendSort.DEFAULT,
    ): Result<PageResponseDto<Member>>

    suspend fun addFriend(email: String): Result<Member>

    /** 서버의 별칭 맵을 받아 [FriendNames] 를 통째로 갈아 끼운다. */
    suspend fun loadFriendNames(): Result<Map<String, String>>

    suspend fun renameFriend(friendMemberId: Long, name: String): Result<Member>

    suspend fun searchByEmail(email: String): Result<List<Member>>

    suspend fun deleteProfileImage(image: String): Result<Member>
}

@Singleton
class MemberRepositoryImpl @Inject constructor(
    private val memberApi: MemberApi,
    private val sessionStore: SessionStore,
    private val friendNames: FriendNames,
) : MemberRepository {

    override suspend fun getMe(): Result<Member> = safeCall {
        val email = requireMe().email
        val member = memberApi.getMemberByEmail(email).toModel()
        sessionStore.saveMember(member)
        member
    }

    override suspend fun getMember(id: Long): Result<Member> = safeCall {
        memberApi.getMember(id).toModel()
    }

    override suspend fun updateProfile(dto: UpdateProfileDto): Result<Member> = safeCall {
        val member = memberApi.updateMember(requireMe().userId, dto).toModel()
        sessionStore.saveMember(member)
        member
    }

    override suspend fun getFriendsPage(
        page: Int,
        size: Int,
        sort: String,
    ): Result<PageResponseDto<Member>> = safeCall {
        val response = memberApi.getFriends(requireMe().userId, sort, page, size)
        // 목록이 들고 오는 별칭을 캐시에 반영해 둔다(알림·채팅방 이름이 같은 이름을 쓰도록).
        response.items.forEach { dto ->
            val userId = dto.userId
            if (!userId.isNullOrBlank()) friendNames.put(userId, dto.friendName)
        }
        response.mapContent { it.toModel() }
    }

    override suspend fun addFriend(email: String): Result<Member> = safeCall {
        val friend = memberApi.addFriend(requireMe().userId, AddFriendDto(email)).toModel()
        if (friend.userId.isNotBlank()) friendNames.put(friend.userId, friend.friendName)
        friend
    }

    override suspend fun loadFriendNames(): Result<Map<String, String>> = safeCall {
        val names = memberApi.getFriendNames(requireMe().userId)
        friendNames.replaceAll(names)
        names
    }

    override suspend fun renameFriend(friendMemberId: Long, name: String): Result<Member> = safeCall {
        val friend = memberApi
            .renameFriend(requireMe().userId, friendMemberId, RenameFriendDto(name))
            .toModel()
        if (friend.userId.isNotBlank()) friendNames.put(friend.userId, name)
        friend
    }

    override suspend fun searchByEmail(email: String): Result<List<Member>> = safeCall {
        memberApi.searchByEmail(email).map { it.toModel() }
    }

    override suspend fun deleteProfileImage(image: String): Result<Member> = safeCall {
        val member = memberApi.deleteProfileImage(requireMe().userId, image).toModel()
        sessionStore.saveMember(member)
        member
    }

    private suspend fun requireMe(): Member =
        sessionStore.memberNow() ?: throw IllegalStateException("로그인 정보가 없다")
}
