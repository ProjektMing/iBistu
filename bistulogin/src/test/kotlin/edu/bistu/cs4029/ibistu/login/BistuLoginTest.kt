package edu.bistu.cs4029.ibistu.login

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BistuLoginTest {

    @Test
    fun `verifySession accepts ticket redirect to configured service endpoint`() = runBlocking {
        val login = BistuLogin(
            injectedClient = redirectResponseClient(
                "${JWXT_ENDPOINT.casLoginUrl}?ticket=ST-123"
            )
        )

        assertTrue(login.verifySession())
    }

    @Test
    fun `verifySession rejects redirect back to SSO`() = runBlocking {
        val login = BistuLogin(
            injectedClient = redirectResponseClient(
                "${BistuLogin.SSO_BASE}/login?service=unexpected"
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
