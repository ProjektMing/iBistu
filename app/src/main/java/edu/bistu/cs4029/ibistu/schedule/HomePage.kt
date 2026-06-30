package edu.bistu.cs4029.ibistu.schedule

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import edu.bistu.cs4029.ibistu.common.state.AppState

/** 课表首页。 */
@Composable
fun HomePage(state: AppState, modifier: Modifier = Modifier) {
    if (state.courses.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("请先在 Profile 中登录", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(state.termName, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        LazyColumn {
            items(state.courses, key = { "${it.code}-${it.dayOfWeek}-${it.beginSection}" }) { course ->
                CourseCard(course)
            }
        }
    }
}

@Composable
private fun CourseCard(course: Course, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(course.name, style = MaterialTheme.typography.titleSmall)
            Text("${course.code}  ${course.credit}学分")
            Text(
                "周${dayLabel(course.dayOfWeek)} 第${course.beginSection}-${course.endSection}节  " +
                    "${course.beginTime}-${course.endTime}"
            )
            Text("教师: ${course.teacher}  ${course.classroom}  ${course.campus}")
        }
    }
}

private fun dayLabel(day: Int): String = when (day) {
    1 -> "一"
    2 -> "二"
    3 -> "三"
    4 -> "四"
    5 -> "五"
    6 -> "六"
    7 -> "日"
    else -> "?"
}
