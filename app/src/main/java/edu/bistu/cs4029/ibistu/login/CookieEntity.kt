package edu.bistu.cs4029.ibistu.login

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import okhttp3.Cookie

/**
 * Room 实体：持久化的 HTTP Cookie
 *
 * 以 (name, domain, path) 为逻辑唯一键，insert 时 upsert。
 */
@Entity(
    tableName = "cookies",
    indices = [Index(value = ["name", "domain", "path"], unique = true)]
)
data class CookieEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "value") val value: String,
    @ColumnInfo(name = "domain") val domain: String,
    @ColumnInfo(name = "path") val path: String = "/",
    @ColumnInfo(name = "expires_at") val expiresAt: Long = 0,  // epoch millis, 0 = session
    @ColumnInfo(name = "secure") val secure: Boolean = false,
    @ColumnInfo(name = "http_only") val httpOnly: Boolean = false,
    @ColumnInfo(name = "host_only") val hostOnly: Boolean = false,
    @ColumnInfo(name = "persistent") val persistent: Boolean = false,
) {
    companion object {
        fun fromOkHttpCookie(cookie: Cookie): CookieEntity {
            return CookieEntity(
                name = cookie.name,
                value = cookie.value,
                domain = cookie.domain,
                path = cookie.path ?: "/",
                expiresAt = cookie.expiresAt,
                secure = cookie.secure,
                httpOnly = cookie.httpOnly,
                hostOnly = cookie.hostOnly,
                persistent = cookie.persistent,
            )
        }
    }

    fun toOkHttpCookie(): Cookie {
        val builder = Cookie.Builder()
            .name(name)
            .value(value)
            .path(path)
            .expiresAt(expiresAt)

        // OkHttp 5.x: hostOnly 通过 hostOnlyDomain / domain 区分
        if (hostOnly) {
            builder.hostOnlyDomain(domain)
        } else {
            builder.domain(domain)
        }

        if (secure) builder.secure()
        if (httpOnly) builder.httpOnly()

        return builder.build()
    }
}
