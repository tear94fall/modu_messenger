package com.example.modumessenger.feature.login

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.modumessenger.R
import com.example.modumessenger.core.ui.components.BrandingHeader
import com.example.modumessenger.core.ui.components.ModuTopBar
import com.example.modumessenger.core.ui.theme.GoogleButtonStroke
import com.example.modumessenger.core.ui.theme.GoogleButtonText
import android.content.Context
import androidx.annotation.StringRes

/**
 * 로그인 화면(부록 A §2, 스펙 §6 login).
 * 버튼은 구글 브랜드 가이드(라이트)를 따른다: 흰 바탕, 1dp `#747775` 테두리, `#1F1F1F` 16sp medium.
 */
@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val helper = rememberGoogleSignInHelper(
        onAccount = { account -> viewModel.onGoogleAccount(account.idToken, account.email) },
        onFailure = viewModel::onGoogleSignInFailed,
    )

    // 진입 시 무음 로그인. 마지막 계정이 없으면 아무것도 하지 않는다.
    LaunchedEffect(helper) {
        if (helper.lastAccount() == null) return@LaunchedEffect
        viewModel.onSilentSignInStarted()
        helper.silentSignIn()
            .onSuccess { account -> viewModel.onGoogleAccount(account.idToken, account.email) }
            .onFailure { viewModel.onSilentSignInFailed() }
    }

    LaunchedEffect(uiState) {
        if (uiState is LoginUiState.Success) onLoggedIn()
    }

    // 안내 문구와 오류 문구 모두 스낵바로 띄운다(기존 앱의 토스트 자리).
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(context.format(message.messageRes, message.code))
        }
    }

    // 진짜 실패였다면 구글에서도 로그아웃한다(기존 앱과 같다).
    LaunchedEffect(viewModel, context) {
        viewModel.signOutRequests.collect { GoogleSignInHelper.signOut(context) }
    }

    val error = uiState as? LoginUiState.Error
    LaunchedEffect(error) {
        if (error != null) {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(context.format(error.messageRes, error.code))
        }
    }

    Scaffold(
        // 기존 앱의 액션바와 같은 자리·같은 제목.
        topBar = { ModuTopBar(title = stringResource(R.string.login_title)) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(modifier = Modifier.weight(1f))
            // 스플래시와 똑같은 브랜딩 블록.
            BrandingHeader()
            Spacer(modifier = Modifier.weight(1f))

            // 로그인 중에는 버튼을 숨긴다(기존 앱의 INVISIBLE 과 같은 동작).
            if (uiState !is LoginUiState.Signing && uiState !is LoginUiState.Success) {
                GoogleSignInButton(
                    onClick = {
                        viewModel.onSignInClicked()
                        helper.signIn()
                    },
                )
            } else {
                Spacer(modifier = Modifier.height(56.dp))
            }
        }
    }
}

@Composable
private fun GoogleSignInButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .height(56.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = GoogleButtonText,
        ),
        border = BorderStroke(1.dp, GoogleButtonStroke),
        contentPadding = ButtonDefaults.ContentPadding,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_google_g),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = Color.Unspecified,
        )
        Spacer(modifier = Modifier.size(12.dp))
        Text(
            text = stringResource(R.string.login_with_google),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = GoogleButtonText,
        )
    }
}

/** 포맷 인자가 있는 문구와 없는 문구를 한 자리에서 만든다. */
private fun Context.format(@StringRes res: Int, arg: Int?): String =
    if (arg == null) getString(res) else getString(res, arg)
