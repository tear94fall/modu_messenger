package com.example.modumessenger.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.modumessenger.feature.chat.chatGraph
import com.example.modumessenger.feature.friends.FindFriendsScreen
import com.example.modumessenger.feature.friends.SearchFriendsScreen
import com.example.modumessenger.feature.friends.SetFriendsScreen
import com.example.modumessenger.feature.login.LoginScreen
import com.example.modumessenger.feature.login.SplashScreen
import com.example.modumessenger.feature.main.MainScreen
import com.example.modumessenger.feature.profile.profileGraph
import com.example.modumessenger.feature.settings.AccountScreen
import com.example.modumessenger.feature.settings.AppInfoScreen
import com.example.modumessenger.feature.settings.NoticeScreen
import com.example.modumessenger.feature.settings.SetupScreen

/**
 * 앱의 모든 화면을 등록한다(스펙 §5).
 * 프로필·채팅 계열은 각 태스크가 소유한 [profileGraph]/[chatGraph] 로 넘긴다.
 *
 * [onMainResume] 은 메인 화면이 다시 보일 때마다 불린다(통합자가 방 목록 새로고침을 건다).
 * [awaitLoggedIn] 은 스플래시가 기다리는 세션 판정이다(`SessionStore.isLoggedIn` 의 첫 값).
 */
@Composable
fun ModuNavHost(
    startDestination: String,
    navController: NavHostController,
    modifier: Modifier = Modifier,
    onMainResume: () -> Unit = {},
    onLoggedIn: () -> Unit = {},
    awaitLoggedIn: suspend () -> Boolean = { false },
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(Routes.SPLASH) {
            SplashScreen(
                awaitLoggedIn = awaitLoggedIn,
                onDecided = { loggedIn ->
                    navController.navigate(if (loggedIn) Routes.MAIN else Routes.LOGIN) {
                        // 스플래시로는 되돌아갈 수 없다.
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(Routes.LOGIN) {
            LoginScreen(
                onLoggedIn = {
                    onLoggedIn()
                    navController.navigate(Routes.MAIN) {
                        // 로그인 화면으로 되돌아갈 수 없게 백스택을 비운다.
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(Routes.MAIN) {
            MainScreen(
                onOpenProfile = { memberId -> navController.navigate(Routes.profile(memberId)) },
                onOpenRoom = { roomId -> navController.navigate(Routes.chat(roomId)) },
                onCreateRoom = { navController.navigate(Routes.CREATE_ROOM) },
                onFindFriends = { navController.navigate(Routes.FIND_FRIENDS) },
                onSearchFriends = { navController.navigate(Routes.SEARCH_FRIENDS) },
                onSetFriends = { navController.navigate(Routes.SET_FRIENDS) },
                onSetup = { navController.navigate(Routes.SETUP) },
                onNotice = { navController.navigate(Routes.NOTICE) },
                onAppInfo = { navController.navigate(Routes.APP_INFO) },
                onResume = onMainResume,
            )
        }

        composable(Routes.SEARCH_FRIENDS) {
            SearchFriendsScreen(
                onBack = { navController.popBackStack() },
                onOpenProfile = { memberId -> navController.navigate(Routes.profile(memberId)) },
            )
        }

        composable(Routes.FIND_FRIENDS) {
            FindFriendsScreen(
                onBack = { navController.popBackStack() },
                onOpenProfile = { memberId -> navController.navigate(Routes.profile(memberId)) },
                onOpenRoom = { roomId -> navController.navigate(Routes.chat(roomId)) },
            )
        }

        composable(Routes.SET_FRIENDS) {
            SetFriendsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.SETUP) {
            SetupScreen(
                onBack = { navController.popBackStack() },
                onAccount = { navController.navigate(Routes.ACCOUNT) },
                onAppInfo = { navController.navigate(Routes.APP_INFO) },
                onNotice = { navController.navigate(Routes.NOTICE) },
                onSetFriends = { navController.navigate(Routes.SET_FRIENDS) },
            )
        }

        composable(Routes.ACCOUNT) {
            AccountScreen(
                onBack = { navController.popBackStack() },
                onLoggedOut = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(Routes.NOTICE) {
            NoticeScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.APP_INFO) {
            AppInfoScreen(onBack = { navController.popBackStack() })
        }

        profileGraph(navController)
        chatGraph(navController)
    }
}
