package edu.bistu.cs4029.ibistu.focus

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import edu.bistu.cs4029.ibistu.common.state.AppState
import edu.bistu.cs4029.ibistu.focus.model.FocusSession
import edu.bistu.cs4029.ibistu.focus.model.FocusTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext

/**
 * 专注页面入口。
 * 包含待办列表和统计视图的切换。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusPage(state: AppState, modifier: Modifier = Modifier) {
    var showStats by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (showStats) "数据统计" else "专注") },
                actions = {
                    IconButton(onClick = { showStats = !showStats }) {
                        Text(
                            text = if (showStats) "计时" else "统计",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { padding ->
        if (showStats) {
            FocusStatsPage(
                state = state,
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            )
        } else {
            FocusTimerPage(
                state = state,
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            )
        }
    }
}

/**
 * 专注计时主页 — 两层结构：
 * - selectedTask == null：展示待办任务列表
 * - selectedTask != null：展示对应任务的计时器
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusTimerPage(
    state: AppState,
    modifier: Modifier = Modifier
) {
    var tasks by remember { mutableStateOf<List<FocusTask>>(emptyList()) }
    var selectedTask by remember { mutableStateOf<FocusTask?>(null) }
    var showNewTaskDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    var taskToDelete by remember { mutableStateOf<FocusTask?>(null) }
    // 返回时保持的计时状态
var showEndConfirmDialog by rememberSaveable { mutableStateOf(false) }
    var endDurationToSave by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current

    // 加载待办列表
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            tasks = state.focusDao.getAllTasks()
        }
    }

    // 新建/删除后刷新
    fun refreshTasks() {
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            tasks = state.focusDao.getAllTasks()
        }
    }

    // 新建任务对话框
    if (showNewTaskDialog) {
        NewTaskDialog(
            onDismiss = { showNewTaskDialog = false },
            onConfirm = { task ->
                kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                    state.focusDao.insertTask(task)
                    refreshTasks()
                }
                showNewTaskDialog = false
            }
        )
    }

    // 删除确认对话框
    if (showDeleteConfirm && taskToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除待办") },
            text = { Text("确定删除「${taskToDelete!!.name}」？关联的专注记录不会被删除。") },
            confirmButton = {
                TextButton(onClick = {
                    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                        state.focusDao.deleteTask(taskToDelete!!.id)
                        refreshTasks()
                    }
                    showDeleteConfirm = false
                    taskToDelete = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 结束确认对话框
    if (showEndConfirmDialog && selectedTask != null) {
        val min = endDurationToSave / 60
        val sec = endDurationToSave % 60
        val durText = if (min > 0) "${min}分${sec}秒" else "${sec}秒"
        AlertDialog(
            onDismissRequest = { showEndConfirmDialog = false },
            title = { Text("结束专注") },
            text = { Text("已专注 ${durText}，确定结束吗？本次记录将保存。") },
            confirmButton = {
                TextButton(onClick = {
                    val now = System.currentTimeMillis()
                    val session = FocusSession(
                        startTime = now - endDurationToSave * 1000L,
                        endTime = now,
                        durationSeconds = endDurationToSave,
                        targetDurationSeconds = state.focusTimerState?.targetSeconds ?: selectedTask!!.targetSeconds,
                        mode = selectedTask!!.mode,
                        taskId = selectedTask!!.id
                    )
                    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                        state.focusDao.insert(session)
                        FocusNotificationHelper.cancel(context)
                    }
                    state.focusTimerState?.reset()
                    state.focusTimerState = null
                    state.activeFocusTask = null
                    selectedTask = null
                    showEndConfirmDialog = false
                }) { Text("保存并结束") }
            },
            dismissButton = {
                TextButton(onClick = { showEndConfirmDialog = false }) {
                    Text("继续计时")
                }
            }
        )
    }

    // 初始化通知渠道
    LaunchedEffect(Unit) {
        FocusNotificationHelper.createChannel(context)
    }

    if (selectedTask != null) {
        // 层 B：计时视图 — 复用或创建计时状态
        state.activeFocusTask = selectedTask
        val timerState = state.focusTimerState ?: remember {
            FocusTimerState(
                initialMode = when (selectedTask!!.mode) { "stopwatch" -> TimerMode.STOPWATCH; else -> TimerMode.COUNTDOWN },
                initialTargetSeconds = selectedTask!!.targetSeconds
            )
        }
        TaskTimerView(
            task = selectedTask!!,
            state = state,
            timerState = timerState,
            onBack = {
                state.focusTimerState = timerState
                selectedTask = null
            },
            onModeChanged = { newMode ->
                kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                    state.focusDao.updateTaskMode(selectedTask!!.id, newMode)
                }
            },
            onEnd = { duration ->
                endDurationToSave = duration
                showEndConfirmDialog = true
            },
            modifier = modifier
        )
    } else {
        // 层 A：待办列表
        Scaffold(
            floatingActionButton = {
                FloatingActionButton(onClick = { showNewTaskDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "新建待办")
                }
            },
            modifier = modifier
        ) { innerPadding ->
            if (tasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "暂无待办",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "点击右下角 + 新建待办任务",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { Spacer(Modifier.height(4.dp)) }
                    items(tasks, key = { it.id }) { task ->
                        TaskCard(
                            task = task,
                            state = state,
                            onClick = { selectedTask = task },
                            onDelete = {
                                taskToDelete = task
                                showDeleteConfirm = true
                            }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

/** 新建任务对话框。 */
@Composable
private fun NewTaskDialog(
    onDismiss: () -> Unit,
    onConfirm: (FocusTask) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf("countdown") }
    var targetSeconds by rememberSaveable { mutableIntStateOf(1500) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建待办") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("任务名称") },
                    placeholder = { Text("如「数学」「复习高数」") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Text("计时模式", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = mode == "countdown",
                        onClick = { mode = "countdown" },
                        shape = SegmentedButtonDefaults.itemShape(0, 2)
                    ) { Text("倒计时") }
                    SegmentedButton(
                        selected = mode == "stopwatch",
                        onClick = { mode = "stopwatch" },
                        shape = SegmentedButtonDefaults.itemShape(1, 2)
                    ) { Text("正向计时") }
                }
                if (mode == "countdown") {
                    Spacer(Modifier.height(12.dp))
                    Text("目标时长", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(15 to "15min", 25 to "25min", 45 to "45min", 60 to "60min").forEach { (min, label) ->
                            FilterChip(
                                selected = targetSeconds == min * 60,
                                onClick = { targetSeconds = min * 60 },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(FocusTask(name = name.trim(), mode = mode, targetSeconds = targetSeconds))
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("创建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** 待办任务卡片。 */
@Composable
private fun TaskCard(
    task: FocusTask,
    state: AppState,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var todayDuration by remember { mutableIntStateOf(0) }

    // 查询今日专注时长
    LaunchedEffect(task.id) {
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val startOfDay = now - (now % 86400000L)
            todayDuration = state.focusDao.getTotalDurationByTask(task.id, startOfDay, now).toInt()
        }
    }

    val modeLabel = when (task.mode) {
        "countdown" -> "倒计时 ${task.targetSeconds / 60}min"
        "stopwatch" -> "正向计时"
        else -> task.mode
    }
    val todayMin = todayDuration / 60

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = modeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (todayMin > 0) {
                    Text(
                        text = "今日已专注 ${todayMin} 分钟",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            TextButton(onClick = onDelete) {
                Text("删除", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** 任务计时视图。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskTimerView(
    task: FocusTask,
    state: AppState,
    timerState: FocusTimerState,
    onBack: () -> Unit,
    onModeChanged: (String) -> Unit,
    onEnd: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showCompleteDialog by rememberSaveable { mutableStateOf(false) }
    var showBreakCompleteDialog by rememberSaveable { mutableStateOf(false) }
    var lastSessionDuration by rememberSaveable { mutableIntStateOf(0) }
    var lastSessionMode by rememberSaveable { mutableStateOf(task.mode) }
    var showCustomTimeDialog by rememberSaveable { mutableStateOf(false) }
    var customMinutesText by rememberSaveable { mutableStateOf("") }
    var showInterruptionDialog by rememberSaveable { mutableStateOf(false) }
    var interruptionType by rememberSaveable { mutableStateOf("") }

    // 模式切换时更新数据库
    fun handleModeChange(newMode: TimerMode) {
        val modeStr = when (newMode) { TimerMode.COUNTDOWN -> "countdown"; TimerMode.STOPWATCH -> "stopwatch" }
        timerState.switchMode(newMode)
        onModeChanged(modeStr)
    }

    // 时钟 tick
    LaunchedEffect(timerState.status) {
        if (timerState.status == TimerStatus.RUNNING) {
            while (true) {
                delay(1000L)
                val running = timerState.tick()
                if (!running) {
                    lastSessionDuration = when (timerState.mode) {
                        TimerMode.COUNTDOWN -> timerState.targetSeconds
                        TimerMode.STOPWATCH -> timerState.elapsedSeconds
                    }
                    lastSessionMode = when (timerState.mode) {
                        TimerMode.COUNTDOWN -> "countdown"
                        TimerMode.STOPWATCH -> "stopwatch"
                    }
                    if (timerState.isBreakTime) showBreakCompleteDialog = true
                    else showCompleteDialog = true
                    break
                }
            }
        }
    }

    // 无级秒针 —— 每帧刷新毫秒数
    var smoothMillis by remember { mutableStateOf(0L) }
    LaunchedEffect(timerState.status == TimerStatus.RUNNING) {
        if (timerState.status == TimerStatus.RUNNING) {
            while (true) {
                withFrameNanos { }
                smoothMillis = timerState.elapsedMillis
            }
        }
    }

    // 保存会话
    fun saveCurrentSession() {
        val now = System.currentTimeMillis()
        val session = FocusSession(
            startTime = now - lastSessionDuration * 1000L,
            endTime = now,
            durationSeconds = lastSessionDuration,
            targetDurationSeconds = timerState.targetSeconds,
            mode = lastSessionMode,
            label = "",
            interruptionType = interruptionType,
            taskId = task.id
        )
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            state.focusDao.insert(session)
        }
    }

    // 专注完成对话框
    if (showCompleteDialog) {
        val durMin = lastSessionDuration / 60
        val durSec = lastSessionDuration % 60
        val durText = if (durMin > 0) "${durMin}分${durSec}秒" else "${durSec}秒"
        AlertDialog(
            onDismissRequest = { showCompleteDialog = false },
            title = { Text("专注完成!") },
            text = { Text("「${task.name}」已专注 $durText") },
            confirmButton = {
                if (timerState.mode == TimerMode.COUNTDOWN) {
                    TextButton(onClick = {
                        saveCurrentSession()
                        showCompleteDialog = false
                        timerState.startBreak()
                        FocusNotificationHelper.notify(context, task.name, timerState.displayTime)
                        interruptionType = ""
                    }) { Text("保存并休息", color = Color(0xFF4CAF50)) }
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        saveCurrentSession()
                        showCompleteDialog = false
                        timerState.reset()
                        FocusNotificationHelper.cancel(context)
                        interruptionType = ""
                    }) { Text("仅保存") }
                    TextButton(onClick = {
                        showCompleteDialog = false
                        timerState.reset()
                        FocusNotificationHelper.cancel(context)
                    }) { Text("放弃") }
                }
            }
        )
    }

    // 休息完成对话框
    if (showBreakCompleteDialog) {
        AlertDialog(
            onDismissRequest = { showBreakCompleteDialog = false },
            title = { Text("休息结束!") },
            text = { Text(if (timerState.isLongBreak) "长休已结束" else "短休已结束") },
            confirmButton = {
                TextButton(onClick = { showBreakCompleteDialog = false; timerState.startNextFocus() }) { Text("开始专注") }
            },
            dismissButton = {
                TextButton(onClick = { showBreakCompleteDialog = false; timerState.reset() }) { Text("结束") }
            }
        )
    }

    // 中断对话框
    if (showInterruptionDialog) {
        AlertDialog(
            onDismissRequest = { showInterruptionDialog = false },
            title = { Text("记录中断原因") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("微信消息", "电话", "走神", "他人打扰", "生理需求", "其他").forEach { reason ->
                        TextButton(
                            onClick = { interruptionType = reason; showInterruptionDialog = false },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(reason, modifier = Modifier.fillMaxWidth()) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showInterruptionDialog = false }) { Text("取消") } }
        )
    }

    // 自定义时间对话框
    if (showCustomTimeDialog) {
        AlertDialog(
            onDismissRequest = { showCustomTimeDialog = false },
            title = { Text("自定义时长") },
            text = {
                OutlinedTextField(
                    value = customMinutesText,
                    onValueChange = { customMinutesText = it.filter { c -> c.isDigit() } },
                    label = { Text("分钟") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    customMinutesText.toIntOrNull()?.let { timerState.setTarget(it.coerceIn(1, 480) * 60) }
                    showCustomTimeDialog = false; customMinutesText = ""
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showCustomTimeDialog = false }) { Text("取消") } }
        )
    }

    // 通知取消（暂停、完成、结束时）
    if (timerState.status == TimerStatus.PAUSED || timerState.status == TimerStatus.FINISHED) {
        FocusNotificationHelper.cancel(context)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))

        // 顶部标题行
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = Color.Unspecified
                )
            }
            Column {
                Text(
                    text = task.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 番茄周期计数
        if (timerState.cycleCount > 0 && !timerState.isBreakTime) {
            Text(
                text = "番茄 · ${timerState.cycleCount}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }

        // 休息指示
        if (timerState.isBreakTime) {
            Text(
                text = if (timerState.isLongBreak) "长休时间" else "休息时间",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF4CAF50),
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(16.dp))

        // 模式切换（仅 IDLE 时，休息时不显示）
        if (timerState.status == TimerStatus.IDLE && !timerState.isBreakTime) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                TimerMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = timerState.mode == mode,
                        onClick = { handleModeChange(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, TimerMode.entries.size)
                    ) {
                        Text(when (mode) { TimerMode.COUNTDOWN -> "倒计时"; TimerMode.STOPWATCH -> "正向计时" })
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        // 机械双针表盘 + 电子时钟
        val displayMillis = if (timerState.status == TimerStatus.RUNNING) smoothMillis
            else timerState.elapsedMillis
        val onSurface = MaterialTheme.colorScheme.onSurface
        AnalogWatchFace(
            displayMillis = displayMillis,
            mode = timerState.mode,
            status = timerState.status,
            targetSeconds = timerState.targetSeconds,
            isBreakTime = timerState.isBreakTime,
            onSurfaceColor = onSurface,
            modifier = Modifier
        )

        Spacer(Modifier.height(24.dp))

        // 预设（仅倒计时 IDLE）
        if (timerState.mode == TimerMode.COUNTDOWN && timerState.status == TimerStatus.IDLE && !timerState.isBreakTime) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(15, 25, 30, 45, 60, 90).forEach { min ->
                    FilterChip(
                        selected = timerState.targetSeconds == min * 60,
                        onClick = { timerState.setTarget(min * 60) },
                        label = { Text("${min}分钟", style = MaterialTheme.typography.labelSmall) }
                    )
                }
                FilterChip(
                    selected = listOf(15, 25, 30, 45, 60, 90).none { it * 60 == timerState.targetSeconds },
                    onClick = { customMinutesText = ""; showCustomTimeDialog = true },
                    label = { Text("自定义", style = MaterialTheme.typography.labelSmall) }
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        // 控制按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (timerState.status) {
                TimerStatus.IDLE -> {
                    Button(
                        onClick = { timerState.start() },
                        modifier = Modifier.width(160.dp).height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = if (timerState.isBreakTime) ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)) else ButtonDefaults.buttonColors()
                    ) {
                        Text(if (timerState.isBreakTime) "开始休息" else "开始", fontWeight = FontWeight.Bold)
                    }
                }
                TimerStatus.RUNNING -> {
                    Button(
                        onClick = { timerState.pause() },
                        modifier = Modifier.width(140.dp).height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) { Text("暂停", fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = { showInterruptionDialog = true },
                        modifier = Modifier.height(56.dp),
                        shape = RoundedCornerShape(28.dp)
                    ) { Text("中断", style = MaterialTheme.typography.labelSmall) }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = { onEnd(timerState.elapsedSeconds) },
                        modifier = Modifier.height(56.dp),
                        shape = RoundedCornerShape(28.dp)
                    ) { Text("结束") }
                }
                TimerStatus.PAUSED -> {
                    Button(
                        onClick = { timerState.resume() },
                        modifier = Modifier.width(160.dp).height(56.dp),
                        shape = RoundedCornerShape(28.dp)
                    ) { Text("继续", fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(12.dp))
                    OutlinedButton(
                        onClick = { onEnd(timerState.elapsedSeconds) },
                        modifier = Modifier.height(56.dp),
                        shape = RoundedCornerShape(28.dp)
                    ) { Text("结束") }
                }
                TimerStatus.FINISHED -> {
                    Button(
                        onClick = { timerState.reset(); FocusNotificationHelper.cancel(context) },
                        modifier = Modifier.width(160.dp).height(56.dp),
                        shape = RoundedCornerShape(28.dp)
                    ) { Text("完成", fontWeight = FontWeight.Bold) }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

/**
 * 机械双针表盘 + 电子时钟。
 * Canvas 绘制双针表盘（分钟针+秒针），下方 Compose Text 显示数字时钟。
 */
@Composable
private fun AnalogWatchFace(
    displayMillis: Long,
    mode: TimerMode,
    status: TimerStatus,
    targetSeconds: Int,
    isBreakTime: Boolean = false,
    onSurfaceColor: Color = Color.Unspecified,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    val actualOnSurface = if (onSurfaceColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else onSurfaceColor
    val ringColor = if (isBreakTime) Color(0xFF4CAF50) else primaryColor

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        // 表盘 Canvas
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(260.dp)) {
                val c = Offset(size.width / 2, size.height / 2)
                val outerR = minOf(size.width, size.height) / 2 - 4.dp.toPx()

                // 外圈边框
                drawCircle(color = onSurfaceColor.copy(alpha = 0.3f), radius = outerR, center = c,
                    style = Stroke(width = 2.dp.toPx()))

                // 60 个刻度 + 数字
                val tickLen = 8.dp.toPx()
                val longTickLen = 16.dp.toPx()
                val textR = outerR - longTickLen - 10.dp.toPx()
                for (i in 0 until 60) {
                    val angle = i * 6f - 90f
                    val isLong = i % 5 == 0
                    val len = if (isLong) longTickLen else tickLen
                    val innerR = outerR - len
                    val rad = Math.toRadians(angle.toDouble())
                    val x1 = c.x + innerR * kotlin.math.cos(rad).toFloat()
                    val y1 = c.y + innerR * kotlin.math.sin(rad).toFloat()
                    val x2 = c.x + outerR * kotlin.math.cos(rad).toFloat()
                    val y2 = c.y + outerR * kotlin.math.sin(rad).toFloat()
                    drawLine(
                        color = actualOnSurface.copy(alpha = if (isLong) 0.8f else 0.4f),
                        start = Offset(x1, y1), end = Offset(x2, y2),
                        strokeWidth = if (isLong) 2.dp.toPx() else 1.dp.toPx()
                    )
                    if (isLong) {
                        val num = if (i == 0) 12 else i / 5
                        val numR = textR
                        val nx = c.x + numR * kotlin.math.cos(rad).toFloat()
                        val ny = c.y + numR * kotlin.math.sin(rad).toFloat()
                        drawContext.canvas.nativeCanvas.drawText(
                            "$num", nx, ny + 6.dp.toPx() / 3,
                            android.graphics.Paint().apply {
                                color = android.graphics.Color.argb(
                                    (actualOnSurface.alpha * 255).toInt(),
                                    (actualOnSurface.red * 255).toInt(),
                                    (actualOnSurface.green * 255).toInt(),
                                    (actualOnSurface.blue * 255).toInt()
                                )
                                textSize = 10.dp.toPx()
                                textAlign = android.graphics.Paint.Align.CENTER
                            }
                        )
                    }
                }

                // 分钟针角度
                val minuteAngle = ((displayMillis / 60000f) % 60) / 60f * 360f
                val minuteHandLen = outerR * 0.45f
                rotate(minuteAngle, c) {
                    drawRoundRect(
                        color = ringColor,
                        topLeft = Offset(c.x - 3.dp.toPx(), c.y - minuteHandLen),
                        size = Size(6.dp.toPx(), minuteHandLen),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx())
                    )
                }

                // 秒针角度
                val secondAngle = (displayMillis / 1000f % 60) / 60f * 360f
                val secondHandLen = outerR * 0.75f
                rotate(secondAngle, c) {
                    drawLine(
                        color = Color(0xFFE53935),
                        start = Offset(c.x, c.y),
                        end = Offset(c.x, c.y - secondHandLen),
                        strokeWidth = 2.dp.toPx()
                    )
                }

                // 中心圆点
                drawCircle(color = Color(0xFF607D8B), radius = 5.dp.toPx(), center = c)
            }
        }

        Spacer(Modifier.height(8.dp))

        // 下方电子时钟
        val totalSecs = displayMillis / 1000
        Text(
            text = "%02d:%02d".format(totalSecs / 60, totalSecs % 60),
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = actualOnSurface
        )
        val subText = when {
            isBreakTime -> if (status == TimerStatus.IDLE) "点击开始休息" else ""
            mode == TimerMode.COUNTDOWN && status == TimerStatus.IDLE && targetSeconds > 0 -> {
                val min = targetSeconds / 60
                val sec = targetSeconds % 60
                if (sec > 0) "目标 ${min}分${sec}秒" else "目标 ${min}分钟"
            }
            mode == TimerMode.STOPWATCH -> "正向计时"
            else -> ""
        }
        if (subText.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(text = subText, fontSize = 14.sp, color = actualOnSurface.copy(alpha = 0.6f))
        }
    }
}
