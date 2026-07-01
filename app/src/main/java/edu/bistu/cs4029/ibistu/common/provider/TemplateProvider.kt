package edu.bistu.cs4029.ibistu.common.provider

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteQueryBuilder
import android.net.Uri
import androidx.core.net.toUri

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

        const val COLUMN_ID = "_id"
        const val COLUMN_NAME = "name"
        const val COLUMN_VALUE = "value"

        val CONTENT_URI: Uri = "content://$AUTHORITY/$PATH_ITEMS".toUri()

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
        val providerContext = context ?: return false
        dbHelper = TemplateDatabaseHelper(providerContext)
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor {
        val (resolvedSelection, resolvedSelectionArgs) = resolveSelection(
            uri = uri,
            selection = selection,
            selectionArgs = selectionArgs
        )
        val queryBuilder = SQLiteQueryBuilder().apply {
            tables = TemplateDatabaseHelper.TABLE_ITEMS
        }
        return queryBuilder.query(
            dbHelper.readableDatabase,
            projection,
            resolvedSelection,
            resolvedSelectionArgs,
            null,
            null,
            sortOrder
        ).apply {
            setNotificationUri(requireNotNull(context).contentResolver, CONTENT_URI)
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri {
        requireCollectionUri(uri)
        val newId = dbHelper.writableDatabase.insertOrThrow(
            TemplateDatabaseHelper.TABLE_ITEMS,
            null,
            requireNotNull(values) { "ContentValues must not be null" }
        )
        return ContentUris.withAppendedId(CONTENT_URI, newId).also { insertedUri ->
            requireNotNull(context).contentResolver.notifyChange(insertedUri, null)
        }
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int {
        val (resolvedSelection, resolvedSelectionArgs) = resolveSelection(uri, selection, selectionArgs)
        val count = dbHelper.writableDatabase.update(
            TemplateDatabaseHelper.TABLE_ITEMS,
            requireNotNull(values) { "ContentValues must not be null" },
            resolvedSelection,
            resolvedSelectionArgs
        )
        if (count > 0) {
            requireNotNull(context).contentResolver.notifyChange(uri, null)
        }
        return count
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
        val (resolvedSelection, resolvedSelectionArgs) = resolveSelection(uri, selection, selectionArgs)
        val count = dbHelper.writableDatabase.delete(
            TemplateDatabaseHelper.TABLE_ITEMS,
            resolvedSelection,
            resolvedSelectionArgs
        )
        if (count > 0) {
            requireNotNull(context).contentResolver.notifyChange(uri, null)
        }
        return count
    }

    override fun getType(uri: Uri): String {
        return when (uriMatcher.match(uri)) {
            CODE_ITEMS -> MIME_TYPE_DIR
            CODE_ITEM_ID -> MIME_TYPE_ITEM
            else -> throw IllegalArgumentException("Unknown URI: $uri")
        }
    }

    private fun requireCollectionUri(uri: Uri) {
        if (uriMatcher.match(uri) != CODE_ITEMS) {
            throw IllegalArgumentException("Insert is only supported for $CONTENT_URI")
        }
    }

    private fun resolveSelection(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<String>?
    ): Pair<String?, Array<String>?> {
        return when (uriMatcher.match(uri)) {
            CODE_ITEMS -> selection to selectionArgs
            CODE_ITEM_ID -> {
                val id = requireNotNull(uri.lastPathSegment) { "Missing item id: $uri" }
                if (selection.isNullOrBlank()) {
                    "$COLUMN_ID = ?" to arrayOf(id)
                } else {
                    "($COLUMN_ID = ?) AND ($selection)" to arrayOf(id, *selectionArgs.orEmpty())
                }
            }
            else -> throw IllegalArgumentException("Unknown URI: $uri")
        }
    }
}

private class TemplateDatabaseHelper(context: android.content.Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_ITEMS (
                ${TemplateProvider.COLUMN_ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                ${TemplateProvider.COLUMN_NAME} TEXT NOT NULL,
                ${TemplateProvider.COLUMN_VALUE} TEXT
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_ITEMS")
        onCreate(db)
    }

    companion object {
        const val TABLE_ITEMS = "items"
        private const val DATABASE_NAME = "template-provider.db"
        private const val DATABASE_VERSION = 1
    }
}
