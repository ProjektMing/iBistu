package example

import edu.bistu.cs4029.ibistu.login.BistuLogin
import edu.bistu.cs4029.ibistu.login.LoginResult
import kotlinx.coroutines.runBlocking

/**
 * bistulogin 纯 JVM 使用示例。
 *
 * 构建并运行：  ./gradlew :examples:run
 *
 * （如需可分发产物可使用 :examples:installDist / :examples:distZip）
 * 该示例使用内存存储和控制台日志——不会持久化 Cookie，每次运行都需要重新登录。
 */
fun main() = runBlocking {

    // 1. 创建 BistuLogin 实例
    val login = BistuLogin(
        cookieStorage = InMemoryCookieStorage(),
        logger = ConsoleLogger()
    )

    // 2. 尝试恢复上次的 Cookie
    login.restoreCookies()
    if (login.hasSavedCookies()) {
        println("已从存储恢复 ${login.getAllCookies().size} 个 Cookie")
    } else {
        println("无已保存的 Cookie，需要登录")
    }

    // 3. 执行登录
    val username = System.getenv("BISTU_USERNAME") ?: "YOUR_STUDENT_ID"
    val password = System.getenv("BISTU_PASSWORD") ?: "YOUR_PASSWORD"

    if (username == "YOUR_STUDENT_ID") {
        println("请设置环境变量 BISTU_USERNAME / BISTU_PASSWORD 后运行")
        println("  export BISTU_USERNAME=你的学号")
        println("  export BISTU_PASSWORD=你的密码")
        return@runBlocking
    }

    println("正在登录: $username ...")
    val result: LoginResult = login.fullLogin(username, password)

    // 4. 处理结果
    if (result.isSuccess) {
        println("登录成功！serviceUrl = ${result.serviceUrl}")

        // 登录成功后 OkHttp 自动管理 Cookie，可直接发认证请求：
        // val coursesJson = login.get("https://jwxt.bistu.edu.cn/...")

        login.persistCookies()
        println("已保存 ${login.getAllCookies().size} 个 Cookie")
    } else {
        println("登录失败: [${result.code}] ${result.message}")
    }

    // 5. 登出：login.clearAllCookies()
}
