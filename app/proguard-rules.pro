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

# Tink (androidx.security.crypto) references Error Prone annotations at
# compile time only. They are not shipped on the device.
-dontwarn com.google.errorprone.annotations.**

# sshj EdDSA engine references JDK-internal X509Key, which Android does not
# provide. The Android KeyFactory path is used at runtime instead.
-dontwarn sun.security.x509.**
-dontwarn net.i2p.crypto.eddsa.**
