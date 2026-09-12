package com.example.modumessenger.feature.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.modumessenger.R
import com.example.modumessenger.core.ui.components.ModuTopBar
import com.example.modumessenger.feature.friends.FriendRow

/** 친구 초대(부록 A §11). */
@Composable
fun InviteScreen(
    onBack: () -> Unit,
    onOpenProfile: (Long) -> Unit,
    viewModel: InviteViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val names by viewModel.names.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(context.getString(message.res, *message.args.toTypedArray()))
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.finished.collect { onBack() }
    }

    Scaffold(
        topBar = { ModuTopBar(title = stringResource(R.string.invite_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(uiState.friends, key = { _, member -> member.id }) { index, member ->
                    LaunchedEffect(index, uiState.friends.size) { viewModel.onItemAppeared(index) }
                    FriendRow(
                        member = member,
                        names = names,
                        onClick = { onOpenProfile(member.id) },
                    ) {
                        Checkbox(
                            checked = member.userId in uiState.selected,
                            onCheckedChange = { viewModel.toggle(member.userId) },
                        )
                    }
                }
            }
            Button(
                onClick = viewModel::invite,
                enabled = !uiState.isInviting,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                Text(stringResource(R.string.invite_button))
            }
        }
    }
}
