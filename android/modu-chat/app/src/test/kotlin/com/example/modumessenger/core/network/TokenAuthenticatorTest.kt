package com.example.modumessenger.core.network

import com.example.modumessenger.core.session.SessionEvents
import com.example.modumessenger.core.session.SessionStore
import com.example.modumessenger.data.api.AuthApi
import com.google.gson.GsonBuilder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class TokenAuthenticatorTest {

    private lateinit var server: MockWebServer
    private lateinit var sessionStore: SessionStore
    private lateinit var sessionEvents: SessionEvents
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        sessionStore = mockk(relaxed = true)
        coEvery { sessionStore.accessToken() } returns OLD_TOKEN
        coEvery { sessionStore.refreshToken() } returns REFRESH_TOKEN

        sessionEvents = SessionEvents()

        val plainAuthApi = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create(GsonBuilder().setLenient().create()))
            .build()
            .create(AuthApi::class.java)

        client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(sessionStore))
            .authenticator(TokenAuthenticator(sessionStore, sessionEvents, plainAuthApi))
            .build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `401 이면 토큰을 갱신하고 새 토큰으로 한 번 재시도한다`() {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"access_token":"$NEW_TOKEN","refresh_token":"new-refresh","expires_in":3600,"token_type":"Bearer"}""",
            ),
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        val response = client.newCall(get("member-service/api-public/member/a@b.c")).execute()

        assertEquals(200, response.code)
        assertEquals("ok", response.body?.string())
        assertEquals(3, server.requestCount)

        val first = server.takeRequest()
        assertEquals("Bearer $OLD_TOKEN", first.getHeader("Authorization"))

        val refresh = server.takeRequest()
        assertEquals("/auth-service/oauth2/token", refresh.path)
        val form = refresh.body.readUtf8()
        assertTrue(form.contains("grant_type=refresh_token"))
        assertTrue(form.contains("client_id=modu-chat"))
        assertTrue(form.contains("refresh_token=$REFRESH_TOKEN"))

        val retried = server.takeRequest()
        assertEquals("Bearer $NEW_TOKEN", retried.getHeader("Authorization"))

        coVerify(exactly = 1) { sessionStore.saveTokens(NEW_TOKEN, "new-refresh") }
    }

    @Test
    fun `갱신이 실패하면 세션을 지우고 재시도하지 않는다`() {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"invalid_grant"}"""))

        val response = client.newCall(get("member-service/api-public/member/a@b.c")).execute()

        assertEquals(401, response.code)
        assertEquals(2, server.requestCount)
        coVerify(exactly = 1) { sessionStore.clearSession() }
        coVerify(exactly = 0) { sessionStore.saveTokens(any(), any()) }
    }

    @Test
    fun `리프레시 토큰이 없으면 바로 포기한다`() {
        coEvery { sessionStore.refreshToken() } returns null
        server.enqueue(MockResponse().setResponseCode(401))

        val response = client.newCall(get("member-service/api-public/member/a@b.c")).execute()

        assertEquals(401, response.code)
        assertEquals(1, server.requestCount)
        coVerify(exactly = 1) { sessionStore.clearSession() }
    }

    private fun get(path: String): Request =
        Request.Builder().url(server.url("/$path")).build()

    private companion object {
        const val OLD_TOKEN = "old-access"
        const val NEW_TOKEN = "new-access"
        const val REFRESH_TOKEN = "refresh-jwt"
    }
}
