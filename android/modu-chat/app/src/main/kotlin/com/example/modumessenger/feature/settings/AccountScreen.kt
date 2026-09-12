package com.example.modumessenger.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.modumessenger.R
import com.example.modumessenger.core.ui.components.ConfirmDialog
import com.example.modumessenger.core.ui.components.ModuTopBar
import com.example.modumessenger.core.ui.theme.ModuRed
import com.example.modumessenger.feature.login.GoogleSignInHelper

/** 계정 설정(부록 A §22). 버튼 하나, 확인 팝업, 그리고 로그인 화면으로. */
@Composable
fun AccountScreen(
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val isLoggingOut by viewModel.isLoggingOut.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.loggedOut.collect {
            // 다음 로그인 때 무음 로그인이 옛 계정으로 붙지 않도록 구글 쪽도 끊는다.
            GoogleSignInHelper.signOut(context)
            onLoggedOut()
        }
    }

    Scaffold(
        topBar = { ModuTopBar(title = stringResource(R.string.account_title), onBack = onBack) },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Button(
                onClick = { showDialog = true },
                enabled = !isLoggingOut,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(stringResource(R.string.account_logout))
            }
        }
    }

    if (showDialog) {
        ConfirmDialog(
            title = stringResource(R.string.account_logout),
            text = stringResource(R.string.account_logout_message),
            confirmText = stringResource(R.string.account_logout),
            dismissText = stringResource(R.string.account_cancel),
            onConfirm = {
                showDialog = false
                viewModel.logout()
            },
            onDismiss = { showDialog = false },
            confirmColor = ModuRed,
        )
    }
}
