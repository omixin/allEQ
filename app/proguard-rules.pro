# Shizuku Binder & Reflection preservation
-keep class rikka.shizuku.** { *; }
-keepclassmembers class rikka.shizuku.Shizuku { *; }

# Audio & Data Models preservation for JSON / State
-keep class com.omix.alleq.model.** { *; }
-keep class com.omix.alleq.service.** { *; }
-keep class com.omix.alleq.audio.** { *; }
-keep class com.omix.alleq.shizuku.** { *; }

# Keep line numbers for meaningful stacktraces in offline logs
-keepattributes SourceFile,LineNumberTable