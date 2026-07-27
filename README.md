# iBistu

> 北京信息科技大学（BISTU）课表查看 Android 应用  
> 23 级移动应用系统课程设计

---

## 功能特性

- **自动登录** — 通过北信科统一身份认证（CAS + SM2 国密加密）一键登录
- **课表展示** — 按周七列网格展示完整课表，支持切换教学周
- **学期切换** — 支持在历史学期（2015 至今）间自由切换课表与考试
- **考试安排** — 展示本学期考试时间、地点、座位号及倒计时
- **空闲教室** — 长按课表单元格查询当前时段空闲教室
- **桌面小组件** — Android AppWidget 显示今日课程与上课状态
- **iCal 导出** — 将课表导出为标准 .ics 文件，导入系统日历
- **自动静音** — 上课时段自动切换静音模式（可开关）
- **会话持久化** — Cookie 存入本地 SQLite，重启后自动恢复登录状态
- **启动语录** — SplashScreen 随机展示学院寄语
- **疯狂星期四** — 每周四彩蛋提醒

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
│   │   ├── HomePage.kt              ← 课表页 + 空教室 BottomSheet
│   │   ├── ExamPage.kt              ← 考试安排页
│   │   ├── Course.kt                ← 数据类（Course, Exam, TermOption 等）
│   │   ├── ScheduleRepository.kt   ← 课表 API 调用层
│   │   ├── CachedScheduleRepository.kt ← 课表 Room 缓存层
│   │   ├── ExamRepository.kt        ← 考试 API 调用层（多端点探测）
│   │   ├── CachedExamRepository.kt  ← 考试 Room 缓存层
│   │   ├── EmptyClassroom.kt        ← 空教室数据类
│   │   ├── EmptyClassroomRepository.kt ← 空教室 API 调用层
│   │   ├── ScheduleToIcal.kt        ← 课表 → iCal (.ics) 导出
│   │   ├── ScheduleUtils.kt         ← 周次解析、日期工具
│   │   ├── WeeksAndTeachersParser.kt ← weeksAndTeachers 字段解析
│   │   └── model/                   ← Room 实体 + DAO（课表/考试缓存）
│   ├── settings/                    ← 设置 + 自动静音
│   │   ├── SettingsPage.kt          ← 设置页 Composable
│   │   ├── AutoMuteScheduler.kt     ← 静音闹钟调度
│   │   ├── AutoMuteReceiver.kt      ← 静音 BroadcastReceiver
│   │   ├── AutoMuteBootReceiver.kt  ← 开机重启 BroadcastReceiver
│   │   └── AutoMuteRescheduleService.kt ← 静音重调度 Service
│   ├── widget/                      ← 桌面小组件
│   │   └── ScheduleWidgetProvider.kt ← 今日课表 AppWidget
│   ├── profile/                     ← 登录 / 账户页
│   │   └── ProfilePage.kt
│   ├── navigate/                    ← 导航页
│   │   └── NavigationPage.kt
│   ├── food/                        ← "今天吃什么"
│   │   └── EatWhatPage.kt
│   ├── text/                        ← SplashScreen 语录
│   │   ├── Splashscreen.kt
│   │   ├── SplashConfig.kt
│   │   └── SplashProvider.kt
│   └── common/                      ← 通用模块
│       ├── base/BaseActivity.kt     ← Activity 基类（Compose 容器）
│       ├── state/AppState.kt        ← 跨页面共享状态
│       ├── navigation/AppNavigation.kt ← 根节点 + 导航逻辑
│       ├── preferences/AppPreferences.kt ← SharedPreferences 封装
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

## 认证与数据流程

```
SSO: GET  /api/reset/rules                  → 获取 SM2 公钥
SSO: GET  /login                            → 建立 session，获取 flowKey
SSO: POST /username-password/login          → TGC Cookie（密码 SM2 加密）
JWXT: GET  /casLogin.do (with TGC)           → 建立教务系统 session
JWXT: GET  /jwapp/sys/homeapp/api/home/kb/xnxq.do                 → 全量学期列表
JWXT: POST /jwapp/sys/kbapp/api/wdkbcx/getMyScheduleDetail.do     → 课表 JSON（支持按周/按学期）
JWXT: POST /jwapp/sys/wdkwapp/api/wdks/queryMyExamArrangeMent.do  → 考试安排 JSON
JWXT: GET  /jwapp/sys/jsjy/*default/index.do                      → 教室借用模块 Cookie
JWXT: POST /jwapp/sys/jsjy/modules/jsjysq/cxkxjs.do               → 空闲教室 JSON（含 querySetting 筛选）
```

密码使用 **SM2**（国密椭圆曲线）加密，输出格式为 C1C3C2 原始拼接（兼容前端 `sm2.min.js`）。详细 API 文档见 [docs/API.md](docs/API.md)。

---

## 快速开始

构建需要本机安装 **JDK 17 或更高版本**（可直接使用 Android Studio 自带的 JBR）。
项目仍以 Java 11 字节码为兼容目标，构建过程不会再自动下载额外 JDK。

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
# 产物：bistulogin/build/libs/bistulogin-*.jar

# 发布到本地 Maven 仓库
./gradlew :bistulogin:publishToMavenLocal
```

### 运行 CLI 示例

```bash
# 设置环境变量
export BISTU_USERNAME=你的学号
export BISTU_PASSWORD=你的密码

# 构建并运行（推荐，Gradle 会自动带上所有依赖）
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
