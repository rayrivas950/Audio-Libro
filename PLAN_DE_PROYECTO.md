# Plan de Proyecto: Audio-Libro App

Este documento es una guía viva para el desarrollo de la aplicación de Audio-Libro. Se actualizará constantemente para reflejar el estado actual, los objetivos y las decisiones técnicas del proyecto.

## 1. Visión del Proyecto

Crear una aplicación nativa de Android que permita a los usuarios importar sus propios documentos (empezando con PDF) y escucharlos como audiolibros a través de un motor de Texto-a-Voz (TTS). La aplicación debe ser robusta, fácil de usar y tener una arquitectura mantenible.

## 2. Objetivos Clave (Features)

-   [x] **Importación de Libros:** Permitir al usuario seleccionar archivos PDF desde el almacenamiento del dispositivo.
-   [x] **Biblioteca de Libros:** Mostrar los libros importados en una biblioteca visualmente atractiva, con portadas generadas automáticamente.
-   [x] **Persistencia de Datos:** Guardar la información de los libros y el progreso de lectura en una base de datos local.
-   [x] **Lector de Texto:** Implementar una pantalla que muestre el contenido de texto extraído del PDF, página por página.
-   [ ] **Reproductor de Audio (TTS):**
    -   [ ] Integrar el motor de Texto-a-Voz (TTS) de Android.
    -   [ ] Añadir controles de reproducción (Play/Pausa) en la pantalla del lector.
    -   [ ] Resaltar la palabra o frase que se está leyendo en tiempo real.
    -   [ ] Permitir la reproducción en segundo plano.
-   [ ] **Gestión de Progreso:** Guardar y reanudar la lectura desde la última palabra o frase leída.
-   [ ] **Marcadores y Notas:** Permitir al usuario guardar marcadores en puntos específicos del libro.
-   [ ] **Soporte para EPUB:** Añadir la capacidad de importar y procesar archivos en formato EPUB.

## 3. Arquitectura y Decisiones Técnicas

-   **Lenguaje:** Kotlin.
-   **UI:** Jetpack Compose, siguiendo los principios de Material Design 3.
-   **Arquitectura General:** Arquitectura Limpia (Clean Architecture) adaptada a Android.
    -   **Capa de Presentación (UI):** Patrón MVVM (Model-View-ViewModel). La `View` (Composable screens) observa el estado expuesto por el `ViewModel`.
    -   **Capa de Dominio (Domain):** Contendrá la lógica de negocio pura en clases `UseCase`. Esta capa no debe depender de ninguna otra capa (especialmente de Android).
    -   **Capa de Datos (Data):** Implementa la interfaz definida en la capa de Dominio. Usa el patrón Repositorio (`Repository`) para abstraer las fuentes de datos.
-   **Inyección de Dependencias:** Hilt, para desacoplar las clases y facilitar las pruebas.
-   **Base de Datos:** Room, para la persistencia de datos locales (metadatos de libros, progreso, etc.).
-   **Navegación:** Jetpack Navigation for Compose.
-   **Análisis de PDF:** `pdfbox-android` para la extracción de texto.

## 4. Hoja de Ruta y Tareas Pendientes

### Hito 1: Lector de PDF Funcional (¡Completado!)
-   [x] Configurar proyecto y dependencias.
-   [x] Crear entidad de base de datos para los libros (`BookEntity`).
-   [x] Implementar DAO de Room (`BookDao`).
-   [x] Crear Repositorio para la gestión de libros (`LibraryRepository`).
-   [x] Implementar lógica para seleccionar y copiar PDFs (`PdfParser`).
-   [x] Crear pantalla de Biblioteca (`LibraryScreen` y `LibraryViewModel`).
-   [x] Crear pantalla de Lector de Texto (`ReaderScreen` y `ReaderViewModel`).

### Hito 2: Implementación de Texto-a-Voz (TTS) - (En Progreso)
-   [x] **Investigación:** Investigar la implementación de `TextToSpeech` en Android con Jetpack Compose.
-   [x] **ViewModel:** Crear una clase `TtsManager` para encapsular la lógica del TTS.
-   [x] **ViewModel:** Inyectar y utilizar `TtsManager` en `ReaderViewModel`.
-   [x] **ViewModel:** Añadir lógica para iniciar la lectura del texto de la página actual.
-   [x] **UI:** Añadir botones de Play/Pausa en `ReaderScreen` y conectarlos al `ReaderViewModel`.
-   [ ] **UI:** Implementar el resaltado de la palabra/frase que se está leyendo.
-   [ ] **Servicio:** Crear un `ForegroundService` para gestionar la reproducción en segundo plano.

### Pruebas Unitarias
-   [x] **TtsManagerTest:** Crear pruebas unitarias para `TtsManager`, verificando su inicialización, configuración de idioma, capacidad de hablar y cierre.
-   [ ] **ReaderViewModelTest:** Crear pruebas unitarias para `ReaderViewModel` que verifiquen la correcta interacción con `TtsManager` (inicialización, reproducción, cierre).
