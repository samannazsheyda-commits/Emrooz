# Keep JNI and optional on-device model integration linkable after optimization.
-keep class ai.onnxruntime.** { *; }
-keep class com.sentencepiece.** { *; }
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.datatransport.** { *; }
# Stable app/test/native entry points; unused dependency code and icons may shrink.
-keep class com.nameemrooz.journal.** { *; }
