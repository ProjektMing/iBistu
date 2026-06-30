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

### 3.1 当前学期

```
POST /jwapp/sys/jwpubapp/modules/gg/cxmrxnxq.do
CSDM=SYS&ZCSDM=DQXNXQDM&SFSY=1
```

**响应字段：** `XNXQDM`（代码）、`XNXQMC`（名称）

### 3.2 教学周

```
POST /jwapp/sys/kbbpapp/api/schoolCalendar/getTermWeeks.do
XNXQDM=2025-2026-3
```

**响应字段：** `serialNumber`（周次）、`startDate`、`endDate`、`name`、`curWeek`

### 3.3 校区

```
POST /jwapp/sys/kbapp/api/wdkbcx/getMyScheduledCampus.do
XNXQDM=2025-2026-3
→ [{"id":"10","name":"沙河校区"}]
```

### 3.4 节次

```
POST /jwapp/sys/kbapp/api/wdkbcx/getMySectionList.do
XNXQDM=2025-2026-3&XQDM=10
→ [{"code":1,"name":"第1节","id":"1"},...]
```

### 3.5 课表 ⭐

```
POST /jwapp/sys/kbapp/api/wdkbcx/getMyScheduleDetail.do
XNXQDM=2025-2026-3&XQDM=10
```

**响应 arrangedList 字段：**

| 字段 | 类型 | 说明 |
|------|------|------|
| courseName | String | 课程名 |
| courseCode | String | 课程代码 |
| credit | String | 学分 |
| dayOfWeek | Int | 星期几（1-7） |
| beginSection | Int | 开始节次 |
| endSection | Int | 结束节次 |
| beginTime | String | 开始时间 |
| endTime | String | 结束时间 |
| placeName | String | 教室 |
| campusName | String | 校区 |
| weeksAndTeachers | String | 周次/教师 |
| teachingTarget | String | 授课对象 |
| color | String | 显示颜色 |

---

## 四、认证流程

```
SSO: GET /api/reset/rules → publicKey
SSO: GET /login → COOKIE_INFO (flowKey)
SSO: POST /username-password/login → TGC Cookie
JWXT: GET /casLogin.do (with TGC) → jwxt session
JWXT: POST /cxmrxnxq.do → 当前学期
JWXT: POST /getMyScheduleDetail.do → 课表 JSON
```
