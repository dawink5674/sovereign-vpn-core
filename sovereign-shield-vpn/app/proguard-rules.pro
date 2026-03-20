# ProGuard / R8 rules for Sovereign Shield VPN
# =============================================================================

# ---- Keep generic type signatures (CRITICAL for Gson + Retrofit) ----
# R8 full mode (default since AGP 8.0) strips generic signatures.
# Gson and Retrofit rely on these at runtime for deserialization.
# Without this, you get: "java.lang.Class cannot be cast to
# java.lang.reflect.ParameterizedType"
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes *Annotation*

# ---- Retrofit 2 ----
# Keep generic signature of Call, Response (R8 full mode strips signatures)
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# Suspend functions are wrapped in continuations where the type argument is used
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# R8 full mode strips generic signatures from return types if not kept
-if interface * { @retrofit2.http.* public *** *(...); }
-keep,allowoptimization,allowshrinking,allowobfuscation class <3>

# Keep Retrofit service interfaces (prevent R8 from nulling proxy values)
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface * extends <1>

# Retrofit does reflection on method and parameter annotations
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# Keep annotation default values (e.g., retrofit2.http.Field.encoded)
-keepattributes AnnotationDefault

# Ignore JSR 305 annotations for embedding nullability information
-dontwarn javax.annotation.**

# Top-level functions that can only be used by Kotlin
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# Guarded by a NoClassDefFoundError try/catch and only used when on the classpath
-dontwarn kotlin.Unit

# ---- Gson ----
# Gson uses generic type information stored in a class file when working with
# fields. R8 removes such information by default, so configure it to keep all of it.
-dontwarn sun.misc.**

# Prevent proguard from stripping interface information from TypeAdapter,
# TypeAdapterFactory, JsonSerializer, JsonDeserializer instances
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep Gson's TypeToken generic signatures
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# Prevent R8 from leaving Data object members always null
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ---- App API models (keep EVERYTHING — class names, fields, constructors) ----
# These are deserialized by Gson via reflection. If R8 strips any field name
# or constructor, deserialization silently produces nulls or crashes.
-keep class com.sovereign.shield.network.** { *; }

# ---- WireGuard tunnel classes ----
-keep class com.wireguard.** { *; }

# ---- Hilt / Dagger ----
-keep class dagger.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ComponentSupplier { *; }

# ---- OkHttp ----
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ---- DataStore ----
-keep class androidx.datastore.** { *; }

# ---- EncryptedSharedPreferences / Tink ----
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi
-dontwarn com.google.errorprone.annotations.InlineMe

# ---- Kotlin Serialization (future-proofing) ----
-keepclassmembers class kotlinx.serialization.** { *; }
