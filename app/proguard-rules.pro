# ProGuard rules for LNXV Player

# Media3 ExoPlayer rules
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Room Database rules
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# DataStore
-keepclassmembers class * extends androidx.datastore.preferences.core.Preferences$Key { *; }

# Keep LNXV entity models
-keep class com.lnxv.player.core.model.** { *; }
-keep class com.lnxv.player.core.database.** { *; }

# LNXV MKV seek workaround sets this private field by reflection
-keepclassmembers class androidx.media3.extractor.mkv.MatroskaExtractor {
    private long cuesContentPosition;
}
