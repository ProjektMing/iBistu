package example

import edu.bistu.cs4029.ibistu.login.CookieStorage
import okhttp3.Cookie

/**
 * [CookieStorage] 的内存实现（仅供示例，不持久化）。
 *
 * 实际项目中应替换为 Room / 文件 / Redis 等持久化方案。
 */
class InMemoryCookieStorage : CookieStorage {

    private val store = mutableListOf<Cookie>()

    override suspend fun loadAll(): List<Cookie> = store.toList()

    override suspend fun saveAll(cookies: List<Cookie>) {
        store.clear()
        store.addAll(cookies)
    }

    override suspend fun clearAll() {
        store.clear()
    }
}
