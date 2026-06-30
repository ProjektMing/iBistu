package edu.bistu.cs4029.ibistu.common.base

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import edu.bistu.cs4029.ibistu.common.ui.theme.IBistuTheme

/**
 * 项目 Activity 基类。
 *
 * 统一处理 Edge-to-Edge、Compose Content 设置。
 * 所有 Activity 应继承此类而非直接继承 ComponentActivity。
 *
 * 使用方式：
 * ```
 * class MyActivity : BaseActivity() {
 *     @Composable
 *     override fun Content() {
 *         // Composable UI
 *     }
 * }
 * ```
 */
abstract class BaseActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IBistuTheme {
                Content()
            }
        }
    }

    /**
     * 子类实现此方法提供 Composable 内容。
     * 不需要再调用 setContent 或 enableEdgeToEdge。
     */
    @Composable
    protected abstract fun Content()
}
