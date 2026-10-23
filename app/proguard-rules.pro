# kotlinx.serialization keeps its generated serializers through this rule.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.everettpenne.baliset.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.everettpenne.baliset.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
