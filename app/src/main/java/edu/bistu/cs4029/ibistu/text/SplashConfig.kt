package edu.bistu.cs4029.ibistu.text

import android.content.Context
import edu.bistu.cs4029.ibistu.R
import org.xmlpull.v1.XmlPullParser

/**
 * 轮播文本配置工具。
 *
 * 从 res/xml/splash_config.xml 直接解析轮播文本列表。
 * 在 ContentProvider 不可用时作为回退数据源。
 */
object SplashConfig {

    /**
     * 从 XML 配置文件中读取所有轮播文本。
     *
     * @param context 上下文
     * @return (内容, 作者) 列表
     */
    fun load(context: Context): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        val parser = context.resources.getXml(R.xml.splash_config)
        try {
            var eventType = parser.next()
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "item") {
                    val content = parser.getAttributeValue(null, "content") ?: ""
                    val author = parser.getAttributeValue(null, "author") ?: ""
                    result.add(content to author)
                }
                eventType = parser.next()
            }
        } finally {
            parser.close()
        }
        return result
    }
}
