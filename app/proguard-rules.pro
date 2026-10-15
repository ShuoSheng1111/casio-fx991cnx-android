# Compose + Kotlin 的默认规则由 AGP 提供,这里只补必要的
-dontwarn org.jetbrains.annotations.**

# 保留自测类的日志方法名,便于 logcat 过滤(可选)
-keep class com.example.casio.core.EngineSelfTest { *; }
