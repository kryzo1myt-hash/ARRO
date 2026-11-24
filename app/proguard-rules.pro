# Unity Ads SDK ProGuard / R8 Rules
-keep class com.unity3d.ads.** { *; }
-dontwarn com.unity3d.ads.**
-keep class com.unity3d.services.** { *; }
-dontwarn com.unity3d.services.**
-keep public class gatewayprotocol.v1.** { *; }
-dontwarn gatewayprotocol.v1.**

-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
