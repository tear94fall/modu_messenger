package com.example.modumessenger.feature.friends

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.modumessenger.R
import com.example.modumessenger.core.ui.components.ModuTopBar

/** 친구 찾기(부록 A §19). `채팅 하기` 는 방을 만들어 방으로 간다. */
@Composable
fun FindFriendsScreen(
    onBack: () -> Unit,
    onOpenProfile: (Long) -> Unit,
    onOpenRoom: (String) -> Unit,
    viewModel: FindFriendsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val names by viewModel.names.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                message.arg?.let { context.getString(message.res, it) } ?: context.getString(message.res),
            )
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.openRoom.collect(onOpenRoom)
    }

    Scaffold(
        topBar = {
            ModuTopBar(title = stringResource(R.string.find_friends_title), onBack = onBack)
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            FriendSearchField(
                query = uiState.query,
                onQueryChange = viewModel::onQueryChange,
                onSubmit = viewModel::search,
            )
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(uiState.results, key = { it.id }) { member ->
                    FriendRow(
                        member = member,
                        names = names,
                        onClick = { onOpenProfile(member.id) },
                    ) {
                        if (member.userId != uiState.myUserId) {
                            TextButton(onClick = { viewModel.startChat(member) }) {
                                Text(stringResource(R.string.find_friends_chat_button))
                            }
                        }
                    }
                }
            }
        }
    }
}
