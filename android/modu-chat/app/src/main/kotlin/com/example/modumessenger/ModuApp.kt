package com.example.modumessenger

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.modumessenger.core.app.AppForeground
import com.example.modumessenger.data.socket.SocketLifecycle
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * 프로세스가 앞에 있는지 표시하고, 소켓 연결/해제는 `SocketLifecycle` 에 맡긴다
 * (로그인 상태에서 포그라운드 진입 시 연결, 백그라운드 시 해제).
 */
@HiltAndroidApp
class ModuApp : Application(), ImageLoaderFactory {

    @Inject lateinit var socketLifecycle: SocketLifecycle
    @Inject lateinit var imageLoader: dagger.Lazy<ImageLoader>

    /** 앱 전체의 AsyncImage 가 인증 헤더가 붙은 로더를 쓴다(storage-service /view 는 토큰이 필요하다). */
    override fun newImageLoader(): ImageLoader = imageLoader.get()

    override fun onCreate() {
        super.onCreate()
        socketLifecycle.start()
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    AppForeground.isForeground = true
                }

                override fun onStop(owner: LifecycleOwner) {
                    AppForeground.isForeground = false
                }
            },
        )
    }
}
