# Referenced from app/build.gradle.kts. Preserve entry points resolved by reflection.

# Room resolves <Database>_Impl by name at runtime.
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class **_Impl { *; }
-keepclassmembers class com.lumen.core.database.model.** { *; }

# Entity/DAO signatures are read through generated code that R8 cannot follow back.
-keep class com.lumen.core.database.dao.** { *; }

# QR import screen instantiates the scanner through reflection.
-keep class com.journeyapps.barcodescanner.** { *; }
-keep class com.google.zxing.** { *; }

# SnakeYAML reads Clash subscriptions as Map/List/scalars. Its optional desktop
# JavaBeans introspection path references APIs absent on Android; no bean binding
# is used. Keep the suppression limited to R8's five missing desktop classes.
-dontwarn java.beans.BeanInfo
-dontwarn java.beans.FeatureDescriptor
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.Introspector
-dontwarn java.beans.PropertyDescriptor

# The OpenVPN PEM/PKCS#8 decryptors use an explicit Bouncy Castle provider.
# Provider registrations load these algorithm classes by constructed names;
# reachability from the decryptor alone cannot preserve them or their names.
-keep class org.bouncycastle.jcajce.provider.symmetric.** { *; }
-keep class org.bouncycastle.jcajce.provider.digest.** { *; }

# Tor (tor-android, jtorctl) and its pluggable transports (IPtProxy) are native-backed and
# reach their own fields and methods across JNI and reflection. Shrinking them breaks the
# Tor service at runtime with NoSuchFieldError, so keep them whole.
-keep class org.torproject.** { *; }
-keep class info.guardianproject.** { *; }
-keep class IPtProxy.** { *; }
-keep class go.** { *; }
-dontwarn org.torproject.**
-dontwarn IPtProxy.**
-dontwarn info.guardianproject.**

# OpenConnect (libopenconnect.so) is JNI-backed: native init() looks up fields such as
# asyncLock/libctx by name and the callbacks (onProgress, onProcessAuthForm, ...) by name.
# R8 renames or strips them in release builds, so GetFieldID returns null and the process
# aborts with "fid == null". Keep the binding and our session class whole.
-keep class org.infradead.libopenconnect.** { *; }
-keep class com.lumen.core.vpn.openconnect.** { *; }
-dontwarn org.infradead.libopenconnect.**
