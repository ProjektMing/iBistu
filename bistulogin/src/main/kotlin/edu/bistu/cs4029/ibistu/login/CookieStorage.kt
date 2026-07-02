package edu.bistu.cs4029.ibistu.login

import okhttp3.Cookie

/**
 * Cookie 持久化策略接口。
 *
 * 库自身不绑定任何存储实现；调用方通过此接口注入（Room / 内存 / 文件 / 数据库等），
 * 即可在不依赖 Android SDK 的情况下完成 Cookie 的持久化与恢复。
 */
interface CookieStorage {

    /** 加载已持久化的所有 Cookie */
    suspend fun loadAll(): List<Cookie>

    /** 保存 Cookie（全量覆盖） */
    suspend fun saveAll(cookies: List<Cookie>)

    /** 清空所有持久化 Cookie */
    suspend fun clearAll()
}
