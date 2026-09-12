package com.example.modumessenger.feature.profile

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.modumessenger.navigation.Routes

/**
 * 프로필 계열 화면(스펙 §6 profile/profileEdit/profileHistory/profileImage).
 */
fun NavGraphBuilder.profileGraph(navController: NavHostController) {
    composable(
        route = Routes.PROFILE,
        arguments = listOf(navArgument(Routes.ARG_MEMBER_ID) { type = NavType.LongType }),
    ) {
        ProfileScreen(
            onClose = { navController.popBackStack() },
            onEdit = { navController.navigate(Routes.PROFILE_EDIT) },
            onHistory = { memberId -> navController.navigate(Routes.profileHistory(memberId)) },
            onOpenImage = { memberId, type ->
                navController.navigate(Routes.profileImage(memberId, type))
            },
            onOpenRoom = { roomId ->
                // 방으로 간 뒤 뒤로 누르면 프로필이 아니라 그 앞 화면으로 간다(기존 앱의 finish()).
                navController.navigate(Routes.chat(roomId)) {
                    popUpTo(Routes.PROFILE) { inclusive = true }
                }
            },
        )
    }

    composable(Routes.PROFILE_EDIT) {
        ProfileEditScreen(onClose = { navController.popBackStack() })
    }

    composable(
        route = Routes.PROFILE_HISTORY,
        arguments = listOf(navArgument(Routes.ARG_MEMBER_ID) { type = NavType.LongType }),
    ) {
        ProfileHistoryScreen(
            onBack = { navController.popBackStack() },
            onOpenProfile = { memberId -> navController.navigate(Routes.profile(memberId)) },
            onOpenImage = { memberId, type, profileId ->
                navController.navigate(Routes.profileImage(memberId, type, profileId))
            },
        )
    }

    composable(
        route = Routes.PROFILE_IMAGE,
        arguments = listOf(
            navArgument(Routes.ARG_MEMBER_ID) { type = NavType.LongType },
            navArgument(Routes.ARG_TYPE) { type = NavType.StringType },
            navArgument(Routes.ARG_PROFILE_ID) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        ),
    ) {
        ProfileImageScreen(onClose = { navController.popBackStack() })
    }
}
