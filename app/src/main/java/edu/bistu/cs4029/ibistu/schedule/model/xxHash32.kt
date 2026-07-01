package edu.bistu.cs4029.ibistu.schedule.model

/**
 * 纯 Kotlin xxHash32 实现（零依赖）。
 * 参考：https://github.com/Cyan4973/xxHash/blob/dev/doc/xxhash_spec.md
 *
 * 用于快速计算课表 JSON 的哈希值，判断数据是否变更。
 */
object XxHash32 {

    private const val PRIME_1 = 0x9E3779B1.toInt()
    private const val PRIME_2 = 0x85EBCA77.toInt()
    private const val PRIME_3 = 0xC2B2AE3D.toInt()
    private const val PRIME_4 = 0x27D4EB2F.toInt()
    private const val PRIME_5 = 0x165667B1.toInt()

    /**
     * 计算字节数组的 xxHash32 值。
     * @param data 输入数据
     * @param seed 种子值，默认 0
     * @return 32 位哈希值
     */
    fun hash(data: ByteArray, seed: Int = 0): Int {
        val len = data.size
        var index = 0
        var h32: Int

        if (len >= 16) {
            val v1 = seed + PRIME_1 + PRIME_2
            val v2 = seed + PRIME_2
            val v3 = seed
            val v4 = seed - PRIME_1

            var acc1 = v1
            var acc2 = v2
            var acc3 = v3
            var acc4 = v4

            val limit = len - 16
            while (index <= limit) {
                acc1 = round(acc1, read32LE(data, index))
                index += 4
                acc2 = round(acc2, read32LE(data, index))
                index += 4
                acc3 = round(acc3, read32LE(data, index))
                index += 4
                acc4 = round(acc4, read32LE(data, index))
                index += 4
            }

            h32 = Integer.rotateLeft(acc1, 1) +
                    Integer.rotateLeft(acc2, 7) +
                    Integer.rotateLeft(acc3, 12) +
                    Integer.rotateLeft(acc4, 18)
        } else {
            h32 = seed + PRIME_5
        }

        h32 += len

        // 处理剩余的 4 字节块
        val limit4 = len - 4
        while (index <= limit4) {
            h32 += read32LE(data, index) * PRIME_3
            h32 = Integer.rotateLeft(h32, 17)
            h32 = h32 * PRIME_4
            index += 4
        }

        // 处理剩余的 1 字节块
        while (index < len) {
            h32 += (data[index].toInt() and 0xFF) * PRIME_5
            h32 = Integer.rotateLeft(h32, 11)
            h32 = h32 * PRIME_1
            index++
        }

        // Avalanche
        h32 = h32 xor (h32 ushr 15)
        h32 = h32 * PRIME_2
        h32 = h32 xor (h32 ushr 13)
        h32 = h32 * PRIME_3
        h32 = h32 xor (h32 ushr 16)

        return h32
    }

    /**
     * 计算字符串的 xxHash32 值（UTF-8 编码）。
     * @return 32 位哈希值
     */
    fun hashString(input: String, seed: Int = 0): Int =
        hash(input.toByteArray(Charsets.UTF_8), seed)

    /**
     * 计算字符串的 xxHash32 值并返回 8 位十六进制字符串。
     */
    fun hashStringHex(input: String, seed: Int = 0): String =
        hashString(input, seed).let { hash ->
            String.format("%08x", hash)
        }

    // ── 内部工具 ────────────────────────────────────────────

    /** xxHash32 的 round 函数 */
    private fun round(acc: Int, input: Int): Int {
        var r = acc + input * PRIME_2
        r = Integer.rotateLeft(r, 13)
        r = r * PRIME_1
        return r
    }

    /** 以小端序读取 4 字节 */
    private fun read32LE(data: ByteArray, offset: Int): Int {
        return (data[offset].toInt() and 0xFF) or
                ((data[offset + 1].toInt() and 0xFF) shl 8) or
                ((data[offset + 2].toInt() and 0xFF) shl 16) or
                ((data[offset + 3].toInt() and 0xFF) shl 24)
    }
}
