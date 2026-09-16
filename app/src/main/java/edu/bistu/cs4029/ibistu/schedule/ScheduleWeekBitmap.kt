package edu.bistu.cs4029.ibistu.schedule

/** 将 API 的周次位图转换为升序紧凑文本；第一个字符对应第 1 周，保留前导零的意义。 */
internal fun scheduleWeekText(bitmap: String): String {
    require(bitmap.all { it == '0' || it == '1' }) { "课表 week 必须为二值字符串" }
    val ranges = mutableListOf<String>()
    var index = 0
    while (index < bitmap.length) {
        if (bitmap[index] == '0') {
            index++
            continue
        }
        val start = index + 1
        while (index < bitmap.length && bitmap[index] == '1') index++
        ranges.add(if (start == index) "$start" else "$start-$index")
    }
    return ranges.joinToString(",")
}
