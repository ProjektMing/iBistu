package edu.bistu.cs4029.ibistu.schedule

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.bistu.cs4029.ibistu.common.state.AppState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.platform.LocalContext

/** 按周展示的七列课程表首页。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomePage(state: AppState, modifier: Modifier = Modifier) {
    // 判断是否已登录：有 Cookie 或 loginResult 成功
    val isLoggedIn = state.login.getAllCookies().isNotEmpty()
        || (state.loginResult?.isSuccess == true)

    // 未登录且无缓存课表数据时才提示登录；允许离线查看已缓存课表
    if (!isLoggedIn && state.courses.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("请先在 Profile 中登录", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    // 已登录但课表为空（如课表尚未发布）—— 仍渲染完整 UI，保留学期切换功能
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    val data = ScheduleData(
                        termName = state.termName,
                        courses = state.courses,
                        termWeeks = state.termWeeks,
                        termCode = state.termCode
                    )
                    ScheduleToIcal.shareIcs(context, data)
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = "导出课表"
                )
            }
            SemesterSelector(
                termOptions = state.termOptions,
                currentTermCode = state.termCode,
                currentTermName = state.termName,
                selectedTermName = state.selectedTermName,
                isLoading = state.isLoadingTerm,
                onSwitchToTerm = { code, name -> state.switchToTerm(code, name) },
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = {
                state.showExamPage = true
            }) {
                Text("考试安排", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(4.dp))
        WeekNavigator(
            currentWeek = state.currentWeek,
            weekRange = state.weekRange,
            onPrevious = {
                if (state.currentWeek > state.weekRange.first) state.currentWeek--
            },
            onNext = {
                if (state.currentWeek < state.weekRange.last) state.currentWeek++
            }
        )
        state.termWeeks[state.currentWeek]?.let { week ->
            // FIXME: 切周/切学期后教学周日期可能因缓存/时区偏移不正确
            if (week.startDate.isNotBlank() || week.endDate.isNotBlank()) {
                Text(
                    text = formatDateRange(week.startDate, week.endDate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        WeeklyTimeTable(
            courses = state.courses,
            currentWeek = state.currentWeek,
            onLongPressCell = { dayOfWeek, section ->
                state.queryEmptyClassroomsAt(dayOfWeek, section)
            }
        )
    }

    // 空教室结果 BottomSheet
    if (state.showEmptyClassroomSheet) {
        EmptyClassroomSheet(state)
    }
}

/** 学期名称下拉选择框。列表为空时仅显示纯文本，有列表时显示可交互下拉框。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SemesterSelector(
    termOptions: List<TermOption>,
    currentTermCode: String,
    currentTermName: String,
    selectedTermName: String,
    isLoading: Boolean,
    onSwitchToTerm: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val displayText = selectedTermName.ifBlank {
        termOptions.firstOrNull { it.termCode == currentTermCode }?.termName ?: currentTermName
    }

    if (termOptions.isEmpty()) {
        // 学期列表尚未加载：显示纯文本，与改造前行为一致
        Text(
            text = if (isLoading) "加载中…" else displayText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = modifier
        )
        return
    }

    // 学期列表已加载：显示可交互下拉框
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (!isLoading) expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = if (isLoading) "加载中…" else displayText,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            ),
            trailingIcon = {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp).padding(end = 4.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = !isLoading)
                .fillMaxWidth(),
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            termOptions.forEach { option ->
                val isSelected = option.termCode == currentTermCode
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.termName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        if (!isSelected) {
                            onSwitchToTerm(option.termCode, option.termName)
                        }
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun formatDateRange(start: String, end: String): String {
    fun format(value: String): String {
        val parts = value.substringBefore(' ').split('-')
        if (parts.size < 3) return value
        val month = parts[1].trimStart('0').ifBlank { "0" }
        val day = parts[2].trimStart('0').ifBlank { "0" }
        return "${month}月${day}日"
    }

    return listOf(start, end)
        .filter(String::isNotBlank)
        .joinToString(" — ", transform = ::format)
}

@Composable
private fun WeekNavigator(
    currentWeek: Int,
    weekRange: IntRange,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious, enabled = currentWeek > weekRange.first) {
            Text("<", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            text = "第 $currentWeek 周",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        IconButton(onClick = onNext, enabled = currentWeek < weekRange.last) {
            Text(">", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WeeklyTimeTable(
    courses: List<Course>,
    currentWeek: Int,
    onLongPressCell: (dayOfWeek: Int, section: Int) -> Unit
) {
    val rowHeight = 52.dp
    val sectionLabelWidth = 38.dp
    val weekCourses = remember(courses, currentWeek) {
        courses.filter { ScheduleUtils.isCourseInWeek(it.week, currentWeek) }
    }

    Column {
        WeekdayHeader(sectionLabelWidth)
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val dayWidth = (maxWidth - sectionLabelWidth) / DAYS_PER_WEEK

            // 课程卡片（下层，穿透触摸）
            weekCourses.forEach { course ->
                val topOffset = (rowHeight + DIVIDER_SIZE) * (course.beginSection - 1).toFloat()
                val sectionSpan = course.endSection - course.beginSection + 1
                val cardHeight = (rowHeight + DIVIDER_SIZE) * sectionSpan.toFloat() - 2.dp
                val leftOffset = sectionLabelWidth + dayWidth * (course.dayOfWeek - 1).toFloat()

                Box(
                    modifier = Modifier
                        .offset(x = leftOffset + CELL_PADDING, y = topOffset + CELL_PADDING)
                        .width(dayWidth - CELL_PADDING * 2)
                        .height(cardHeight)
                ) {
                    TimeTableCell(course)
                }
            }

            // 网格单元格（上层，响应长按）
            TimetableGrid(
                rowHeight = rowHeight,
                sectionLabelWidth = sectionLabelWidth,
                onLongPressCell = onLongPressCell
            )

            if (weekCourses.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (courses.isEmpty()) "课表尚未发布" else "本周无课",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekdayHeader(sectionLabelWidth: androidx.compose.ui.unit.Dp) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(vertical = 6.dp)
    ) {
        Spacer(Modifier.width(sectionLabelWidth))
        for (day in 1..DAYS_PER_WEEK) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = ScheduleUtils.dayLabels[day] ?: "?",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimetableGrid(
    rowHeight: androidx.compose.ui.unit.Dp,
    sectionLabelWidth: androidx.compose.ui.unit.Dp,
    onLongPressCell: (dayOfWeek: Int, section: Int) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        for (section in 1..SECTION_COUNT) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
            ) {
                SectionLabel(section = section, width = sectionLabelWidth)
                repeat(DAYS_PER_WEEK) { dayIndex ->
                    val dayOfWeek = dayIndex + 1
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .combinedClickable(
                                onClick = {},
                                onLongClick = { onLongPressCell(dayOfWeek, section) }
                            )
                    ) {
                        if (dayIndex < DAYS_PER_WEEK - 1) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .width(DIVIDER_SIZE)
                                    .fillMaxHeight()
                                    .background(GRID_COLOR)
                            )
                        }
                    }
                }
            }
            if (section < SECTION_COUNT) {
                HorizontalDivider(thickness = DIVIDER_SIZE, color = GRID_COLOR)
            }
        }
    }
}

@Composable
private fun SectionLabel(section: Int, width: androidx.compose.ui.unit.Dp) {
    val startTime = sectionStartTimes.getOrNull(section - 1)
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = section.toString(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            startTime?.let {
                Text(
                    text = it,
                    fontSize = 7.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun TimeTableCell(course: Course, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = courseColor(course.name.hashCode())),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 2.dp, vertical = 3.dp)
        ) {
            Text(
                text = course.name,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 11.sp
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = "${course.beginTime.take(5)}-${course.endTime.take(5)}",
                fontSize = 8.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (course.classroom.isNotBlank()) {
                Text(
                    text = course.classroom,
                    fontSize = 8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun courseColor(hash: Int): Color = courseColors[(hash and Int.MAX_VALUE) % courseColors.size]

// ── 空教室查询 BottomSheet ────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmptyClassroomSheet(state: AppState) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = {
            state.showEmptyClassroomSheet = false
        },
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "查询空教室",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            if (state.queryContextText.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = state.queryContextText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))

            if (state.isClassTimeQuery) {
                Text(
                    text = "📚 要好好上课哦",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(8.dp))
            }

            when {
                state.isLoadingEmptyClassrooms -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(8.dp))
                            Text("正在查询空闲教室…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                state.emptyClassroomError.isNotBlank() && state.emptyClassrooms.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = state.emptyClassroomError,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                else -> {
                    var searchText by remember { mutableStateOf("") }
                    val filtered = remember(state.emptyClassrooms, searchText) {
                        if (searchText.isBlank()) state.emptyClassrooms
                        else state.emptyClassrooms.filter {
                            it.name.contains(searchText, ignoreCase = true)
                        }
                    }

                    OutlinedTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        placeholder = { Text("搜索教室名称…") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("emptyClassroomSearch")
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "共 ${filtered.size} 间空闲教室" +
                            if (searchText.isNotBlank()) "（共 ${state.emptyClassrooms.size} 间）" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    if (filtered.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(60.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("无匹配结果", style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.height(400.dp)) {
                            items(filtered, key = { it.id }) { room ->
                                EmptyClassroomRow(room)
                                HorizontalDivider(
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyClassroomRow(room: EmptyClassroom) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = room.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${room.classSeats}座",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(2.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = room.buildingName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${room.floor}层",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = room.typeName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (room.note.isNotBlank()) {
            Text(
                text = room.note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

private const val DAYS_PER_WEEK = 7
private const val SECTION_COUNT = 12
private val DIVIDER_SIZE = 0.5.dp
private val CELL_PADDING = 1.5.dp
private val GRID_COLOR = Color(0xFFE0E0E0)

private val sectionStartTimes = listOf(
    "08:00", "08:50", "09:50", "10:40", "11:30", "13:30", "14:20",
    "15:20", "16:10", "18:30", "19:20", "20:10"
)

private val sectionEndTimes = listOf(
    "08:50", "09:50", "10:40", "11:30", "12:20", "14:20", "15:20",
    "16:10", "17:00", "19:20", "20:10", "21:00"
)

private val courseColors = listOf(
    Color(0xFFE3F2FD),
    Color(0xFFE8F5E9),
    Color(0xFFFFF3E0),
    Color(0xFFF3E5F5),
    Color(0xFFE0F7FA),
    Color(0xFFFFEBEE),
    Color(0xFFF1F8E9),
    Color(0xFFFFF8E1),
    Color(0xFFE8EAF6),
    Color(0xFFFCE4EC)
)
