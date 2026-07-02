package edu.bistu.cs4029.ibistu

import edu.bistu.cs4029.ibistu.login.BistuLogin
import edu.bistu.cs4029.ibistu.login.LoginLogger
import edu.bistu.cs4029.ibistu.testing.MockResponses
import edu.bistu.cs4029.ibistu.testing.MockServerTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * BISTU SSO 登录流程的仪器测试。
 *
 * 使用 MockWebServer 模拟 SSO 服务端响应，验证登录全流程的解析逻辑。
 */
class LoginInstrumentedTest {

    @get:Rule
    val server = MockServerTestRule()

    private val testLogger = object : LoginLogger {
        override fun debug(msg: String) { println("[TEST] $msg") }
        override fun info(msg: String) { println("[TEST] $msg") }
        override fun warn(msg: String) { println("[TEST] $msg") }
        override fun error(msg: String) { println("[TEST] $msg") }
    }

    // ── 登录成功 ──────────────────────────────────────────────

    @Test
    fun fullLogin_success() = runTest {
        // getPublicKey: 1) redirectClient GET /login?service=... → 2) client GET /api/reset/rules
        server.enqueueJson("{}")                                  // redirect prime (body ignored)
        server.enqueueJson(MockResponses.PUBLIC_KEY_RESPONSE)     // /api/reset/rules
        // infoQuery
        server.enqueueJson(MockResponses.INFO_QUERY_RESPONSE)     // /info-query
        // login
        server.enqueueJson(MockResponses.LOGIN_SUCCESS_RESPONSE)  // /username-password/login
        // jwxtLogin
        server.enqueueJson("{}")                                  // casLogin.do (body ignored)

        val login = BistuLogin(
            logger = testLogger,
            injectedRedirectClient = server.newRedirectClient(),
            injectedClient = server.newClient()
        )

        val result = login.fullLogin("114514", "testPassword")

        assertTrue("Login should succeed", result.isSuccess)
        assertEquals(666666, result.code)
        assertEquals("登录成功", result.message)
        assertNotNull(result.serviceUrl)
    }

    // ── 登录失败 ──────────────────────────────────────────────

    @Test
    fun fullLogin_failure() = runTest {
        // getPublicKey
        server.enqueueJson("{}")
        server.enqueueJson(MockResponses.PUBLIC_KEY_RESPONSE)
        // infoQuery
        server.enqueueJson(MockResponses.INFO_QUERY_RESPONSE)
        // login (fails → jwxtLogin skipped)
        server.enqueueJson(MockResponses.LOGIN_FAILURE_RESPONSE)

        val login = BistuLogin(
            logger = testLogger,
            injectedRedirectClient = server.newRedirectClient(),
            injectedClient = server.newClient()
        )

        val result = login.fullLogin("bad_user", "wrong_pwd")

        assertFalse("Login should fail", result.isSuccess)
        assertEquals(170002, result.code)
    }

    // ── 单独获取公钥 ──────────────────────────────────────────

    @Test
    fun getPublicKey_parsesCorrectly() = runTest {
        // getPublicKey 发送 2 个请求：redirect prime + rules
        server.enqueueJson("{}")
        server.enqueueJson(MockResponses.PUBLIC_KEY_RESPONSE)

        val login = BistuLogin(
            logger = testLogger,
            injectedRedirectClient = server.newRedirectClient(),
            injectedClient = server.newClient()
        )

        val publicKey = login.getPublicKey()

        assertEquals(
            "BHGxY2MkSYNhEEwqNHpo2L4KQHNaJ4/r6lVKOWnKxHNfnE5OV/Rg+6qP+oNp0Q5PYNx8p0BBqDRfZxkAL9gO3As=",
            publicKey
        )
    }

    // ── encryptPassword 不依赖网络 ────────────────────────────

    @Test
    fun encryptPassword_producesBase64Output() {
        val login = BistuLogin(logger = testLogger)

        val encrypted = login.encryptPassword(
            "password123",
            "BHGxY2MkSYNhEEwqNHpo2L4KQHNaJ4/r6lVKOWnKxHNfnE5OV/Rg+6qP+oNp0Q5PYNx8p0BBqDRfZxkAL9gO3As="
        )

        assertNotNull(encrypted)
        assertTrue("Encrypted password should be non-empty Base64", encrypted.isNotBlank())
        // Base64 只包含合法字符
        assertTrue(encrypted.matches(Regex("^[A-Za-z0-9+/=]+$")))
    }
}
