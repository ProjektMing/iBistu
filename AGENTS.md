# iBistu — BISTU 课表查看 Android 应用

北京信息科技大学课表查看应用，Kotlin + Jetpack Compose + Material 3。课程设计项目。

## Project

- **Stack**: Kotlin, Jetpack Compose, Material 3, OkHttp, Room (SQLite), Kotlin Coroutines, kotlinx.serialization, Tencent KonaSM (SM2 国密)
- **Modules**: `:app` (Android app), `:bistulogin` (纯 Kotlin/JVM 登录库), `:examples` (CLI 示例)
- **Entry**: `app/src/main/java/edu/bistu/cs4029/ibistu/MainActivity.kt` → `BaseActivity` → `IBistuRoot()` in `common/navigation/AppNavigation.kt`
- **Min SDK**: 35 | **Target**: 36 | **Root package**: `edu.bistu.cs4029.ibistu`

## Commands

| Purpose | Command |
|---|---|
| Build debug APK | `./gradlew assembleDebug` |
| Install to device | `./gradlew installDebug` |
| Unit tests | `./gradlew test` |
| Instrumented tests | `./gradlew connectedCheck` (needs device/emulator) |
| All tests | `./gradlew test connectedCheck` |
| Publish bistulogin lib | `./gradlew :bistulogin:publishToMavenLocal` |
| Run CLI example | `BISTU_USERNAME=学号 BISTU_PASSWORD=密码 ./gradlew :examples:run` |

## Architecture

- **`:app` modules** — Feature-based packages under `edu.bistu.cs4029.ibistu`:
  - `login/` — Android 端登录实现 (RoomCookieStorage, AndroidLogger, AppDatabase)
  - `schedule/` — 课表/考试/空教室 (Repository, CachedRepository, 数据类, iCal 导出)
  - `settings/` — 设置页 + 自动静音 (AutoMuteScheduler, BroadcastReceivers)
  - `widget/` — 桌面 AppWidget (ScheduleWidgetProvider)
  - `common/` — 基类 (BaseActivity), 导航 (AppNavigation, AppDestination), 共享状态 (AppState), 主题 (ui/theme), Preferences
  - `profile/` — 登录/账户页 (ProfilePage)
  - `navigate/` — 导航页 (NavigationPage)
  - `food/` — "今天吃什么" (EatWhatPage)
  - `text/` — SplashScreen 语录 (Splashscreen, SplashConfig, SplashProvider)
- **`:bistulogin`** — 纯 JVM 登录库 (BistuLogin, CookieStorage interface, LoginLogger interface)
- **State management**: `AppState` (common/state/AppState.kt) holds all shared app state as `mutableStateOf` properties; passed as parameter to page Composables.
- **Data flow**: OkHttp (CookieJar) → Repository → CachedRepository (Room) → AppState → Composable UI
- **Auth flow**: SM2 encrypted password → CAS SSO (TGC) → JWXT session → API cookies

## Conventions

- **Code style**: See `CODING_STYLE.md` — the authoritative reference.
- **Naming**: PascalCase for classes/Composables, camelCase for functions/vars. Compose pages suffix `Page`, previews suffix `Preview`.
- **Formatting**: 4-space indent, 120-char line width. Wildcard imports (`.*`) when >5 classes from same package.
- **Compose**: Expose `modifier: Modifier = Modifier` as first param. State hoisting with callbacks, no mutable state in reusable components.
- **KDoc**: On all public classes/functions. `// TODO:` / `// FIXME:` inline.
- **Resources**: `ic_` for icons, `bg_` for backgrounds. Theme in Color.kt / Theme.kt / Type.kt under `common/ui/theme/`.
- **Git**: `<type>(<scope>): <description>` — types: feat / fix / refactor / style / docs / chore. Always create a new branch before committing.
- **Testing**: JUnit 4 + MockK for unit tests; MockWebServer3 + Compose UI Test for instrumented tests. Repository tests use `MockServerTestRule`.
- **TDD workflow**: 所有开发必须遵循测试驱动开发流程：
  1. **写失败测试** — 先编写测试覆盖新功能/修复的场景，运行 `./gradlew test` 确认失败
  2. **实现最少代码** — 写刚好让测试通过的最小实现
  3. **验证全量** — `./gradlew test connectedCheck` 确认无回归
  4. **重构** — 清理代码后重新运行全量测试
  5. **提交** — 全量通过后才能提交（约定式提交 + 新分支，不推送）
  - 单元测试: JUnit 4 + MockK (`app/src/test/`)
  - 仪器化测试: MockWebServer3 + Compose UI Test (`app/src/androidTest/`)
  - 提交前必须全量通过，不通过不能提交

## Project Snapshot (pre-KMP)

| 模块 | 类型 | Android 依赖占比 |
|------|------|---|
| `:app` | Android App | 42/47 文件 (89%) 含 Android/Compose 依赖 |
| `:bistulogin` | JVM Library | 3/3 文件含 JVM 依赖 (OkHttp + Java Security + org.json) |
| `:examples` | CLI | 纯 JVM |

**可共享纯 Kotlin 文件** (5 个, ~400 行): `Course.kt`, `EmptyClassroom.kt`, `ScheduleUtils.kt`, `WeeksAndTeachersParser.kt`, `xxHash32.kt`

**KMP 迁移路线图**: Phase 1 建立 `:shared` → Phase 2 迁移 bistulogin (Ktor + kotlinx.serialization + 纯 Kotlin SM2) → Phase 3 业务逻辑共享 → Phase 4 iOS. 详见记忆 [[kmp-migration-plan]].

## Notes

— (add project-specific notes as needed)
