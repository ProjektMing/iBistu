package edu.bistu.cs4029.ibistu

import androidx.compose.runtime.Composable
import edu.bistu.cs4029.ibistu.common.base.BaseActivity
import edu.bistu.cs4029.ibistu.common.navigation.IBistuRoot

/** 应用唯一入口 Activity，仅负责承载 Compose 根节点。 */
class MainActivity : BaseActivity() {
    @Composable
    override fun Content() {
        IBistuRoot()
    }
}
