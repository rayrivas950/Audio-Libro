# Audiobook App (Android)

## Descripción
Aplicación nativa de Android para reproducir audiolibros a partir de archivos PDF y EPUB. Diseñada con un enfoque "Offline First", priorizando la privacidad, el bajo consumo de batería y una experiencia de usuario premium.

## Características Clave
- **Conversión Texto-a-Voz (TTS)**: Soporte híbrido para motor nativo (Offline) y APIs en la nube.
- **Offline First**: Persistencia robusta del progreso de lectura usando Room Database.
- **Seguridad**: Validación estricta de archivos (Magic Numbers) y Scoped Storage.
- **Accesibilidad**: Soporte completo para TalkBack.

## Arquitectura
El proyecto sigue los estándares de **Modern Android Development (MAD)**:
- **Lenguaje**: Kotlin
- **UI**: Jetpack Compose
- **Inyección de Dependencias**: Hilt
- **Patrón**: MVVM + Clean Architecture
- **Gestión de Dependencias**: Gradle Version Catalog (`libs.versions.toml`)

## Estructura del Proyecto
- `app/`: Módulo principal de la aplicación.
- `gradle/libs.versions.toml`: Catálogo centralizado de dependencias.

## Requisitos
- Android Studio Koala o superior.
- JDK 17.
- Min SDK: 26 (Android 8.0).
