# iBistu

> 北京信息科技大学（BISTU）课表查看 Android 应用  
> 23 级移动应用系统课程设计

---

## 功能特性

- **自动登录** — 通过北信科统一身份认证（CAS + SM2 国密加密）一键登录
- **课表展示** — 按周七列网格展示完整课表，支持切换教学周
- **会话持久化** — Cookie 存入本地 SQLite，重启后自动恢复登录状态
- **启动语录** — SplashScreen 随机展示学院寄语
- **设置页** — 预留收藏 / 设置入口，便于后续功能扩展

---

## 技术栈

| 层次 | 技术 |
|------|------|
| 语言 | Kotlin |
| UI | Jetpack Compose + Material 3 |
| 导航 | Navigation Compose · NavigationSuiteScaffold |
| 网络 | OkHttp |
| 加密 | Tencent KonaSM Suite（SM2） |
| 本地存储 | Room (SQLite) |
| 并发 | Kotlin Coroutines |
| 最低 SDK | Android 15（API 35） |
| 目标 SDK | Android 16（API 36） |

> `:bistulogin` 模块已拆分为纯 Kotlin/JVM 库，可在任意 JVM 项目（服务器、CLI、桌面）中独立使用。

---

## 项目结构

```
iBistu/
├── bistulogin/                      ← 纯 Kotlin/JVM 库（发布为 jar，可跨平台使用）
│   └── src/main/kotlin/edu/bistu/cs4029/ibistu/login/
│       ├── BistuLogin.kt            ← SSO 登录 + SM2 加密 + Cookie 管理
│       ├── CookieStorage.kt         ← Cookie 持久化接口（调用方实现）
│       └── LoginLogger.kt           ← 日志接口（调用方实现）
├── app/src/main/java/edu/bistu/cs4029/ibistu/
│   ├── login/                       ← 登录模块（Android 端实现）
│   │   ├── AndroidLogger.kt        ← LoginLogger → logcat
│   │   ├── RoomCookieStorage.kt    ← CookieStorage 的 Room 实现
│   │   ├── AppDatabase.kt          ← 主 Room 数据库
│   │   ├── LoginDatabase.kt        ← Cookie 专用 Room 数据库
│   │   ├── CookieDao.kt            ← Cookie DAO
│   │   └── CookieEntity.kt         ← Cookie 数据实体
│   ├── MainActivity.kt              ← 应用唯一入口 Activity
│   ├── schedule/                    ← 课表模块
│   │   ├── HomePage.kt              ← 课表页 Composable
│   │   ├── Course.kt                ← 课程数据类
│   │   ├── ScheduleRepository.kt   ← 课表数据获取逻辑
│   │   └── ScheduleUtils.kt         ← 周次解析、日期工具
│   ├── profile/                     ← 登录 / 账户页
│   │   └── ProfilePage.kt
│   ├── favorites/                   ← 收藏 / 设置页
│   │   └── FavoritesPage.kt
│   ├── text/                        ← SplashScreen 语录
│   │   ├── Splashscreen.kt
│   │   ├── SplashConfig.kt
│   │   └── SplashProvider.kt
│   └── common/                      ← 通用模块
│       ├── base/BaseActivity.kt     ← Activity 基类（Compose 容器）
│       ├── state/AppState.kt        ← 跨页面共享状态
│       ├── navigation/AppNavigation.kt ← 根节点 + 导航逻辑
│       ├── ui/theme/                ← Material 3 主题
│       ├── service/                 ← Service 模板
│       ├── receiver/                ← BroadcastReceiver 模板
│       └── provider/                ← ContentProvider 模板
├── examples/                        ← CLI 使用示例
│   └── src/main/kotlin/example/
│       ├── Main.kt                  ← 完整登录流程演示
│       ├── InMemoryCookieStorage.kt ← CookieStorage 内存实现
│       └── ConsoleLogger.kt         ← LoginLogger 控制台实现
├── docs/                            ← 项目文档
│   ├── README.md                    ← 组件文档索引
│   ├── API.md                       ← 教务系统 API 文档
│   └── components/                  ← 四大组件详细文档
├── CODING_STYLE.md                  ← 编码风格规范
├── gradle/libs.versions.toml        ← 版本目录
├── build.gradle.kts                 ← 根构建脚本
└── settings.gradle.kts              ← 模块注册
```

---

## 认证流程

```
1. GET  sso.bistu.edu.cn/api/reset/rules   → 获取 SM2 公钥
2. GET  sso.bistu.edu.cn/login             → 建立 session，获取 flowKey
3. POST sso.bistu.edu.cn/username-password/login
        body: { flowKey, username, password(SM2加密) }
        → TGC Cookie
4. GET  jwxt.bistu.edu.cn/casLogin.do      → 建立教务系统 session
5. POST jwxt.bistu.edu.cn/ ...             → 获取学期、课表数据
```

密码使用 **SM2**（国密椭圆曲线）加密，输出格式为 C1C3C2 原始拼接（兼容前端 `sm2.min.js`）。

---

## 快速开始

### 构建运行（Android）

```bash
git clone https://github.com/ProjektMing/iBistu.git
cd iBistu

# 构建 + 安装到设备
./gradlew assembleDebug
./gradlew installDebug
```

### 构建 bistulogin 库（纯 JVM）

```bash
# 产物：bistulogin/build/libs/bistulogin.jar
./gradlew :bistulogin:jar

# 发布到本地 Maven 仓库
./gradlew :bistulogin:publishToMavenLocal
```

### 运行 CLI 示例

```bash
# 设置环境变量
export BISTU_USERNAME=你的学号
export BISTU_PASSWORD=你的密码

# 构建并运行
./gradlew :examples:jar
java -cp "bistulogin/build/libs/bistulogin.jar:examples/build/libs/examples.jar" example.MainKt

# 或直接用 Gradle
./gradlew :examples:run
```

### 在其他 JVM 项目中使用 bistulogin

```kotlin
// build.gradle.kts
dependencies {
    implementation("edu.bistu:bistulogin:1.0.0")
}
```

```kotlin
// 使用方式
val login = BistuLogin(
    cookieStorage = MyCookieStorage(),   // 实现 CookieStorage 接口
    logger = MyLogger()                  // 实现 LoginLogger 接口（可选，默认不输出）
)

// 一键登录
val result = login.fullLogin(username, password)
if (result.isSuccess) {
    // Cookie 已由 OkHttp 自动管理，直接发认证请求
    val data = login.get("https://jwxt.bistu.edu.cn/...")
}
```

---

## 文档

| 文档 | 说明 |
|------|------|
| [docs/API.md](docs/API.md) | 教务系统 REST API（SSO、UC、JWXT） |
| [docs/README.md](docs/README.md) | 四大组件规范索引 |
| [CODING_STYLE.md](CODING_STYLE.md) | Kotlin / Compose 编码风格规范 |

---

## Git 提交规范

```
<type>(<scope>): <description>

类型: feat / fix / refactor / style / docs / chore
示例:
  feat(schedule): add alarm reminder
  fix(login): handle flowKey expiry
  docs: update README
```

---

## 贡献

本项目为课程设计作业，贡献者均为小组成员：

| GitHub | 角色 |
|--------|------|
| [@ProjektMing](https://github.com/ProjektMing) | 负责人 |
| [@dreamseven7](https://github.com/dreamseven7) | 成员 |
| [@wsljjjzn](https://github.com/wsljjjzn) | 成员 |
| [@LE0NEEDS](https://github.com/LE0NEEDS) | 成员 |
| [@Fufuzka](https://github.com/Fufuzka) | 成员 |

提交前请阅读 [CODING_STYLE.md](CODING_STYLE.md) 确保代码风格一致。
