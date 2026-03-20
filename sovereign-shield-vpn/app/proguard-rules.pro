# ProGuard / R8 rules for Sovereign Shield VPN

# Keep WireGuard tunnel classes
-keep class com.wireguard.** { *; }

# Keep Retrofit service interfaces
-keep,allowobfuscation interface com.sovereign.shield.network.VpnApiService
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Keep Gson data models
-keepclassmembers class com.sovereign.shield.network.** {
    <fields>;
}

# Hilt
-keep class dagger.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ComponentSupplier { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# DataStore
-keep class androidx.datastore.** { *; }

# Suppress R8 warnings for missing error-prone annotations (used by Tink/security-crypto)
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi
