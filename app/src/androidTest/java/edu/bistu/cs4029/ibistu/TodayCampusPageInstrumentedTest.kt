package edu.bistu.cs4029.ibistu

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import edu.bistu.cs4029.ibistu.schedule.Course
import edu.bistu.cs4029.ibistu.schedule.Exam
import edu.bistu.cs4029.ibistu.testing.ComposeTestActivity
import edu.bistu.cs4029.ibistu.today.TodayCampusContent
import edu.bistu.cs4029.ibistu.today.TodayCampusUiModel
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 今日校园首页的真实 Compose 渲染与交互测试。 */
class TodayCampusPageInstrumentedTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComposeTestActivity>()

    @Test
    fun todayCampusContent_showsSummaryAndOpensFood() {
        var foodOpened = false
        val highlightedCourse = course("大学物理", "10:00", "11:35", "WLA-106")
        val model = TodayCampusUiModel(
            greeting = "早上好",
            dateLabel = "7月27日 星期一",
            weekLabel = "第 3 周",
            todayCourses = listOf(
                course("高等数学", "08:00", "09:35", "教5-101"),
                highlightedCourse
            ),
            highlightedCourse = highlightedCourse,
            highlightedLabel = "下一节",
            highlightedStatus = "20 分钟后开始",
            highlightedProgress = 0f,
            nextExam = Exam(
                courseName = "线性代数",
                examDate = "2026-07-29",
                examTime = "09:00-11:00",
                location = "教5-101",
                seatNumber = "12",
                examType = "期末考试",
                campus = "小营校区"
            ),
            nextExamDays = 2
        )

        composeTestRule.setContent {
            TodayCampusContent(
                model = model,
                onOpenSchedule = {},
                onOpenExams = {},
                onOpenFood = { foodOpened = true }
            )
        }

        composeTestRule.onNodeWithText("早上好").assertIsDisplayed()
        composeTestRule.onNodeWithText("下一节").assertIsDisplayed()
        composeTestRule.onNodeWithText("20 分钟后开始").assertIsDisplayed()
        composeTestRule.onNodeWithTag("today-highlight-card").assertIsDisplayed()
        composeTestRule.onNodeWithText("线性代数").assertIsDisplayed()
        composeTestRule.onNodeWithText("完整课表").assertIsDisplayed()
        composeTestRule.onNodeWithText("今天吃啥").performClick()

        assertTrue(foodOpened)

        val highlightedCourseTag =
            "today-course-${highlightedCourse.code}-${highlightedCourse.beginTime}"
        composeTestRule.onNodeWithTag("today-campus-page")
            .performScrollToNode(hasTestTag(highlightedCourseTag))
        composeTestRule.onNodeWithTag(highlightedCourseTag).assertIsDisplayed()
    }

    private fun course(name: String, begin: String, end: String, classroom: String) = Course(
        name = name,
        code = name,
        credit = "2",
        teacher = "张老师",
        classroom = classroom,
        campus = "小营校区",
        week = "1-16周",
        dayOfWeek = 1,
        beginSection = 1,
        endSection = 2,
        beginTime = begin,
        endTime = end
    )
}
