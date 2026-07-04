# iBistu 组件文档

## 四大组件规范

| 组件 | 文档 | 说明 |
|------|------|------|
| 🟢 **Activity** | [`activity.md`](activity.md) | 基类 BaseActivity、Navigation Compose、生命周期 |
| 🔵 **Service** | [`service.md`](service.md) | BaseWorkerService、BaseForegroundService、协程管理 |
| 🟡 **BroadcastReceiver** | [`broadcast-receiver.md`](broadcast-receiver.md) | 静态/动态注册、Android 13+ 导出标志 |
| 🟣 **ContentProvider** | [`content-provider.md`](content-provider.md) | UriMatcher、CRUD、SQLiteOpenHelper |

## 编码规范

| 文档 | 说明 |
|------|------|
| [`CODING_STYLE.md`](../CODING_STYLE.md) | 项目编码风格、Compose 写法、资源文件规范 |

## 包结构

```
edu.bistu.cs4029.ibistu/
├── MainActivity.kt              ← 入口 Activity
├── login/                       ← 登录模块（Android 端实现）
│   ├── AndroidLogger.kt         ← LoginLogger → logcat
│   ├── RoomCookieStorage.kt     ← CookieStorage 的 Room 实现
│   ├── AppDatabase.kt           ← 主 Room 数据库
│   ├── LoginDatabase.kt         ← Cookie 专用 Room 数据库
│   ├── CookieDao.kt             ← Cookie DAO
│   └── CookieEntity.kt          ← Cookie 数据实体
├── schedule/                    ← 课表模块（多学期切换）
│   ├── HomePage.kt              ← 课表页 + 空教室 BottomSheet
│   ├── ExamPage.kt              ← 考试安排页
│   ├── Course.kt                ← 数据类（Course, Exam, TermOption 等）
│   ├── ScheduleRepository.kt    ← 课表 API 调用层
│   ├── CachedScheduleRepository.kt ← 课表 Room 缓存层
│   ├── ExamRepository.kt        ← 考试 API 调用层（多端点探测）
│   ├── CachedExamRepository.kt  ← 考试 Room 缓存层
│   ├── EmptyClassroom.kt        ← 空教室数据类
│   ├── EmptyClassroomRepository.kt ← 空教室 API 调用层
│   ├── ScheduleToIcal.kt        ← 课表 → iCal (.ics) 导出
│   ├── ScheduleUtils.kt         ← 周次解析、日期工具
│   ├── WeeksAndTeachersParser.kt ← weeksAndTeachers 字段解析
│   └── model/                   ← Room 实体 + DAO（课表/考试缓存）
├── settings/                    ← 设置 + 自动静音
│   ├── SettingsPage.kt          ← 设置页 Composable
│   ├── AutoMuteScheduler.kt     ← 静音闹钟调度
│   ├── AutoMuteReceiver.kt      ← 静音 BroadcastReceiver
│   ├── AutoMuteBootReceiver.kt  ← 开机重启 BroadcastReceiver
│   └── AutoMuteRescheduleService.kt ← 静音重调度 Service
├── widget/                      ← 桌面小组件
│   └── ScheduleWidgetProvider.kt ← 今日课表 AppWidget
├── profile/                     ← 登录 / 账户页
│   └── ProfilePage.kt
├── navigate/                    ← 导航页
│   └── NavigationPage.kt
├── food/                        ← "今天吃什么"
│   └── EatWhatPage.kt
├── text/                        ← SplashScreen 语录
│   ├── Splashscreen.kt
│   ├── SplashConfig.kt
│   └── SplashProvider.kt
└── common/                      ← 通用模块
    ├── base/BaseActivity.kt     ← Activity 基类
    ├── state/AppState.kt        ← 跨页面状态（含学期选择）
    ├── navigation/AppNavigation.kt ← 导航 Compose
    ├── preferences/AppPreferences.kt ← SharedPreferences 封装
    ├── ui/theme/                ← Compose 主题
    ├── service/                 ← Service 模板
    ├── receiver/                ← BroadcastReceiver 模板
    └── provider/                ← ContentProvider 模板

> `BistuLogin` 核心实现位于独立模块 `:bistulogin`（纯 Kotlin/JVM），
> 详见根目录 [README.md](../README.md)。
```

## API 文档

| 文档 | 说明 |
|------|------|
| [`API.md`](API.md) | 教务系统 REST API 参考（SSO、UC、JWXT） |
| [`bistu-api.http`](bistu-api.http) | HTTP 请求示例（可直接导入 IDEA/VS Code） |

## 开发流程

本项目使用 **spec-skill** 规范驱动开发，规划文档位于 `.planning/` 目录。
