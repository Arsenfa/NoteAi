# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# 'proguardFiles' setting in build.gradle.kts.

# Keep Kotlin metadata for reflection
-keepattributes *Annotation*, InnerClasses
-dontnote kotlin.reflect.jvm.internal.ReflectionFactoryImpl

# Keep data classes used with Moshi/Room/Firestore
-keep class com.example.data.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Firestore - keep all model classes
-keep class com.example.data.Note { *; }
-keep class com.example.data.ChatMessage { *; }

# Firebase
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**

# Moshi
-keep class com.squareup.moshi.** { *; }
-keep @com.squareup.moshi.JsonQualifier interface *
-keepclassmembers class * {
    @com.squareup.moshi.* <methods>;
}

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Kotlin Coroutines
-keep class kotlinx.coroutines.flow.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Retrofit
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }

# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
