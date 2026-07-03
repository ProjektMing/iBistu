package edu.bistu.cs4029.ibistu.food

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import org.json.JSONArray

private val defaultFoodOptions = listOf(
    "麻辣香锅", "兰州拉面", "黄焖鸡", "饺子",
    "烤肉拌饭", "盖浇饭", "汉堡炸鸡", "食堂盲盒"
)

@Composable
fun EatWhatPage(
    modifier: Modifier = Modifier,
    isThursday: Boolean = LocalDate.now().dayOfWeek == DayOfWeek.THURSDAY,
    showThursdayReminder: Boolean = true,
    initialOptions: List<String>? = null,
    persistChanges: Boolean = true,
    pickIndex: (Int) -> Int = { Random.nextInt(it) }
) {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences("eat_what_preferences", 0)
    }
    val options = remember {
        mutableStateListOf<String>().apply {
            addAll(initialOptions ?: loadFoodOptions(preferences.getString("food_options", null)))
        }
    }
    var newOption by rememberSaveable { mutableStateOf("") }
    var selectedFood by rememberSaveable { mutableStateOf("点一下，让命运替你决定") }
    var hasSpun by rememberSaveable { mutableStateOf(false) }
    var rotationTarget by remember { mutableFloatStateOf(0f) }
    val wheelRotation by animateFloatAsState(
        targetValue = rotationTarget,
        animationSpec = tween(durationMillis = 1600),
        label = "food-wheel"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("今天吃啥？", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "别纠结了，把午饭交给幸运轮盘",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (isThursday && showThursdayReminder) {
            Spacer(Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("🍗 今天是疯狂星期四", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("给自己一个合理吃炸鸡的理由——当然，理性消费也很酷。")
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        FoodWheel(
            options = options,
            rotation = wheelRotation,
            result = selectedFood,
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .aspectRatio(1f)
        )

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                val selectedIndex = pickIndex(options.size)
                selectedFood = options[selectedIndex]
                hasSpun = true
                val sweep = 360f / options.size
                val targetModulo = normalizeDegrees(-selectedIndex * sweep - sweep / 2f)
                val currentModulo = normalizeDegrees(rotationTarget)
                rotationTarget += 1440f + normalizeDegrees(targetModulo - currentModulo)
            },
            enabled = options.size >= 2,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (hasSpun) "不服，再转一次" else "开始转盘")
        }

        Spacer(Modifier.height(20.dp))
        Text("今日候选", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = newOption,
                onValueChange = { newOption = it.take(12) },
                label = { Text("添加想吃的") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    val option = newOption.trim()
                    options.add(option)
                    newOption = ""
                    if (persistChanges) saveFoodOptions(preferences, options)
                },
                enabled = newOption.trim().isNotEmpty() &&
                    newOption.trim() !in options && options.size < MAX_OPTIONS
            ) {
                Text("添加")
            }
        }
        Text(
            "可保留 2–$MAX_OPTIONS 个候选，修改后会自动保存",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        options.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowItems.forEach { food ->
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(food, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                            IconButton(
                                onClick = {
                                    if (options.size > MIN_OPTIONS) {
                                        options.remove(food)
                                        if (selectedFood == food) selectedFood = "候选已更新，再转一次吧"
                                        if (persistChanges) saveFoodOptions(preferences, options)
                                    }
                                },
                                enabled = options.size > MIN_OPTIONS
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "删除 $food")
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        Text(
            "小提示：轮盘只负责消灭选择困难，过敏和饮食禁忌还是要听自己的。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun FoodWheel(
    options: List<String>,
    rotation: Float,
    result: String,
    modifier: Modifier = Modifier
) {
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer,
        MaterialTheme.colorScheme.inversePrimary,
        MaterialTheme.colorScheme.surfaceVariant
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val sweep = 360f / options.size
            options.forEachIndexed { index, option ->
                val color = colors[index % colors.size]
                drawArc(
                    color = color,
                    startAngle = index * sweep - 90f + rotation,
                    sweepAngle = sweep,
                    useCenter = true
                )
            }
            drawCircle(
                color = colors.first(),
                radius = size.minDimension * 0.23f,
                center = center
            )
            val labelPaint = android.graphics.Paint().apply {
                color = Color.White.toArgb()
                textAlign = android.graphics.Paint.Align.CENTER
                textSize = size.minDimension * if (options.size > 8) 0.035f else 0.045f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                isAntiAlias = true
                setShadowLayer(4f, 0f, 1f, android.graphics.Color.BLACK)
            }
            drawIntoCanvas { canvas ->
                options.forEachIndexed { index, option ->
                    val angle = Math.toRadians(
                        (index * sweep + sweep / 2f - 90f + rotation).toDouble()
                    )
                    val radius = size.minDimension * 0.36f
                    canvas.nativeCanvas.drawText(
                        option.take(6),
                        center.x + cos(angle).toFloat() * radius,
                        center.y + sin(angle).toFloat() * radius + labelPaint.textSize / 3f,
                        labelPaint
                    )
                }
            }
        }
        Text(
            result,
            modifier = Modifier.padding(46.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
            lineHeight = 22.sp
        )
        Text(
            "▼",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 2.dp),
            color = MaterialTheme.colorScheme.error,
            fontSize = 28.sp
        )
    }
}

private fun loadFoodOptions(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return defaultFoodOptions
    return runCatching {
        val array = JSONArray(raw)
        List(array.length()) { index -> array.getString(index) }
            .filter { it.isNotBlank() }
            .take(MAX_OPTIONS)
            .takeIf { it.size >= MIN_OPTIONS }
            ?: defaultFoodOptions
    }.getOrDefault(defaultFoodOptions)
}

private fun saveFoodOptions(
    preferences: android.content.SharedPreferences,
    options: List<String>
) {
    preferences.edit().putString("food_options", JSONArray(options).toString()).apply()
}

private const val MIN_OPTIONS = 2
private const val MAX_OPTIONS = 12

private fun normalizeDegrees(value: Float): Float = ((value % 360f) + 360f) % 360f
