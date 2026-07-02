package edu.bistu.cs4029.ibistu.testing

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * JUnit TestRule 封装 MockWebServer，自动管理启动/关闭。
 *
 * 用法：
 * ```
 * @get:Rule
 * val server = MockServerTestRule()
 *
 * val login = BistuLogin(
 *     injectedClient = server.newClient(),
 *     injectedRedirectClient = server.newRedirectClient()
 * )
 * ```
 */
class MockServerTestRule : TestRule {

    val mockWebServer = MockWebServer()

    /** MockWebServer 的 base URL（e.g. http://127.0.0.1:54321） */
    val baseUrl: HttpUrl get() = mockWebServer.url("/")

    /** 便捷：入队一个 MockResponse */
    fun enqueue(response: MockResponse) {
        mockWebServer.enqueue(response)
    }

    /** 便捷：入队一个 JSON 响应（200 OK）。 */
    fun enqueueJson(body: String) {
        mockWebServer.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .body(body)
                .build()
        )
    }

    /** 便捷：入队一个重定向响应。 */
    fun enqueueRedirect(location: String) {
        mockWebServer.enqueue(
            MockResponse.Builder()
                .code(302)
                .addHeader("Location", location)
                .build()
        )
    }

    /**
     * 创建一个 OkHttpClient，其所有请求都会被重写到 MockWebServer 的地址。
     * 不跟随重定向（对应 BistuLogin.client）。
     */
    fun newClient(followRedirects: Boolean = false): OkHttpClient {
        return OkHttpClient.Builder()
            .followRedirects(followRedirects)
            .addInterceptor(UrlRewritingInterceptor(baseUrl))
            .build()
    }

    /** 创建一个跟随重定向的 client（对应 BistuLogin.redirectClient）。 */
    fun newRedirectClient(): OkHttpClient = newClient(followRedirects = true)

    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                mockWebServer.start()
                try {
                    base.evaluate()
                } finally {
                    mockWebServer.close()
                }
            }
        }
    }
}
