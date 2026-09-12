package com.example.modumessenger.feature.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.ui.unit.dp
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.background
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.modumessenger.R
import com.example.modumessenger.core.model.ChatType
import com.example.modumessenger.core.ui.components.ModuTopBar
import com.example.modumessenger.core.ui.components.rememberComingSoon
import com.example.modumessenger.core.ui.components.rememberSnackbarThen
import com.example.modumessenger.feature.chat.ChatTab
import com.example.modumessenger.feature.friends.FriendsTab
import com.example.modumessenger.feature.settings.SettingsTab

/** 하단 탭. 순서는 기존 앱의 ViewPager2 페이지 순서와 같다. */
enum class MainTab { FRIENDS, CHAT, SETTINGS }

/**
 * 메인 화면(부록 A §4). 탭 전환은 NavHost 를 쓰지 않고 [rememberSaveable] 로 들고 있는다(스펙 §5).
 *
 * [onResume] 은 통합자가 `ChatRepository.refreshChatRooms()` 를 걸 자리다.
 */
@Composable
fun MainScreen(
    onOpenProfile: (Long) -> Unit,
    onOpenRoom: (String) -> Unit,
    onCreateRoom: () -> Unit,
    onFindFriends: () -> Unit,
    onSearchFriends: () -> Unit,
    onSetFriends: () -> Unit,
    onSetup: () -> Unit,
    onNotice: () -> Unit,
    onAppInfo: () -> Unit,
    onResume: () -> Unit = {},
    viewModel: MainViewModel = hiltViewModel(),
) {
    // 기존 앱(ViewPager2)처럼 좌우 스와이프로도 탭을 옮긴다. 선택된 탭은 페이저의 현재 페이지에서 나온다.
    val pagerState = rememberPagerState(initialPage = MainTab.FRIENDS.ordinal) { MainTab.entries.size }
    val tabScope = rememberCoroutineScope()
    val selectedTab = MainTab.entries[pagerState.currentPage]
    val snackbarHostState = remember { SnackbarHostState() }
    val comingSoon = rememberComingSoon(snackbarHostState)
    // 기존 앱은 툴바 메뉴를 누르면 무엇을 눌렀는지 토스트로 알려 줬다. 그 자리다.
    val announce = rememberSnackbarThen(snackbarHostState)
    val findFriendsLabel = stringResource(R.string.find_friends_title)
    val addFriendLabel = stringResource(R.string.search_friends_title)
    val setFriendsLabel = stringResource(R.string.set_friends_title)
    val totalUnread by viewModel.totalUnread.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel) { viewModel.start() }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { onResume() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { res ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(context.getString(res))
        }
    }

    // 다른 방에서 온 메시지는 스낵바 배너로 알리고, "이동" 을 누르면 그 방으로 간다(부록 A §0.7).
    LaunchedEffect(viewModel) {
        viewModel.banners.collect { banner ->
            val preview = when (banner.chatType) {
                ChatType.IMAGE -> context.getString(R.string.preview_image)
                ChatType.FILE -> context.getString(R.string.preview_file)
                ChatType.AUDIO -> context.getString(R.string.preview_audio)
                else -> banner.message
            }
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = context.getString(R.string.banner_text, banner.senderName, preview),
                actionLabel = context.getString(R.string.banner_action),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) onOpenRoom(banner.roomId)
        }
    }

    Scaffold(
        topBar = {
            ModuTopBar(
                title = stringResource(selectedTab.titleRes()),
            ) {
                when (selectedTab) {
                    MainTab.FRIENDS -> {
                        IconButton(onClick = { announce(findFriendsLabel, onFindFriends) }) {
                            Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.friends_menu_search))
                        }
                        IconButton(onClick = { announce(addFriendLabel, onSearchFriends) }) {
                            Icon(Icons.Filled.PersonAdd, contentDescription = stringResource(R.string.friends_menu_add))
                        }
                        IconButton(onClick = { announce(setFriendsLabel, onSetFriends) }) {
                            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.friends_menu_settings))
                        }
                    }
                    // 채팅방 검색·설정은 기존 앱에서도 미구현이라 "준비 중" 만 띄운다.
                    MainTab.CHAT -> {
                        IconButton(onClick = comingSoon) {
                            Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.friends_menu_search))
                        }
                        IconButton(onClick = comingSoon) {
                            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.friends_menu_settings))
                        }
                    }
                    MainTab.SETTINGS -> Unit
                }
            }
        },
        bottomBar = {
            // Material3 NavigationBar 기본 높이(80dp)는 기존 BottomNavigationView(56dp)보다 훨씬 높다.
            // 높이를 직접 정하고, 시스템 내비게이션 바 인셋은 바깥 Box 가 같은 색으로 채운다.
            Box(
                modifier = Modifier
                    .background(NavigationBarDefaults.containerColor)
                    .navigationBarsPadding(),
            ) {
                NavigationBar(
                    modifier = Modifier.height(BOTTOM_NAV_HEIGHT),
                    windowInsets = WindowInsets(0.dp),
                ) {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { tabScope.launch { pagerState.animateScrollToPage(tab.ordinal) } },
                        icon = {
                            if (tab == MainTab.CHAT && totalUnread > 0) {
                                BadgedBox(badge = { Badge { Text(badgeText(totalUnread)) } }) {
                                    Icon(tab.icon(), contentDescription = null)
                                }
                            } else {
                                Icon(tab.icon(), contentDescription = null)
                            }
                        },
                        label = { Text(stringResource(tab.titleRes())) },
                    )
                }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().padding(padding),
            beyondViewportPageCount = 0,
        ) { page ->
            val pageModifier = Modifier.fillMaxSize()
            when (MainTab.entries[page]) {
                MainTab.FRIENDS -> FriendsTab(
                    onOpenProfile = onOpenProfile,
                    snackbarHostState = snackbarHostState,
                    modifier = pageModifier,
                )

                MainTab.CHAT -> Box(modifier = pageModifier) {
                    ChatTab(onOpenRoom = onOpenRoom, onCreateRoom = onCreateRoom)
                }

                MainTab.SETTINGS -> SettingsTab(
                    onOpenProfile = onOpenProfile,
                    onSetup = onSetup,
                    onNotice = onNotice,
                    onAppInfo = onAppInfo,
                    snackbarHostState = snackbarHostState,
                    modifier = pageModifier,
                )
            }
        }
    }
}

internal fun badgeText(count: Int): String = if (count > 999) "999+" else count.toString()

private fun MainTab.titleRes(): Int = when (this) {
    MainTab.FRIENDS -> R.string.tab_friends
    MainTab.CHAT -> R.string.tab_chat
    MainTab.SETTINGS -> R.string.tab_setting
}

@Composable
private fun MainTab.icon(): Painter = when (this) {
    MainTab.FRIENDS -> rememberVectorPainter(Icons.Filled.Person)
    // 기존 앱과 같은 시스템 말풍선 아이콘
    MainTab.CHAT -> painterResource(android.R.drawable.sym_action_chat)
    MainTab.SETTINGS -> rememberVectorPainter(Icons.Filled.Settings)
}

private val BOTTOM_NAV_HEIGHT = 60.dp
