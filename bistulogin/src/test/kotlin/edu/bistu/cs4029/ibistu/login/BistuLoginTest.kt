package edu.bistu.cs4029.ibistu.login

import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

/** BistuLogin 的 SSO/CAS 会话行为回归测试。 */
class BistuLoginTest {

    /** casLogin 必须始终从 SSO 发起，并将业务入口作为 service 参数。 */
    @Test
    fun casLoginAlwaysStartsFromSsoWithEndpointAsService() = runBlocking {
        val requestUrl = AtomicReference<String>()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                requestUrl.set(chain.request().url.toString())
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("".toResponseBody())
                    .build()
            }
            .build()
        val login = BistuLogin(injectedRedirectClient = client)

        login.casLogin(JWXT_ENDPOINT)

        val url = requireNotNull(requestUrl.get()).toHttpUrl()
        assertEquals("${BistuLogin.SSO_BASE}/login", "${url.scheme}://${url.host}${url.encodedPath}")
        assertEquals(JWXT_ENDPOINT.casLoginUrl, url.queryParameter("service"))
    }

    /** 带有效 ticket 的目标业务入口重定向应被接受。 */
    @Test
    fun verifySessionAcceptsTicketRedirectToConfiguredServiceEndpoint() = runBlocking {
        val login = BistuLogin(
            injectedClient = redirectResponseClient(
                "${JWXT_ENDPOINT.casLoginUrl}?ticket=ST-123"
            )
        )

        assertTrue(login.verifySession())
    }

    /** 返回 SSO 登录页的重定向不代表业务会话有效。 */
    @Test
    fun verifySessionRejectsRedirectBackToSso() = runBlocking {
        val login = BistuLogin(
            injectedClient = redirectResponseClient(
                "${BistuLogin.SSO_BASE}/login?service=unexpected"
            )
        )

        assertFalse(login.verifySession())
    }

    /** 目标业务入口未携带 ticket 时必须判定为无效。 */
    @Test
    fun verifySessionRejectsRedirectWithoutTicket() = runBlocking {
        val login = BistuLogin(
            injectedClient = redirectResponseClient(
                "${JWXT_ENDPOINT.casLoginUrl}?error=expired"
            )
        )

        assertFalse(login.verifySession())
    }

    /** 与业务入口仅有字符串前缀关系的其他路径必须判定为无效。 */
    @Test
    fun verifySessionRejectsServiceUrlPrefixCollision() = runBlocking {
        val login = BistuLogin(
            injectedClient = redirectResponseClient(
                "${JWXT_ENDPOINT.casLoginUrl}.unexpected?ticket=ST-123"
            )
        )

        assertFalse(login.verifySession())
    }

    private fun redirectResponseClient(location: String): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(302)
                    .message("Found")
                    .header("Location", location)
                    .body("".toResponseBody())
                    .build()
            }
            .build()
}
