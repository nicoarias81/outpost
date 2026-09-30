# Validación 0.2.0 · solo emulador · 29 de septiembre de 2026

> Historical record: observations, proposals and commands below describe the recorded release/session. For Outpost 0.12 use [current state](current-state.md), [handoff](handoff.md) and [current validation](validation-0.12.md). Original results and language are preserved.

## Implementación

Motor llama.cpp compilado desde la revisión fijada en `llama-revision.txt`, enlazado en la app mediante JNI. Inferencia CPU dentro del proceso Android: no hay servidor de inferencia en el ordenador ni API remota.

El APK contiene solamente bibliotecas x86_64. La ejecución se limita al emulador propio `Brujula35`, Android 15/API 35, AOSP sin Google APIs, cuatro núcleos y 4 GiB configurados. Los scripts rechazan seriales de teléfonos físicos; la prueba de generación comprueba además `ro.kernel.qemu=1`.

## Controles y pruebas

- Compilación de APK y pruebas, más Android Lint.
- Modo avión activado, Wi-Fi y datos desactivados. `INTERNET` ausente del APK, comprobado por PackageManager.
- Modelo fijado por revisión, tamaño y SHA-256. La importación no entrega un archivo al parser nativo hasta verificarlo; un archivo incorrecto no reemplaza el modelo existente.
- Recuperación de los pasajes antes de generar. Sin coincidencias no se ofrece generación. Con coincidencias parciales todavía puede faltar evidencia: no hay un detector semántico completo.
- Streaming UTF-8 sin usar JNI modified UTF-8 para el texto. Límite de contexto de 2048 tokens, máximo de 192 tokens de salida en UI y 120 segundos para la operación completa.
- Cancelación antes de empezar, cancelación durante decodificación y consulta posterior con contexto nuevo. Una petición cancelada no cancela las posteriores.
- Validación de números de referencias, incluyendo números fuera de rango y demasiado largos. No implica comprobación factual de la afirmación.
- Prueba del botón de generación de la Activity y captura de su resultado real.

Los archivos de evidencia contienen los resultados efectivos. No se editan las respuestas del modelo para mejorar su presentación.

## Comparación inicial del modelo

Se probó primero Qwen2.5 0.5B Instruct Q4_K_M, 491400032 bytes. La integración funcionó, pero en la pregunta sobre kW y kWh produjo una primera frase incorrecta que agrupaba potencia y energía como magnitudes medidas en vatios. También omitió citas. Ajustar las instrucciones consiguió referencias en ese caso, pero no eliminó el error conceptual; la respuesta sobre GPS siguió sin citas.

Se conservan ambos registros: [instrucción inicial](../evidence/0.7-before-outpost/generation-baseline.json), [instrucción revisada con 0.5B](../evidence/0.7-before-outpost/generation-0.5b.json) y [captura de 0.5B](../evidence/0.7-before-outpost/generation-0.5b.png). El modelo seleccionado para la siguiente prueba es Qwen2.5 1.5B Instruct Q4_K_M, 1117320736 bytes, con metadatos en `model-lock.json`.

Las preguntas son las mismas y el corpus es el mismo. Es una comparación exploratoria muy pequeña, no una evaluación independiente ni una prueba del umbral de calidad del concurso.

## Resultado de la prueba con 1.5B

Compilación correcta y Android Lint sin incidencias. Se superaron **42 comprobaciones de recuperación/UI** y **17 comprobaciones funcionales de generación**, dentro del emulador. Estos contadores no son pruebas de precisión factual.

| Consulta con Qwen 1.5B | Tokens de entrada / salida | Primer token | Total |
|---|---:|---:|---:|
| Diferencia kW/kWh, motor directo | 337 / 73 | 17,3 s | 25,0 s |
| GPS, después de cancelar otra consulta | 414 / 23 | 25,6 s | 28,1 s |
| Diferencia kW/kWh, botón de la app | 337 / 73 | 21,9 s | 30,7 s |

La cancelación solicitada al tercer token terminó con tres tokens y estado de cancelación. La siguiente consulta funcionó. Lectura de PSS al finalizar: **1172374 KiB**, aproximadamente 1,12 GiB; no es un pico de memoria.

Revisión manual de calidad: 1.5B añadió `[1]` en las dos preguntas y respondió correctamente la idea básica de recepción de señales GPS. En energía calculó correctamente 100 W durante tres horas = 300 Wh = 0,3 kWh, pero su definición de energía sigue siendo imprecisa (la relaciona con «se mide en el tiempo»), no explica limpiamente la distinción de unidades y cita `[1]` para un ejemplo que aparece en el segundo pasaje. **El problema de calidad no está resuelto.** El modelo queda disponible para pruebas de integración, no como asistente de investigación fiable.

No se han corregido manualmente sus respuestas ni se han sustituido por texto predefinido. Los registros de ambos modelos permiten continuar la evaluación en el emulador.

Los mensajes de error del backend durante la prueba de cancelación corresponden a la interrupción intencional de `llama_decode`; las pruebas comprueban que la consulta posterior se ejecuta.

## Evidencia y límites

- [Pruebas de recuperación y UI](../evidence/0.7-before-outpost/0.2/checks.json).
- [Pruebas de generación y respuestas completas](../evidence/0.7-before-outpost/0.2/generation-checks.json).
- [Captura de generación](../evidence/0.7-before-outpost/0.2/generation.png).
- `firstTokenMs` incluye preparación, carga del modelo cuando procede y evaluación del prompt. La caché de archivos puede estar caliente.
- `totalMs` es tiempo de pared de la operación. Una consulta cancelada no es una medición comparable a una respuesta completa.
- `processPssKiBAtEnd` es una lectura puntual al terminar, **no el máximo de memoria** y tampoco la RAM total de Android.
- Ninguna cifra del emulador predice rendimiento, consumo o temperatura de un Pixel.
- El selector de archivos con distintos proveedores y los cambios de orientación durante inferencia requieren más pruebas de interacción. Se prueba la clase de importación que utiliza la UI.
- No se ha probado ni instalado esta versión en teléfonos físicos. No hay artefacto ARM64, evaluación GrapheneOS ni candidatura publicada.

## Reproducir

Preparar las dependencias según el README. Con el emulador arrancado, ejecutar `scripts/build.ps1 -Offline`, `scripts/test-emulator.ps1` y `scripts/test-generation.ps1`. El último script copia el modelo previamente descargado al emulador y lo importa; no conecta el emulador a internet.

