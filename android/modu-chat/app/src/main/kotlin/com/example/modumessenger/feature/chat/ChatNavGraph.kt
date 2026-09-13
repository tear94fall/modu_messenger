package com.example.modumessenger.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.modumessenger.navigation.Routes

/**
 * 채팅 계열 화면(스펙 §6 chat 탭/chat/chatRoomEdit/invite/createRoom/chatImages).
 */
fun NavGraphBuilder.chatGraph(navController: NavHostController) {
    composable(
        route = Routes.CHAT,
        arguments = listOf(navArgument(Routes.ARG_ROOM_ID) { type = NavType.StringType }),
    ) {
        ChatScreen(
            onBack = { navController.popBackStack() },
            onOpenProfile = { memberId -> navController.navigate(Routes.profile(memberId)) },
            onOpenRoomEdit = { roomId -> navController.navigate(Routes.chatRoomEdit(roomId)) },
            onOpenInvite = { roomId, members -> navController.navigate(Routes.invite(roomId, members)) },
            onOpenImages = { ids -> navController.navigate(Routes.chatImages(ids)) },
        )
    }

    composable(
        route = Routes.CHAT_ROOM_EDIT,
        arguments = listOf(navArgument(Routes.ARG_ROOM_ID) { type = NavType.StringType }),
    ) {
        ChatRoomEditScreen(onBack = { navController.popBackStack() })
    }

    composable(
        route = Routes.INVITE,
        arguments = listOf(
            navArgument(Routes.ARG_ROOM_ID) { type = NavType.StringType },
            navArgument(Routes.ARG_MEMBERS) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        ),
    ) {
        InviteScreen(
            onBack = { navController.popBackStack() },
            onOpenProfile = { memberId -> navController.navigate(Routes.profile(memberId)) },
        )
    }

    composable(Routes.CREATE_ROOM) {
        CreateRoomScreen(
            onBack = { navController.popBackStack() },
            onOpenProfile = { memberId -> navController.navigate(Routes.profile(memberId)) },
            onOpenRoom = { roomId ->
                navController.navigate(Routes.chat(roomId)) {
                    // 방을 만든 화면은 백스택에서 지운다(뒤로 가면 방 목록으로).
                    popUpTo(Routes.CREATE_ROOM) { inclusive = true }
                }
            },
        )
    }

    composable(
        route = Routes.CHAT_IMAGES,
        arguments = listOf(
            navArgument(Routes.ARG_IDS) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        ),
    ) {
        ChatImageScreen(onBack = { navController.popBackStack() })
    }
}

/**
 * 메인 화면의 채팅 탭(부록 A §6). [onOpenRoom] 은 방 클릭, [onCreateRoom] 은 FAB(+).
 */
@Composable
fun ChatTab(
    onOpenRoom: (String) -> Unit,
    onCreateRoom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ChatRoomListScreen(
        onOpenRoom = onOpenRoom,
        onCreateRoom = onCreateRoom,
        modifier = modifier,
    )
}
