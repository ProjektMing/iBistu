# Release 包不保留高频调试日志，警告和错误日志仍保留用于故障定位。
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# KonaCryptoProvider 通过类名字符串注册 JCA 服务，R8 无法自动追踪这些反射入口。
-keep class com.tencent.kona.sun.security.ec.ECKeyFactory { *; }
-keep class com.tencent.kona.crypto.provider.SM2Cipher { *; }
