# Keep the widget and all its entry points
-keep class com.mehdi.analogclock.ClockWidgetProvider { *; }
-keep class com.mehdi.analogclock.SettingsActivity { *; }

# Keep weather and solar helpers
-keep class com.mehdi.analogclock.SolarCalculator { *; }
-keep class com.mehdi.analogclock.SolarCalculator$* { *; }

# Keep R classes for layouts and ids
-keepclassmembers class **.R$* {
    public static <fields>;
}

# Standard Android / Kotlin
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-dontwarn kotlin.**
-dontwarn kotlinx.**
