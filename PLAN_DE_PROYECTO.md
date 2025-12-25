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

### Fase 4: Motor de Análisis de Contenido y TTS Estructurado - ✅ COMPLETADA

Esta fase refactoriza el sistema para que se base en contenido pre-analizado y estructurado, sentando las bases para un TTS avanzado. El objetivo es diferenciar entre narración y diálogo.

1.  **Diseño del Analizador de Texto (`TextAnalyzer`):**
    -   [x] Crear una clase que, además de limpiar HTML (`TextSanitizer`), implemente una heurística para detectar diálogos (ej. texto entre comillas).
    -   [x] Definir las estructuras de datos (data classes de Kotlin) que representarán el contenido segmentado (`NarrationSegment`, `DialogueSegment`).
2.  **Ampliación de la Base de Datos (con JSON):**
    -   [x] `CleanPageEntity` se modificó para que su campo `content` almacene una cadena de texto en formato JSON, representando la lista de segmentos analizados para esa página.
    -   [x] Añadida la dependencia `kotlinx.serialization` para la serialización/deserialización.
    -   [x] Configurado `classDiscriminator` en Json para serialización polimórfica de sealed interfaces.
3.  **Implementación del Worker de Análisis (`BookProcessingWorker`):**
    -   [x] Creado `BookProcessingWorker` con inyección de dependencias correcta.
    -   [x] Implementada la lógica: por cada página, usar el `Extractor`, pasar el texto al `TextAnalyzer`, serializar la estructura resultante a JSON y guardar en `CleanPageEntity`.
    -   [x] Agregado manejo robusto de errores con mensajes descriptivos.
    -   [x] Implementado logging detallado para diagnóstico.
    -   [x] Validación de permisos de URI persistentes.
4.  **Refactorización del Flujo de Importación:**
    -   [x] Al importar un libro, se encola una solicitud de trabajo para el `BookProcessingWorker`.
    -   [x] Actualizada la UI para mostrar indicador de "Analysing book for the first time, please wait...".
    -   [x] Configurado `HiltWorkerFactory` en AndroidManifest para correcta instanciación del Worker.
5.  **Refactorización de la Capa de Lectura:**
    -   [x] `ReaderRepository` consulta el JSON de la base de datos.
    -   [x] `ReaderViewModel` deserializa el JSON y gestiona la lista de segmentos.
    -   [x] La UI muestra el texto concatenado correctamente.
6.  **Validación del TTS Estructurado:**
    -   [x] `TextToSpeechManager` funciona con el texto procesado.
    -   [x] TTS funcional para PDF y EPUB.

**Correcciones Técnicas Implementadas:**
-   [x] Corregida inyección de dependencias en `ExtractorFactory` (ahora usa `@Singleton` e inyecta extractores).
-   [x] Mejorado manejo de excepciones en extractores (PDF/EPUB) con logging exhaustivo.
-   [x] Solucionado problema de `ParserConfigurationException` en EPUB (warning no fatal).
-   [x] Implementado sistema de diagnóstico con `DebugHelper` para pruebas sin ADB.

### Fase 4B: Optimización de Procesamiento y TTS Cinematográfico - 🚧 PLANIFICADA

Esta fase optimiza el rendimiento del procesamiento y agrega capacidades avanzadas de TTS con análisis emocional e identificación de personajes.

#### 4B.1: Optimización de Procesamiento (Prioridad Alta)
-   [x] **Procesamiento en Batch:**
    -   [x] Refactorizar `TextExtractor` para agregar método `extractAllPages()`.
    -   [x] Modificar `EpubExtractor` para cargar el Reader una sola vez y extraer todas las páginas.
    -   [x] Modificar `PdfExtractor` para cargar el PDDocument una sola vez.
    -   [x] Actualizar `BookProcessingWorker` para usar procesamiento en batch.
    -   **Resultado:** Reducción de tiempos de carga (EPUB 15s -> 3s).

#### 4B.2: Modelo de Datos Extendido (Prioridad Alta)
-   [x] **Estructuras para TTS Emocional:**
    -   [x] Crear `TTSParameters` data class (pitch, speed, volume, emphasis, pause).
    -   [x] Crear enum `Emotion` (NEUTRAL, JOY, SADNESS, ANGER, FEAR, SURPRISE, URGENCY, WHISPER).
    -   [x] Crear enum `NarrationStyle` (NEUTRAL, DESCRIPTIVE, TENSE, CALM, MYSTERIOUS).
    -   [x] Extender `DialogueSegment` con campos: `speakerId`, `emotion`, `intensity`.
    -   [x] Extender `NarrationSegment` con campo: `style`.
    -   [x] Agregar `ttsParams: TTSParameters?` a `TextSegment`.

-   [x] **Estructuras para Personajes:**
    -   [x] Crear `Character` data class (id, name, voiceProfile, voiceModel, gender, ageRange).
    -   [x] Crear `BookMetadata` entity para almacenar personajes identificados por libro.

#### 4B.3: Análisis Emocional Básico (Prioridad Media)
-   [x] **Detección por Heurísticas:**
    -   [x] Implementar `EmotionDetector` con reglas (puntuación, keywords).
    -   [x] Detectar URGENCY, SURPRISE, WHISPER, ANGER.
    -   [x] Implementar `extractTTSParams()` para asignar parámetros prosódicos.

-   [x] **Integración en Pipeline:**
    -   [x] Modificar `TextAnalyzer.analyze()` para incluir análisis emocional.
    -   [x] Guardar emociones y parámetros TTS en JSON.

#### 4B.4: Identificación de Personajes con Reglas (Prioridad Media)

**Enfoque:** Sistema 100% offline basado en reglas y patrones regex para identificar personajes y asignar diálogos.

-   [x] **Extracción de Nombres con Patrones:**
    -   [x] Implementar `CharacterDetector` con regex para detectar hablantes.
    -   [x] Crear diccionario de verbos de diálogo comunes.
    -   [x] Detectar nombres propios por mayúscula inicial.
    
-   [x] **Asignación de Diálogos a Personajes:**
    -   [x] Implementar lógica de inferencia por contexto inmediato.
    -   [x] Marcar diálogos sin atribución como "Unknown Speaker" (o Narrador).
    
-   [x] **Construcción de Mapa de Personajes:**
    -   [x] Implementar `CharacterRegistry` para procesar todo el libro.
    -   [x] Inferir género por pronombres en contexto.
    -   [x] Persistir mapa de personajes en `BookMetadata` entity.
    
-   [ ] **Refinamiento Manual (UI Opcional):**
    -   [ ] Pantalla para revisar personajes detectados
    -   [ ] Permitir fusionar personajes duplicados (ej. "Juan" y "El Doctor")
    -   [ ] Permitir cambiar asignación de voces manualmente
    -   [ ] Guardar preferencias de usuario por libro

**Limitaciones Conocidas:**
-   **Libros sin atribución clara:** Autores modernos que no usan "dijo Juan" explícitamente
    -   *Solución futura:* Análisis de co-ocurrencia y contexto narrativo
-   **Nombres ambiguos:** Palabras que pueden ser nombres o sustantivos comunes (ej. "Rosa", "León")
    -   *Solución futura:* Diccionario de nombres comunes españoles (offline)
-   **Apodos y referencias indirectas:** "El Doctor" vs "John Watson", "mamá" vs nombre real
    -   *Solución futura:* Clustering de diálogos por estilo y vocabulario
-   **Diálogos en grupo:** Conversaciones con múltiples participantes sin atribución clara
    -   *Solución futura:* Análisis de turnos de conversación
-   **Narradores en primera persona:** "Yo dije" no identifica al personaje
    -   *Solución futura:* Detectar narrador principal en metadatos del libro

**Mejoras Futuras (Fase 4B.4+):**
-   [ ] **Diccionario de Nombres Offline:**
    -   [ ] Integrar lista de nombres propios comunes en español (~5MB)
    -   [ ] Filtrar falsos positivos (sustantivos comunes)
    
-   [ ] **Análisis de Co-ocurrencia:**
    -   [ ] Detectar qué personajes aparecen juntos frecuentemente
    -   [ ] Usar para resolver ambigüedades en diálogos sin atribución
    
-   [ ] **Clustering de Estilo de Diálogo:**
    -   [ ] Analizar vocabulario característico de cada personaje
    -   [ ] Analizar longitud promedio de frases
    -   [ ] Usar para asignar diálogos sin atribución explícita
    
-   [ ] **NER Ligero Offline (Opcional):**
    -   [ ] Evaluar modelos de Named Entity Recognition pequeños (~5-10MB)
    -   [ ] Integrar con TensorFlow Lite para ejecución en dispositivo
    -   [ ] Usar solo si las reglas no son suficientes

**Métricas de Éxito:**
-   Detectar correctamente >80% de personajes principales
-   Asignar correctamente >70% de diálogos a personajes
-   Tiempo de procesamiento: <500ms por libro completo


#### 4B.5: TTS Offline de Alta Calidad (Prioridad Baja - Fase 2)
-   [ ] **Investigación de Tecnologías:**
    -   [ ] Evaluar Piper TTS (recomendado - ligero, natural).
    -   [ ] Evaluar Coqui TTS (más control emocional, más pesado).
#### 4B.5. Asignación de Voces (Completado)
- [x] **Modelo de Voz:**
    - [x] Crear `VoiceProfile` data class (id, name, pitch, speed).
    - [x] Definir arquetipos: Héroe, Villano, Niño, Anciano, Gigante, Misterioso.
- [x] **Inferencia de Voz:**
    - [x] Implementar `VoiceInferenceEngine` basado en género, edad y keywords físicas.
    - [x] Manejo automático de personajes "Misteriosos" (Sombra, Encapuchado).
- [x] **Escenarios Avanzados:**
    - [x] Detección de Pensamientos (`THOUGHT`) mediante cursivas.
    - [x] Manejo de Tartamudeo (preservación de guiones).

#### 4B.6. Análisis Emocional Avanzado con ML (Pendiente - Fase Futura)
- [ ] **Integración de Modelos ML:**
    - [ ] Evaluar modelos de análisis de sentimientos (BERT, DistilBERT).
    - [ ] Implementar análisis de contexto (tensión, calma, misterio).
    - [ ] Optimizar para ejecución en dispositivo (TensorFlow Lite).

### Fase 5: Integración de Motor de Audio (Piper + TarsosDSP)

Esta fase se centra en reemplazar el TTS nativo con un motor de alta calidad y capacidades de post-procesamiento.

1.  **Integración de Piper TTS (Base):**
    *   [ ] Integrar librería nativa de Piper para Android.
    *   [ ] Implementar descarga y gestión de modelos de voz en español (es_ES, es_MX).
    *   [ ] Crear `PiperTTSEngine` que reemplace al `AndroidTTSEngine` actual.

2.  **Procesamiento de Audio (DSP):**
    *   [ ] Integrar librería **TarsosDSP**.
    *   [ ] Implementar `AudioEffectProcessor` para aplicar efectos en tiempo real/post-proceso.
    *   [ ] Crear efectos específicos:
        *   **Pitch Shifting:** Para Gigantes (bajo) y Niños (alto).
        *   **Time Stretching:** Para hablar lento o rápido sin cambiar tono.
        *   **Reverb/Echo:** Para pensamientos y voces etéreas.

3.  **Conexión con Lógica de Voces:**
    *   [ ] Mapear `VoiceProfile` (creado en Fase 4B) a parámetros de Piper + Tarsos.
    *   [ ] Ejemplo: `VoiceProfile.GIANT` -> Piper Voice A + Pitch -4 semitonos + Speed 0.8.

4.  **Gestión de Voces por Personaje (UI):**
    *   [ ] Implementar UI para revisar y editar personajes identificados.
    *   [ ] Permitir al usuario asignar manualmente voces a personajes.
    *   [ ] Guardar preferencias de voz por personaje.

### Fase 10: Expresividad Humana y Prosodia (Motor de Prosodia Extensible)

Esta fase eleva la calidad de la síntesis de "funcional" a "artística", permitiendo que la app adapte su estilo de lectura según el género del libro.

1.  **Arquitectura Modular (`domain.analyzer.prosody`):**
    *   [ ] Implementar interfaz `ProsodyProfile` para definir comportamientos vocales.
    *   [ ] Crear `ProsodyEngine` como orquestador de capas de expresión.

2.  **Implementación de Perfiles de Género:**
    *   [ ] **Perfil Épico/Literario:** Énfasis en drama, micro-respiros y variaciones de ritmo.
    *   [ ] **Perfil Técnico:** Enfoque en claridad, pausas rítmicas y énfasis en términos complejos.
    *   [ ] **Perfil Legal/Solemne:** Ritmo pausado, pausas largas tras artículos y tono autoritario.
    *   [ ] **Perfil Histórico:** Énfasis en fechas y nombres, ritmo reflexivo.

3.  **Detección Inteligente:**
    *   [ ] Implementar `GenreDetector` para asignar perfiles automáticamente basándose en el vocabulario del libro.

4.  **Rasgos de Expresividad:**
    *   [ ] **Micro-respiros:** Inserción de inhalaciones sutiles.
    *   [ ] **Curvas de Entonación:** Inflexiones automáticas en `?`, `!` y finales de párrafo.
    *   [ ] **Énfasis Léxico:** Contraste de velocidad entre palabras de función y de contenido.
    *   [ ] **Pausas Dramáticas:** Silencios estratégicos para generar expectativa.

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

- **Análisis de Texto Básico:**
    - **Deuda:** El `TextAnalyzer` actual usa regex simple para detectar diálogos, lo cual puede fallar con formatos complejos o no estándar.
    - **Plan de Pago:** En Fase 4B.6, implementar análisis con ML para mayor precisión.

- **TTS Nativo de Android:**
    - **Deuda:** El TTS actual usa el motor nativo de Android, que tiene calidad variable según el dispositivo.
    - **Plan de Pago:** En Fase 5, integrar Piper TTS para calidad consistente y offline.

- **Dependencia de Sherpa-ONNX:**
    - **Deuda:** Se utiliza `sherpa-onnx` como intermediario para ejecutar modelos de Piper, lo que añade una dependencia externa.
    - **Motivo:** Acelerar la implementación evitando la compilación manual de `espeak-ng` y ONNX Runtime para Android en esta etapa.
    - **Plan de Pago:** En una fase futura de optimización, implementar la integración directa de Piper (compilando C++ nativo y JNI) para eliminar la dependencia de Sherpa y tener control total del stack de audio.

## 9. Métricas de Rendimiento

**Estado Actual (Fase 4):**
- Procesamiento EPUB (29 páginas): ~15 segundos
- Procesamiento PDF (150 páginas): ~30-40 segundos
- TTS: Funcional con motor nativo de Android

**Objetivos Fase 4B.1:**
- Procesamiento EPUB (29 páginas): ~3 segundos (mejora 5x)
- Procesamiento PDF (150 páginas): ~10 segundos (mejora 3-4x)

**Objetivos Fase 4B.5:**
- Calidad de audio: Nivel audiolibro profesional
- Latencia TTS: <100ms para inicio de reproducción
- Almacenamiento: ~1-5MB por hora de audio pre-generado

*Este es un documento vivo y será actualizado a medida que el proyecto evolucione.*

