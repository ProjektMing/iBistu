package edu.bistu.cs4029.ibistu.focus

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 计时模式。 */
enum class TimerMode {
    /** 倒计时：设定目标时长，从目标倒减至 0。 */
    COUNTDOWN,
    /** 正向计时：从 0 开始不计上限累加。 */
    STOPWATCH
}

/** 计时状态。 */
enum class TimerStatus {
    /** 初始状态或已重置。 */
    IDLE,
    /** 正在倒计时/正向计时。 */
    RUNNING,
    /** 已暂停。 */
    PAUSED,
    /** 计时完成（仅倒计时模式达到目标时长）。 */
    FINISHED
}

/**
 * 专注计时状态管理器。
 *
 * 核心逻辑：
 * - 倒计时（COUNTDOWN）：从 [targetSeconds] 向下递减至 0。
 * - 正向计时（STOPWATCH）：从 0 开始递增，无上限。
 * - 休息计时（isBreakTime=true）：倒计时，视觉上以不同颜色区分。
 *
 * 番茄周期：每完成一个专注会话，[cycleCount] 递增。
 * 每完成 [maxCyclesBeforeLongBreak] 个番茄后，休息为长休。
 */
class FocusTimerState(
    /** 初始目标时长（秒）。倒计时默认 25 分钟（1500 秒）。 */
    initialTargetSeconds: Int = 1500,
    /** 初始计时模式。 */
    initialMode: TimerMode = TimerMode.COUNTDOWN
) {
    /** 当前计时模式。 */
    var mode: TimerMode by mutableStateOf(initialMode)
        private set

    /** 当前计时状态。 */
    var status: TimerStatus by mutableStateOf(TimerStatus.IDLE)
        private set

    /** 目标时长（秒），仅倒计时模式使用。 */
    var targetSeconds: Int by mutableIntStateOf(initialTargetSeconds)
        private set

    /** 用户设定的专注时长（秒），切换专注/休息时保持不变。 */
    var regularTargetSeconds: Int by mutableIntStateOf(initialTargetSeconds)
        private set

    /** 已过秒数。倒计时模式下为已消耗的秒数（剩余 = targetSeconds - elapsedSeconds）。 */
    var elapsedSeconds: Int by mutableIntStateOf(0)
        private set

    /** 当前连续番茄完成数（每完成一个倒计时会话递增）。 */
    var cycleCount: Int by mutableIntStateOf(0)
        private set

    /** 是否处于休息计时状态。 */
    var isBreakTime: Boolean by mutableStateOf(false)
        private set

    /** 当前休息是否为长休。 */
    var isLongBreak: Boolean by mutableStateOf(false)
        private set

    /** 短休时长（秒），默认 5 分钟。 */
    var shortBreakSeconds: Int by mutableIntStateOf(300)

    /** 长休时长（秒），默认 15 分钟。 */
    var longBreakSeconds: Int by mutableIntStateOf(900)

    /** 每完成多少个番茄安排一次长休，默认 4。 */
    var maxCyclesBeforeLongBreak: Int by mutableIntStateOf(4)

    /** 计时开始的 wall-clock 时间戳（epoch millis），用于后台恢复。 */
    private var startTimeMillis: Long = 0L

    /** 暂停前累计的秒数。 */
    private var accumulatedBeforePause: Int = 0

    /** 已过毫秒数。RUNNING 时实时读取系统时钟，用于秒针平滑动画。 */
    val elapsedMillis: Long
        get() {
            if (status == TimerStatus.RUNNING) {
                return accumulatedBeforePause * 1000L + (System.currentTimeMillis() - startTimeMillis)
            }
            return accumulatedBeforePause * 1000L
        }

    // ── 暴露给 UI 的辅助属性 ──

    /** 剩余秒数（仅倒计时模式有意义）。 */
    val remainingSeconds: Int
        get() = (targetSeconds - elapsedSeconds).coerceAtLeast(0)

    /** 进度百分比（0.0 ~ 1.0），仅倒计时模式有意义；正向计时返回 0f。 */
    val progress: Float
        get() = when (mode) {
            TimerMode.COUNTDOWN -> if (targetSeconds > 0) {
                (elapsedSeconds.toFloat() / targetSeconds).coerceIn(0f, 1f)
            } else 0f
            TimerMode.STOPWATCH -> 0f
        }

    /** 格式化的 MM:SS 显示字符串。 */
    val displayTime: String
        get() {
            val totalSecs = when (mode) {
                TimerMode.COUNTDOWN -> remainingSeconds
                TimerMode.STOPWATCH -> elapsedSeconds
            }
            val min = totalSecs / 60
            val sec = totalSecs % 60
            return "%02d:%02d".format(min, sec)
        }

    // ── 操作方法 ──

    /** 切换计时模式。仅允许在 IDLE 状态下切换。 */
    fun switchMode(newMode: TimerMode) {
        if (status != TimerStatus.IDLE) return
        mode = newMode
        reset()
    }

    /** 设置目标时长（秒）。仅允许在 IDLE 状态下设置。倒计时更改目标。 */
    fun setTarget(seconds: Int) {
        if (status != TimerStatus.IDLE) return
        targetSeconds = seconds.coerceAtLeast(60) // 最少 1 分钟
        regularTargetSeconds = targetSeconds
        reset()
    }

    /** 开始专注计时（倒计时或正向计时）。 */
    fun start() {
        if (status != TimerStatus.IDLE) return
        status = TimerStatus.RUNNING
        startTimeMillis = System.currentTimeMillis()
        accumulatedBeforePause = 0
        elapsedSeconds = 0
    }

    /** 暂停计时。 */
    fun pause() {
        if (status != TimerStatus.RUNNING) return
        status = TimerStatus.PAUSED
        accumulatedBeforePause = elapsedSeconds
    }

    /** 恢复计时。 */
    fun resume() {
        if (status != TimerStatus.PAUSED) return
        status = TimerStatus.RUNNING
        startTimeMillis = System.currentTimeMillis()
    }

    /** 重置为 IDLE 状态。 */
    fun reset() {
        status = TimerStatus.IDLE
        elapsedSeconds = 0
        accumulatedBeforePause = 0
        startTimeMillis = 0L
        isBreakTime = false
        isLongBreak = false
    }

    /**
     * 开始休息计时。
     * 仅在 FINISHED 状态下调用（专注完成时）。
     */
    fun startBreak() {
        if (status != TimerStatus.FINISHED) return
        // 递增番茄计数
        cycleCount++
        // 判断是否需要长休
        val needLongBreak = cycleCount % maxCyclesBeforeLongBreak == 0
        isLongBreak = needLongBreak
        isBreakTime = true

        // 切换到休息倒计时
        mode = TimerMode.COUNTDOWN
        targetSeconds = if (needLongBreak) longBreakSeconds else shortBreakSeconds
        status = TimerStatus.IDLE
        elapsedSeconds = 0
        accumulatedBeforePause = 0
        startTimeMillis = 0L
    }

    /**
     * 休息完成，开始下一个专注会话。
     */
    fun startNextFocus() {
        if (!isBreakTime) return
        isBreakTime = false
        isLongBreak = false
        status = TimerStatus.IDLE
        mode = TimerMode.COUNTDOWN
        targetSeconds = regularTargetSeconds
        elapsedSeconds = 0
        accumulatedBeforePause = 0
        startTimeMillis = 0L
    }

    /**
     * 时钟 tick：每秒调用一次，更新 [elapsedSeconds]。
     * 倒计时模式下若达到目标时长则自动切换状态为 [TimerStatus.FINISHED]。
     *
     * @return true 表示计时仍在进行；false 表示已结束（FINISHED）。
     */
    fun tick(): Boolean {
        if (status != TimerStatus.RUNNING) return false

        val now = System.currentTimeMillis()
        val delta = ((now - startTimeMillis) / 1000).toInt()
        elapsedSeconds = accumulatedBeforePause + delta

        if (mode == TimerMode.COUNTDOWN && elapsedSeconds >= targetSeconds) {
            elapsedSeconds = targetSeconds
            status = TimerStatus.FINISHED
            return false
        }
        return true
    }
}
