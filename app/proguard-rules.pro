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

# Per-source buffering: a java.lang.reflect.Proxy forwards calls on this interface (SwitchingProxy).
-keep interface androidx.media3.exoplayer.LoadControl { *; }

# Native decoder fallbacks (FFmpeg audio, dav1d AV1). DefaultRenderersFactory loads these renderers reflectively
# (extension mode), so R8 must not strip or rename them. JNI keep rules live in decoders/proguard-rules.txt.
-keep class androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer { <init>(...); }
-keep class androidx.media3.decoder.av1.Libdav1dVideoRenderer { <init>(...); }
-keep class androidx.media3.decoder.ffmpeg.FfmpegLibrary { *; }
-keep class androidx.media3.decoder.av1.Dav1dLibrary { *; }
-keep class com.lnxv.player.decoders.Av1SoftwareGate { *; }
