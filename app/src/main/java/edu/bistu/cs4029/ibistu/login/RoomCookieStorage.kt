package edu.bistu.cs4029.ibistu.login

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie

/**
 * 基于 Room 的 [CookieStorage] 实现。
 *
 * 将 [BistuLogin] 的 Cookie 持久化到独立的 bistulogin.db 中。
 */
class RoomCookieStorage(private val dao: CookieDao) : CookieStorage {

    override suspend fun loadAll(): List<Cookie> = withContext(Dispatchers.IO) {
        dao.getAllCookies().map { it.toOkHttpCookie() }
    }

    override suspend fun saveAll(cookies: List<Cookie>) = withContext(Dispatchers.IO) {
        val entities = cookies.map { CookieEntity.fromOkHttpCookie(it) }
        dao.clearAll()
        dao.insertAll(entities)
    }

    override suspend fun clearAll() = withContext(Dispatchers.IO) {
        dao.clearAll()
    }
}
