# Plan de Proyecto: Cititor

## 1. Visión del Proyecto

El objetivo es desarrollar una aplicación nativa de Android, **Cititor**, para la lectura y escucha de libros digitales.

### 1.1. Características Clave

- **Importación de Libros:** Los usuarios podrán importar archivos `.pdf` y `.epub` a su biblioteca personal.
- **Biblioteca Local:** Los libros importados se mostrarán en una biblioteca personal con capacidades de búsqueda y filtrado.
- **Modo de Lectura Inmersivo:** Una pantalla de lectura optimizada con desplazamiento vertical y una interfaz mínima para evitar distracciones.
- **Sincronización Audio-Texto:**
    - Capacidad para reproducir el texto de la página actual como audio.
    - Un marcador visual indicará en el texto la palabra o frase que se está reproduciendo.
    - El progreso de la lectura (página actual y posición del marcador) se guardará automáticamente.
- **Búsqueda Avanzada:**
    - Búsqueda por título o autor en la biblioteca.
    - Búsqueda de texto completo dentro del contenido de un libro abierto.
- **(Experimental) Voces Fluidas Offline:** Investigar la posibilidad de pre-generar archivos de audio de alta calidad para ofrecer una experiencia de escucha offline superior y de bajo consumo.

## 2. Arquitectura y Principios

- **Lenguaje:** Kotlin
- **UI:** Jetpack Compose
- **Arquitectura:** Arquitectura Limpia (Clean Architecture) con un enfoque MVVM en la capa de presentación.
- **Inyección de Dependencias:** Hilt
- **Base de Datos:** Room
- **Asincronía:** Corrutinas de Kotlin y Flow

## 3. Dependencias Clave

- `androidx.compose`: Para la UI.
- `androidx.navigation`: Para la navegación entre pantallas de Compose.
- `androidx.room`: Para la base de datos local.
- `com.google.dagger:hilt`: Para la inyección de dependencias.
- `io.coil-kt:coil-compose`: Para la carga de imágenes (portadas de libros).
- `com.tom-roush:pdfbox-android`: Para la extracción de texto e información de archivos PDF.
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

### Fase 3: Módulo de Lector (`feature_reader`) - 🚧 EN PROGRESO
1.  **Capa de Dominio:**
    -   [x] Definir la interfaz `ReaderRepository`.
    -   [x] Crear los Casos de Uso: `GetBookPageUseCase`, `UpdateBookProgressUseCase`.
2.  **Capa de Datos:**
    -   [x] Implementar `ReaderRepositoryImpl` que interactuará con `pdfbox` y `BookDao`.
3.  **Capa de Presentación:**
    -   [ ] Crear el `ReaderViewModel`.
    -   [ ] Diseñar la `ReaderScreen` que mostrará el texto con scroll vertical y el modo de lectura inmersivo.
    -   [ ] Implementar la navegación básica entre páginas.

### Fase 4: Funcionalidad Avanzada
1.  Integrar el `TextToSpeech` de Android en `ReaderScreen`.
2.  Implementar la lógica del marcador visual que se sincroniza con el audio.
3.  Añadir el soporte para importar y leer archivos `.epub`.
4.  Implementar la búsqueda de texto completo dentro de un libro abierto.

### Fase 5: Experimentación
1.  Investigar y prototipar el sistema de voces fluidas offline.

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

## 7. Control de Versiones

Se utilizarán **commits atómicos**. Cada commit debe representar un cambio pequeño y completo. Los mensajes de commit seguirán la especificación de Commits Convencionales (ej. `feat:`, `fix:`, `refactor:`, `docs:`, `test:`).

## 8. Deuda Técnica

Esta sección documenta las decisiones técnicas tomadas para acelerar el desarrollo que deberán ser "pagadas" en el futuro.

- **Dependencia de Seguridad en Versión Alpha:**
    - **Deuda:** Se está utilizando `androidx.security:security-crypto:1.1.0-alpha06` en lugar de la versión estable recomendada (`1.0.0`).
    - **Motivo:** Un bug inexplicable y persistente en el sistema de compilación de Gradle impide que la versión estable `1.0.0` se resuelva correctamente en el classpath de las pruebas instrumentadas, incluso después de limpiezas exhaustivas de la caché.
    - **Plan de Pago:** Antes de cualquier lanzamiento público o al iniciar una nueva fase de desarrollo mayor, se debe investigar nuevamente este problema (posiblemente con una nueva versión del Android Gradle Plugin) para poder volver a la versión estable de la librería.

*Este es un documento vivo y será actualizado a medida que el proyecto evolucione.*