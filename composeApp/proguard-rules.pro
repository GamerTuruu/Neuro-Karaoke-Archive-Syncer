# Add project specific ProGuard rules here.

# Ktor
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# Koin
-keep class org.koin.** { *; }
-dontwarn org.koin.**

# SQLDelight
-keep class app.cash.sqldelight.** { *; }
-dontwarn app.cash.sqldelight.**

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.neurok.syncer.**$$serializer { *; }
-keepclassmembers class com.neurok.syncer.** {
    *** Companion;
}
-keepclasseswithmembers class com.neurok.syncer.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# JAudioTagger
-keep class org.jaudiotagger.** { *; }
-dontwarn org.jaudiotagger.**

# Keep app models
-keep class com.neurok.syncer.domain.model.** { *; }
