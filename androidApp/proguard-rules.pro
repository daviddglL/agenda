# Reglas especificas del proyecto para R8/ProGuard (punto 4 de seguridad de markdown.md).
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes SerialVersionUID

# Room
-keep class androidx.room.** { *; }
-keepclasseswithmembers class * {
    @androidx.room.* <methods>;
}

# Ktor / kotlinx.serialization: conservar los serializadores generados
-keepattributes *Annotation*
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class **$$serializer {
    *** INSTANCE;
}

# Koin: evita ofuscar las clases que Koin resuelve por reflexion en modulos generados por KSP
-keep class org.koin.** { *; }
-keepclassmembers class * {
    public <init>(...);
}

# Uncomment this to preserve the line number information for
# stack traces when debugging.
#-keepattributes SourceFile,LineNumberTable
