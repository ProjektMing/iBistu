# iBistu 编码风格规范 (CODING_STYLE)

## 1. Kotlin 编码约定

### 1.1 命名规范

| 类别 | 规范 | 示例 |
|------|------|------|
| 类/对象 | PascalCase | `MainActivity`, `BistuLogin` |
| 函数/方法 | camelCase | `onCreate()`, `fullLogin()` |
| 变量/属性 | camelCase | `studentId`, `loginResult` |
| 常量 (`val` 顶层) | PascalCase | `val DEFAULT_TIMEOUT = 30L` |
| Compose 函数 | PascalCase | `IBistuApp()`, `ProfilePage()` |
| 包名 | 全小写，点分隔 | `edu.bistu.cs4029.ibistu.login` |
| 枚举/注解 | PascalCase | `enum class AppDestinations` |

### 1.2 文件命名

- 每个类一个文件，文件名与类名一致
- 扩展函数文件以 `_Ext.kt` 后缀（如 `ContextExt.kt`）

### 1.3 包结构

```
edu.bistu.cs4029.ibistu
├── <module>/               ← 功能模块（login/schedule/common）
│   ├── model/              ← 数据类
│   ├── <name>Activity.kt  ← Activity
│   ├── <name>ViewModel.kt ← ViewModel
│   └── <name>Api.kt       ← API 接口
├── common/                 ← 通用模块
│   ├── base/               ← 基类
│   ├── ui/                 ← 通用 UI 组件
│   ├── service/            ← 共享 Service
│   ├── receiver/           ← 共享 BroadcastReceiver
│   └── provider/           ← 共享 ContentProvider
└── MainActivity.kt         ← 入口
```

### 1.4 注释规范

- **类/接口** — KDoc `/** ... */` 描述用途和使用方式
- **公开函数** — KDoc 说明参数、返回值、异常
- **私有函数** — 可选单行注释
- **TODO** — 标注 `// TODO: 原因` 或 `// FIXME: 原因`

### 1.5 格式

- 缩进：4 空格（Kotlin 默认）
- 最大行宽：120 字符
- 导入：使用 wildcard import（`.*`）当导入同一包超过 5 个类时

## 2. Compose 写法规范

### 2.1 Composable 函数命名

- **页面级**：PascalCase，以 Page/Screen 结尾：`HomePage()`, `ProfilePage()`
- **组件级**：PascalCase，名词性：`CourseCard()`, `LoginForm()`
- **布局级**：PascalCase：`IBistuApp()`, `NavigationBar()`

### 2.2 状态管理

- 页面级状态：使用 `mutableStateOf` + `remember`
- 跨组件状态：使用 `remember { mutableStateOf(...) }` 或 ViewModel
- 避免：将 `State` 对象直接传给深层子组件，使用回调代替

```kotlin
// ✅ 推荐：状态提升 + 回调
@Composable
fun LoginForm(
    studentId: String,
    onStudentIdChange: (String) -> Unit,
    onLogin: () -> Unit
)

// ❌ 避免：在组件内部直接持有可变状态
@Composable
fun LoginForm() {
    var studentId by remember { mutableStateOf("") }
}
```

### 2.3 Modifier 使用

- 组件**必须**暴露 `modifier: Modifier` 参数，默认 `Modifier`
- 从外部传入的 Modifier 应放在参数首位
- 链式调用按功能分组排列

```kotlin
@Composable
fun CourseCard(
    course: Course,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) { ... }
}
```

### 2.4 预览函数

- 每个页面级 Composable 应包含 `@Preview` 函数
- 预览函数后缀为 `Preview`：`fun HomePagePreview()`

```kotlin
@Preview(showBackground = true, showSystemUi = true)
@Composable
fun HomePagePreview() {
    IBistuTheme { HomePage(AppState()) }
}
```

### 2.5 主题使用

- 颜色：优先使用 `MaterialTheme.colorScheme`，避免硬编码颜色值
- 字体：优先使用 `MaterialTheme.typography`
- 间距：优先使用 `Modifier.padding()` 和间距常量

## 3. 资源文件规范

### 3.1 res/values/strings.xml

```xml
<resources>
    <string name="app_name">iBistu</string>

    <!-- 登录模块 -->
    <string name="login_title">iBistu 登录</string>
    <string name="login_student_id_hint">学号</string>
    <string name="login_password_hint">密码</string>
    <string name="login_button">登录</string>
    <string name="login_logout">退出</string>
    <string name="login_success">已登录</string>

    <!-- 课表模块 -->
    <string name="schedule_title">课表</string>
    <string name="schedule_empty">暂无课表</string>

    <!-- 通用 -->
    <string name="loading">加载中...</string>
    <string name="network_error">网络错误: %1$s</string>
</resources>
```

### 3.2 res/values/colors.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="black">#FF000000</color>
    <color name="white">#FFFFFFFF</color>

    <!-- 主题色 -->
    <color name="primary">#FF6650A4</color>
    <color name="primary_container">#FFEADDFF</color>
    <color name="secondary">#FF625B71</color>

    <!-- 功能色 -->
    <color name="error">#FFB3261E</color>
    <color name="success">#FF4CAF50</color>
</resources>
```

### 3.3 Compose Theme 规范

- `Color.kt` — 定义调色板常量
- `Theme.kt` — 定义 `IBistuTheme` Composable，设置 colorScheme / typography / shapes
- `Type.kt` — 定义字体排版（Typography）

### 3.4 drawable 资源命名

| 类型 | 命名规范 | 示例 |
|------|---------|------|
| 图标 | `ic_<name>` | `ic_home`, `ic_account_box` |
| 背景 | `bg_<name>` | `bg_splash` |
| 图片 | `img_<name>` | `img_empty_state` |

## 4. 项目特有约定

### 4.1 组件命名后缀

| 组件类型 | 后缀 | 示例 |
|---------|------|------|
| Activity | `...Activity` | `LoginActivity`, `MainActivity` |
| Service | `...Service` | `SyncService`, `DownloadService` |
| BroadcastReceiver | `...Receiver` | `BootReceiver`, `TimeReceiver` |
| ContentProvider | `...Provider` | `ScheduleProvider`, `DataProvider` |

### 4.2 ViewModel 命名

- `LoginViewModel`, `ScheduleViewModel`, `HomeViewModel`

### 4.3 网络 API 命名

- `LoginApi`, `ScheduleApi`, `CommonApi`

### 4.4 Git 提交规范

```
<type>(<scope>): <description>

类型: feat / fix / refactor / style / docs / chore
示例:
  feat(login): add SM2 encrypted login
  fix(schedule): correct week display
  refactor(common): extract BaseActivity
  docs: add CODING_STYLE.md
```
