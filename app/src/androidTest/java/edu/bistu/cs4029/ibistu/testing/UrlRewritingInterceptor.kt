package edu.bistu.cs4029.ibistu.testing

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp Interceptor：将所有请求重写到目标 [targetBase] URL。
 *
 * 只替换 scheme / host / port，保留原始 path、query、fragment，
 * 这样 MockWebServer 可以根据不同的 path 来匹配并返回预置的响应。
 */
class UrlRewritingInterceptor(private val targetBase: HttpUrl) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val originalUrl = original.url

        val newUrl = originalUrl.newBuilder()
            .scheme(targetBase.scheme)
            .host(targetBase.host)
            .port(targetBase.port)
            .build()

        val newRequest = original.newBuilder()
            .url(newUrl)
            .build()

        return chain.proceed(newRequest)
    }
}
