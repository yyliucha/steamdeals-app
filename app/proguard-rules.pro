# Keep kotlinx.serialization generated serializers (R8)
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature, Exceptions
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class cn.yyliucha.steamdeals.**$$serializer { *; }
-keepclassmembers class cn.yyliucha.steamdeals.** { *** Companion; }
-keepclasseswithmembers class cn.yyliucha.steamdeals.** { kotlinx.serialization.KSerializer serializer(...); }

# OkHttp / okio
-dontwarn okhttp3.internal.platform.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
