# Proguard rules for MyStream

# Keep Annotations & Signatures
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Kotlinx Serialization
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}
-keepclassmembers class * {
    kotlinx.serialization.KSerializer serializer();
}

# Libtorrent4j (JNI native SWIG interfaces - DO NOT OBFUSCATE OR STRIP)
-dontwarn org.libtorrent4j.**
-keep class org.libtorrent4j.** { *; }
-keepclassmembers class org.libtorrent4j.** { *; }
-keep class org.libtorrent4j.swig.** { *; }
-keepclassmembers class org.libtorrent4j.swig.** { *; }

# PostgreSQL JDBC Driver (reflection and database connections)
-dontwarn org.postgresql.**
-dontwarn java.lang.management.**
-dontwarn javax.naming.**
-dontwarn javax.security.**
-dontwarn javax.sql.**
-dontwarn javax.transaction.**
-dontwarn javax.xml.**
-dontwarn org.ietf.jgss.**
-dontwarn org.osgi.**
-dontwarn waffle.**
-keep class org.postgresql.** { *; }
-keepclassmembers class org.postgresql.** { *; }
-keep class java.sql.** { *; }
-keep class javax.sql.** { *; }

# NanoHTTPD (Local embedded streaming server)
-dontwarn fi.iki.elonen.**
-keep class fi.iki.elonen.** { *; }
-keepclassmembers class fi.iki.elonen.** { *; }

# OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# AndroidX Media3 / ExoPlayer
-dontwarn androidx.media3.**
-keep class androidx.media3.** { *; }
-keepclassmembers class androidx.media3.** { *; }

# Coil Image Loader
-dontwarn coil3.**
-keep class coil3.** { *; }
