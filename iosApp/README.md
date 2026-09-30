# iosApp (pendiente, requiere macOS/Xcode)

Este directorio es un marcador de posicion. El proyecto Xcode real (`iosApp.xcodeproj` o `iosApp.xcworkspace`)
no se puede generar ni compilar desde Windows: hace falta crearlo con Xcode en una Mac.

Pasos para el equipo iOS:

1. Crear un proyecto SwiftUI vacio en Xcode llamado `iosApp`, en esta carpeta.
2. Anadir el framework `Shared.framework` generado por el modulo `:shared` (Kotlin Multiplatform)
   como dependencia: `./gradlew :shared:embedAndSignAppleFrameworkForXcode` (o via Kotlin
   Multiplatform Mobile plugin de Android Studio, o CocoaPods/SPM segun se decida).
3. En `App.swift`, llamar a `Shared.doInitKoinIos()` antes de mostrar la primera vista.
4. Mostrar la UI compartida con `ComposeUIViewControllerRepresentable` envolviendo
   `Shared.MainViewControllerKt.mainViewController()` (definida en
   `shared/src/iosMain/kotlin/.../MainViewController.kt`).
5. Verificar/terminar en Xcode el pinning SSL real de
   `core/data/src/iosMain/.../core/data/networking/HttpClientFactory.ios.kt` y el Keychain de
   `core/data/src/iosMain/.../SecureStorage.ios.kt`: ambos quedaron implementados
   pero no se han podido compilar ni probar sin Xcode.
