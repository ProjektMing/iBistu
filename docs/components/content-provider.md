# ContentProvider 组件规范

## 概述

ContentProvider 是 Android 四大组件之一，用于在不同应用之间共享结构化数据，通过 URI 形式对外提供 CRUD 操作。

## 创建 ContentProvider 的步骤

### 1. 创建 ContentProvider 类

```kotlin
package edu.bistu.cs4029.ibistu.schedule

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri

class ScheduleProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "edu.bistu.cs4029.ibistu.schedule"
        const val PATH_COURSES = "courses"
        const val PATH_COURSE_ID = "courses/#"

        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/$PATH_COURSES")

        private const val CODE_COURSES = 1
        private const val CODE_COURSE_ID = 2

        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, PATH_COURSES, CODE_COURSES)
            addURI(AUTHORITY, PATH_COURSE_ID, CODE_COURSE_ID)
        }
    }

    override fun onCreate(): Boolean {
        // 初始化数据库
        return true
    }

    override fun query(uri, projection, selection, selectionArgs, sortOrder): Cursor? {
        /* 见下方 UriMatcher 分发模式 */
    }

    override fun insert(uri, values): Uri? { /* ... */ }
    override fun update(uri, values, selection, selectionArgs): Int { /* ... */ }
    override fun delete(uri, selection, selectionArgs): Int { /* ... */ }
    override fun getType(uri): String? { /* ... */ }
}
```

### 2. Manifest 注册

```xml
<provider
    android:name=".schedule.ScheduleProvider"
    android:authorities="edu.bistu.cs4029.ibistu.schedule"
    android:exported="false" />
```

## UriMatcher 使用规范

UriMatcher 是 ContentProvider 的核心，用于将 URI 匹配到对应的操作。

### 定义规则

| URI 模式 | 匹配码 | 说明 |
|----------|--------|------|
| `items` | `CODE_ITEMS` | 操作所有记录 |
| `items/#` | `CODE_ITEM_ID` | 操作单条记录（# 匹配数字） |
| `items/*` | `CODE_ITEM_NAME` | 操作单条记录（* 匹配文本） |

```kotlin
private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
    addURI(AUTHORITY, "items", CODE_ITEMS)       // 集合
    addURI(AUTHORITY, "items/#", CODE_ITEM_ID)   // 单条
}

// 分发模式
override fun query(...): Cursor? {
    return when (uriMatcher.match(uri)) {
        CODE_ITEMS -> {
            // 查全部
        }
        CODE_ITEM_ID -> {
            val id = uri.lastPathSegment
            // 查单条
        }
        else -> throw IllegalArgumentException("Unknown URI: $uri")
    }
}
```

## CRUD 操作

### query — 查询

```kotlin
override fun query(
    uri: Uri, projection: Array<String>?, selection: String?,
    selectionArgs: Array<String>?, sortOrder: String?
): Cursor? {
    val db = dbHelper.readableDatabase
    return when (uriMatcher.match(uri)) {
        CODE_ITEMS -> db.query(TABLE, projection, selection, selectionArgs, null, null, sortOrder)
        CODE_ITEM_ID -> {
            val id = uri.lastPathSegment
            db.query(TABLE, projection, "_id = ?", arrayOf(id), null, null, null)
        }
        else -> throw IllegalArgumentException("Unknown URI: $uri")
    }
}
```

### insert — 插入

```kotlin
override fun insert(uri: Uri, values: ContentValues?): Uri? {
    val db = dbHelper.writableDatabase
    val newId = db.insert(TABLE, null, values)
    context?.contentResolver?.notifyChange(uri, null)
    return ContentUris.withAppendedId(uri, newId)
}
```

### update — 更新

```kotlin
override fun update(uri: Uri, values: ContentValues?, selection: String?,
                    selectionArgs: Array<String>?): Int {
    val db = dbHelper.writableDatabase
    val count = when (uriMatcher.match(uri)) {
        CODE_ITEMS -> db.update(TABLE, values, selection, selectionArgs)
        CODE_ITEM_ID -> {
            val id = uri.lastPathSegment
            db.update(TABLE, values, "_id = ?", arrayOf(id))
        }
        else -> throw IllegalArgumentException("Unknown URI: $uri")
    }
    if (count > 0) context?.contentResolver?.notifyChange(uri, null)
    return count
}
```

### delete — 删除

```kotlin
override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
    val db = dbHelper.writableDatabase
    return when (uriMatcher.match(uri)) {
        CODE_ITEMS -> db.delete(TABLE, selection, selectionArgs)
        CODE_ITEM_ID -> {
            val id = uri.lastPathSegment
            db.delete(TABLE, "_id = ?", arrayOf(id))
        }
        else -> throw IllegalArgumentException("Unknown URI: $uri")
    }
}
```

### getType — 返回 MIME 类型

```kotlin
override fun getType(uri: Uri): String? {
    return when (uriMatcher.match(uri)) {
        CODE_ITEMS -> "vnd.android.cursor.dir/$AUTHORITY.$PATH_ITEMS"
        CODE_ITEM_ID -> "vnd.android.cursor.item/$AUTHORITY.$PATH_ITEMS"
        else -> null
    }
}
```

## SQLiteOpenHelper 配合示例

```kotlin
package edu.bistu.cs4029.ibistu.common.provider

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class MyDatabaseHelper(context: Context) : SQLiteOpenHelper(
    context, "ibistu.db", null, 1
) {
    companion object {
        const val TABLE_COURSES = "courses"
        const val COL_ID = "_id"
        const val COL_NAME = "name"
        const val COL_CODE = "code"
        const val COL_CREDIT = "credit"
        const val COL_TEACHER = "teacher"
        const val COL_CLASSROOM = "classroom"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE $TABLE_COURSES (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_NAME TEXT NOT NULL,
                $COL_CODE TEXT,
                $COL_CREDIT TEXT,
                $COL_TEACHER TEXT,
                $COL_CLASSROOM TEXT
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_COURSES")
        onCreate(db)
    }
}
```

## ContentResolver 调用方式

```kotlin
// 查询
val cursor = contentResolver.query(
    ScheduleProvider.CONTENT_URI,
    null, null, null, null
)

// 插入
val values = ContentValues().apply {
    put("name", "高等数学")
    put("code", "MATH101")
}
val newUri = contentResolver.insert(ScheduleProvider.CONTENT_URI, values)

// 更新
val updateValues = ContentValues().apply {
    put("classroom", "教二楼201")
}
contentResolver.update(
    ScheduleProvider.CONTENT_URI,
    updateValues,
    "_id = ?", arrayOf("1")
)

// 删除
contentResolver.delete(ScheduleProvider.CONTENT_URI, "_id = ?", arrayOf("1"))
```

## Manifest 注册规范

```xml
<provider
    android:name=".module.ClassNameProvider"
    android:authorities="edu.bistu.cs4029.ibistu.module"
    android:exported="false" />
```

| 属性 | 说明 |
|------|------|
| `android:name` | Provider 完整类名 |
| `android:authorities` | 唯一标识符，通常为 `包名.provider` 或 `包名.模块名` |
| `android:exported` | 是否允许其他应用访问 |
| `android:readPermission` | 读权限 |
| `android:writePermission` | 写权限 |

## 注意事项

1. **线程安全** — ContentProvider 的 CRUD 方法运行在调用者的线程中，不是自动的后台线程
2. **UriMatcher 唯一性** — 确保每个 URI 模式在 Matcher 中有唯一的匹配码
3. **getType() 必须实现** — 返回正确的 MIME 类型，否则一些系统功能可能异常
4. **数据变更通知** — 修改数据后调用 `ContentResolver.notifyChange()` 通知监听者
5. **权限控制** — 跨应用共享时，通过 `android:exported` 和权限来控制访问
