package com.example.modumessenger.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 네트워크가 돌아온 순간만 알려 준다. 소켓 재접속이 대기 시간을 다 기다리지 않고 바로 붙게 하는 용도다.
 *
 * (스펙 구조상 `data/socket/NetworkMonitor.kt` 자리지만 그 패키지는 T2 소유라 여기에 둔다.)
 */
interface NetworkMonitor {
    val available: Flow<Unit>
}

@Singleton
class AndroidNetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) : NetworkMonitor {

    override val available: Flow<Unit> = callbackFlow {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (manager == null) {
            awaitClose { }
            return@callbackFlow
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(Unit)
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        runCatching { manager.registerNetworkCallback(request, callback) }

        awaitClose { runCatching { manager.unregisterNetworkCallback(callback) } }
    }
}
