# ProGuard / R8 Rules for AR Measurement App

# Preserve line numbers for stack traces
-keepattributes SourceFile,LineNumberTable

# ARCore ProGuard Rules
-keep class com.google.ar.core.** { *; }
-dontwarn com.google.ar.core.**

# AndroidX Room
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**
-keep class * extends androidx.room.RoomDatabase
-keep class * extends androidx.room.RoomDatabase$Callback
-keep class com.example.data.** { *; }

# Kotlinx Serialization
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Project Models & Logic
-keep class com.example.ui.viewmodel.Point3D { *; }
-keep class com.example.ui.viewmodel.MeasureRecord { *; }
-keep class com.example.data.model.** { *; }
-keep class com.example.logic.ar.** { *; }
