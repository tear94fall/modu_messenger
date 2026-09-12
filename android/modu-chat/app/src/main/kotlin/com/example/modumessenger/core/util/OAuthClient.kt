package com.example.modumessenger.core.util

/** auth-service 토큰 발급 폼. 문자열은 서버 `AuthorizationServerConfig` 에 등록된 값과 정확히 같아야 한다. */
object OAuthClient {

    const val CLIENT_ID = "modu-chat"
    const val GRANT_GOOGLE = "urn:modu:params:oauth:grant-type:google_id_token"
    const val GRANT_REFRESH = "refresh_token"

    fun googleForm(idToken: String): Map<String, String> = mapOf(
        "grant_type" to GRANT_GOOGLE,
        "client_id" to CLIENT_ID,
        "id_token" to idToken,
    )

    fun refreshForm(refreshToken: String?): Map<String, String> = mapOf(
        "grant_type" to GRANT_REFRESH,
        "client_id" to CLIENT_ID,
        "refresh_token" to stripBearer(refreshToken),
    )

    /** 기존 앱은 DataStore 에 `"Bearer xxx"` 로 저장했다. 폼에는 앞머리를 뗀 값이 들어가야 한다. */
    fun stripBearer(stored: String?): String = when {
        stored == null -> ""
        stored.startsWith("Bearer ") -> stored.substring(7)
        else -> stored
    }
}
