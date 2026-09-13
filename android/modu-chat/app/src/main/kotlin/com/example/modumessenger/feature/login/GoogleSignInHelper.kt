package com.example.modumessenger.feature.login

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.modumessenger.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import com.google.android.gms.common.api.ApiException as GmsApiException

/**
 * 구글 로그인 한 곳. `ActivityResultLauncher` 와 무음 로그인을 감싼다(부록 A §2).
 *
 * `GoogleSignInOptions` 는 기존 앱과 같다: `DEFAULT_SIGN_IN` + `requestIdToken(default_web_client_id)` +
 * `requestEmail()`. 웹 클라이언트 id 는 `google-services.json` 에서 자동 생성되는 문자열 리소스다.
 */
class GoogleSignInHelper internal constructor(
    private val client: GoogleSignInClient,
    private val context: Context,
    private val launch: () -> Unit,
) {

    /** 버튼을 눌렀을 때. 구글 계정 선택 화면을 띄운다. */
    fun signIn() = launch()

    /** 예전에 이 기기에서 로그인한 적이 있으면 그 계정. 무음 로그인 가능 여부를 이것으로 판단한다. */
    fun lastAccount(): GoogleSignInAccount? = GoogleSignIn.getLastSignedInAccount(context)

    /** 화면 없이 다시 로그인한다. 실패하면 실패 이유(구글 상태 코드)를 담아 돌려준다. */
    suspend fun silentSignIn(): Result<GoogleSignInAccount> = suspendCancellableCoroutine { cont ->
        client.silentSignIn()
            .addOnSuccessListener { account -> cont.resume(Result.success(account)) }
            .addOnFailureListener { error -> cont.resume(Result.failure(error)) }
    }

    fun signOut() {
        client.signOut()
    }

    companion object {

        fun client(context: Context): GoogleSignInClient {
            val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(context.getString(R.string.default_web_client_id))
                .requestEmail()
                .build()
            return GoogleSignIn.getClient(context, options)
        }

        /** 로그아웃(계정 설정 화면)에서 쓴다. */
        fun signOut(context: Context) {
            client(context).signOut()
        }

        /** 구글 쪽 실패의 상태 코드. 모르면 null. */
        fun statusCodeOf(error: Throwable?): Int? = (error as? GmsApiException)?.statusCode
    }
}

/**
 * [onAccount] 는 계정 선택이 끝났을 때, [onFailure] 는 취소·실패했을 때(구글 상태 코드가 있으면 같이).
 */
@Composable
fun rememberGoogleSignInHelper(
    onAccount: (GoogleSignInAccount) -> Unit,
    onFailure: (Int?) -> Unit,
): GoogleSignInHelper {
    val context = LocalContext.current
    val client = remember(context) { GoogleSignInHelper.client(context) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        runCatching { task.getResult(GmsApiException::class.java) }
            .onSuccess(onAccount)
            .onFailure { error -> onFailure(GoogleSignInHelper.statusCodeOf(error)) }
    }
    return remember(client, context, launcher) {
        GoogleSignInHelper(client, context) { launcher.launch(client.signInIntent) }
    }
}
