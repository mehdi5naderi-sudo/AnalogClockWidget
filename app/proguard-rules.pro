# Keep AppWidgetProvider and related classes
-keep class com.mehdi.analogclock.ClockWidgetProvider { *; }
-keep class com.mehdi.analogclock.WeatherData { *; }
-keep class com.mehdi.analogclock.SolarCalculator { *; }
-keep class com.mehdi.analogclock.SettingsActivity { *; }

# Keep data classes used in weather parsing
-keepclassmembers class com.mehdi.analogclock.WeatherData$* { *; }

# Standard Android rules
-keepattributes *Annotation*
-dontwarn kotlinx.**
