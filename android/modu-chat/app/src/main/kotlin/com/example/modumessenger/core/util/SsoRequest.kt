package com.example.modumessenger.core.util

/** 다른 앱이 SSO 코드를 요청할 때 넘긴 값 검증. 허용 앱·클라이언트만, PKCE 챌린지는 S256 만 받는다. */
data class SsoRequest(
    val clientId: String,
    val codeChallenge: String,
    val codeChallengeMethod: String,
) {
    companion object {
        const val ACTION = "com.example.modumessenger.action.REQUEST_SSO_CODE"
        val ALLOWED_CALLERS = listOf("com.example.moducommerce")
        val ALLOWED_CLIENTS = listOf("modu-commerce")
        private val CHALLENGE = Regex("^[A-Za-z0-9_-]{43,128}$")

        /** 유효하지 않으면 null. */
        fun from(clientId: String?, codeChallenge: String?, codeChallengeMethod: String?): SsoRequest? {
            if (clientId == null || clientId !in ALLOWED_CLIENTS) return null
            if (codeChallenge == null || !CHALLENGE.matches(codeChallenge)) return null
            if (codeChallengeMethod != "S256") return null
            return SsoRequest(clientId, codeChallenge, codeChallengeMethod)
        }

        fun isAllowedCaller(callingPackage: String?): Boolean =
            callingPackage != null && callingPackage in ALLOWED_CALLERS
    }
}
