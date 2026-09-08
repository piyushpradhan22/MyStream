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

# Strip verbose logging from release: removes Log.d/v/i calls AND their string
# concatenation from hot paths (progress ticker, pagination, per-focus handlers),
# cutting CPU/GC churn on weak Android TV. Warnings/errors are retained.
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
    public static int i(...);
}

# libtorrent4j — SWIG JNI bindings. The native .so calls back into these Java
# classes via SWIG director methods (SwigDirector_*) and JNI native bindings, so
# R8 must NOT rename or remove them or the engine crashes with NoSuchMethodError.
-keep class org.libtorrent4j.** { *; }
-keepclassmembers class org.libtorrent4j.** { *; }
-keepclasseswithmembernames class org.libtorrent4j.** {
    native <methods>;
}
-dontwarn org.libtorrent4j.**

# NanoHTTPD embedded server
-dontwarn org.nanohttpd.**
-keep class org.nanohttpd.** { *; }

# NewPipeExtractor (YouTube stream extraction) + its Rhino JS engine for signature deciphering
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**
# Rhino references optional JDK classes absent on Android; safe to ignore.
-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**

# Coil Image Loader (AAR provides consumer rules)
-dontwarn coil3.**

# Jetpack Compose Accessibility Optimization for Android TV (defuse Buttons Remapper geometry flood)
-keep class androidx.compose.ui.platform.AndroidComposeView {
    androidx.compose.ui.platform.AndroidComposeViewAccessibilityDelegateCompat composeAccessibilityDelegate;
}
-keep class androidx.compose.ui.platform.AndroidComposeViewAccessibilityDelegateCompat {
    java.util.List enabledServices;
    android.view.accessibility.AccessibilityManager$AccessibilityStateChangeListener enabledStateListener;
    android.view.accessibility.AccessibilityManager$TouchExplorationStateChangeListener touchExplorationStateListener;
    android.view.accessibility.AccessibilityManager accessibilityManager;
}
