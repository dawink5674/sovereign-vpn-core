# ProGuard / R8 rules for Dragon Scale VPN

# Keep WireGuard tunnel classes
-keep class com.wireguard.** { *; }

# Keep Retrofit service interfaces
-keep,allowobfuscation interface com.sovereign.dragonscale.network.VpnApiService
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Keep Gson data models
-keepclassmembers class com.sovereign.dragonscale.network.** {
    <fields>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
