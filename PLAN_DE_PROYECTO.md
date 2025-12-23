# Plan de Proyecto: Cititor

## 1. Visión del Proyecto

El objetivo es desarrollar una aplicación nativa de Android, **Cititor**, para la lectura y escucha de libros digitales, con un enfoque en una experiencia de audio avanzada y personalizable.

### 1.1. Características Clave

- **Importación de Libros:** Los usuarios podrán importar archivos `.pdf` y `.epub` a su biblioteca personal.
- **Biblioteca Local:** Los libros importados se mostrarán en una biblioteca personal con capacidades de búsqueda y filtrado.
- **Modo de Lectura Inmersivo:** Una pantalla de lectura optimizada con desplazamiento vertical y una interfaz mínima para evitar distracciones.
- **Sincronización Audio-Texto:**
    - Capacidad para reproducir el texto de la página actual como audio.
    - Un marcador visual indicará en el texto la palabra o frase que se está reproduciendo.
    - El progreso de la lectura se guardará automáticamente.
- **(Futuro) Capacidades Avanzadas de TTS:**
    - Investigar y desarrollar la capacidad de asignar voces diferentes a personajes distintos dentro de una conversación.

## 2. Arquitectura y Principios

- **Lenguaje:** Kotlin
- **UI:** Jetpack Compose
- **Arquitectura:** Arquitectura Limpia (Clean Architecture) con un enfoque MVVM en la capa de presentación.
- **Motor de Análisis de Contenido:** Para optimizar el rendimiento y habilitar funcionalidades avanzadas de TTS, el contenido de los libros no solo se extraerá y limpiará, sino que se analizará para identificar su estructura (ej. narración vs. diálogo). El resultado se almacenará en un formato estructurado (JSON) durante un proceso de importación en segundo plano.
- **Inyección de Dependencias:** Hilt
- **Base de Datos:** Room
- **Asincronía:** Corrutinas de Kotlin, Flow y WorkManager para trabajos en segundo plano.

## 3. Dependencias Clave

- `androidx.compose`: Para la UI.
- `androidx.navigation`: Para la navegación entre pantallas de Compose.
- `androidx.room`: Para la base de datos local y el almacenamiento de contenido pre-procesado.
- `androidx.work:work-runtime-ktx`: Para la gestión de trabajos en segundo plano.
- `org.jetbrains.kotlinx:kotlinx-serialization-json`: Para la serialización y deserialización de datos estructurados.
- `com.google.dagger:hilt`: Para la inyección de dependencias.
- `io.coil-kt:coil-compose`: Para la carga de imágenes (portadas de libros).
- `com.tom-roush:pdfbox-android`: Para la extracción de texto de archivos PDF.
- **Adicional para Pruebas:** `junit`, `mockk`, `androidx.test.*`
- **Adicional para Seguridad:** `net.zetetic:android-database-sqlcipher` (para encriptar Room)

## 4. Fases de Desarrollo (Roadmap Detallado)

### Fase 1: Configuración y Base del Proyecto - ✅ COMPLETADA
- [x] Configurar las dependencias de `Hilt`, `Room`, `Compose Navigation`, etc., en los archivos `build.gradle.kts` y `libs.versions.toml`.
- [x] Establecer la estructura inicial de paquetes: `core`, `app`, `ui.theme`.
- [x] Definir la entidad de la base de datos (`BookEntity`) que representará un libro.
- [x] Crear la configuración de la base de datos Room, incluyendo el `Dao` (Data Access Object) y la clase principal de la base de datos cifrada con `SQLCipher`.
- [x] Crear la clase `Application` (`CititorApp`) y configurarla para Hilt.

### Fase 2: Módulo de Biblioteca (`feature_library`) - ✅ COMPLETADA
1.  **Capa de Dominio:**
    -   [x] Definir la interfaz `LibraryRepository` (el "contrato" de lo que se puede hacer con los datos de la biblioteca).
    -   [x] Crear los Casos de Uso: `GetBooksUseCase`, `AddBookUseCase`, `SearchBooksUseCase`.
2.  **Capa de Datos:**
    -   [x] Implementar `LibraryRepositoryImpl` que usará el `BookDao` para interactuar con la base de datos Room.
3.  **Capa de Presentación:**
    -   [x] Crear el `LibraryViewModel` que utilizará los casos de uso para obtener y modificar los datos.
    -   [x] Diseñar la `LibraryScreen` en Jetpack Compose que mostrará la lista de libros y una barra de búsqueda.
    -   [x] Implementar la funcionalidad para importar un nuevo libro `.pdf`.

### Fase 3: Módulo de Lector (`feature_reader`) - ✅ COMPLETADA
1.  **Capa de Dominio:**
    -   [x] Definir la interfaz `ReaderRepository`.
    -   [x] Crear los Casos de Uso: `GetBookPageUseCase`, `UpdateBookProgressUseCase`.
2.  **Capa de Datos:**
    -   [x] Implementar `ReaderRepositoryImpl` que interactuará con `pdfbox` y `BookDao`.
3.  **Capa de Presentación:**
    -   [x] Crear el `ReaderViewModel`.
    -   [x] Diseñar la `ReaderScreen` que mostrará el texto con scroll vertical y el modo de lectura inmersivo.
    -   [x] Implementar la navegación básica entre páginas.

### Fase 4: Motor de Análisis de Contenido y TTS Estructurado - 🚧 EN PROGRESO

Esta fase refactoriza el sistema para que se base en contenido pre-analizado y estructurado, sentando las bases para un TTS avanzado. El objetivo es diferenciar entre narración y diálogo.

1.  **Diseño del Analizador de Texto (`TextAnalyzer`):**
    -   [ ] Crear una clase que, además de limpiar HTML (`TextSanitizer`), implemente una heurística para detectar diálogos (ej. texto entre comillas).
    -   [ ] Definir las estructuras de datos (data classes de Kotlin) que representarán el contenido segmentado (ej. `NarrationSegment`, `DialogueSegment`).
2.  **Ampliación de la Base de Datos (con JSON):**
    -   [ ] `CleanPageEntity` se modificará para que su campo `content` almacene una cadena de texto en formato JSON, representando la lista de segmentos analizados para esa página.
    -   [ ] Añadir la dependencia `kotlinx.serialization` para la serialización/deserialización.
3.  **Implementación del Worker de Análisis (`BookProcessingWorker`):**
    -   [ ] Crear un `BookProcessingWorker`.
    -   [ ] Implementar la lógica: por cada página, usar el `Extractor`, pasar el texto al `TextAnalyzer`, serializar la estructura resultante a JSON y guardar la cadena JSON en la `CleanPageEntity`.
4.  **Refactorización del Flujo de Importación:**
    -   [ ] Al importar un libro, encolar una nueva solicitud de trabajo para el `BookProcessingWorker`.
    -   [ ] (Opcional) Actualizar la UI para mostrar un indicador de "Procesando...".
5.  **Refactorización de la Capa de Lectura:**
    -   [ ] `ReaderRepository` consultará el JSON de la base de datos.
    -   [ ] `ReaderViewModel` deserializará el JSON y gestionará la lista de segmentos. La UI mostrará el texto concatenado.
6.  **Validación del TTS Estructurado:**
    -   [ ] Actualizar `TextToSpeechManager` para que acepte la lista de segmentos.
    -   [ ] Verificar que se puede aplicar una voz para la narración y otra voz distinta para los diálogos.

### Fase 5: Funcionalidad Avanzada y TTS con Identidad

1.  **Sincronización Audio-Texto:**
    -   [ ] Implementar la lógica del marcador visual que se sincroniza con el audio.
2.  **Búsqueda Interna:**
    -   [ ] Implementar la búsqueda de texto completo dentro de un libro abierto.
3.  **Identificación de Personajes en TTS:**
    -   [ ] Mejorar el `TextAnalyzer` con heurísticas para asociar los diálogos con nombres de personajes (ej. analizando "tags" como "dijo Juan").
    -   [ ] Implementar un sistema en el `ViewModel` o `TTSManager` para asignar voces únicas a cada `characterId` identificado.

## 5. Calidad y Pruebas

El desarrollo seguirá una estricta disciplina de pruebas.
- **Pruebas Unitarias:** Se crearán para toda la lógica de negocio en las capas de `domain` y `data`. Se usará `MockK` para el aislamiento.
- **Pruebas de UI:** Se implementarán flujos de usuario clave con el framework de testing de Compose para verificar el comportamiento de la `presentation`.
- **Cobertura:** Se buscará una alta cobertura de pruebas como indicador de la calidad y robustez del código.

## 6. Seguridad

La seguridad de los datos del usuario es una prioridad.
- **Cifrado de la Base de Datos:** La base de datos de Room será cifrada utilizando `SQLCipher` para proteger la información de la biblioteca en reposo.
- **Almacenamiento Seguro:** Los archivos de los libros (`.pdf`, `.epub`) se almacenarán en el directorio interno y privado de la aplicación.
- **Permisos:** Se seguirá el principio de mínimo privilegio en la solicitud de permisos al usuario.
- **Análisis de Seguridad de Parsers:** Investigar y robustecer las librerías de procesamiento de archivos (e.g., para EPUB y PDF) para prevenir vulnerabilidades comunes como "Zip Slip" y "XML External Entity (XXE)".

## 7. Control de Versiones

Se utilizarán **commits atómicos**. Cada commit debe representar un cambio pequeño y completo. Los mensajes de commit seguirán la especificación de Commits Convencionales (ej. `feat:`, `fix:`, `refactor:`, `docs:`, `test:`).

## 8. Deuda Técnica

Esta sección documenta las decisiones técnicas tomadas para acelerar el desarrollo que deberán ser "pagadas" en el futuro.

- **Dependencia de Seguridad en Versión Alpha:**
    - **Deuda:** Se está utilizando `androidx.security:security-crypto:1.1.0-alpha06` en lugar de la versión estable recomendada (`1.0.0`).
    - **Motivo:** Un bug inexplicable y persistente en el sistema de compilación de Gradle impide que la versión estable `1.0.0` se resuelva correctamente en el classpath de las pruebas instrumentadas, incluso después de limpiezas exhaustivas de la caché.
    - **Plan de Pago:** Antes de cualquier lanzamiento público o al iniciar una nueva fase de desarrollo mayor, se debe investigar nuevamente este problema (posiblemente con una nueva versión del Android Gradle Plugin) para poder volver a la versión estable de la librería.

*Este es un documento vivo y será actualizado a medida que el proyecto evolucione.*
