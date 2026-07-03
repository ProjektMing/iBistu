package edu.bistu.cs4029.ibistu

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import edu.bistu.cs4029.ibistu.common.state.AppState
import edu.bistu.cs4029.ibistu.food.EatWhatPage
import edu.bistu.cs4029.ibistu.schedule.Course
import edu.bistu.cs4029.ibistu.schedule.Exam
import edu.bistu.cs4029.ibistu.schedule.ExamPage
import edu.bistu.cs4029.ibistu.schedule.HomePage
import edu.bistu.cs4029.ibistu.schedule.TermOption
import edu.bistu.cs4029.ibistu.schedule.TermWeek
import edu.bistu.cs4029.ibistu.testing.ComposeTestActivity
import edu.bistu.cs4029.ibistu.profile.ProfilePage
import edu.bistu.cs4029.ibistu.navigate.NavigationPage
import edu.bistu.cs4029.ibistu.login.LoginResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.json.JSONObject
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI"真人操作"仪器测试。
 *
 * 使用 mock 数据渲染实际 Compose 页面，模拟用户点击/输入等操作，
 * 验证 UI 对数据变化的正确响应。所有网络数据均为 mock，
 * 但 UI 渲染和交互流程在真机上完整执行。
 */
class ComposeUiInstrumentedTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComposeTestActivity>()

    private lateinit var state: AppState

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        state = AppState(context)
    }

    @Test
    fun eatWhatPage_showsWheelAndCandidates() {
        composeTestRule.setContent {
            EatWhatPage(
                isThursday = false,
                initialOptions = listOf("麻辣香锅", "兰州拉面", "黄焖鸡", "饺子", "汉堡炸鸡"),
                persistChanges = false
            )
        }

        composeTestRule.onNodeWithText("今天吃啥？").assertIsDisplayed()
        composeTestRule.onNodeWithText("开始转盘").assertIsDisplayed()
        composeTestRule.onNodeWithText("麻辣香锅").assertIsDisplayed()
        composeTestRule.onNodeWithText("汉堡炸鸡").assertIsDisplayed()
    }

    @Test
    fun eatWhatPage_showsCrazyThursdayReminder() {
        composeTestRule.setContent {
            EatWhatPage(
                isThursday = true,
                initialOptions = listOf("炸鸡", "汉堡"),
                persistChanges = false
            )
        }

        composeTestRule.onNodeWithText("🍗 今天是疯狂星期四").assertIsDisplayed()
    }

    @Test
    fun eatWhatPage_hidesCrazyThursdayReminder_whenDisabled() {
        composeTestRule.setContent {
            EatWhatPage(
                isThursday = true,
                showThursdayReminder = false,
                initialOptions = listOf("炸鸡", "汉堡"),
                persistChanges = false
            )
        }

        composeTestRule.onAllNodesWithText("🍗 今天是疯狂星期四").assertCountEquals(0)
    }

    // ── HomePage：课表视图 ────────────────────────────────────

    @Test
    fun homePage_displaysCourseNames() {
        state.courses = listOf(
            Course(
                "高等数学", "MATH201", "4", "张老师", "教5-101", "小营校区",
                "1-16周", 1, 1, 2, "08:00", "09:35"
            ),
            Course(
                "大学物理", "PHY101", "3", "李老师", "理学院-201", "小营校区",
                "1-16周", 2, 3, 4, "10:00", "11:35"
            )
        )
        state.termName = "2025-2026学年第2学期"
        state.currentWeek = 1
        state.weekRange = 1..16
        state.termWeeks = mapOf(
            1 to TermWeek(1, "2026-02-23 00:00:00", "2026-03-01 23:59:59")
        )

        composeTestRule.setContent {
            HomePage(state = state)
        }

        // 验证学期名称
        composeTestRule.onNodeWithText("2025-2026学年第2学期").assertIsDisplayed()

        // 验证课程名出现在课表中
        composeTestRule.onNodeWithText("高等数学").assertIsDisplayed()
        composeTestRule.onNodeWithText("大学物理").assertIsDisplayed()

        // 验证时间
        composeTestRule.onNodeWithText("08:00-09:35").assertIsDisplayed()
        composeTestRule.onNodeWithText("10:00-11:35").assertIsDisplayed()

        // 验证教室
        composeTestRule.onNodeWithText("教5-101").assertIsDisplayed()
        composeTestRule.onNodeWithText("理学院-201").assertIsDisplayed()

        // 验证周导航器显示当前周
        composeTestRule.onNodeWithText("第 1 周").assertIsDisplayed()
    }

    @Test
    fun homePage_weekNavigation_changesWeek() {
        state.courses = listOf(
            Course(
                "高等数学", "MATH201", "4", "张老师", "教5-101", "小营校区",
                "1-16周", 1, 1, 2, "08:00", "09:35"
            )
        )
        state.termName = "2025-2026-2"
        state.currentWeek = 1
        state.weekRange = 1..16

        composeTestRule.setContent {
            HomePage(state = state)
        }

        // 初始：第 1 周
        composeTestRule.onNodeWithText("第 1 周").assertIsDisplayed()
        composeTestRule.onNodeWithText("高等数学").assertIsDisplayed()

        // 点击 ">" 进入下一周
        composeTestRule.onNodeWithText(">").performClick()

        // 周数应更新为第 2 周
        composeTestRule.onNodeWithText("第 2 周").assertIsDisplayed()

        // 点击 "<" 返回上一周
        composeTestRule.onNodeWithText("<").performClick()
        composeTestRule.onNodeWithText("第 1 周").assertIsDisplayed()
    }

    @Test
    fun homePage_emptyWeek_showsNoCourses() {
        state.courses = listOf(
            Course(
                "高等数学", "MATH201", "4", "张老师", "教5-101", "小营校区",
                "1-8周", 1, 1, 2, "08:00", "09:35"
            ) // 仅第 1-8 周
        )
        state.termName = "2025-2026-2"
        state.currentWeek = 10 // 设置为第 10 周——无课
        state.weekRange = 1..16

        composeTestRule.setContent {
            HomePage(state = state)
        }

        // 课程不应显示（不在第 10 周范围内）
        composeTestRule.onNodeWithText("本周无课").assertIsDisplayed()
    }

    @Test
    fun homePage_emptyCourses_showsLoginPrompt() {
        state.courses = emptyList()
        state.termName = ""

        composeTestRule.setContent {
            HomePage(state = state)
        }

        // 无课表时提示登录
        composeTestRule.onNodeWithText("请先在 Profile 中登录").assertIsDisplayed()
    }

    @Test
    fun homePage_shareButton_visible() {
        state.courses = listOf(
            Course(
                "测试课", "TEST001", "1", "老师", "教室", "校区",
                "1周", 1, 1, 1, "08:00", "08:45"
            )
        )
        state.termName = "2025-2026-2"
        state.currentWeek = 1
        state.weekRange = 1..1

        composeTestRule.setContent {
            HomePage(state = state)
        }

        // 导出按钮应可见
        composeTestRule.onNodeWithContentDescription("导出课表").assertIsDisplayed()
    }

    @Test
    fun homePage_examButton_visible() {
        state.courses = listOf(
            Course(
                "测试课", "TEST001", "1", "老师", "教室", "校区",
                "1周", 1, 1, 1, "08:00", "08:45"
            )
        )
        state.termName = "2025-2026-2"
        state.currentWeek = 1
        state.weekRange = 1..1

        composeTestRule.setContent {
            HomePage(state = state)
        }

        // "考试安排" 按钮应可见
        composeTestRule.onNodeWithText("考试安排").assertIsDisplayed()
    }

    // ── 学期下拉框 ───────────────────────────────────────────

    @Test
    fun homePage_semesterDropdown_showsCurrentTerm() {
        state.courses = listOf(
            Course("测试课", "TEST001", "1", "老师", "教室", "校区",
                "1周", 1, 1, 1, "08:00", "08:45")
        )
        state.termName = "2025-2026学年第2学期"
        state.termCode = "2025-2026-2"
        state.selectedTermName = "2025-2026学年第2学期"
        state.currentWeek = 1
        state.weekRange = 1..1

        composeTestRule.setContent {
            HomePage(state = state)
        }

        // 下拉框显示的文本为当前选中的学期名
        composeTestRule.onNodeWithText("2025-2026学年第2学期").assertIsDisplayed()
    }

    @Test
    fun homePage_semesterDropdown_showsLoading() {
        state.courses = listOf(
            Course("测试课", "TEST001", "1", "老师", "教室", "校区",
                "1周", 1, 1, 1, "08:00", "08:45")
        )
        state.termName = "2025-2026学年第2学期"
        state.termCode = "2025-2026-2"
        state.isLoadingTerm = true
        state.currentWeek = 1
        state.weekRange = 1..1

        composeTestRule.setContent {
            HomePage(state = state)
        }

        // 加载中状态显示 "加载中…"
        composeTestRule.onNodeWithText("加载中…").assertIsDisplayed()
    }

    @Test
    fun homePage_semesterDropdown_showsSelectedFromOptions() {
        state.courses = listOf(
            Course("测试课", "TEST001", "1", "老师", "教室", "校区",
                "1周", 1, 1, 1, "08:00", "08:45")
        )
        state.termName = "2025-2026学年 小学期"
        state.termCode = "2025-2026-3"
        state.selectedTermName = "2025-2026学年 小学期"
        state.termOptions = listOf(
            TermOption("2025-2026-3", "2025-2026学年 小学期"),
            TermOption("2025-2026-2", "2025-2026学年 第二学期"),
            TermOption("2025-2026-1", "2025-2026学年 第一学期")
        )
        state.currentWeek = 1
        state.weekRange = 1..1

        composeTestRule.setContent {
            HomePage(state = state)
        }

        // 下拉框应显示选中的学期
        composeTestRule.onNodeWithText("2025-2026学年 小学期").assertIsDisplayed()
    }

    // ── ProfilePage：登录表单交互 ─────────────────────────────

    @Test
    fun profilePage_showsLoginForm_whenNotRestoring() {
        state.isRestoring = false

        composeTestRule.setContent {
            ProfilePage(
                state = state,
                scope = CoroutineScope(Dispatchers.Main)
            )
        }

        // 页面标题
        composeTestRule.onNodeWithText("iBistu 登录").assertIsDisplayed()
        // 登录按钮
        composeTestRule.onNodeWithText("登录").assertIsDisplayed()
    }

    @Test
    fun profilePage_showsRestoring_whenRestoring() {
        // isRestoring 默认为 true
        composeTestRule.setContent {
            ProfilePage(
                state = state,
                scope = CoroutineScope(Dispatchers.Main)
            )
        }

        composeTestRule.onNodeWithText("恢复登录中...").assertIsDisplayed()
    }

    @Test
    fun profilePage_prefilledInput_showsInFields() {
        state.isRestoring = false
        state.studentId = "114514"
        state.password = "secret123"

        composeTestRule.setContent {
            ProfilePage(
                state = state,
                scope = CoroutineScope(Dispatchers.Main)
            )
        }

        // 验证输入框显示了预设的学号
        composeTestRule.onNodeWithText("114514").assertIsDisplayed()
        // 密码字段使用了 PasswordVisualTransformation，视觉上显示为圆点
        // 但 InputText 语义属性仍包含原始文本
        // 只需验证密码输入框存在（通过 label "密码" 确认）
        composeTestRule.onNodeWithText("密码").assertIsDisplayed()
    }

    @Test
    fun profilePage_showsLoggedIn_whenLoginSuccess() {
        state.isRestoring = false
        state.termName = "2025-2026学年 小学期"
        state.loginResult = LoginResult(
            code = 666666,
            message = "登录成功",
            serviceUrl = null,
            rawJson = JSONObject()
        )

        composeTestRule.setContent {
            ProfilePage(
                state = state,
                scope = CoroutineScope(Dispatchers.Main)
            )
        }

        composeTestRule.onNodeWithText("登录成功").assertIsDisplayed()
        composeTestRule.onNodeWithText("欢迎回来，今天也要好好上课").assertIsDisplayed()
        composeTestRule.onNodeWithText("本学期课程").assertIsDisplayed()
        composeTestRule.onNodeWithText("考试安排").assertIsDisplayed()
        composeTestRule.onNodeWithText("2025-2026学年 小学期").assertIsDisplayed()
        composeTestRule.onNodeWithText("退出登录").assertIsDisplayed()
    }

    @Test
    fun profilePage_showsError_whenErrorMessageSet() {
        state.isRestoring = false
        state.errorMessage = "用户名或密码错误"

        composeTestRule.setContent {
            ProfilePage(
                state = state,
                scope = CoroutineScope(Dispatchers.Main)
            )
        }

        // 错误消息应显示
        composeTestRule.onNodeWithText("用户名或密码错误").assertIsDisplayed()
    }

    // ── ExamPage：考试安排页面（对齐 API 文档 §3.6） ──────────

    @Test
    fun examPage_displaysExamCards() {
        state.exams = listOf(
            Exam(
                "高等数学", "2026-07-06", "09:00-11:00",
                "沙河校区文理楼A-101", "12", "期末考试", "沙河校区"
            ),
            Exam(
                "大学物理", "2026-07-07", "14:00-16:00",
                "沙河校区文理楼B-202", "8", "补考", "沙河校区"
            )
        )

        composeTestRule.setContent {
            ExamPage(state = state)
        }

        // 页面标题 + 返回按钮
        composeTestRule.onNodeWithText("考试安排").assertIsDisplayed()
        composeTestRule.onNodeWithText("← 返回课表").assertIsDisplayed()

        // 课程名
        composeTestRule.onNodeWithText("高等数学").assertIsDisplayed()
        composeTestRule.onNodeWithText("大学物理").assertIsDisplayed()

        // 时间
        composeTestRule.onNodeWithText("09:00-11:00").assertIsDisplayed()
        composeTestRule.onNodeWithText("14:00-16:00").assertIsDisplayed()

        // 地点
        composeTestRule.onNodeWithText("沙河校区文理楼A-101").assertIsDisplayed()
        composeTestRule.onNodeWithText("沙河校区文理楼B-202").assertIsDisplayed()

        // 座位号
        composeTestRule.onNodeWithText("12").assertIsDisplayed()
        composeTestRule.onNodeWithText("8").assertIsDisplayed()

        // 考试类型（两门不同，可独立断言）
        composeTestRule.onNodeWithText("期末考试").assertIsDisplayed()
        composeTestRule.onNodeWithText("补考").assertIsDisplayed()
    }

    @Test
    fun examPage_showsEmptyState() {
        state.exams = emptyList()

        composeTestRule.setContent {
            ExamPage(state = state)
        }

        composeTestRule.onNodeWithText("考试安排").assertIsDisplayed()
        composeTestRule.onNodeWithText("暂无考试安排").assertIsDisplayed()
    }

    @Test
    fun examPage_showsErrorState() {
        state.exams = emptyList()
        state.errorMessage = "无法获取考试安排"

        composeTestRule.setContent {
            ExamPage(state = state)
        }

        composeTestRule.onNodeWithText("无法获取考试安排").assertIsDisplayed()
    }

    @Test
    fun examPage_returnButton_closesPage() {
        state.showExamPage = true
        state.exams = listOf(
            Exam(
                "测试", "2026-12-31", "10:00-11:00",
                "测试教室", "1", "期末", "测试校区"
            )
        )

        composeTestRule.setContent {
            ExamPage(state = state)
        }

        composeTestRule.onNodeWithText("← 返回课表").performClick()
        org.junit.Assert.assertFalse(
            "showExamPage should be false after return",
            state.showExamPage
        )
    }

    // ── NavigationPage：导航页面 ──────────────────────────

    @Test
    fun navigationPage_showsLoginPrompt_whenNoCourses() {
        state.courses = emptyList()
        state.currentWeek = 1

        composeTestRule.setContent {
            NavigationPage(state = state)
        }

        composeTestRule.onNodeWithText("请先在「登录」中登录以加载课表").assertIsDisplayed()
    }

    @Test
    fun navigationPage_showsTitle_whenCoursesExist() {
        state.courses = listOf(
            Course(
                "高等数学", "MATH201", "4", "张老师", "教5-101", "小营校区",
                "1-16周", 1, 1, 2, "08:00", "09:35"
            )
        )
        state.currentWeek = 1

        composeTestRule.setContent {
            NavigationPage(state = state)
        }

        // 页面标题始终显示
        composeTestRule.onNodeWithText("教室导航").assertIsDisplayed()
    }

    @Test
    fun navigationPage_showsNavigateButton_whenCourseFound() {
        // 设置一门今天的课程（time = 00:00-23:59 确保匹配当前时间）
        val now = java.util.Calendar.getInstance()
        val calendarDayOfWeek = now.get(java.util.Calendar.DAY_OF_WEEK)
        val appDayOfWeek = (calendarDayOfWeek + 5) % 7 + 1

        state.courses = listOf(
            Course(
                "数据结构", "CS301", "3", "王老师", "教5-101", "小营校区",
                "1-16周", appDayOfWeek, 1, 2, "00:00", "23:59"
            )
        )
        state.currentWeek = 1

        composeTestRule.setContent {
            NavigationPage(state = state)
        }

        // 课程名和教室应显示
        composeTestRule.onNodeWithText("数据结构").assertIsDisplayed()
        composeTestRule.onNodeWithText("教5-101", substring = true).assertIsDisplayed()
        // "导航去这里" 按钮应可见
        composeTestRule.onNodeWithText("导航去这里").assertIsDisplayed()
    }

    @Test
    fun navigationPage_showsNoCourse_whenNotToday() {
        // 设置一个不在今天的课程（用不同的 dayOfWeek）
        val now = java.util.Calendar.getInstance()
        val calendarDayOfWeek = now.get(java.util.Calendar.DAY_OF_WEEK)
        val appDayOfWeek = (calendarDayOfWeek + 5) % 7 + 1
        // 找一个不同的日子
        val otherDay = if (appDayOfWeek == 7) 1 else appDayOfWeek + 1

        state.courses = listOf(
            Course(
                "线性代数", "MATH301", "3", "李老师", "理学院-201", "小营校区",
                "1-16周", otherDay, 3, 4, "10:00", "11:35"
            )
        )
        state.currentWeek = 1

        composeTestRule.setContent {
            NavigationPage(state = state)
        }

        // 页面标题依然显示
        composeTestRule.onNodeWithText("教室导航").assertIsDisplayed()
        // 应显示 "今日无课"（或含 "明天" 的提示）
        composeTestRule.onNodeWithText("今日无课", substring = true).assertIsDisplayed()
    }
}
