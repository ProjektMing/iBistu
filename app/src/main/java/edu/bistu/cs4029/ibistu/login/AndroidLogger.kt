package edu.bistu.cs4029.ibistu.login

import android.util.Log

/**
 * [LoginLogger] 的 Android 实现，将日志输出到 logcat。
 */
class AndroidLogger(private val tag: String) : LoginLogger {

    override fun debug(msg: String) { Log.d(tag, msg) }
    override fun info(msg: String) { Log.i(tag, msg) }
    override fun warn(msg: String) { Log.w(tag, msg) }
    override fun error(msg: String) { Log.e(tag, msg) }
}
