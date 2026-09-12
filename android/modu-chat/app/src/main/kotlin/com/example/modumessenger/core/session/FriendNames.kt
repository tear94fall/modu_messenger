package com.example.modumessenger.core.session

import com.example.modumessenger.core.di.ApplicationScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 친구 userId → 내가 정한 이름. DataStore `friend-names` 에 JSON 한 덩어리로 남겨 두어
 * FCM 이 콜드 스타트로 깨워도 알림 제목에 별칭을 쓸 수 있다.
 */
@Singleton
class FriendNames @Inject constructor(
    private val store: SessionStore,
    private val gson: Gson,
    @ApplicationScope scope: CoroutineScope,
) {

    private val _names = MutableStateFlow<Map<String, String>>(emptyMap())
    val names: StateFlow<Map<String, String>> = _names.asStateFlow()

    private val mutex = Mutex()

    init {
        scope.launch { restore() }
    }

    /** 친구가 아니면 null, 별칭을 지운 친구면 `""`. */
    fun get(userId: String): String? = _names.value[userId]

    suspend fun put(userId: String, name: String?) {
        mutex.withLock {
            _names.value = _names.value.toMutableMap().apply { put(userId, name ?: "") }
            persist()
        }
    }

    suspend fun replaceAll(map: Map<String, String>) {
        mutex.withLock {
            _names.value = map.toMap()
            persist()
        }
    }

    private suspend fun restore() {
        val json = store.friendNamesJson() ?: return
        val restored = runCatching {
            gson.fromJson<Map<String, String>>(json, object : TypeToken<Map<String, String>>() {}.type)
        }.getOrNull() ?: return
        mutex.withLock {
            // 복원 중에 서버 응답이 먼저 들어왔으면 그것이 더 새것이다.
            if (_names.value.isEmpty()) _names.value = restored
        }
    }

    private suspend fun persist() {
        store.saveFriendNamesJson(gson.toJson(_names.value))
    }
}
