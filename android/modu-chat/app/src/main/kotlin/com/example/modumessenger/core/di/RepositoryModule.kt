package com.example.modumessenger.core.di

import com.example.modumessenger.data.repository.AuthRepository
import com.example.modumessenger.data.repository.AuthRepositoryImpl
import com.example.modumessenger.data.repository.CommonRepository
import com.example.modumessenger.data.repository.CommonRepositoryImpl
import com.example.modumessenger.data.repository.MemberRepository
import com.example.modumessenger.data.repository.MemberRepositoryImpl
import com.example.modumessenger.data.repository.NoticeRepository
import com.example.modumessenger.data.repository.NoticeRepositoryImpl
import com.example.modumessenger.data.repository.ProfileRepository
import com.example.modumessenger.data.repository.ProfileRepositoryImpl
import com.example.modumessenger.data.repository.PushRepository
import com.example.modumessenger.data.repository.PushRepositoryImpl
import com.example.modumessenger.data.repository.StorageRepository
import com.example.modumessenger.data.repository.StorageRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindMemberRepository(impl: MemberRepositoryImpl): MemberRepository

    @Binds
    @Singleton
    abstract fun bindProfileRepository(impl: ProfileRepositoryImpl): ProfileRepository

    @Binds
    @Singleton
    abstract fun bindStorageRepository(impl: StorageRepositoryImpl): StorageRepository

    @Binds
    @Singleton
    abstract fun bindPushRepository(impl: PushRepositoryImpl): PushRepository

    @Binds
    @Singleton
    abstract fun bindNoticeRepository(impl: NoticeRepositoryImpl): NoticeRepository

    @Binds
    @Singleton
    abstract fun bindCommonRepository(impl: CommonRepositoryImpl): CommonRepository
}
