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

/** 친구 추가(부록 A §18). */
@Composable
fun SearchFriendsScreen(
    onBack: () -> Unit,
    onOpenProfile: (Long) -> Unit,
    viewModel: SearchFriendsViewModel = hiltViewModel(),
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

    Scaffold(
        topBar = {
            ModuTopBar(title = stringResource(R.string.search_friends_title), onBack = onBack)
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
                    val isMe = member.userId == uiState.myUserId
                    val alreadyAdded = member.id in uiState.addedMemberIds
                    FriendRow(
                        member = member,
                        names = names,
                        onClick = { onOpenProfile(member.id) },
                    ) {
                        if (!isMe && !alreadyAdded) {
                            TextButton(onClick = { viewModel.addFriend(member) }) {
                                Text(stringResource(R.string.add_friend_button))
                            }
                        }
                    }
                }
            }
        }
    }
}
