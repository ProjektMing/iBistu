package edu.bistu.cs4029.ibistu

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import edu.bistu.cs4029.ibistu.login.BistuLogin
import edu.bistu.cs4029.ibistu.ui.theme.IBistuTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            val login = BistuLogin()
            try {
                // 1. SSO 登录
                Log.w("BistuLogin", "SSO 登录...")
                val result = login.fullLogin("2023011210", "18701218707aA")
                Log.w("BistuLogin", "SSO: code=${result.code} ${result.message}")
                if (!result.isSuccess) return@launch

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    // 2. 通过 casLogin.do 进入教务系统
                    val casUrl = "https://jwxt.bistu.edu.cn/jwapp/sys/yjsrzfwapp/bistuLogin/casLogin.do"
                    val resp1 = login.redirectClient.newCall(
                        okhttp3.Request.Builder().url(casUrl).get().build()
                    ).execute()
                    resp1.close()

                    // 3. 查当前学期
                    val termBody = login.post(
                        "https://jwxt.bistu.edu.cn/jwapp/sys/jwpubapp/modules/gg/cxmrxnxq.do",
                        mapOf("CSDM" to "SYS", "ZCSDM" to "DQXNXQDM", "SFSY" to "1")
                    )
                    Log.w("BistuLogin", "学期: ${termBody.take(500)}")
                    val xnxqdm = org.json.JSONObject(termBody).getJSONObject("datas")
                        .getJSONObject("cxmrxnxq").getJSONArray("rows")
                        .getJSONObject(0).getString("XNXQDM")
                    Log.w("BistuLogin", "当前学期: $xnxqdm")

                    // 4. 查课表
                    val schedule = login.post(
                        "https://jwxt.bistu.edu.cn/jwapp/sys/kbapp/api/wdkbcx/getMyScheduleDetail.do",
                        mapOf("XNXQDM" to xnxqdm, "XQDM" to "10")
                    )
                    Log.w("BistuLogin", "课表: ${schedule.take(2000)}")
                }
            } catch (e: Exception) {
                Log.e("BistuLogin", "失败", e)
            }
        }

        setContent {
            IBistuTheme {
                IBistuApp()
            }
        }
    }
}

@PreviewScreenSizes
@Composable
fun IBistuApp() {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = {
                        Icon(
                            painterResource(it.icon),
                            contentDescription = it.label
                        )
                    },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Greeting(
                name = "Android",
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: Int,
) {
    HOME("Home", R.drawable.ic_home),
    FAVORITES("Favorites", R.drawable.ic_favorite),
    PROFILE("Profile", R.drawable.ic_account_box),
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    IBistuTheme {
        Greeting("Android")
    }
}