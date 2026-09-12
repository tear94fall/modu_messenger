package com.example.modumessenger.data.api

import com.example.modumessenger.data.dto.AddFriendDto
import com.example.modumessenger.data.dto.MemberDto
import com.example.modumessenger.data.dto.PageResponseDto
import com.example.modumessenger.data.dto.RenameFriendDto
import com.example.modumessenger.data.dto.UpdateProfileDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface MemberApi {

    @GET("member-service/api-public/member/{email}")
    suspend fun getMemberByEmail(@Path("email") email: String): MemberDto

    @GET("member-service/api-public/member/member/{id}")
    suspend fun getMember(@Path("id") id: Long): MemberDto

    @POST("member-service/api-public/member/{userId}")
    suspend fun updateMember(
        @Path("userId") userId: String,
        @Body body: UpdateProfileDto,
    ): MemberDto

    /** `sort` 는 서버 `FriendSort` 문자열, `size` 는 서버가 100 까지만 받는다. */
    @GET("member-service/api-public/member/{userId}/friends")
    suspend fun getFriends(
        @Path("userId") userId: String,
        @Query("sort") sort: String,
        @Query("page") page: Int,
        @Query("size") size: Int,
    ): PageResponseDto<MemberDto>

    @POST("member-service/api-public/member/{userId}/friends")
    suspend fun addFriend(
        @Path("userId") userId: String,
        @Body body: AddFriendDto,
    ): MemberDto

    /** 친구 userId → 내가 정한 이름. */
    @GET("member-service/api-public/member/{userId}/friends/names")
    suspend fun getFriendNames(@Path("userId") userId: String): Map<String, String>

    @PUT("member-service/api-public/member/{userId}/friends/{friendMemberId}/name")
    suspend fun renameFriend(
        @Path("userId") userId: String,
        @Path("friendMemberId") friendMemberId: Long,
        @Body body: RenameFriendDto,
    ): MemberDto

    @GET("member-service/api-public/member/friends/{email}")
    suspend fun searchByEmail(@Path("email") email: String): List<MemberDto>

    /** 서버가 DELETE 에 본문을 요구한다(서버 계약이라 그대로 둔다). */
    @HTTP(method = "DELETE", path = "member-service/api-public/member/profile/{userId}", hasBody = true)
    suspend fun deleteProfileImage(
        @Path("userId") userId: String,
        @Body image: String,
    ): MemberDto
}
