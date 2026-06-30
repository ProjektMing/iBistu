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
 */
class BistuLogin {

    companion object {
        const val SSO_BASE = "https://sso.bistu.edu.cn"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        init {
            if (Security.getProvider("KonaCrypto") == null) {
                Security.addProvider(KonaCryptoProvider())
            }
        }
    }

    // ── Cookie 管理 ──────────────────────────────────────────

    private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()

    private val cookieJar = object : CookieJar {
        override fun loadForRequest(url: HttpUrl): List<Cookie> =
            cookieStore[url.host] ?: emptyList()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookieStore.getOrPut(url.host) { mutableListOf() }.apply {
                cookies.forEach { new ->
                    removeAll { it.name == new.name }
                    add(new)
                }
            }
        }
    }

    val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .followRedirects(false)
        .build()

    /** 获取指定域名下指定名称的 Cookie 值 */
    fun getCookie(host: String, name: String): String? =
        cookieStore[host]?.firstOrNull { it.name == name }?.value

    /** 获取所有 Cookie（用于持久化） */
    fun getAllCookies(): Map<String, List<Cookie>> = cookieStore.toMap()

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
     * @return Base64 密文（与 sm2.min.js 输出格式兼容）
     */
    fun encryptPassword(password: String, publicKeyBase64: String): String {
        val publicKey = parsePublicKey(publicKeyBase64)
        val cipher = Cipher.getInstance("SM2", "KonaCrypto")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        val encrypted = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(encrypted)
    }

    // ── SSO API ──────────────────────────────────────────────

    /** 步骤 1: 访问 SSO 首页获取 COOKIE_INFO（含 flowKey）+ SM2 公钥 */
    suspend fun getPublicKey(): String = withContext(Dispatchers.IO) {
        // 构造带 timestamp 和 service 的 SSO 首页 URL，
        // 服务端会通过 Set-Cookie 返回 COOKIE_INFO（内含 flowKey）
        val timestamp = System.currentTimeMillis()
        val service = java.net.URLEncoder.encode(
            "https://uc.bistu.edu.cn/api/login",
            "UTF-8"
        )
        val initUrl = "$SSO_BASE/login?service=$service"
        val initReq = Request.Builder().url(initUrl).get().build()
        client.newCall(initReq).execute().close()

        // 从 COOKIE_INFO cookie 中提取 flowKey
        val cookieInfoRaw = getCookie("sso.bistu.edu.cn", "COOKIE_INFO")
        System.err.println("=== COOKIE_INFO ===")
        System.err.println(cookieInfoRaw ?: "(null)")
        System.err.println("=== end ===")

        // 获取公钥
        val req = Request.Builder().url("$SSO_BASE/api/reset/rules").get().build()
        val resp = client.newCall(req).execute()
        val body = resp.body?.string() ?: throw AuthException("获取公钥失败：空响应")
        resp.close()

        val json = JSONObject(body)
        if (json.optInt("code") != 200) throw AuthException("获取公钥失败: $body")

        json.getJSONObject("data")
            .getJSONObject("encrypt")
            .getString("publicKey")
    }

    /** 从 COOKIE_INFO cookie 解析 flowKey */
    private fun getFlowKey(): String {
        val raw = getCookie("sso.bistu.edu.cn", "COOKIE_INFO") ?: return ""
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
        val respBody = resp.body?.string() ?: "{}"
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
            val respBody = resp.body?.string() ?: "{}"
            resp.close()

            System.err.println("=== /username-password/login response ===")
            System.err.println(respBody)
            System.err.println("=== end ===")

            val json = JSONObject(respBody)
            LoginResult(
                code = json.optInt("code"),
                message = json.optString("msg", ""),
                serviceUrl = json.optJSONObject("data")?.optString("service"),
                rawJson = json
            )
        }

    /** 步骤 5: 用 TGC Cookie 换取目标系统的 ST ticket */
    suspend fun getServiceTicket(targetBase: String): String? = withContext(Dispatchers.IO) {
        // 构造 service 参数：目标系统的 CAS 回调地址
        val service = "$targetBase?target=$targetBase"
        val encoded = java.net.URLEncoder.encode(service, "UTF-8")

        val req = Request.Builder()
            .url("$SSO_BASE/login?service=$encoded")
            .get()
            .build()

        val resp = client.newCall(req).execute()
        val location = resp.header("Location")
        resp.close()

        location?.let { Regex("ticket=(ST-[^&]+)").find(it)?.groupValues?.get(1) }
    }

    /** 一键登录：获取公钥 → 查询 → 登录 */
    suspend fun fullLogin(username: String, password: String): LoginResult {
        val publicKey = getPublicKey()
        runCatching { infoQuery() }
        return login(username, password, publicKey)
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
