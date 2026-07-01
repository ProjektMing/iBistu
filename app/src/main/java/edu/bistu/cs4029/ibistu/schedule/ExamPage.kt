package edu.bistu.cs4029.ibistu.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import edu.bistu.cs4029.ibistu.common.state.AppState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/** 考试安排页面。 */
@Composable
fun ExamPage(state: AppState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // 顶栏：返回按钮 + 标题
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { state.showExamPage = false }) {
                Text("← 返回课表", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "考试安排",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.weight(1f))
            // 右侧占位保持标题居中
            Spacer(Modifier.width(80.dp))
        }
        Spacer(Modifier.height(4.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))

        // 内容区
        when {
            state.exams.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (state.errorMessage.isNotBlank()) {
                            Text(
                                text = state.errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = {
                                state.errorMessage = ""
                            }) {
                                Text("返回课表")
                            }
                        } else {
                            Text("暂无考试安排", style = MaterialTheme.typography.bodyLarge)
                        }
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { state.showExamPage = false }) {
                            Text("返回课表")
                        }
                    }
                }
            }

            else -> {
                ExamList(exams = state.exams.sortedBy { it.examDate }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ExamList(exams: List<Exam>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        exams.forEach { exam ->
            ExamCard(exam)
        }
        // 底部留白
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ExamCard(exam: Exam) {
    val daysLeft = daysUntil(exam.examDate)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = cardColorForDays(daysLeft))
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            // 课程名 + 倒计时
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = exam.courseName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                DaysBadge(daysLeft)
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(thickness = 0.5.dp)
            Spacer(Modifier.height(8.dp))

            // 详细信息
            InfoRow("📅 日期", formatExamDate(exam.examDate))
            if (exam.examTime.isNotBlank()) {
                InfoRow("⏰ 时间", exam.examTime)
            }
            if (exam.location.isNotBlank()) {
                InfoRow("📍 地点", exam.location)
            }
            if (exam.seatNumber.isNotBlank()) {
                InfoRow("💺 座位号", exam.seatNumber)
            }
            if (exam.examType.isNotBlank()) {
                InfoRow("📝 类型", exam.examType)
            }
            if (exam.campus.isNotBlank()) {
                InfoRow("🏫 校区", exam.campus)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(72.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun DaysBadge(days: Long?) {
    val (text, containerColor, textColor) = when {
        days == null -> Triple("—", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        days < 0 -> Triple("已考完", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.outline)
        days == 0L -> Triple("今天考试", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.error)
        days <= 3 -> Triple("还有 ${days} 天", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.error)
        days <= 7 -> Triple("还有 ${days} 天", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.tertiary)
        else -> Triple("还有 ${days} 天", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

/** 根据距今天数返回卡片背景色。已考完的保持正常显示，不过分淡化。 */
@Composable
private fun cardColorForDays(days: Long?) = when {
    days == null -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    days < 0 -> MaterialTheme.colorScheme.surface
    days == 0L -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
    days <= 3 -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
    else -> MaterialTheme.colorScheme.surface
}

/**
 * 计算距考试日期的天数。
 * 支持格式："2025-01-06"、"2025-01-06 00:00:00"、"2025/01/06"
 */
private fun daysUntil(dateStr: String): Long? {
    if (dateStr.isBlank()) return null
    val clean = dateStr.trim().substringBefore(' ')
    val patterns = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd"),
        DateTimeFormatter.ofPattern("yyyyMMdd")
    )
    for (fmt in patterns) {
        try {
            val examDate = LocalDate.parse(clean, fmt)
            return ChronoUnit.DAYS.between(LocalDate.now(), examDate)
        } catch (_: DateTimeParseException) { /* try next */ }
    }
    return null
}

/** 格式化考试日期为更友好的中文显示。 */
private fun formatExamDate(dateStr: String): String {
    val clean = dateStr.trim().substringBefore(' ')
    val patterns = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd"),
        DateTimeFormatter.ofPattern("yyyyMMdd")
    )
    for (fmt in patterns) {
        try {
            val date = LocalDate.parse(clean, fmt)
            val weekdays = listOf("一", "二", "三", "四", "五", "六", "日")
            val weekday = weekdays[date.dayOfWeek.value - 1]
            return "${date.monthValue}月${date.dayOfMonth}日 周$weekday"
        } catch (_: DateTimeParseException) { /* try next */ }
    }
    return dateStr
}
