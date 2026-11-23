# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.nanobuo.myfifty.**$$serializer { *; }
-keepclassmembers class com.nanobuo.myfifty.** {
    *** Companion;
}
# Ktor / OkHttp / Supabase
-dontwarn org.slf4j.**
-dontwarn io.ktor.**
-dontwarn java.lang.management.**
-keep class io.ktor.** { *; }
-keep class io.github.jan.supabase.** { *; }
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
