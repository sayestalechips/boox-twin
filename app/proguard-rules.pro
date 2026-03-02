# PalmaMirror ProGuard Rules

# Keep BLE-related classes (reflection-heavy)
-keep class android.bluetooth.** { *; }
-keep class com.stalechips.palmamirror.ble.** { *; }
-keep class com.stalechips.palmamirror.ancs.** { *; }

# Keep Room entities and DAOs
-keep class com.stalechips.palmamirror.data.** { *; }
-keepclassmembers class com.stalechips.palmamirror.data.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Keep service classes (started via manifest)
-keep class com.stalechips.palmamirror.service.** { *; }
