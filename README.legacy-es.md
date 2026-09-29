<!-- Historical Spanish snapshot of the 0.7.0 README. Current documentation: README.md and docs/index.md. -->

# Brújula · prototipo Android offline

Nombre provisional. Primera etapa de una app de investigación local para la convocatoria [poidh #31](https://poidh.xyz/mainnet/bounty/31).

Diseño en discusión: [dos dominios, inferencia y conocimiento local](docs/two-domain-design.md), al servicio de los [casos de uso en el terreno](docs/field-use-cases.md). Las integraciones propuestas en ese diseño no están todas implementadas.

[APK 0.7 para emulador x86_64](dist/brujula-0.7.0-emulator-debug.apk) · [Especulación y MTP: evaluación](docs/validation-0.7.md) · [Kernel y caché de Strata](docs/validation-0.6.md) · [Selección de kernels](docs/device-runtime.md) · [Evaluación de Kev](docs/validation-0.3.md)

## Estado 0.7.0 · solo emulador

- App Android nativa (Java 17 + C++/JNI), mínimo API 28. Esta compilación incluye únicamente x86_64 y se prueba en el emulador AOSP Android 15.
- Búsqueda real de pasajes con SQLite FTS4, normalización de acentos y ranking léxico sencillo.
- Lectura offline del documento completo y resaltado del pasaje citado.
- Seis notas didácticas de ejemplo, con referencia y fecha de incorporación visibles.
- Importación persistente de archivos `.txt`, `.md` y `.markdown` UTF-8 de hasta 1 MiB mediante el selector de documentos de Android. Los proveedores externos del selector podrían necesitar conexión; usar archivos locales para pruebas offline.
- Sin permisos de internet, almacenamiento amplio, localización ni otros; sin Google Play Services, analítica o SDKs externos de ejecución.
- 20 casos de recuperación reproducibles desde Estado, más pruebas instrumentadas de persistencia, validación y UI.
- Generación real con llama.cpp dentro del proceso Android, sin servidor de inferencia en el ordenador.
- Modelo de prueba Qwen2.5 1.5B Instruct Q4_K_M: 1,12 GB, revisión y SHA-256 fijados. Importación con copia en bloques y validación del hash antes de cargar.
- Selector de generador en Estado: Qwen2.5 1.5B, Ternary Bonsai 1.7B (490 MB) o Ternary Bonsai 4B (1,14 GB). Se conservan en archivos independientes; cambiar de modelo no borra los anteriores. Solo se aceptan los archivos y hashes fijados.
- Bonsai usa los archivos **Q2_0_g64**, compatibles con la revisión existente de llama.cpp. No se usa PQ2_0 ni el antiguo Q2_0 g128. Se respeta el sufijo fijo de respuesta sin razonamiento intermedio incluido en sus plantillas oficiales.
- Kernel propio Q2_0 × Q8_0 con AVX2/F16C, seleccionado según CPU y soporte del sistema operativo; fallback al kernel escalar original. La dependencia llama.cpp permanece sin modificar. Estado muestra la ruta disponible. Esta optimización es x86_64; no se ha implementado ni medido su equivalente ARM.
- Detección separada de la selección: AVX-VNNI y AVX-512 VNNI en x86; NEON, DotProd e I8MM en ARM64. El registro distingue compatibilidad, implementación incluida y habilitación. VNNI y las variantes ARM siguen como candidatos pendientes. Se verificó la lógica con 64 casos sintéticos en el emulador y se compilaron los cuatro módulos C propios para ambas ABI; el APK sigue siendo únicamente x86_64.
- Kernel de prefill que reutiliza pesos entre hasta cuatro tokens de entrada. El perfil medido de este emulador usa cuatro hilos, lote 128 y grupos de cuatro tokens; redujo un 23 % la lectura en el control comparativo 0.6. La ruta normal genera un token cada vez.
- Especulación de contexto opcional para Bonsai 4B, apagada por defecto. Busca sufijos de 8–16 tokens después de ocho tokens emitidos, propone hasta tres y verifica con el sampler del objetivo. Descarta el KV rechazado, respeta EOS/cancelación/límites y vuelve a la ruta normal si tres ventanas no justifican su coste. No usa otro modelo ni una cabeza MTP.
- En el control largo final redujo un 8,5 % la fase de generación. El beneficio depende del texto. La evaluación por lotes puede cambiar logits y redacción incluso con la misma semilla; no se promete igualdad literal universal ni veracidad. La inspección del 4B instalado informa cero capas MTP.
- Caché en RAM de bloques completos idénticos y logits para repeticiones exactas. Reinicia el sampler y elimina las respuestas anteriores de la atención. Libera el contexto al pasar a segundo plano, ante su callback de memoria o tras cancelación/error; no lo retiene si Android indica memoria baja.
- Prompt de fuentes más compacto y muestreo para Bonsai: top-k 20, top-p 0,8, temperatura 0,7 y semilla 42. Qwen conserva greedy. Una salida formada solo por referencias se indica como ausencia de explicación.
- Borrador en streaming, cancelación incluso antes de empezar, y límites de 192 tokens en UI y 120 segundos incluyendo carga y lectura del prompt. Reutilizar el prefijo no añade historial de conversación implícito.
- Referencias numéricas comprobadas contra los pasajes suministrados. Se señalan referencias ausentes o fuera de rango. **Esta comprobación no verifica que una afirmación sea correcta ni esté respaldada.**
- Revisor opcional **Kev 0.8B Q8_0**, inspirado en Jev: clasificador bilineal sobre representaciones internas, una evaluación de la secuencia sin generar texto. Se revisa exclusivamente la primera frase del borrador contra hasta tres pasajes. Una frase puede contener varias afirmaciones y sigue siendo un caso difícil.
- Se muestran tres puntuaciones orientativas: apoyo, contradicción y evidencia insuficiente. **El revisor también puede aceptar errores.** No modifica ni oculta el borrador, no concede un sello de veracidad y no verifica el documento completo.
- Generador y revisor se cargan alternativamente en la misma sesión nativa. El trabajo de inferencia tiene su propia cola para mantener disponible la lectura/búsqueda local.

Los modelos sirven para validar la integración. Pueden omitir referencias, redactar afirmaciones incorrectas o aprobarlas, incluso con buenas fuentes. La biblioteca de seis notas y esta versión no demuestran el nivel de investigación del concurso. Se conservan los resultados desfavorables en las evidencias.

Bonsai 4B pasó de cortes a 120 segundos sin texto en 0.4 a completar las consultas de control en 0.5. En 0.6 una segunda pregunta sobre la misma ficha pasó de 30,3 a 7,1 segundos y una repetición exacta a 3,0 segundos en el control final. La caché retuvo unos 305 MiB adicionales en lecturas PSS posteriores. Los tiempos dependen de la carga del host y del contenido. El [informe 0.6](docs/validation-0.6.md) separa las mejoras y conserva tanto un fallo del caso de viajero como la corrección de una primera implementación de caché. Bonsai 4B queda seleccionado en el emulador.

## Compilar

Requisitos: JDK 17–23 (probado: 21), Android SDK Platform 35 y Build Tools 35.0.0, Git y Python 3.9+. Gradle 8.11.1, AGP 8.9.2, NDK r28b y CMake 3.22.1 fijados. La preparación nativa incluida está orientada a Windows.

1. Establecer `JAVA_HOME` y crear `local.properties` con `sdk.dir=C:/ruta/al/sdk` (o configurar `ANDROID_HOME`). No versionar rutas locales.
2. Ejecutar `python scripts/prepare-native.py`. Descarga aproximadamente 1,9 GB de herramientas oficiales y pesos, verifica los hashes, obtiene la revisión fijada de llama.cpp y configura `cmake.dir` en `local.properties`. La extracción requiere varios GB adicionales. No sustituye la instalación del SDK/JDK.
3. Ejecutar `python scripts/prepare-judge.py`. Descarga y verifica 814 MB adicionales de pesos Kev, clasificador auxiliar y metadatos; copia los dos archivos auxiliares pequeños a los assets de compilación. El GGUF queda fuera del APK. Para probar los dos Bonsai, ejecutar además `python scripts/prepare-bonsai.py` (1,63 GB adicionales).
4. Ejecutar `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` (Windows: `gradlew.bat`). La primera compilación descarga dependencias Java; después se puede añadir `--offline` con la caché completa.
5. Instalar únicamente en un emulador x86_64. Los scripts de prueba rechazan seriales de teléfonos físicos.

En esta máquina, `pwsh -File scripts/build.ps1 -Offline` reutiliza el JDK, SDK y caché ya presentes en `../work/bug-hunter-toolchain`. Ese atajo local no es un requisito del proyecto.

## Emulador independiente

Se usa Android 15/API 35 **AOSP sin Google APIs**, x86_64, 4 GiB configurados, pantalla 1080×2400. El emulador vive en `.local/avd/Brujula35.avd` y no usa los datos de otros proyectos.

```powershell
pwsh -File scripts/start-emulator.ps1
pwsh -File scripts/build.ps1 -Offline
pwsh -File scripts/test-emulator.ps1
pwsh -File scripts/test-generation.ps1
pwsh -File scripts/test-review.ps1
pwsh -File scripts/test-bonsai.ps1
pwsh -File scripts/test-kernel.ps1 -Phase numeric
pwsh -File scripts/test-kernel.ps1 -Phase dispatch
pwsh -File scripts/test-kernel.ps1 -Phase decoder4
```

Esperar a que termine el arranque antes de probar. El script de pruebas solo acepta un serial `emulator-*`, activa modo avión y desactiva Wi-Fi/datos en ese emulador. Las evidencias se guardan en `evidence/`. No modifica un teléfono físico.

`test-generation.ps1` verifica el modelo local, lo copia al emulador y lo importa usando la misma clase `ModelStore` que utiliza la interfaz. Prueba generación real, cancelación y una consulta posterior, y guarda todas las respuestas y medidas. No habilita la red del emulador.

Uso en la app: buscar un tema y pulsar **Redactar con las fuentes**. El botón **Detener generación** cancela el trabajo. Una consulta sin pasajes no ofrece generación. La primera generación carga el modelo; las siguientes pueden reutilizar pesos y un prefijo compatible mientras la app sigue visible.

Después de generar se puede pulsar **Revisar primera afirmación con Kev**. La app muestra qué frase está revisando y el resultado atribuido al modelo. **Detener revisión** cancela el cálculo. Se usa la temperatura publicada por Kev, pero no se ha validado la calibración en español ni en esta tarea. El registro incluye un falso positivo sobre una respuesta real de nuestro generador.

Para instalar Kev manualmente, seleccionar `kev-0.8b-q8_0.gguf` en **Estado → Importar revisor Kev**. `judge-lock.json` fija publicador, revisión, tamaños y hashes. Su cabeza de decisión, de unos 2 MB, está incluida en el APK y se verifica antes del uso. No es el Jev de TypeSafe y no se llama a su API.

Para importación manual del generador, copiar el GGUF oficial descrito en `model-lock.json` a una carpeta local del emulador y seleccionarlo en **Estado → Importar modelo de prueba**. Se acepta únicamente ese archivo exacto; no cualquier modelo GGUF. El selector puede mostrar proveedores externos: usar un archivo local.

Para Bonsai, elegir primero su perfil en Estado y después importar el archivo correspondiente de `bonsai-lock.json`. Los indicadores de tamaño/estado y el nombre sobre el borrador reflejan el perfil elegido. No se puede importar un archivo de otro perfil por error: tamaño y SHA-256 se verifican antes de sustituir nada.

`test-bonsai.ps1` presupone que el generador Qwen ya se instaló con `test-generation.ps1`. Copia e importa los dos Bonsai y elimina únicamente las copias temporales de instalación creadas por ese script, conservando los modelos instalados. Compara los tres perfiles con cuatro consultas de desarrollo idénticas en contenido, y guarda resultados separados. `-UiProfile bonsai17` o `bonsai4` añade una prueba/captura de la interfaz. `-SkipInstall` reutiliza modelos ya importados. No hay puntuación automática de calidad: los textos, cortes por límite y errores se conservan.

Para repetir la prueba visible del 4B instalado: `scripts/test-bonsai.ps1 -UiOnly -UiProfile bonsai4 -SkipInstall -KeepSelected`. Genera, prueba una repetición con caché y otra después de invocar el callback de memoria, guarda las capturas y comprueba el selector. Con `-KeepSelected` deja elegido el perfil; sin esa opción la prueba simple devuelve la selección a Qwen.

`test-kernel.ps1 -Phase numeric` compara 16.241 vectores contra el kernel escalar, verifica dispatch/fallback y límites de lectura, y mide el producto escalar por separado. `-Phase decoder4` compara la misma generación breve con ambas rutas usando Bonsai 4B ya instalado; `-Phase decoder` usa el 1.7B. Estas pruebas requieren un emulador que anuncie AVX2/F16C. Las evidencias se guardan en `evidence/optimization/`.

`-Phase dispatch` prueba la política con perfiles sintéticos y guarda las capacidades reales. `-Phase batch` comprueba el kernel agrupado y su integración en grafos reales. `scripts/check-native-portability.ps1` compila los cuatro módulos C para x86_64 y ARM64, sin construir un APK ARM completo. El [diseño por dispositivo](docs/device-runtime.md) distingue implementaciones y propuestas.

Para repetir las hipótesis de Strata, `scripts/test-runtime.ps1 -Phase calibrate`, después `-Phase batch`, `-Phase cache` y `-Phase missions`. Cada fase guarda entradas, respuestas y tiempos en `evidence/strata/`; `-SkipInstall` evita reinstalar los APK cuando ya coinciden con la compilación actual. La calibración y agrupación ganadora se guardan para esta combinación de dispositivo y modelo. `test-bonsai.ps1 -SelectOnly -UiProfile bonsai4 -SkipInstall` restaura la selección sin ejecutar otra generación.

Para una ventana interactiva, detener primero este emulador con `adb -s emulator-5580 emu kill` y ejecutar `start-emulator.ps1 -Visible`. Sin `-Visible` corre en segundo plano.

## Alcance de las mediciones

En Estado se puede activar la especulación experimental para Bonsai 4B. El beneficio depende de la tarea y la política final sigue apagada por defecto. `scripts/test-speculation.ps1` ofrece fases `unit`, `benchmark`, `guard`, `lifecycle`, `draft-cost`, `profile`, `audit`, `energy-audit`, `missions` y `ui`, con registros en `evidence/speculation/`. `-SkipInstall` requiere APK instalados que coincidan con la compilación. `scripts/audit-spec-models.py` inspecciona cabeceras y tensores sin ejecutar modelos. El chequeo de portabilidad incluye también el controlador C++ de especulación.

Las comprobaciones de recuperación usan preguntas conocidas sobre un corpus diminuto. No prueban calidad de investigación, ausencia general de alucinaciones, rendimiento del Pixel, autonomía ni el criterio «50 %» del concurso. La RAM del emulador tampoco demuestra el comportamiento de un LLM dentro de un teléfono de 12 GB.

Por instrucción del usuario, el desarrollo y las pruebas de ejecución de esta etapa permanecen en el emulador. No se ha instalado ni probado nada en los Pixel.

## Siguiente etapa

Prioridad actual: [situaciones de uso en el terreno](docs/field-use-cases.md): viajero, granjero, ingeniero de campo, montañero y desplazamiento por carretera. La app actual es una base técnica; aún no resuelve esos recorridos completos. La [consulta de restaurantes](docs/travel-evaluation.md) queda como un subcaso de viaje.

Organización técnica: dos dominios separados por un contrato de evidencia —inferencia intercambiable y base de conocimiento con adaptadores—, definidos en [la propuesta de arquitectura](docs/two-domain-design.md).

Las [ideas de Strata](docs/strata-review.md) se probaron en [0.6](docs/validation-0.6.md) y [0.7](docs/validation-0.7.md): calibración, kernel agrupado, caché y especulación de contexto. Una cabeza MTP compatible y el rendimiento de teléfonos físicos siguen pendientes.

1. Preparar una misión mínima por contexto, con objetivo, documentos/mapas, observaciones, criterios de éxito y errores críticos.
2. Construir la preparación de paquetes y la recuperación de documentación que comparten, conservando fuente, revisión y cobertura.
3. Incorporar contexto corregible, aclaraciones útiles y herramientas locales de cálculo/consulta; mostrar información útil antes de una generación larga.
4. Evaluar resolución de la tarea y manejo de incertidumbre, además de memoria, almacenamiento y latencia. Las preguntas generales anteriores quedan como comprobaciones técnicas.
5. Comparar modelos y revisores sobre esas misiones, manteniendo la ejecución en el emulador. Voz, visión y rendimiento físico de batería/temperatura siguen pendientes.

## Datos y dependencias

`app/src/main/assets/library.json` contiene resúmenes redactados para esta demostración. Cada nota identifica su fuente; no incluye contenido web completo. La fecha es de incorporación, no una garantía de actualidad. Los documentos importados se etiquetan como contenido sin verificar.

Dependencias, licencias, revisiones y hashes: [THIRD_PARTY.md](THIRD_PARTY.md). Las licencias del código nativo y del runtime del NDK se incluyen en `assets/licenses`. Los pesos están separados del APK y se descargan directamente del publicador durante la preparación. La app no puede realizar conexiones de red.

No se ha publicado un repositorio ni se ha presentado una candidatura.

