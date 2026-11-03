# kotlinx.serialization: keep generated serializers for app models (saved state and map files).
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class com.accesspath.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.accesspath.**$$serializer { *; }
-keepclassmembers class com.accesspath.** {
    *** Companion;
}
-keepclasseswithmembers class com.accesspath.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.accesspath.domain.BarrierCategorySerializer { *; }
