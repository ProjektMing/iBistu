package edu.bistu.cs4029.ibistu.login

import com.tencent.kona.crypto.KonaCryptoProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.math.BigInteger
import java.security.KeyFactory
import java.security.Security
import java.security.spec.ECFieldFp
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.security.spec.EllipticCurve
import javax.crypto.Cipher
import java.util.Base64

/**
 * BISTU SSO 登录管理（CAS 协议变体 + SM2 密码加密）
 *
 * 完整流程：
 *   1. GET  /api/reset/rules          → 获取 SM2 公钥 + 建立 session cookie
 *   2. POST /info-query               → 查询是否需要验证码 / MFA
 *   3. SM2 加密密码
 *   4. POST /username-password/login  → 登录，获取 TGC Cookie
 *   5. GET  /login?service=<target>   → 302 换取 ST ticket
 *   6. 携带 ticket 访问目标系统
 *
 * @param injectedRedirectClient 会跟随重定向的 OkHttp 客户端（测试可注入指向 MockWebServer 的实例）
 * @param injectedClient         不跟随重定向的 OkHttp 客户端（测试可注入指向 MockWebServer 的实例）
 */
class BistuLogin(
    private val cookieStorage: CookieStorage? = null,
    private val logger: LoginLogger = LoginLogger.NONE,
    injectedRedirectClient: OkHttpClient? = null,
    injectedClient: OkHttpClient? = null
) {

    companion object {
        const val SSO_BASE = "https://sso.bistu.edu.cn"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        init {
            if (Security.getProvider("KonaCrypto") == null) {
                Security.addProvider(KonaCryptoProvider())
            }
        }

        /** 已注册的 CAS 端点列表（所有实例共享） */
        private val _endpoints = mutableListOf(JWXT_ENDPOINT)
        val casEndpoints: List<CasEndpoint> get() = _endpoints.toList()

        /** 注册一个 CAS 登录端点 */
        fun addEndpoint(endpoint: CasEndpoint) {
            if (_endpoints.none { it.casLoginUrl == endpoint.casLoginUrl }) {
                _endpoints.add(endpoint)
            }
        }

        /** 便捷方法：注册 JWXT 端点 */
        fun addJwxtEndpoint() { addEndpoint(JWXT_ENDPOINT) }

        /** @see addJwxtEndpoint */
        fun addJwxtLogin() { addJwxtEndpoint() }

        /** DSL 风格配置：BistuLogin { addJwxtLogin() } */
        operator fun invoke(block: Companion.() -> Unit) { block() }
    }


    // ── Cookie 管理 ──────────────────────────────────────────

    private val cookieStore = mutableListOf<Cookie>()

    private val cookieJar = object : CookieJar {
        override fun loadForRequest(url: HttpUrl): List<Cookie> =
            cookieStore.filter { it.matches(url) }

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookies.forEach { newCookie ->
                cookieStore.removeAll { it.name == newCookie.name && it.matches(url) }
                cookieStore.add(newCookie)
            }
        }
    }

    /** 会跟随重定向的 client */
val redirectClient: OkHttpClient =
    (injectedRedirectClient?.newBuilder() ?: OkHttpClient.Builder())
        .cookieJar(cookieJar)
        .followRedirects(true)
        .build()
    /** 不跟随重定向的 client */
val client: OkHttpClient =
    (injectedClient?.newBuilder() ?: OkHttpClient.Builder())
        .cookieJar(cookieJar)
        .followRedirects(false)
        .build()

    /** 获取指定名称的 Cookie 值（匹配所有域） */
    fun getCookie(name: String): String? =
        cookieStore.firstOrNull { it.name == name }?.value

    /** 获取所有 Cookie（用于持久化） */
    fun getAllCookies(): List<Cookie> = cookieStore.toList()

    /** 从持久化存储恢复 Cookie 到内存 */
    suspend fun restoreCookies() {
        val storage = cookieStorage ?: return
        val cookies = storage.loadAll()
        logger.debug("restoreCookies: loaded ${cookies.size} cookies from storage")
        cookies.forEach { cookie ->
            cookieStore.removeAll { it.name == cookie.name && it.domain == cookie.domain }
            cookieStore.add(cookie)
        }
    }

    /** 持久化当前所有 Cookie（排除一次性 token：COOKIE_INFO） */
    suspend fun persistCookies() {
        val storage = cookieStorage ?: return
        val transientNames = setOf("COOKIE_INFO")
        val toSave = cookieStore.filter { it.name !in transientNames }
        logger.debug("persistCookies: saving ${toSave.size}/${cookieStore.size} cookies (filtered: COOKIE_INFO)")
        storage.saveAll(toSave)
    }

    /** 是否有已保存的 Cookie */
    suspend fun hasSavedCookies(): Boolean {
        val storage = cookieStorage ?: return false
        return storage.loadAll().isNotEmpty()
    }

    /** 清空所有 Cookie（内存 + 持久化存储） */
    suspend fun clearAllCookies() {
        logger.debug("clearAllCookies: clearing ${cookieStore.size} cookies")
        cookieStore.clear()
        cookieStorage?.let { storage ->
            storage.clearAll()
        }
    }

    /** Debug: dump 已持久化和内存中的 Cookie */
    suspend fun dumpToLog() {
        cookieStorage?.let { storage ->
            val stored = storage.loadAll()
            logger.debug("========== STORAGE DUMP (${stored.size} cookies) ==========")
            stored.forEach { c ->
                logger.debug("  ${c.name}: domain=${c.domain} path=${c.path} value=${c.value.take(300)}")
            }
            logger.debug("========== STORAGE DUMP END ==========")
        }
        logger.debug("========== MEMORY DUMP (${cookieStore.size} cookies) ==========")
        cookieStore.forEach { c ->
            logger.debug("  ${c.name}: domain=${c.domain} path=${c.path} value=${c.value.take(300)}")
        }
        logger.debug("========== MEMORY DUMP END ==========")
    }

    // ── SM2 公钥解析 ─────────────────────────────────────────

    /** SM2 / secp256r1 曲线参数 */
    private val sm2Params: ECParameterSpec by lazy {
        val p  = BigInteger("FFFFFFFEFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF00000000FFFFFFFFFFFFFFFF", 16)
        val a  = BigInteger("FFFFFFFEFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF00000000FFFFFFFFFFFFFFFC", 16)
        val b  = BigInteger("28E9FA9E9D9F5E344D5A9E4BCF6509A7F39789F515AB8F92DDBCBD414D940E93", 16)
        val gx = BigInteger("32C4AE2C1F1981195F9904466A39C9948FE30BBFF2660BE1715A4589334C74C7", 16)
        val gy = BigInteger("BC3736A2F4F6779C59BDCEE36B692153D0A9877CC62A474002DF32E52139F0A0", 16)
        val n  = BigInteger("FFFFFFFEFFFFFFFFFFFFFFFFFFFFFFFF7203DF6B21C6052B53BBF40939D54123", 16)
        val curve = EllipticCurve(ECFieldFp(p), a, b)
        val g = ECPoint(gx, gy)
        ECParameterSpec(curve, g, n, 1)
    }

    /**
     * 将服务端返回的 Base64 SM2 公钥（65字节 04||x||y）解析为 PublicKey
     */
    private fun parsePublicKey(base64Key: String): java.security.PublicKey {
        val keyBytes = Base64.getDecoder().decode(base64Key)
        require(keyBytes.size == 65 && keyBytes[0] == 0x04.toByte()) {
            "Expected 65-byte uncompressed EC point, got ${keyBytes.size} bytes"
        }
        val x = BigInteger(1, keyBytes.copyOfRange(1, 33))
        val y = BigInteger(1, keyBytes.copyOfRange(33, 65))
        val point = ECPoint(x, y)
        val spec = ECPublicKeySpec(point, sm2Params)
        val keyFactory = KeyFactory.getInstance("EC", "KonaCrypto")
        return keyFactory.generatePublic(spec)
    }

    // ── SM2 密码加密 ──────────────────────────────────────────

    /**
     * 使用 SM2 + 服务端公钥加密密码
     *
     * 注意：Java SM2 Cipher 输出 ASN.1 DER 格式（SEQUENCE{x, y, hash, cipher}），
     * 但服务端（对应 sm2.min.js）期望原始 C1C3C2 拼接格式：
     *   0x04 || x(32字节) || y(32字节) || C3(32字节) || C2(变长)
     * 因此需要解析 ASN.1 并转换。
     *
     * @return Base64 密文（与 sm2.min.js 输出格式兼容）
     */
    fun encryptPassword(password: String, publicKeyBase64: String): String {
        val publicKey = parsePublicKey(publicKeyBase64)
        val cipher = Cipher.getInstance("SM2", "KonaCrypto")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        val asn1Output = cipher.doFinal(password.toByteArray(Charsets.UTF_8))

        // 尝试将 ASN.1 DER 转换为 C1C3C2 原始格式
        val rawOutput = try {
            derToRawC1C3C2(asn1Output)
        } catch (_: Exception) {
            // 如果解析失败，可能已经是原始格式，直接返回
            asn1Output
        }

        return Base64.getEncoder().encodeToString(rawOutput)
    }

    /**
     * 将 SM2 ASN.1 DER 密文转换为 C1C3C2 原始拼接格式
     *
     * ASN.1 结构: SEQUENCE { INTEGER x, INTEGER y, OCTET STRING c3, OCTET STRING c2 }
     */
    private fun derToRawC1C3C2(der: ByteArray): ByteArray {
        var pos = 0

        // SEQUENCE tag
        require(der[pos] == 0x30.toByte()) { "Expected SEQUENCE tag, got ${der[pos]}" }
        pos++
        val (_, newPos0) = readDerLength(der, pos); pos = newPos0

        // INTEGER x
        require(der[pos] == 0x02.toByte()) { "Expected INTEGER (x)" }
        pos++
        val (xLen, newPos1) = readDerLength(der, pos); pos = newPos1
        val xBytes = der.copyOfRange(pos, pos + xLen)
        pos += xLen

        // INTEGER y
        require(der[pos] == 0x02.toByte()) { "Expected INTEGER (y)" }
        pos++
        val (yLen, newPos2) = readDerLength(der, pos); pos = newPos2
        val yBytes = der.copyOfRange(pos, pos + yLen)
        pos += yLen

        // OCTET STRING c3 (hash)
        require(der[pos] == 0x04.toByte()) { "Expected OCTET STRING (c3)" }
        pos++
        val (c3Len, newPos3) = readDerLength(der, pos); pos = newPos3
        val c3Bytes = der.copyOfRange(pos, pos + c3Len)
        pos += c3Len

        // OCTET STRING c2 (ciphertext)
        require(der[pos] == 0x04.toByte()) { "Expected OCTET STRING (c2)" }
        pos++
        val (c2Len, newPos4) = readDerLength(der, pos); pos = newPos4
        val c2Bytes = der.copyOfRange(pos, pos + c2Len)

        // 标准化坐标到 32 字节
        fun normalize32(b: ByteArray): ByteArray {
            return when {
                b.size == 32 -> b
                b.size < 32 -> ByteArray(32 - b.size) + b
                else -> b.copyOfRange(b.size - 32, b.size)
            }
        }

        val nx = normalize32(xBytes)
        val ny = normalize32(yBytes)

        // C1C3C2: 0x04 || x || y || c3 || c2
        return byteArrayOf(0x04) + nx + ny + c3Bytes + c2Bytes
    }

    private fun readDerLength(data: ByteArray, start: Int): Pair<Int, Int> {
        val first = data[start].toInt() and 0xFF
        return if (first < 0x80) {
            first to (start + 1)
        } else {
            val numBytes = first and 0x7F
            var value = 0
            for (i in 1..numBytes) {
                value = (value shl 8) or (data[start + i].toInt() and 0xFF)
            }
            value to (start + 1 + numBytes)
        }
    }

    // ── SSO API ──────────────────────────────────────────────

    /** 步骤 1: 访问 SSO 首页获取 COOKIE_INFO（含 flowKey）+ SM2 公钥 */
    suspend fun getPublicKey(): String = withContext(Dispatchers.IO) {
        val service = java.net.URLEncoder.encode(
            "https://uc.bistu.edu.cn/api/login?target=https://uc.bistu.edu.cn/user/login",
            "UTF-8"
        )
        val initUrl = "$SSO_BASE/login?service=$service"
        redirectClient.newCall(Request.Builder().url(initUrl).get().build()).execute().close()

        val req = Request.Builder().url("$SSO_BASE/api/reset/rules").get().build()
        val resp = client.newCall(req).execute()
        val body = resp.body.string()
        resp.close()

        val json = JSONObject(body)
        if (json.optInt("code") != 200) throw AuthException("获取公钥失败: $body")

        json.getJSONObject("data")
            .getJSONObject("encrypt")
            .getString("publicKey")
    }

    /** 从 COOKIE_INFO cookie 解析 flowKey */
    private fun getFlowKey(): String {
        val raw = getCookie("COOKIE_INFO") ?: return ""
        return try {
            val json = JSONObject(java.net.URLDecoder.decode(raw, "UTF-8"))
            json.optJSONObject("data")?.optString("flowKey") ?: ""
        } catch (_: Exception) { "" }
    }

    /** 步骤 2: 查询是否需要验证码 / MFA */
    suspend fun infoQuery(): JSONObject = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("username", "")
            put("flowKey", getFlowKey())
        }

        val req = Request.Builder()
            .url("$SSO_BASE/info-query")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()

        val resp = client.newCall(req).execute()
        val respBody = resp.body.string()
        resp.close()
        JSONObject(respBody)
    }

    /** 步骤 3+4: SM2 加密密码并登录 */
    suspend fun login(username: String, password: String, publicKey: String): LoginResult =
        withContext(Dispatchers.IO) {
            val encrypted = encryptPassword(password, publicKey)

            val body = JSONObject().apply {
                put("flowKey", getFlowKey())
                put("username", username)
                put("password", encrypted)
            }

            val req = Request.Builder()
                .url("$SSO_BASE/username-password/login")
                .post(body.toString().toRequestBody(JSON_MEDIA))
                .build()

            val resp = client.newCall(req).execute()
            val respBody = resp.body.string()
            resp.close()

            val json = JSONObject(respBody)
            LoginResult(
                code = json.optInt("code"),
                message = json.optString("msg", ""),
                serviceUrl = json.optJSONObject("data")?.optString("service"),
                rawJson = json
            )
        }

    /** 通过 casLogin 端点建立系统 session（携带 SSO TGC），非 2xx 抛 AuthException */
    suspend fun casLogin(endpoint: CasEndpoint) = withContext(Dispatchers.IO) {
        logger.debug("casLogin: $endpoint.name — GET ${endpoint.casLoginUrl}")
        redirectClient.newCall(Request.Builder()
            .url(endpoint.casLoginUrl)
            .get().build()).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw AuthException("casLogin ${endpoint.name} failed: HTTP ${resp.code}")
            }
        }
    }

    /** @deprecated 使用 casLogin() + CasEndpoint 替代 */
    @Deprecated("Use casLogin(CasEndpoint)", ReplaceWith("casLogin(JWXT_ENDPOINT)"))
    suspend fun jwxtLogin() = casLogin(JWXT_ENDPOINT)

    /** 验证 TGC 是否有效（只读检查，不修改 session 状态） */
    suspend fun verifySession(): Boolean = withContext(Dispatchers.IO) {
        val service = java.net.URLEncoder.encode(
            casEndpoints.first().casLoginUrl, "UTF-8")
        val url = "$SSO_BASE/login?service=$service"
        logger.info("verifySession: GET $url")
        val resp = client.newCall(Request.Builder().url(url).get().build()).execute()
        try {
            val code = resp.code
            val location = resp.header("Location") ?: "(none)"
            val setCookies = resp.headers("Set-Cookie")
            val bodyLen = resp.body.contentLength()

            val jumpReasonCookies = setCookies.filter { it.contains("JUMP_REASON=") }
            val hasNotTgc = jumpReasonCookies.any { it.contains("COOKIE_NOT_TGC") }
            val jsessionCookie = setCookies.find { it.contains("JSESSIONID") }

            // 严格验证：必须 302 + Location 指向 SSO 域 + 无 COOKIE_NOT_TGC
            val valid = code == 302
                    && location.startsWith(SSO_BASE)
                    && !hasNotTgc

            logger.debug("< HTTP $code | Location: $location")
            logger.debug("< Set-Cookie names: ${setCookies.map { it.substringBefore("=").take(60) }}")
            logger.debug("< Body: ${bodyLen}B")
            logger.info("verifySession: TGC=${!hasNotTgc} | HTTP=$code | Location=$location | JSESSIONID=${jsessionCookie != null}")
            valid
        } finally {
            resp.close()
        }
    }

    /** GET 请求（携带 session cookie） */
    suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { response ->
            response.body.string()
        }
    }

    /** POST 表单请求（携带 session cookie） */
    suspend fun post(url: String, formBody: Map<String, String>): String = withContext(Dispatchers.IO) {
        val form = okhttp3.FormBody.Builder()
        formBody.forEach { (k, v) -> form.add(k, v) }
        val req = Request.Builder().url(url).post(form.build()).build()
        client.newCall(req).execute().use { response ->
            response.body.string()
        }
    }

    /** 一键登录（SSO + 教务系统 session） */
    suspend fun fullLogin(username: String, password: String): LoginResult {
        logger.debug("fullLogin: START username=$username")
        val publicKey = getPublicKey()
        runCatching { infoQuery() }
        val result = login(username, password, publicKey)
        logger.debug("fullLogin: code=${result.code} ${result.message}")
        if (result.isSuccess) {
            casEndpoints.forEach { casLogin(it) }
            runCatching { persistCookies() }
        }
        return result
    }
}

// ── 数据类 ──────────────────────────────────────────────────

data class LoginResult(
    val code: Int,
    val message: String,
    val serviceUrl: String?,
    val rawJson: JSONObject
) {
    val isSuccess: Boolean get() = code == 666666
}

class AuthException(message: String) : Exception(message)

/** CAS 登录端点（通过 TGC 桥接建立对应系统 session） */
data class CasEndpoint(val name: String, val casLoginUrl: String)

/** 默认 JWXT 教务系统端点 */
val JWXT_ENDPOINT = CasEndpoint(
    "JWXT",
    "https://jwxt.bistu.edu.cn/jwapp/sys/yjsrzfwapp/bistuLogin/casLogin.do"
)
