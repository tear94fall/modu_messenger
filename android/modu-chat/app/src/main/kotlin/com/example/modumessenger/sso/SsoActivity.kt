package com.example.modumessenger.sso

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.example.modumessenger.R
import com.example.modumessenger.core.session.SessionStore
import com.example.modumessenger.core.ui.theme.ModuTheme
import com.example.modumessenger.core.util.SsoRequest
import com.example.modumessenger.data.dto.SsoCodeRequestDto
import com.example.modumessenger.data.repository.AuthRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 다른 모두 앱(커머스)이 "이 사람 모두 계정으로 로그인시켜 줘" 라고 부를 때 뜨는 화면(부록 A §3).
 *
 * 화면은 대화상자뿐이고(투명 테마), 액세스 토큰은 절대 넘기지 않는다 — 한 번 쓰고 버리는 코드만 준다.
 * 실패는 전부 `RESULT_CANCELED` + `reason` extra 로 알린다.
 */
@AndroidEntryPoint
class SsoActivity : ComponentActivity() {

    @Inject lateinit var authRepository: AuthRepository

    @Inject lateinit var sessionStore: SessionStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 서명 권한만으로는 부족하다. 부르는 앱 이름도 확인한다(호출자는 startActivityForResult 를 써야 한다).
        if (!SsoRequest.isAllowedCaller(callingPackage)) {
            cancel(REASON_CALLER_NOT_ALLOWED)
            return
        }

        val request = SsoRequest.from(
            clientId = intent.getStringExtra(EXTRA_CLIENT_ID),
            codeChallenge = intent.getStringExtra(EXTRA_CODE_CHALLENGE),
            codeChallengeMethod = intent.getStringExtra(EXTRA_CODE_CHALLENGE_METHOD),
        )
        if (request == null) {
            cancel(REASON_BAD_REQUEST)
            return
        }

        setContent {
            ModuTheme {
                var ready by remember { mutableStateOf(false) }
                var working by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    val loggedIn = sessionStore.memberNow() != null &&
                        !sessionStore.accessToken().isNullOrBlank()
                    if (loggedIn) ready = true else cancel(REASON_NOT_LOGGED_IN)
                }

                if (!ready) return@ModuTheme

                AlertDialog(
                    onDismissRequest = { if (!working) cancel(REASON_DENIED) },
                    title = { Text(stringResource(R.string.sso_dialog_title)) },
                    text = { Text(stringResource(R.string.sso_dialog_message)) },
                    confirmButton = {
                        TextButton(
                            enabled = !working,
                            onClick = { working = true },
                        ) { Text(stringResource(R.string.sso_allow)) }
                    },
                    dismissButton = {
                        TextButton(
                            enabled = !working,
                            onClick = { cancel(REASON_DENIED) },
                        ) { Text(stringResource(R.string.sso_deny)) }
                    },
                )

                LaunchedEffect(working) {
                    if (!working) return@LaunchedEffect
                    authRepository
                        .issueSsoCode(
                            SsoCodeRequestDto(
                                clientId = request.clientId,
                                codeChallenge = request.codeChallenge,
                                codeChallengeMethod = request.codeChallengeMethod,
                            ),
                        )
                        .onSuccess { response ->
                            val code = response.code
                            if (code.isNullOrBlank()) cancel(REASON_SERVER_ERROR) else succeed(code)
                        }
                        .onFailure { cancel(REASON_SERVER_ERROR) }
                }
            }
        }
    }

    private fun succeed(code: String) {
        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_CODE, code))
        finish()
    }

    private fun cancel(reason: String) {
        setResult(Activity.RESULT_CANCELED, Intent().putExtra(EXTRA_REASON, reason))
        finish()
    }

    companion object {
        const val EXTRA_CLIENT_ID = "client_id"
        const val EXTRA_CODE_CHALLENGE = "code_challenge"
        const val EXTRA_CODE_CHALLENGE_METHOD = "code_challenge_method"
        const val EXTRA_CODE = "code"
        const val EXTRA_REASON = "reason"

        const val REASON_CALLER_NOT_ALLOWED = "caller_not_allowed"
        const val REASON_BAD_REQUEST = "bad_request"
        const val REASON_NOT_LOGGED_IN = "not_logged_in"
        const val REASON_DENIED = "denied"
        const val REASON_SERVER_ERROR = "server_error"
    }
}
