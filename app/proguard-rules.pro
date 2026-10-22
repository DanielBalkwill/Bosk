# Bosk ProGuard / R8 rules.

# Keep kotlinx.serialization generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.bosk.**$$serializer { *; }
-keepclassmembers class app.bosk.** {
    *** Companion;
}

# Room — entities/DAOs are referenced reflectively by generated code.
-keep class androidx.room.** { *; }

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep data model classes used by Gemini JSON (added in CP2).
-keep class app.bosk.core.ai.model.** { *; }

# TODO CP6: tighten rules + verify release shrink keeps Compose previews out.
