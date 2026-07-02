package edu.bistu.cs4029.ibistu.login

/**
 * 日志接口（库自身不绑定任何日志框架）。
 *
 * 调用方通过此接口注入日志实现（SLF4J / android.util.Log / java.util.logging 等），
 * 即可在不依赖特定平台的情况下控制库的日志输出。
 */
interface LoginLogger {

    /** Debug 级别日志 */
    fun debug(msg: String)

    /** Info 级别日志 */
    fun info(msg: String)

    /** Warning 级别日志 */
    fun warn(msg: String)

    /** Error 级别日志 */
    fun error(msg: String)

    companion object {
        /** 默认空实现：丢弃所有日志 */
        val NONE: LoginLogger = object : LoginLogger {
            override fun debug(msg: String) {}
            override fun info(msg: String) {}
            override fun warn(msg: String) {}
            override fun error(msg: String) {}
        }
    }
}
