# ---------------------------------------------------------------------------
# Gson-serialised models
#
# Everything below is persisted to disk (DataStore) or parsed from the remote
# config, and Gson maps JSON keys onto *field names* by reflection. R8 is free
# to rename those fields, and the names it picks are not stable between builds:
# MeterCardState.balance became "v" in this build, and adding a single field
# next release would shift the whole alphabet. Data written by one version
# would then be read back into the wrong fields by the next — silently showing
# wrong balances rather than failing loudly.
#
# Keeping the field names pins the on-disk/on-wire format. Classes may still be
# shrunk and their methods optimised; only the names are held stable.
# ---------------------------------------------------------------------------
-keepclassmembers class com.butterflydevs.billboard.viewmodel.MeterCardState { <fields>; }
-keepclassmembers class com.butterflydevs.billboard.data.** { <fields>; }

# Gson's own reflective machinery.
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}
-dontwarn sun.misc.**

# OkHttp / Okio ship their own consumer rules; these silence platform-only
# references that are never reachable on Android.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
