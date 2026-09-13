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

# google-tink (dependencia transitiva de androidx.security.crypto, usada por SecureStorage
# para EncryptedSharedPreferences) referencia anotaciones de error-prone que solo existen en
# tiempo de compilacion; en tiempo de ejecucion no hacen falta y R8 no las encuentra.
-dontwarn com.google.errorprone.annotations.**

# slf4j (dependencia transitiva de ktor-client-logging): sin un binding real en el APK, R8
# avisa de las clases del mecanismo de descubrimiento de implementacion; no afecta al
# logging, que sigue funcionando con el binding "NOP" por defecto de slf4j.
-dontwarn org.slf4j.impl.**

# Uncomment this to preserve the line number information for
# stack traces when debugging.
#-keepattributes SourceFile,LineNumberTable
