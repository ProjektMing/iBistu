package edu.bistu.cs4029.ibistu.text

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import edu.bistu.cs4029.ibistu.R
import org.xmlpull.v1.XmlPullParser

/**
 * 轮播内容 ContentProvider。
 *
 * 从 res/xml/splash_config.xml 读取轮播文本并对外提供查询。
 *
 * 参考 TemplateProvider 实现，遵循 docs/components/content-provider.md 规范。
 */
class SplashProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "edu.bistu.cs4029.ibistu.text"
        const val PATH_SPLASH_ITEMS = "splash_items"
        const val PATH_SPLASH_ITEM_ID = "splash_items/#"

        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/$PATH_SPLASH_ITEMS")

        /** 列名常量 */
        const val COL_ID = "_id"
        const val COL_CONTENT = "content"
        const val COL_AUTHOR = "author"

        private val COLUMNS = arrayOf(COL_ID, COL_CONTENT, COL_AUTHOR)

        private const val CODE_SPLASH_ITEMS = 1
        private const val CODE_SPLASH_ITEM_ID = 2

        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, PATH_SPLASH_ITEMS, CODE_SPLASH_ITEMS)
            addURI(AUTHORITY, PATH_SPLASH_ITEM_ID, CODE_SPLASH_ITEM_ID)
        }

        /** MIME 类型 */
        const val MIME_TYPE_DIR = "vnd.android.cursor.dir/$AUTHORITY.$PATH_SPLASH_ITEMS"
        const val MIME_TYPE_ITEM = "vnd.android.cursor.item/$AUTHORITY.$PATH_SPLASH_ITEMS"
    }

    /** 内存中的轮播文本列表，启动时从 XML 加载 */
    private data class SplashItem(val id: Long, val content: String, val author: String)

    private val items = mutableListOf<SplashItem>()

    override fun onCreate(): Boolean {
        parseConfig()
        return true
    }

    /**
     * 解析 res/xml/splash_config.xml，加载轮播文本到内存。
     */
    private fun parseConfig() {
        val res = context?.resources ?: return
        val parser = res.getXml(R.xml.splash_config)
        try {
            var id = 0L
            var eventType = parser.next()
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "item") {
                    val content = parser.getAttributeValue(null, "content") ?: ""
                    val author = parser.getAttributeValue(null, "author") ?: ""
                    items.add(SplashItem(++id, content, author))
                }
                eventType = parser.next()
            }
        } finally {
            parser.close()
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        return when (uriMatcher.match(uri)) {
            CODE_SPLASH_ITEMS -> {
                buildCursor(projection, items)
            }
            CODE_SPLASH_ITEM_ID -> {
                val id = uri.lastPathSegment?.toLongOrNull()
                val filtered = if (id != null) items.filter { it.id == id } else emptyList()
                buildCursor(projection, filtered)
            }
            else -> throw IllegalArgumentException("Unknown URI: $uri")
        }
    }

    /**
     * 将内存数据构建为 MatrixCursor，按 projection 过滤列。
     */
    private fun buildCursor(projection: Array<String>?, data: List<SplashItem>): Cursor {
        val cols = projection ?: COLUMNS
        val cursor = MatrixCursor(cols)
        for (item in data) {
            val row = cols.map { col ->
                when (col) {
                    COL_ID -> item.id
                    COL_CONTENT -> item.content
                    COL_AUTHOR -> item.author
                    else -> null
                }
            }.toTypedArray<Any?>()
            cursor.addRow(row)
        }
        return cursor
    }

    /**
     * 配置文件为只读，不支持写入操作。
     */
    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        throw UnsupportedOperationException("splash_config.xml is read-only")
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int {
        throw UnsupportedOperationException("splash_config.xml is read-only")
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
        throw UnsupportedOperationException("splash_config.xml is read-only")
    }

    override fun getType(uri: Uri): String? {
        return when (uriMatcher.match(uri)) {
            CODE_SPLASH_ITEMS -> MIME_TYPE_DIR
            CODE_SPLASH_ITEM_ID -> MIME_TYPE_ITEM
            else -> null
        }
    }
}
