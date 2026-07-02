package example

import edu.bistu.cs4029.ibistu.login.LoginLogger

/**
 * [LoginLogger] 的控制台实现（仅供示例）。
 *
 * 实际项目中可替换为 SLF4J / java.util.logging / android.util.Log 等。
 */
class ConsoleLogger(private val tag: String = "bistulogin") : LoginLogger {

    override fun debug(msg: String) = println("[$tag] DEBUG: $msg")
    override fun info(msg: String)  = println("[$tag] INFO : $msg")
    override fun warn(msg: String)  = println("[$tag] WARN : $msg")
    override fun error(msg: String) = println("[$tag] ERROR: $msg")
}
