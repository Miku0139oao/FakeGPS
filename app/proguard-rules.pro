# Project-specific R8/ProGuard rules.

# lite-orm uses reflection over annotations and model classes.
-keep class com.litesuits.orm.** { *; }
-keep @com.litesuits.orm.db.annotation.Table class * { *; }
-keep class com.github.fakegps.model.** { *; }
-keepattributes *Annotation*,Signature

# Strip verbose/debug log calls from release builds. Errors are kept.
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
}
