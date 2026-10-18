-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses,EnclosingMethod
# Moshi + Kotlin reflection (KotlinJsonAdapterFactory) needs Kotlin metadata and the models
-keep class kotlin.Metadata { *; }
-keep class com.example.data.** { *; }
-keep class com.example.** implements java.io.Serializable { *; }
-dontwarn org.jetbrains.annotations.**
# Strip debug/verbose logging from release builds (nothing sensitive reaches logcat)
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
