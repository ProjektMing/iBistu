package edu.bistu.cs4029.ibistu.testing

/**
 * 预置的模拟 HTTP 响应体（对应教务系统 SSO 和 API 返回值）。
 * 所有 JSON 均来自真实接口的实际返回值结构。
 */
object MockResponses {

    /** SM2 公钥获取成功（/api/reset/rules） */
    val PUBLIC_KEY_RESPONSE = """
    {
        "code": 200,
        "data": {
            "encrypt": {
                "algorithm": "sm2",
                "publicKey": "BHGxY2MkSYNhEEwqNHpo2L4KQHNaJ4/r6lVKOWnKxHNfnE5OV/Rg+6qP+oNp0Q5PYNx8p0BBqDRfZxkAL9gO3As="
            }
        }
    }
    """.trimIndent()

    /** info-query 成功（无验证码/MFA） */
    val INFO_QUERY_RESPONSE = """
    {
        "code": 200,
        "data": {
            "needCaptcha": false,
            "needMfa": false
        }
    }
    """.trimIndent()

    /** 登录成功（/username-password/login） */
    val LOGIN_SUCCESS_RESPONSE = """
    {
        "code": 666666,
        "msg": "登录成功",
        "data": {
            "tgc": "TGT-testmocktoken",
            "expire": 1789993357899,
            "service": "https://uc.bistu.edu.cn/api/login?target=https://uc.bistu.edu.cn/user/login"
        }
    }
    """.trimIndent()

    /** 登录失败（/username-password/login） */
    val LOGIN_FAILURE_RESPONSE = """
    {
        "code": 170002,
        "msg": "用户名或密码错误",
        "data": null
    }
    """.trimIndent()

    /** 当前学期信息（/modules/gg/cxmrxnxq.do） */
    val CURRENT_TERM_RESPONSE = """
    {
        "datas": {
            "cxmrxnxq": {
                "rows": [
                    {
                        "XNXQDM": "2025-2026-2",
                        "XNXQMC": "2025-2026学年第2学期",
                        "ZCSDM": "DQXNXQDM"
                    }
                ]
            }
        }
    }
    """.trimIndent()

    /** 学期周次（/api/schoolCalendar/getTermWeeks.do） */
    val TERM_WEEKS_RESPONSE = """
    {
        "datas": {
            "getTermWeeks": [
                { "serialNumber": 1, "startDate": "2026-02-23", "endDate": "2026-03-01" },
                { "serialNumber": 2, "startDate": "2026-03-02", "endDate": "2026-03-08" },
                { "serialNumber": 3, "startDate": "2026-03-09", "endDate": "2026-03-15" },
                { "serialNumber": 4, "startDate": "2026-03-16", "endDate": "2026-03-22" }
            ]
        }
    }
    """.trimIndent()

    /** 课表数据（/api/wdkbcx/getMyScheduleDetail.do） */
    val SCHEDULE_RESPONSE = """
    {
        "datas": {
            "getMyScheduleDetail": {
                "arrangedList": [
                    {
                        "courseName": "高等数学",
                        "courseCode": "MATH201",
                        "credit": "4",
                        "weeksAndTeachers": "1-16周 张老师",
                        "placeName": "教5-101",
                        "campusName": "小营校区",
                        "week": "1-16",
                        "dayOfWeek": 1,
                        "beginSection": 1,
                        "endSection": 2,
                        "beginTime": "08:00",
                        "endTime": "09:35"
                    },
                    {
                        "courseName": "大学物理",
                        "courseCode": "PHY101",
                        "credit": "3",
                        "weeksAndTeachers": "1-16周 李老师",
                        "placeName": "理学院-201",
                        "campusName": "小营校区",
                        "week": "1-16",
                        "dayOfWeek": 2,
                        "beginSection": 3,
                        "endSection": 4,
                        "beginTime": "10:00",
                        "endTime": "11:35"
                    },
                    {
                        "courseName": "大学英语",
                        "courseCode": "ENG301",
                        "credit": "2",
                        "weeksAndTeachers": "1-8周 王老师",
                        "placeName": "外语楼-302",
                        "campusName": "小营校区",
                        "week": "1-8",
                        "dayOfWeek": 3,
                        "beginSection": 5,
                        "endSection": 6,
                        "beginTime": "14:00",
                        "endTime": "15:35"
                    }
                ]
            }
        }
    }
    """.trimIndent()

    /** 考试安排（/api/wdks/queryMyExamArrangeMent.do） — 严格对齐 API 文档 §3.6 */
    val EXAM_RESPONSE = """
    {
        "datas": {
            "queryMyExamArrangeMent": {
                "rows": [
                    {
                        "KCM": "高等数学",
                        "KSRQ": "2026-07-06",
                        "KSSJMS": "09:00-11:00",
                        "JASMC": "沙河校区文理楼A-101",
                        "ZWH": "12",
                        "KSLXDM_DISPLAY": "期末考试",
                        "YXDM_DISPLAY": "沙河校区"
                    },
                    {
                        "KCM": "大学物理",
                        "KSRQ": "2026-07-07",
                        "KSSJMS": "14:00-16:00",
                        "JASMC": "沙河校区文理楼B-202",
                        "ZWH": "8",
                        "KSLXDM_DISPLAY": "期末考试",
                        "YXDM_DISPLAY": "沙河校区"
                    }
                ]
            }
        }
    }
    """.trimIndent()

    /** 空考试安排（命中端点但无考试） */
    val EMPTY_EXAM_RESPONSE = """
    {
        "datas": {
            "queryMyExamArrangeMent": {
                "rows": []
            }
        }
    }
    """.trimIndent()

    /** casLogin 重定向到教务系统首页（302 → jwxt 首页） */
    val JWXT_INDEX_HTML = "<html><body>教务系统首页</body></html>"

    /** 全量学期列表（xnxq.do），覆盖多个学期 */
    val XNXQ_LIST_RESPONSE = """
    {
        "code": "0",
        "datas": [
            { "itemCode": "2025-2026-3", "itemName": "2025-2026学年 小学期", "selected": true },
            { "itemCode": "2025-2026-2", "itemName": "2025-2026学年 第二学期", "selected": null },
            { "itemCode": "2025-2026-1", "itemName": "2025-2026学年 第一学期", "selected": null },
            { "itemCode": "2024-2025-2", "itemName": "2024-2025学年 第二学期", "selected": null },
            { "itemCode": "2024-2025-1", "itemName": "2024-2025学年 第一学期", "selected": null }
        ]
    }
    """.trimIndent()

    /** 指定学期 2024-2025-2 的课表（与 CURRENT_TERM_RESPONSE 不同学期） */
    val SCHEDULE_RESPONSE_2024_2 = """
    {
        "datas": {
            "getMyScheduleDetail": {
                "arrangedList": [
                    {
                        "courseName": "数据结构",
                        "courseCode": "CS201",
                        "credit": "3",
                        "weeksAndTeachers": "1-16周 赵老师",
                        "placeName": "计算中心-301",
                        "campusName": "小营校区",
                        "week": "1-16",
                        "dayOfWeek": 1,
                        "beginSection": 3,
                        "endSection": 4,
                        "beginTime": "10:00",
                        "endTime": "11:35"
                    }
                ]
            }
        }
    }
    """.trimIndent()
}
