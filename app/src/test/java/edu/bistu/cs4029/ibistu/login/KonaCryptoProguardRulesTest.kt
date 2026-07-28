package edu.bistu.cs4029.ibistu.login

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** KonaCrypto 的 release 混淆配置回归测试。 */
class KonaCryptoProguardRulesTest {

    /** JCA 通过类名创建的 KonaCrypto SPI 实现必须在 R8 压缩后保留。 */
    @Test
    fun proguardRulesKeepRequiredKonaCryptoServices() {
        val rules = loadProguardRules()

        assertTrue(
            "EC KeyFactory implementation must be kept for reflective JCA loading",
            rules.contains("-keep class com.tencent.kona.sun.security.ec.ECKeyFactory { *; }")
        )
        assertTrue(
            "SM2 Cipher implementation must be kept for reflective JCA loading",
            rules.contains("-keep class com.tencent.kona.crypto.provider.SM2Cipher { *; }")
        )
    }

    private fun loadProguardRules(): String {
        val candidates = listOf(
            File("proguard-rules.pro"),
            File("app/proguard-rules.pro")
        )
        return candidates.firstOrNull(File::isFile)?.readText()
            ?: error("Cannot locate app/proguard-rules.pro from ${File(".").absolutePath}")
    }
}
