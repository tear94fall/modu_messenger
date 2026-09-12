package com.example.modumessenger.data.api

import com.example.modumessenger.data.dto.CreateProfileDto
import com.example.modumessenger.data.dto.ProfileDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ProfileApi {

    @GET("profile-service/api-public/profile/{memberId}")
    suspend fun getProfiles(@Path("memberId") memberId: Long): List<ProfileDto>

    @GET("profile-service/api-public/profile/{memberId}/{id}")
    suspend fun getProfile(
        @Path("memberId") memberId: Long,
        @Path("id") id: Long,
    ): ProfileDto

    @POST("profile-service/api-public/profile")
    suspend fun createProfile(@Body body: CreateProfileDto): ProfileDto

    /** 지운 기록의 id 를 돌려준다. */
    @DELETE("profile-service/api-public/profile/{memberId}/{id}")
    suspend fun deleteProfile(
        @Path("memberId") memberId: Long,
        @Path("id") id: Long,
    ): Long
}
