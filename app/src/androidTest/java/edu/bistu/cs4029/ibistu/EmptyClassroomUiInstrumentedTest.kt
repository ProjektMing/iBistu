package edu.bistu.cs4029.ibistu

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import edu.bistu.cs4029.ibistu.common.state.AppState
import edu.bistu.cs4029.ibistu.schedule.Course
import edu.bistu.cs4029.ibistu.schedule.EmptyClassroom
import edu.bistu.cs4029.ibistu.schedule.HomePage
import edu.bistu.cs4029.ibistu.testing.ComposeTestActivity
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * 空教室查询 BottomSheet 的 Compose UI 仪器测试。
 */
class EmptyClassroomUiInstrumentedTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComposeTestActivity>()

    private lateinit var state: AppState

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        state = AppState(context)
    }

    private fun setupBasicState() {
        state.courses = listOf(
            Course("测试课", "TEST001", "1", "老师", "WLA-101", "沙河校区",
                "1周", 1, 3, 3, "09:50", "10:40")
        )
        state.termName = "2025-2026-2"
        state.currentWeek = 1
        state.weekRange = 1..1
    }

    // ── 结果展示 ──────────────────────────────────────────────

    @Test
    fun showsRoomList() {
        setupBasicState()
        state.showEmptyClassroomSheet = true
        state.queryContextText = "周一 第3节"
        state.emptyClassrooms = listOf(
            EmptyClassroom("WLA-106", "501", "文理楼A座 沙河校区",
                "10", "沙河校区", 1, 40, 20, "02", "多媒体",
                "050101", "id-001", "", true, true, true),
            EmptyClassroom("XXB-301", "503", "信息楼B座 沙河校区",
                "10", "沙河校区", 3, 60, 45, "01", "普通",
                "050301", "id-002", "智慧教室", true, false, true)
        )

        composeTestRule.setContent { HomePage(state = state) }

        // 标题 + 上下文
        composeTestRule.onNodeWithText("查询空教室").assertIsDisplayed()
        composeTestRule.onNodeWithText("周一 第3节").assertIsDisplayed()
        // 教室名
        composeTestRule.onNodeWithText("WLA-106").assertIsDisplayed()
        composeTestRule.onNodeWithText("XXB-301").assertIsDisplayed()
        // 附属信息
        composeTestRule.onNodeWithText("40座").assertIsDisplayed()
        composeTestRule.onNodeWithText("1层").assertIsDisplayed()
        composeTestRule.onNodeWithText("多媒体").assertIsDisplayed()
        composeTestRule.onNodeWithText("智慧教室").assertIsDisplayed()
        composeTestRule.onNodeWithText("共 2 间空闲教室").assertIsDisplayed()
    }

    @Test
    fun filtersRoomsByNameAndShowsNoMatchState() {
        setupBasicState()
        state.showEmptyClassroomSheet = true
        state.emptyClassrooms = listOf(
            EmptyClassroom("WLA-106", "501", "文理楼A座", "10", "沙河校区",
                1, 40, 20, "02", "多媒体", "050101", "id-001", "", true, true, true),
            EmptyClassroom("XXB-301", "503", "信息楼B座", "10", "沙河校区",
                3, 60, 45, "01", "普通", "050301", "id-002", "", true, false, true)
        )

        composeTestRule.setContent { HomePage(state = state) }

        composeTestRule.onNodeWithText("搜索教室名称…").performTextInput("WLA")
        composeTestRule.onNodeWithText("WLA-106").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("XXB-301").assertCountEquals(0)
        composeTestRule.onNodeWithText("共 1 间空闲教室（共 2 间）").assertIsDisplayed()

        composeTestRule.onNodeWithText("搜索教室名称…").performTextInput("-999")
        composeTestRule.onNodeWithText("无匹配结果").assertIsDisplayed()
        composeTestRule.onNodeWithText("共 0 间空闲教室（共 2 间）").assertIsDisplayed()
    }

    // ── 上课时段警告 ──────────────────────────────────────────

    @Test
    fun showsWarningWhenClassTime() {
        setupBasicState()
        state.showEmptyClassroomSheet = true
        state.isClassTimeQuery = true
        state.queryContextText = "周一 第3节 测试课 WLA-101"
        state.emptyClassrooms = listOf(
            EmptyClassroom("WLA-106", "501", "文理楼A座", "10", "沙河校区",
                1, 40, 20, "02", "多媒体", "050101", "id-001",
                "", true, true, true)
        )

        composeTestRule.setContent { HomePage(state = state) }

        composeTestRule.onNodeWithText("📚 要好好上课哦").assertIsDisplayed()
        composeTestRule.onNodeWithText("周一 第3节 测试课 WLA-101").assertIsDisplayed()
    }

    @Test
    fun noWarningWhenEmptyCell() {
        setupBasicState()

        state.showEmptyClassroomSheet = true
        state.isClassTimeQuery = false
        state.queryContextText = "周三 第5节"
        state.emptyClassrooms = listOf(
            EmptyClassroom("WLA-106", "501", "文理楼A座", "10", "沙河校区",
                1, 40, 20, "02", "多媒体", "050101", "id-001",
                "", true, true, true)
        )

        composeTestRule.setContent { HomePage(state = state) }

        composeTestRule.onNodeWithText("查询空教室").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("📚 要好好上课哦").assertCountEquals(0)
    }

    // ── 状态展示 ──────────────────────────────────────────────

    @Test
    fun showsLoading() {
        setupBasicState()

        state.showEmptyClassroomSheet = true
        state.isLoadingEmptyClassrooms = true

        composeTestRule.setContent { HomePage(state = state) }

        composeTestRule.onNodeWithText("查询空教室").assertIsDisplayed()
        composeTestRule.onNodeWithText("正在查询空闲教室…").assertIsDisplayed()
    }

    @Test
    fun showsError() {
        setupBasicState()

        state.showEmptyClassroomSheet = true
        state.emptyClassroomError = "未找到符合条件的空闲教室"

        composeTestRule.setContent { HomePage(state = state) }

        composeTestRule.onNodeWithText("查询空教室").assertIsDisplayed()
        composeTestRule.onNodeWithText("未找到符合条件的空闲教室").assertIsDisplayed()
    }
}
