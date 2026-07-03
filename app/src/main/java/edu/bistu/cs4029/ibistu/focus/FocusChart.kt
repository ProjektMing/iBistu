package edu.bistu.cs4029.ibistu.focus

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 将 Compose Color 转为 Android framework ARGB Int，用于 nativeCanvas.drawText。 */
private fun Color.toArgbInt(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(),
    (red * 255).toInt(),
    (green * 255).toInt(),
    (blue * 255).toInt()
)

/** 缓存 Paint 对象，避免每帧分配。 */
@Composable
private fun rememberPaint(): android.graphics.Paint = remember { android.graphics.Paint() }

/**
 * 专注时长柱状图。
 * X 轴为日期标签，Y 轴为专注分钟数。
 *
 * @param data 列表，每项为 (标签, 分钟数)
 * @param barColor 柱子颜色
 * @param modifier Compose Modifier
 */
@Composable
fun DurationBarChart(
    data: List<Pair<String, Int>>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary
) {
    if (data.isEmpty()) {
        Box(modifier = modifier.padding(16.dp)) {
            Text(
                text = "暂无数据",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
        return
    }

    val maxValue = data.maxOf { it.second }.coerceAtLeast(1)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val textSizePx = with(LocalDensity.current) { 10.sp.toPx() }
    val paint = rememberPaint()

    Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val bottomMargin = textSizePx * 3
        val topMargin = textSizePx * 2
        val leftMargin = textSizePx * 4
        val rightMargin = textSizePx

        val chartLeft = leftMargin
        val chartTop = topMargin
        val chartRight = canvasWidth - rightMargin
        val chartBottom = canvasHeight - bottomMargin
        val chartWidth = chartRight - chartLeft
        val chartHeight = chartBottom - chartTop

        // 画 Y 轴刻度线
        val ySteps = 4
        for (i in 0..ySteps) {
            val y = chartBottom - (chartHeight * i / ySteps)
            val value = maxValue * i / ySteps
            drawLine(
                color = gridColor,
                start = Offset(chartLeft, y),
                end = Offset(chartRight, y),
                strokeWidth = 1f
            )
            paint.apply {
                this.color = labelColor.toArgbInt()
                this.textSize = textSizePx
                textAlign = android.graphics.Paint.Align.RIGHT
            }
            drawContext.canvas.nativeCanvas.drawText(
                "$value",
                chartLeft - textSizePx,
                y + textSizePx / 3,
                paint
            )
        }

        // 画柱子
        val barCount = data.size
        val totalBarAreaWidth = chartWidth
        val barSpacing = totalBarAreaWidth / barCount * 0.3f
        val barWidth = (totalBarAreaWidth / barCount) * 0.7f

        paint.apply {
            this.color = labelColor.toArgbInt()
            this.textSize = textSizePx
            textAlign = android.graphics.Paint.Align.CENTER
        }
        data.forEachIndexed { index, (label, value) ->
            val barHeight = if (maxValue > 0) (chartHeight * value / maxValue) else 0f
            val barX = chartLeft + (totalBarAreaWidth / barCount) * index + barSpacing / 2
            val barY = chartBottom - barHeight

            // 柱体
            drawRoundRect(
                color = barColor,
                topLeft = Offset(barX, barY),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(4f, 4f)
            )

            // X 轴标签
            drawContext.canvas.nativeCanvas.drawText(
                label,
                barX + barWidth / 2,
                canvasHeight - textSizePx / 2,
                paint
            )
        }
    }
}

/**
 * 时段分布图。
 * X 轴为 0-23 小时，Y 轴为专注次数。
 */
@Composable
fun HourlyDistributionChart(
    data: List<Pair<Int, Int>>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.tertiary
) {
    if (data.isEmpty()) {
        Box(modifier = modifier.padding(16.dp)) {
            Text(
                text = "暂无时段数据",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
        return
    }

    val maxValue = data.maxOf { it.second }.coerceAtLeast(1)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val textSizePx = with(LocalDensity.current) { 9.sp.toPx() }
    val paint = rememberPaint()

    // 构建完整 24 小时映射
    val fullData = (0..23).map { hour ->
        data.find { it.first == hour }?.let { hour to it.second } ?: (hour to 0)
    }

    Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val bottomMargin = textSizePx * 3
        val topMargin = textSizePx * 2
        val leftMargin = textSizePx * 4
        val rightMargin = textSizePx

        val chartLeft = leftMargin
        val chartTop = topMargin
        val chartRight = canvasWidth - rightMargin
        val chartBottom = canvasHeight - bottomMargin
        val chartWidth = chartRight - chartLeft
        val chartHeight = chartBottom - chartTop

        // Y 轴刻度
        val ySteps = 4
        for (i in 0..ySteps) {
            val y = chartBottom - (chartHeight * i / ySteps)
            val value = maxValue * i / ySteps
            drawLine(
                color = gridColor,
                start = Offset(chartLeft, y),
                end = Offset(chartRight, y),
                strokeWidth = 1f
            )
            paint.apply {
                this.color = labelColor.toArgbInt()
                this.textSize = textSizePx
                textAlign = android.graphics.Paint.Align.RIGHT
            }
            drawContext.canvas.nativeCanvas.drawText(
                "$value",
                chartLeft - textSizePx,
                y + textSizePx / 3,
                paint
            )
        }

        // 柱子
        val barCount = fullData.size
        val barSpacing = (chartWidth / barCount) * 0.2f
        val barWidth = (chartWidth / barCount) * 0.8f

        paint.apply {
            this.color = labelColor.toArgbInt()
            this.textSize = textSizePx
            textAlign = android.graphics.Paint.Align.CENTER
        }
        fullData.forEachIndexed { index, (_, value) ->
            val barHeight = if (maxValue > 0) (chartHeight * value / maxValue) else 0f
            val barX = chartLeft + (chartWidth / barCount) * index + barSpacing / 2
            val barY = chartBottom - barHeight

            drawRoundRect(
                color = if (value > 0) barColor else Color.Transparent,
                topLeft = Offset(barX, barY),
                size = Size(barWidth, barHeight.coerceAtLeast(if (value > 0) 2f else 0f)),
                cornerRadius = CornerRadius(2f, 2f)
            )

            // 偶数列显示标签（避免拥挤）
            if (index % 3 == 0 || index == 23) {
                drawContext.canvas.nativeCanvas.drawText(
                    "${index}时",
                    barX + barWidth / 2,
                    canvasHeight - textSizePx / 2,
                    paint
                )
            }
        }
    }
}

/**
 * 摘要卡片：用于显示总专注次数、总时长、日均时长等指标。
 */
@Composable
fun FocusSummaryCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
