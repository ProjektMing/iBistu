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
├── login/                       ← 登录模块
│   └── BistuLogin.kt
├── schedule/                    ← 课表模块（多学期切换）
│   ├── HomePage.kt              ← 首页课表 + 学期下拉框
│   ├── Course.kt                ← 数据类（Course, TermOption 等）
│   ├── ScheduleRepository.kt    ← API 调用层
│   ├── CachedScheduleRepository.kt ← Room 缓存层
│   └── model/                   ← Room 实体 + DAO
├── common/                      ← 通用模块
│   ├── state/AppState.kt        ← 跨页面状态（含学期选择）
│   ├── base/BaseActivity.kt     ← Activity 基类
│   ├── ui/theme/                ← Compose 主题
│   ├── navigation/              ← Navigation Compose
│   ├── service/                 ← Service 模板
│   ├── receiver/                ← BroadcastReceiver 模板
│   └── provider/                ← ContentProvider 模板
└── docs/                        ← 本文档目录
```

## 开发流程

本项目使用 **spec-skill** 规范驱动开发，规划文档位于 `.planning/` 目录。
