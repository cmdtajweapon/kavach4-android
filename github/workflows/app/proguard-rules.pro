# Keep the JavaScript bridge — its methods are called by name from the
# WebView's JS and must not be renamed or stripped by R8/ProGuard.
-keepclassmembers class com.cmdtaj.kavach4assistant.AndroidBridge {
   public *;
}
-keep class com.cmdtaj.kavach4assistant.AndroidBridge { *; }

# Google Mobile Ads SDK keep rules
-keep class com.google.android.gms.ads.** { *; }
-keep public class com.google.android.gms.ads.internal.ClientApi { public *; }

# Standard Android WebView / speech classes are framework classes and
# do not need explicit keep rules.
