# BISTU 教务系统 API 文档

基于实际抓包分析，涵盖 SSO、UC 用户中心、教务系统。

---

## 一、SSO 统一身份认证 (`sso.bistu.edu.cn`)

CAS 24.1.4 协议变体 + SM2 国密密码加密。

### 1.1 获取 SM2 公钥

```
GET /api/reset/rules
```

**响应：**
```json
{
  "code": 200,
  "data": {
    "encrypt": {
      "algorithm": "sm2",
      "publicKey": "BN6l0mvj55Fvvas/vgLD8/xYTA9Ni1+zsKivNpJJ1Scw7th3Wr3ZH/+GnF/rdULFRQR7Zs05t9Zz7z5MbQlvnm0="
    }
  }
}
```

### 1.2 获取 flowKey

首次访问 SSO 时服务器 Set-Cookie `COOKIE_INFO`（URL-encoded JSON），含 `data.flowKey`。后续所有 POST 需携带此字段。

### 1.3 登录

```
POST /username-password/login
Content-Type: application/json
```

**请求体：**
```json
{"flowKey": "flow.xxx", "username": "学号", "password": "<SM2密文Base64>"}
```

密码先用 `/api/reset/rules` 返回的 SM2 公钥加密。Java Cipher 输出为 ASN.1 DER，需转为 C1C3C2 原始格式（`04||x||y||C3||C2`），再 Base64。

**成功响应（code=666666）：**
```json
{
  "code": 666666,
  "msg": "登录成功",
  "data": {
    "tgc": "TGT-xxx",
    "expire": 1789993357899,
    "service": "/login?service=..."
  }
}
```

### 1.4 错误码

| Code | 含义 |
|------|------|
| 666666 | 登录成功 |
| 600901 | 未找到 TGC |
| 170002 | 用户名或密码错误 |
| 180046 | FlowKey 未找到 |
| 180028 | 登录失败次数过多，锁定30分钟 |

---

## 二、UC 用户中心 (`uc.bistu.edu.cn`)

### 2.1 用户状态

```
GET /api/uc/status?selfTimestamp=<ts>
→ {"code":0,"data":{"name":"...","schoolid":"...","username":"..."}}
```

### 2.2 用户详情

```
GET /api/uc/userinfo?selfTimestamp=<ts>
→ 含 phone, email, idCardType 等
```

---

## 三、教务系统 (`jwxt.bistu.edu.cn`)

### 认证方式

SSO 登录后通过 `casLogin.do` 桥接进入教务系统：

```
GET /jwapp/sys/yjsrzfwapp/bistuLogin/casLogin.do
```

携带 SSO TGC cookie，响应 302 跳转至教务首页并建立 session。

### 3.1 学期列表 🆕

```
GET /jwapp/sys/homeapp/api/home/kb/xnxq.do
```

**响应：**

```json
{
  "code": "0",
  "datas": [
    {
      "itemCode": "2025-2026-3",
      "itemName": "2025-2026学年 小学期",
      "selected": true
    },
    {
      "itemCode": "2025-2026-2",
      "itemName": "2025-2026学年 第二学期",
      "selected": null
    }
  ]
}
```

| 字段 | 说明 |
|------|------|
| `itemCode` | 学期代码，用于后续课表/考试 API 的 `XNXQDM` 参数 |
| `itemName` | 学期中文全称 |
| `selected` | `true` 表示当前所在学期 |

> 此 API 支持 `GET` 或 `POST`（无参数）。列表按学期倒序排列（最新在前），覆盖 2015-2016-1 至 2030-2031-3。
> iBistu 启动后后台调用此接口加载全量学期，供 HomePage 下拉框切换使用。

### 3.2 当前学期

```
POST /jwapp/sys/jwpubapp/modules/gg/cxmrxnxq.do
CSDM=SYS&ZCSDM=DQXNXQDM&SFSY=1
```

**响应字段：** `XNXQDM`（代码）、`XNXQMC`（名称）

### 3.3 教学周

```
POST /jwapp/sys/kbbpapp/api/schoolCalendar/getTermWeeks.do
XNXQDM=2025-2026-3
```

**响应字段：** `serialNumber`（周次）、`startDate`、`endDate`、`name`、`curWeek`

### 3.4 校区

```
POST /jwapp/sys/kbapp/api/wdkbcx/getMyScheduledCampus.do
XNXQDM=2025-2026-3
→ [{"id":"10","name":"沙河校区"}]
```

### 3.5 节次

```
POST /jwapp/sys/kbapp/api/wdkbcx/getMySectionList.do
XNXQDM=2025-2026-3&XQDM=10
→ [{"code":1,"name":"第1节","id":"1"},...]
```

### 3.6 课表 ⭐

```
POST /jwapp/sys/kbapp/api/wdkbcx/getMyScheduleDetail.do
```

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| XNXQDM | String | 是 | 学期代码，如 `2025-2026-3` |
| XQDM | String | 是 | 校区代码，如 `"10"`（沙河校区） |
| ZC | String | 否 | 周次，如 `"1"`。传入时仅返回该周课程；省略时返回整学期课程 |

> **获取策略：** iBistu 先通过 `getTermWeeks.do` 获取学期总周数，再并发调用本接口（每次传入不同 `ZC`），合并所有周的课程。
> 使用 `ZC` 分周请求的原因是：API 返回的 `week` 字段在实际测试中恒为 `"1"`，无法可靠表示课程的真实周次范围。
> 因此代码以请求参数 `ZC` 作为该批次课程的权威周次，而非依赖响应中的 `week` 字段。

**响应 `arrangedList` 各项字段：**

| 字段 | 类型 | 说明 |
|------|------|------|
| courseName | String | 课程名 |
| courseCode | String | 课程代码 |
| credit | String | 学分 |
| dayOfWeek | Int | 星期几（1-7） |
| beginSection | Int | 开始节次 |
| endSection | Int | 结束节次 |
| beginTime | String | 开始时间（如 `"09:50"`） |
| endTime | String | 结束时间（如 `"16:05"`） |
| placeName | String | 教室 |
| campusName | String | 校区 |
| week | String | ⚠️ 课程周次（**不可靠**：分周请求时恒为 `"1"`；整学期请求时可能为 `"1-16"` 等范围） |
| weeksAndTeachers | String | 周次与教师信息，格式如 `"1周[实验]/张翠平[主讲]"` 或 `"1-16周/张三[主讲]"` |
| teachingTarget | String | 授课对象（班级列表） |
| color | String | UI 显示颜色（如 `"#FFF0CC"`） |
| teachClassId | String | 教学班 ID |
| teachClassName | String | 教学班名称 |
| courseSerialNo | String | 课程序号（如 `"01"`, `"01S01"`） |

---

### 3.7 考试安排 ⭐

```
POST /jwapp/sys/wdkwapp/api/wdks/queryMyExamArrangeMent.do
XNXQDM=2025-2026-3
```

> **注意：** 此端点通过浏览器 DevTools 抓包确认。实际响应结构可能因教务系统版本而异，`ExamRepository.kt` 已内置多种 JSON 格式的自动识别逻辑。

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| XNXQDM | String | 是 | 学期代码，如 `2025-2026-3`（8位，YYYY-YYYY-N 格式） |

**响应格式（推测）：**

```json
{
  "datas": {
    "queryMyExamArrangeMent": {
      "rows": [
        {
          "KCM": "高等数学",
          "KSRQ": "2025-07-06",
          "KSSJMS": "09:00-11:00",
          "JASMC": "沙河校区文理楼A-101",
          "ZWH": "12",
          "KSLXDM_DISPLAY": "期末考试",
          "YXDM_DISPLAY": "沙河校区"
        }
      ]
    }
  }
}
```

**响应字段（通过字段名模糊匹配，兼容中英文变体）：**

| 字段 | 候选名 | 说明 |
|------|--------|------|
| courseName | `KCM`, `courseName`, `KCMC`, `className`, `KSMC` | 课程名称 |
| examDate | `examDate`, `KSRQ`, `testDate`, `RQ`, `date` | 考试日期 |
| examTime | `KSSJMS`, `examTime`, `KSSJ`, `testTime`, `SJ` | 考试时间（如 `09:00-11:00`） |
| location | `JASMC`, `placeName`, `KSDD`, `examRoom`, `JSM` | 考场/教室 |
| seatNumber | `seatNo`, `ZWH`, `seat`, `ZW`, `seatNumber` | 座位号 |
| examType | `KSLXDM_DISPLAY`, `examType`, `KSLX`, `KSLXMC` | 考试类型（补考/期末等） |
| campus | `YXDM_DISPLAY`, `campusName`, `XQMC`, `campus`, `XQ` | 所属校区 |

**其他可能的响应结构：**

端点探测代码还会尝试以下格式（自动兼容）：

- `{ datas: { xxx: { arranged: [...], notArranged: [...] } } }` — 已安排/未安排分组
- `{ data: { rows: [...] } }` — 简化格式
- `{ success: true, result: [...] }` — 通用 API 格式
- `[ {...}, {...} ]` — 顶层数组

**请求前置条件：**

与课表 API 相同，需要先通过 SSO → casLogin.do 建立教务系统 session。此外建议先 GET `/jwapp/sys/wdkwapp/*default/index.do?THEME=indigo&EMAP_LANG=zh&forceApp=wdkwapp` 以确保相关 Cookie 已下发。

---

### 3.8 空闲教室查询 ⭐

> **来源：** 通过浏览器 DevTools 抓包 `jsjy`（教室借用）模块确认。模块入口页：
> `GET /jwapp/sys/jsjy/*default/index.do?THEME=indigo&EMAP_LANG=zh&forceApp=jsjy`

#### 3.8.1 查询空闲教室

```
POST /jwapp/sys/jsjy/modules/jsjysq/cxkxjs.do
Content-Type: application/x-www-form-urlencoded; charset=UTF-8
```

**核心机制：** 该接口使用 `querySetting` JSON 参数来描述查询条件。`querySetting` 是一组 `{name, builder, value}` 对象的 JSON 数组，每个对象对应一个筛选维度及其匹配方式（`builder`）。

**请求参数（form 编码）：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `querySetting` | String (JSON) | 是 | 查询条件数组，URL-encoded JSON。见下方说明 |
| `pageNumber` | int | 否 | 页码，从 1 开始（默认 1） |
| `pageSize` | int | 否 | 每页条数（默认 10） |
| `*order` | String | 否 | 排序方式，如 `+LC,+SKZWS,+WID`（`+` 升序，`-` 降序） |
| `KSRQ` | String | 否 | 开始日期，如 `2026-07-05`（与 `querySetting` 中重复，一般同时传入） |
| `JSRQ` | String | 否 | 结束日期 |
| `KSSJ` | String | 否 | 开始时间，HH:mm 格式 |
| `JSSJ` | String | 否 | 结束时间 |

**`querySetting` 结构：**

```json
[
  {
    "name": "XXXQDM",
    "caption": "校区",
    "linkOpt": "AND",
    "builderList": "cbl_List",
    "builder": "equal",
    "value": "10",
    "value_display": "沙河校区"
  },
  {
    "name": "KSRQ",
    "caption": "开始日期",
    "linkOpt": "AND",
    "builderList": "cbl_String",
    "builder": "include",
    "value": "2026-07-05"
  },
  {
    "name": "JSRQ",
    "caption": "结束日期",
    "linkOpt": "AND",
    "builderList": "cbl_String",
    "builder": "include",
    "value": "2026-07-06"
  },
  {
    "name": "KSSJ",
    "caption": "开始时间",
    "linkOpt": "AND",
    "builderList": "cbl_String",
    "builder": "include",
    "value": "19:30"
  },
  {
    "name": "JSSJ",
    "caption": "结束时间",
    "linkOpt": "AND",
    "builderList": "cbl_String",
    "builder": "include",
    "value": "23:30"
  },
  {
    "name": "SKZWS",
    "caption": "上课座位数",
    "linkOpt": "AND",
    "builderList": "cbl_Other",
    "builder": "moreEqual",
    "value": 1
  },
  {
    "name": "DZLYLX",
    "value": "JSJY"
  }
]
```

| `querySetting` 字段 | 说明 |
|---------------------|------|
| `name` | 查询字段名（与响应字段名对应） |
| `builder` | 匹配方式：`equal`（等于）、`include`（包含）、`moreEqual`（≥）等 |
| `builderList` | builder 值域约束：`cbl_String`（字符串）、`cbl_List`（枚举/下拉）、`cbl_Other`（数值/其他） |
| `value` / `value_display` | 筛选值 / 显示名。枚举字段（校区、教学楼等）需填代码 |
| `linkOpt` | 与其他条件的连接方式，固定 `AND` |

> **注意：** `DZLYLX=JSJY` 似乎是一个固定的隐藏参数（"动作来源类型 = 教室借用"），所有请求均需携带。

**支持的筛选字段（`asjcxkxjs` 模型）：**

| `name` | 中文 | 类型 | builderList | 字典接口 |
|--------|------|------|-------------|----------|
| `XXXQDM` | 校区 | String | `cbl_List` | `code/83a986fc-e677-400e-99a4-c7bb39c2ca35.do` |
| `JXLDM` | 教学楼 | String | `cbl_List` | `code/ebb0e845-6ae0-44ab-aa5d-4b9f57ed111d.do` |
| `JASLXDM` | 教室类型 | String | `cbl_List` | `code/aa53c930-f7a2-43b4-af6a-d91e96504556.do` |
| `DWDM` | 管理单位 | String | `cbl_List` | `code/a13e88da-06f0-4fa3-a01e-23795373f284.do` |
| `JASMC` | 教室名称 | String | `cbl_String` | — |
| `KSRQ` / `JSRQ` | 日期范围 | String | `cbl_String` | — |
| `KSSJ` / `JSSJ` | 时间范围 | String | `cbl_String` | — |
| `SKZWS` | 上课座位数 | int | `cbl_Other` | — |
| `LC` | 楼层 | int | `cbl_Other` | — |

**响应格式：**

```json
{
  "code": "0",
  "datas": {
    "cxkxjs": {
      "totalSize": 166,
      "pageNumber": 1,
      "pageSize": 10,
      "rows": [
        {
          "JASMC": "WLA-106",
          "JXLDM": "501",
          "JXLDM_DISPLAY": "文理楼A座 沙河校区",
          "XXXQDM": "10",
          "XXXQDM_DISPLAY": "沙河校区",
          "LC": 1.0,
          "SKZWS": 40.0,
          "KSZWS": 20.0,
          "JASLXDM": "02",
          "JASLXDM_DISPLAY": "多媒体",
          "DWDM": "17",
          "DWDM_DISPLAY": "教务处",
          "JASDM": "050101",
          "WID": "a74cd0905f01464b9f2844f440431448",
          "SFYXPK": "1",
          "SFYXJY": "1",
          "SFYXKS": "1",
          "BZ": null,
          "ZT": "1"
        }
      ]
    }
  }
}
```

**响应 `rows` 每项常用字段：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `JASMC` | String | 教室名称（如 `WLA-106`） |
| `JXLDM` / `JXLDM_DISPLAY` | String | 教学楼代码 / 显示名 |
| `XXXQDM` / `XXXQDM_DISPLAY` | String | 校区代码 / 显示名 |
| `LC` | float | 楼层 |
| `SKZWS` | float | 上课座位数 |
| `KSZWS` | float | 考试座位数 |
| `JASLXDM` / `JASLXDM_DISPLAY` | String | 教室类型代码 / 显示名（如 `多媒体`） |
| `DWDM` / `DWDM_DISPLAY` | String | 管理单位代码 / 显示名（如 `教务处`） |
| `JASDM` | String | 教室代码 |
| `WID` | String | 唯一标识（UUID） |
| `BZ` | String | 备注（如 `智慧教室`） |
| `SFYXPK` | String | 是否允许排课（`"1"`=是） |
| `SFYXJY` | String | 是否允许借用（`"1"`=是） |
| `SFYXKS` | String | 是否允许考试（`"1"`=是） |

#### 3.8.2 字典/码表接口

查询空闲教室时，下拉选项（校区、教学楼、教室类型、管理单位）通过以下字典接口获取：

```
GET /jwapp/code/{uuid}.do
```

| UUID | 字典内容 |
|------|----------|
| `83a986fc-e677-400e-99a4-c7bb39c2ca35` | 校区 |
| `ebb0e845-6ae0-44ab-aa5d-4b9f57ed111d` | 教学楼 |
| `9edfd823-1f71-4bb6-b002-9fb1fb8c4b2b` | 教室类型 |
| `aa53c930-f7a2-43b4-af6a-d91e96504556` | 教室类型（备用） |
| `a13e88da-06f0-4fa3-a01e-23795373f284` | 管理单位 |
| `1daca2c6-abd2-4278-af49-5af038db0926` | 学年学期 |

**响应格式（推测）：** 返回 `[{code, name}]` 或 `[{itemCode, itemName}]` 数组。

#### 3.8.3 前置条件

与课表 API 相同，需要先通过 SSO → `casLogin.do` 建立教务系统 session。此外建议先 GET：

```
GET /jwapp/sys/jsjy/*default/index.do?THEME=indigo&EMAP_LANG=zh&forceApp=jsjy
```

以确保教室借用模块的 Cookie 已下发。

---
## 四、认证流程

```
SSO: GET /api/reset/rules → publicKey
SSO: GET /login → COOKIE_INFO (flowKey)
SSO: POST /username-password/login → TGC Cookie
JWXT: GET /casLogin.do (with TGC) → jwxt session
JWXT: GET /xnxq.do → 全量学期列表
JWXT: POST /getMyScheduleDetail.do → 课表 JSON（可指定 XNXQDM 切换学期）
JWXT: POST /queryMyExamArrangeMent.do → 考试安排 JSON
JWXT: GET /jsjy/*default/index.do → 教室借用模块 Cookie
JWXT: POST /jsjysq/cxkxjs.do → 空闲教室 JSON（需 querySetting 筛选条件）
```
