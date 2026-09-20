# Keep OkHttp / Okio internals happy (they use reflection in a controlled way).
-dontwarn okhttp3.**
-dontwarn okio.**

# WorkManager keeps Worker subclasses via reflection; we already list ours via manifest, but be safe.
-keep class * extends androidx.work.CoroutineWorker
-keep class * extends androidx.work.ListenableWorker

# AppWidgetProvider subclasses are instantiated by the system via reflection.
-keep class * extends android.appwidget.AppWidgetProvider

# Application subclass is referenced from AndroidManifest.
-keep class com.acornjuice.downloadwidget.DownloadWidgetApp

# Kotlin metadata for reflective operations (Coroutines & serialization use it).
-keepclassmembers class kotlin.Metadata { *; }
