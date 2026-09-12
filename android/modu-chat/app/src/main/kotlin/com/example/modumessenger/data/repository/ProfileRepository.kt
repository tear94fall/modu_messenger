package com.example.modumessenger.data.repository

import com.example.modumessenger.core.model.Profile
import com.example.modumessenger.core.model.ProfileType
import com.example.modumessenger.core.network.safeCall
import com.example.modumessenger.data.api.ProfileApi
import com.example.modumessenger.data.dto.CreateProfileDto
import com.example.modumessenger.data.dto.toModel
import javax.inject.Inject
import javax.inject.Singleton

interface ProfileRepository {

    suspend fun getProfiles(memberId: Long): Result<List<Profile>>

    suspend fun getProfile(memberId: Long, id: Long): Result<Profile>

    suspend fun addProfile(memberId: Long, type: ProfileType, value: String): Result<Profile>

    /** 지운 기록의 id. */
    suspend fun deleteProfile(memberId: Long, id: Long): Result<Long>
}

@Singleton
class ProfileRepositoryImpl @Inject constructor(
    private val profileApi: ProfileApi,
) : ProfileRepository {

    override suspend fun getProfiles(memberId: Long): Result<List<Profile>> = safeCall {
        profileApi.getProfiles(memberId).map { it.toModel() }
    }

    override suspend fun getProfile(memberId: Long, id: Long): Result<Profile> = safeCall {
        profileApi.getProfile(memberId, id).toModel()
    }

    override suspend fun addProfile(
        memberId: Long,
        type: ProfileType,
        value: String,
    ): Result<Profile> = safeCall {
        profileApi.createProfile(CreateProfileDto(memberId, type.name, value)).toModel()
    }

    override suspend fun deleteProfile(memberId: Long, id: Long): Result<Long> = safeCall {
        profileApi.deleteProfile(memberId, id)
    }
}
