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
| 最低 SDK | Android 14（API 35） |
| 目标 SDK | Android 16（API 36） |

---

## 项目结构

```
iBistu/
├── app/src/main/java/edu/bistu/cs4029/ibistu/
│   ├── MainActivity.kt              ← 应用唯一入口 Activity
│   ├── login/                       ← 登录模块
│   │   ├── BistuLogin.kt            ← SSO 登录 + SM2 加密 + Cookie 管理
│   │   ├── AppDatabase.kt           ← Room 数据库
│   │   ├── CookieDao.kt             ← Cookie 持久化 DAO
│   │   └── CookieEntity.kt          ← Cookie 数据实体
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
├── docs/                            ← 项目文档
│   ├── README.md                    ← 组件文档索引
│   ├── API.md                       ← 教务系统 API 文档
│   └── components/                  ← 四大组件详细文档
├── CODING_STYLE.md                  ← 编码风格规范
└── build.gradle.kts
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

### 构建运行

```bash
# 克隆仓库
git clone https://github.com/ProjektMing/iBistu.git
cd iBistu

# 使用 Android Studio 打开项目，或命令行构建
./gradlew assembleDebug

# 安装到设备/模拟器（需 Android 14+）
./gradlew installDebug
```

### 使用说明

1. 启动应用后，点击底部导航 **"登录"** 标签页
2. 输入北信科学号和统一身份认证密码
3. 点击 **"登录"** 按钮，等待课表自动加载
4. 切换到 **"课表"** 标签页查看本周课程
5. 使用左右箭头切换教学周

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
