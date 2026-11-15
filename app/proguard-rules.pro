# ProGuard / R8 rules for LNXV Player
# Media3, Room, DataStore, Coil and Coroutines ship their own consumer rules; only LNXV-specific needs are kept.

# LNXV MKV seek workaround sets this private field by reflection (see SeekFixMatroskaExtractor).
# Keep BOTH the class name and the field, otherwise R8 would rename/strip it and the fix silently stops working.
-keep class androidx.media3.extractor.mkv.MatroskaExtractor {
    private long cuesContentPosition;
}

# Room entities are referenced by generated code; Room's consumer rules cover DAOs/databases.
-keep class com.lnxv.player.core.database.*Entity { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
