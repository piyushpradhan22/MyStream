# Proguard / R8 optimization rules for MyStream

# Keep Annotations & Signatures
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Kotlinx Serialization
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}
-keepclassmembers class * {
    kotlinx.serialization.KSerializer serializer();
}

# Data models used with reflection / serialization
-keep class com.mystream.app.data.model.** { *; }
-keepclassmembers class com.mystream.app.data.model.** { *; }

# Chaquopy Python Bridge (called dynamically from Python)
-dontwarn com.chaquo.python.**
-keep class com.chaquo.python.** { *; }
-keepclassmembers class com.chaquo.python.** { *; }

# OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# AndroidX Media3 / ExoPlayer (AAR provides consumer rules; keep necessary extractors)
-dontwarn androidx.media3.**

# Coil Image Loader (AAR provides consumer rules)
-dontwarn coil3.**
