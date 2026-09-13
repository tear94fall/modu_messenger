package com.example.modumessenger

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.modumessenger.core.session.SessionEvents
import com.example.modumessenger.data.repository.ChatRepository
import com.example.modumessenger.data.socket.SocketLifecycle
import com.example.modumessenger.core.session.SessionStore
import com.example.modumessenger.core.ui.components.ConfirmDialog
import com.example.modumessenger.core.ui.theme.ModuTheme
import com.example.modumessenger.navigation.ModuNavHost
import com.example.modumessenger.navigation.Routes
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 하나뿐인 액티비티(스펙 §5).
 *
 * - 시스템 스플래시는 `SessionStore.isLoggedIn` 의 첫 값이 나올 때까지만 붙잡아 둔다. 그 다음은 같은 그림의
 *   Compose `splash` 화면(부록 A §1)이 이어받아 최소 시간만큼 브랜딩을 보여 주고 로그인/메인으로 간다.
 * - 알림을 눌러 들어오면 인텐트 extra `roomId` 가 있고, 메인이 뜬 뒤 그 방으로 간다(`onNewIntent` 도 같다).
 * - 토큰 갱신이 끝내 실패하면 `SessionEvents.loggedOut` 이 와서 로그인 화면으로 되돌린다.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var sessionStore: SessionStore

    @Inject lateinit var sessionEvents: SessionEvents
    @Inject lateinit var socketLifecycle: SocketLifecycle
    @Inject lateinit var chatRepository: ChatRepository

    /** false 면 아직 세션 판정 전 = 시스템 스플래시 유지. */
    private var sessionDecided by mutableStateOf(false)

    /** 알림·배너로 들어온 방. 한 번 쓰면 비운다. */
    private val pendingRoomId = MutableStateFlow<String?>(null)

    // STARTED 전에 등록해야 한다. 거절은 조용히 넘긴다(기존 앱과 같음).
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Compose 스플래시가 바로 같은 그림을 이어 그리므로, 시스템 스플래시는 세션 판정까지만 잡아 둔다.
        splashScreen.setKeepOnScreenCondition { !sessionDecided }

        pendingRoomId.value = intent?.getStringExtra(EXTRA_ROOM_ID)

        setContent {
            ModuTheme {
                val navController = rememberNavController()

                ModuNavHost(
                    startDestination = Routes.SPLASH,
                    navController = navController,
                    onMainResume = { lifecycleScope.launch { chatRepository.refreshRooms() } },
                    onLoggedIn = { socketLifecycle.connectFromSession() },
                    awaitLoggedIn = {
                        val loggedIn = sessionStore.isLoggedIn.first()
                        sessionDecided = true
                        loggedIn
                    },
                )

                val currentRoute = navController.currentBackStackEntryAsState().value
                    ?.destination?.route

                LaunchedEffect(navController) {
                    sessionEvents.loggedOut.collect {
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }

                val roomId by pendingRoomId.collectAsState()
                LaunchedEffect(roomId, currentRoute, navController) {
                    val target = roomId ?: return@LaunchedEffect
                    // 스플래시가 곧 백스택을 비운다. 메인/로그인이 정해진 다음에 다시 들어온다.
                    if (currentRoute == null || currentRoute == Routes.SPLASH) return@LaunchedEffect
                    // 로그인 전이면 딥링크를 버린다(로그인 화면 위에 방을 얹지 않는다).
                    if (!sessionStore.isLoggedIn.first()) {
                        pendingRoomId.value = null
                        return@LaunchedEffect
                    }
                    pendingRoomId.value = null
                    navController.navigate(Routes.chat(target))
                }

                NotificationPermissionEffect(
                    enabled = currentRoute == Routes.MAIN,
                    onRequest = { notificationPermissionLauncher.launch(POST_NOTIFICATIONS) },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_ROOM_ID)?.let { pendingRoomId.value = it }
    }

    /**
     * Android 13+ 에서만 필요하다. 이유를 보여 줘야 하는 경우에는 먼저 팝업을 띄운다(부록 A §4).
     */
    @Composable
    private fun NotificationPermissionEffect(enabled: Boolean, onRequest: () -> Unit) {
        var showRationale by remember { mutableStateOf(false) }
        var asked by rememberSaveable { mutableStateOf(false) }

        LaunchedEffect(enabled, asked) {
            if (!enabled || asked) return@LaunchedEffect
            if (!needsNotificationPermission()) return@LaunchedEffect
            asked = true
            if (shouldShowRequestPermissionRationale(POST_NOTIFICATIONS)) {
                showRationale = true
            } else {
                onRequest()
            }
        }

        if (showRationale) {
            ConfirmDialog(
                title = stringResource(R.string.notification_permission_rationale_title),
                text = stringResource(R.string.notification_permission_rationale_message),
                confirmText = stringResource(R.string.notification_permission_rationale_confirm),
                dismissText = stringResource(R.string.notification_permission_rationale_cancel),
                onConfirm = {
                    showRationale = false
                    onRequest()
                },
                onDismiss = { showRationale = false },
            )
        }
    }

    private fun needsNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        return ContextCompat.checkSelfPermission(this, POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
    }

    companion object {
        /** FCM 알림·인앱 배너가 넘기는 방 id. */
        const val EXTRA_ROOM_ID = "roomId"

        private const val POST_NOTIFICATIONS = Manifest.permission.POST_NOTIFICATIONS
    }
}
