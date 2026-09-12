package com.example.modumessenger.feature.settings

import app.cash.turbine.test
import com.example.modumessenger.data.repository.CommonRepository
import com.example.modumessenger.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AppInfoViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeCommonRepository(
        private val result: Result<String>,
    ) : CommonRepository {
        var calls = 0
            private set

        override suspend fun getServerVersion(): Result<String> {
            calls++
            return result
        }
    }

    @Test
    fun `서버 버전을 받아 오면 흘려 보낸다`() = runTest {
        val repository = FakeCommonRepository(Result.success("1.2.3"))
        val viewModel = AppInfoViewModel(repository)

        viewModel.serverVersion.test {
            assertEquals("", awaitItem())
            assertEquals("1.2.3", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, repository.calls)
    }

    @Test
    fun `값이 없으면 빈 문자열 그대로다`() = runTest {
        val viewModel = AppInfoViewModel(FakeCommonRepository(Result.success("")))
        advanceUntilIdle()

        assertEquals("", viewModel.serverVersion.value)
    }

    @Test
    fun `조회가 실패해도 빈 문자열로 남는다`() = runTest {
        val viewModel = AppInfoViewModel(FakeCommonRepository(Result.failure(IOException("offline"))))
        advanceUntilIdle()

        assertEquals("", viewModel.serverVersion.value)
    }
}
