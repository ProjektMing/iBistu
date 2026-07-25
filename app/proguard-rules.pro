# Release 包不保留高频调试日志，警告和错误日志仍保留用于故障定位。
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
