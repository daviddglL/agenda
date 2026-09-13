# ESPECIFICACIONES TÉCNICAS Y REGLAS DEL PROYECTO

Este documento establece las restricciones técnicas obligatorias para el desarrollo de la aplicación. 
NO se permite modificar ni sustituir ninguna de las tecnologías o patrones aquí descritos durante el proceso.

## 1. Arquitectura y Patrones
- **Arquitectura:** Clean Architecture + Arquitectura Modularizada (Estructura por módulos independientes: `:core:network`, `:core:database`, `:core:designsystem`, `:feature:login`, etc.).
- **Patrón de Presentación:** MVI (Model-View-Intent).
- **Inyección de Dependencias:** Koin (sostenible y optimizado para Kotlin Multiplatform).

## 2. Frontend, Estado y Persistencia
- **Lenguaje & Plataforma:** Kotlin Multiplatform (KMP) enfocado en compartir lógica entre Android e iOS.
- **UI:** Jetpack Compose / Compose Multiplatform (Diseño dinámico, soporte Dark/Light Mode).
- **Gestión de Estado:** Centralizada e inmutable en torno a MVI mediante `StateFlow` y eventos/intenciones (`SharedFlow`).
- **Base de Datos Local:** Room KMP (o SQLDelight) con arquitectura Offline-First y Repositorios como Única Fuente de Verdad (SSOT).

## 3. Redes y Comunicación
- **Cliente HTTP:** Ktor Client (con engines multiplataforma e interceptores para refresco de tokens).
- **Serialización:** Kotlinx.serialization para el formateo y deserialización de JSON.
- **Tiempo Real:** Ktor WebSockets para comunicación bidireccional en tiempo real.

## 4. Seguridad
- **Almacenamiento Seguro:** EncryptedSharedPreferences / KeyStore (Android) y Keychain (iOS).
- **Comunicaciones:** HTTPS / TLS 1.3 y SSL Certificate Pinning configurado en el cliente Ktor.
- **Protección de Código:** Ofuscación de código y optimización de binarios mediante R8 / ProGuard.

## 5. Calidad y Pruebas
- **Pruebas Unitarias:** Kotlin Test + Mockk / Fake Repositories para la lógica de dominio, reductores MVI y casos de uso.
- **Pruebas de Integración/UI:** Compose UI Testing.
- **Estilo:** Análisis estático de código con Ktlint / Detekt.

## 6. Flujo de Trabajo y Entregas
- **Commits y Control de Versiones:** Generar commits estructurados (Conventional Commits) al finalizar cada caso de uso o módulo técnico.
- **Análisis de Errores:** Realizar revisión de código estático y reportar posibles fallos o incompatibilidades entre plataformas antes de cerrar una tarea.