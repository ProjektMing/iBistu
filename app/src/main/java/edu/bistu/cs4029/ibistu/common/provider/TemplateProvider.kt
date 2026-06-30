package edu.bistu.cs4029.ibistu.common.provider

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri

/**
 * ContentProvider 参考模板。
 *
 * 展示完整的 UriMatcher 分发和 CRUD 操作模式。
 * 实际使用时替换为具体的数据模型和数据库操作。
 */
class TemplateProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "edu.bistu.cs4029.ibistu.provider"
        const val PATH_ITEMS = "items"
        const val PATH_ITEM_ID = "items/#"

        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/$PATH_ITEMS")

        private const val CODE_ITEMS = 1
        private const val CODE_ITEM_ID = 2

        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, PATH_ITEMS, CODE_ITEMS)
            addURI(AUTHORITY, PATH_ITEM_ID, CODE_ITEM_ID)
        }

        /** MIME 类型 */
        const val MIME_TYPE_DIR = "vnd.android.cursor.dir/$AUTHORITY.$PATH_ITEMS"
        const val MIME_TYPE_ITEM = "vnd.android.cursor.item/$AUTHORITY.$PATH_ITEMS"
    }

    private lateinit var dbHelper: SQLiteOpenHelper

    override fun onCreate(): Boolean {
        // 初始化数据库
        // dbHelper = MyDatabaseHelper(context)
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        return when (uriMatcher.match(uri)) {
            CODE_ITEMS -> {
                // val db = dbHelper.readableDatabase
                // db.query(TABLE_NAME, projection, selection, selectionArgs, null, null, sortOrder)
                null
            }
            CODE_ITEM_ID -> {
                val id = uri.lastPathSegment
                // val db = dbHelper.readableDatabase
                // db.query(TABLE_NAME, projection, "_id = ?", arrayOf(id), null, null, null)
                null
            }
            else -> throw IllegalArgumentException("Unknown URI: $uri")
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        return when (uriMatcher.match(uri)) {
            CODE_ITEMS -> {
                // val db = dbHelper.writableDatabase
                // val newId = db.insert(TABLE_NAME, null, values)
                // ContentUris.withAppendedId(uri, newId)
                null
            }
            else -> throw IllegalArgumentException("Unknown URI: $uri")
        }
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int {
        return when (uriMatcher.match(uri)) {
            CODE_ITEMS -> {
                // val db = dbHelper.writableDatabase
                // db.update(TABLE_NAME, values, selection, selectionArgs)
                0
            }
            CODE_ITEM_ID -> {
                val id = uri.lastPathSegment
                // val db = dbHelper.writableDatabase
                // db.update(TABLE_NAME, values, "_id = ?", arrayOf(id))
                0
            }
            else -> throw IllegalArgumentException("Unknown URI: $uri")
        }
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
        return when (uriMatcher.match(uri)) {
            CODE_ITEMS -> {
                // val db = dbHelper.writableDatabase
                // db.delete(TABLE_NAME, selection, selectionArgs)
                0
            }
            CODE_ITEM_ID -> {
                val id = uri.lastPathSegment
                // val db = dbHelper.writableDatabase
                // db.delete(TABLE_NAME, "_id = ?", arrayOf(id))
                0
            }
            else -> throw IllegalArgumentException("Unknown URI: $uri")
        }
    }

    override fun getType(uri: Uri): String? {
        return when (uriMatcher.match(uri)) {
            CODE_ITEMS -> MIME_TYPE_DIR
            CODE_ITEM_ID -> MIME_TYPE_ITEM
            else -> null
        }
    }
}
