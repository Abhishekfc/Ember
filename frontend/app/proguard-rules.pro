# kotlinx.serialization needs its generated serializer classes and companion objects kept, or
# R8 can strip metadata the runtime reflection-free serialization still depends on.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclasseswithmembers class kotlinx.serialization.json.** { *; }
-keep,includedescriptorclasses class com.emigo.app.**$$serializer { *; }
-keepclassmembers class com.emigo.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.emigo.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit/OkHttp keep rules for the generated API interface and its Kotlin coroutine bridging.
-keepattributes Signature, Exceptions
-keep interface com.emigo.app.data.remote.EmberApi { *; }

# androidx.security:security-crypto pulls in Google Tink, which references error_prone_annotations
# classes that only ever matter at compile time (e.g. @CanIgnoreReturnValue) — safe to ignore at
# runtime, and R8 otherwise refuses to proceed since it can't verify the annotation classes exist.
-dontwarn com.google.errorprone.annotations.**
