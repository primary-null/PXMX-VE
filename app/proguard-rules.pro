# Retrofit / Kotlinx Serialization
-keepattributes Signature
-keepattributes *Annotation*
-keepclassmembers,allowobfuscation class * {
  @kotlinx.serialization.SerialName <fields>;
}
-keep class com.pxmx.app.data.model.** { *; }
-keepclassmembers class com.pxmx.app.data.model.** {
  *** Companion;
}
