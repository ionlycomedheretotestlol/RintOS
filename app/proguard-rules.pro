-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class dev.rint.launcher.**$$serializer { *; }
-keepclassmembers class dev.rint.launcher.** { *** Companion; }
-keepclasseswithmembers class dev.rint.launcher.** { kotlinx.serialization.KSerializer serializer(...); }

# Anthropic Java SDK (Claude provider): Jackson (de)serializes SDK models reflectively.
-keep class com.anthropic.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keepattributes Signature, RuntimeVisibleAnnotations, AnnotationDefault, EnclosingMethod
-dontwarn com.fasterxml.jackson.**
-dontwarn org.slf4j.**
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn java.beans.**
-dontwarn kotlin.reflect.jvm.internal.**
# JSON-schema generator pulled in by the SDK uses JVM-only reflection types absent on Android.
-dontwarn com.github.victools.jsonschema.**
-dontwarn java.lang.reflect.AnnotatedType
-dontwarn java.lang.reflect.AnnotatedParameterizedType
# Jackson's Kotlin module reads Kotlin metadata reflectively (Claude provider in release builds).
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
-dontwarn kotlin.reflect.**

# the intro 3D stage talks to the app through this bridge
-keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }
