# Validación 0.1.0 · 29 de septiembre de 2026

## Entorno

Emulador propio `Brujula35`, serial `emulator-5580`, AOSP Android 15/API 35, x86_64, 4 GiB configurados. No es una emulación del procesador Tensor G5. Imagen sin Google APIs. Datos propios en `.local/avd`.

Modo avión activado, Wi-Fi y datos desactivados antes de instalar y ejecutar las pruebas. Se verificaron `airplane_mode_on=1` y `wifi_on=0`. El APK no solicita `android.permission.INTERNET`; esa ausencia se comprueba también mediante PackageManager.

## Resultados

- Compilación de app y APK de instrumentación correcta con Gradle 8.11.1, AGP 8.9.2 y JDK 21, usando caché local y `--offline`.
- Android Lint: **sin incidencias**.
- **42 comprobaciones instrumentadas correctas**. Registro completo: [`checks.json`](../evidence/0.7-before-outpost/0.1/checks.json).
- Incluyen 20 consultas de recuperación conocidas (18 con fuente esperada entre los primeros tres pasajes y dos sin coincidencias); acentos, puntuación y sintaxis; ingestión, fragmentación y persistencia de documentos; rechazo de datos vacíos, binarios o demasiado grandes; búsqueda de un documento próximo a 1 MiB; carga de metadatos sin cuerpos completos; lanzamiento de Activity, botón de búsqueda y lector de fuente.
- Biblioteca y búsqueda funcionan sin un modelo instalado. Seis notas iniciales, no datos externos descargados.
- Capturas reales de la app revisadas: [inicio](../evidence/0.7-before-outpost/0.1/home.png), [búsqueda](../evidence/0.7-before-outpost/0.1/search.png), [lector de fuente](../evidence/0.7-before-outpost/0.1/source.png).

Los documentos de prueba se crean en una base separada, se cierran y se eliminan al finalizar; no se añaden a la biblioteca del usuario.

## Límites de lo verificado

- No hay inferencia ni evaluación de un modelo de lenguaje. Estas pruebas no demuestran razonamiento, comparación o síntesis.
- Las consultas están diseñadas para el corpus de ejemplo; no son un benchmark independiente de investigación ni equivalen al criterio «50 %» de la convocatoria.
- No se ha probado aún en Pixel 3 XL, Pixel 10 Pro ni GrapheneOS. La compatibilidad con API 28 se comprobó estáticamente mediante Lint, no ejecutando ese sistema.
- Se prueba la ingestión y persistencia del documento en la capa de datos. El selector de archivos y sus distintos proveedores necesitan una prueba manual adicional en el teléfono.
- No se ha validado consumo sostenido, memoria máxima de un LLM, autonomía, temperatura, corpus de gran tamaño ni instalación por un usuario externo.
- La recuperación léxica examina hasta 500 pasajes candidatos y devuelve ocho. Es una implementación inicial para colecciones pequeñas; no ofrece todavía ranking semántico ni garantías de relevancia a gran escala.

## Reproducir

Consultar requisitos en [README](../README.md). Ejecutar `scripts/build.ps1 -Offline` y, con el emulador ya arrancado, `scripts/test-emulator.ps1`. El modo offline de Gradle requiere que las dependencias estén previamente almacenadas en caché.

El APK de `dist/` está firmado con la clave de desarrollo local. Su SHA-256 se entrega junto al archivo. No es una versión de distribución pública y no se ha presentado al concurso.

