# Viagara Launcher R8 rules.
#
# Compose, AndroidX and kotlinx ship their own consumer rules, so this file only
# covers the two places where the framework reaches into our code by name.

# AppWidgetHost.createView() instantiates the host view reflectively, and the
# system inflates our container around it.
-keep class dev.viagaralauncher.widget.ViagaraAppWidgetHost { *; }
-keep class dev.viagaralauncher.widget.LongPressFrameLayout {
    public <init>(android.content.Context);
}

# Services named in AndroidManifest.xml are instantiated by name by the system.
-keep class dev.viagaralauncher.service.ViagaraAccessibilityService { *; }
-keep class dev.viagaralauncher.media.NowPlayingListenerService { *; }
-keep class dev.viagaralauncher.ViagaraApp { *; }

# SystemUi falls back to reflection into StatusBarManager on pre-Android-12
# builds; keep the call site legible in crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# MediaPipe Tasks GenAI & LLM Inference
-keep class com.google.mediapipe.** { *; }
-keep interface com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# TensorFlow Lite & LiteRT
-keep class org.tensorflow.** { *; }
-dontwarn org.tensorflow.**

# Protobuf
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**

# AutoValue, Guava and compile-time annotation processor references
-dontwarn javax.annotation.processing.**
-dontwarn javax.lang.model.**
-dontwarn com.google.auto.value.**
-dontwarn com.google.common.**
-dontwarn org.checkerframework.**
-dontwarn com.google.errorprone.**
-dontwarn com.google.j2objc.**

# Prevent R8 from failing on missing compile-time optional classes
-ignorewarnings


