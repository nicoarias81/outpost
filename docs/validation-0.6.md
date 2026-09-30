# Brújula 0.6 · experimentos derivados de Strata

> Historical record: observations, proposals and commands below describe the recorded release/session. For Outpost 0.12 use [current state](current-state.md), [handoff](handoff.md) and [current validation](validation-0.12.md). Original results and language are preserved.

Se probaron las tres hipótesis prioritarias del [análisis de Strata](strata-review.md): calibración, cálculo de varios tokens y caché de prefijos. En este emulador se conservan cuatro hilos y lotes de 128, se activa el kernel de hasta cuatro tokens de entrada y se reutilizan contextos compatibles. No se cambiaron los pesos, el prompt de la app, su muestreo ni el plazo de 120 segundos.

Toda inferencia y prueba numérica se ejecutó en Android AOSP API 35 x86_64, con cuatro CPU lógicas y 4 GiB configurados, modo avión y Wi-Fi/datos desactivados. No se ejecutaron modelos en el host ni en teléfonos físicos. El APK sigue siendo x86_64; compilar objetos ARM64 no valida su ejecución en ARM.

## 1. Calibración: se conserva la configuración anterior

Se separaron tiempos de carga, preparación, lectura del prompt y generación. Se utiliza una ficha **ficticia** de una bomba de riego, 304 tokens de entrada y un presupuesto fijo de 32 tokens de salida. Las respuestas se cortan deliberadamente en esta medición; no son respuestas completas de una evaluación de calidad.

Se exploraron dos, tres y cuatro hilos para generación y prefill, y lotes de 32, 64, 128 y 256. El candidato se confirmó contra la base en tres parejas alternadas: ganó solo **0,27 %** en la mediana total, por debajo del umbral del 5 %. Se conservan **4 hilos de generación, 4 de lectura y lote 128**. Dos hilos de lectura aumentaron el prefill de unos 23 a 43 segundos. [16 llamadas registradas, incluido calentamiento](../evidence/0.7-before-outpost/strata/runtime-calibrate.json).

El perfil se guarda por huella del sistema, API, ABI, capacidades y número de CPU lógicas, versión de la app/motor y SHA-256 del modelo. Otro dispositivo o revisión usa la configuración inicial. La calibración se lanza mediante el script de desarrollo, no automáticamente al abrir la app.

## 2. Kernel que reutiliza pesos entre tokens

[q2_batch.c](../app/src/main/cpp/q2_batch.c) utiliza el mismo bloque de pesos Q2_0 g64 desempaquetado para varias columnas de activaciones Q8_0. Conserva el orden de acumulación por salida y no habilita FMA. Se integra con `--wrap=ggml_compute_forward_mul_mat_tiled`; llama.cpp permanece sin modificaciones en la revisión fijada.

La ruta comprueba CPU, tipos, dimensiones, strides, espacio de trabajo y modo de referencia. Cuantiza activaciones con la función de GGML, sincroniza los trabajadores y distribuye filas entre ellos. Casos incompatibles y generación de un único token conservan la ruta anterior. **Procesar cuatro tokens del prompt no significa generar cuatro tokens de respuesta por paso.**

| Agrupación | Mediana de lectura | Mediana total |
|---|---:|---:|
| Un token por producto, base 0.5.1 | 22,951 s | 27,256 s |
| Dos tokens | 19,786 s | 24,070 s |
| Cuatro tokens | 17,646 s | 21,956 s |

Tres rondas en orden rotativo, calentamiento excluido, mismo prompt, 32 tokens de salida, greedy y caché desactivada. Cuatro tokens reducen **23,1 % el prefill y 19,4 % el total**. Coincidieron la huella de los logits iniciales y el texto. La generación posterior permanece prácticamente igual. [Resultados](../evidence/0.7-before-outpost/strata/runtime-batch.json).

Se compararon **7932 valores con igualdad de bits**, incluidos grafos GGML reales, lotes impares, colas de uno a cuatro tokens y uno, dos y cuatro hilos. El modelo completo confirmó el uso de la ruta nueva. Se conserva además el control de **16.241 vectores** del producto escalar anterior. [Kernel agrupado](../evidence/0.7-before-outpost/optimization/kernel-batch.json) · [producto escalar](../evidence/0.7-before-outpost/optimization/kernel-numeric.json).

## 3. Caché con fronteras de lote estables

La sesión mantiene un único contexto de hasta 2048 tokens y una copia de los logits finales del prompt, unos 0,6 MB para este vocabulario. El sampler se reinicia en cada petición; las respuestas previas se eliminan de la memoria de atención antes de la siguiente consulta. No se añade conversación implícita.

- **Entrada idéntica:** conserva el KV del prompt y muestrea el primer token con los logits guardados mediante la API pública del sampler. Después genera normalmente.
- **Prefijo parcialmente común:** reutiliza únicamente bloques completos que coinciden exactamente en tokens, manteniendo las fronteras de lote de una ejecución desde cero.
- **Cambio de modelo/configuración, cancelación, error o liberación:** descarta el contexto incompatible. Se cubrieron también ida y vuelta a Qwen y al revisor Kev.

La primera implementación recalculaba solo el último token de una entrada idéntica. En la interfaz, esa evaluación produjo logits y redacción diferentes, aunque los controles breves iniciales coincidían. Se conservó el [diagnóstico desfavorable](../evidence/0.7-before-outpost/strata/cache-initial-ui-mismatch.json). Esa variante se sustituyó; sus [medidas iniciales](../evidence/0.7-before-outpost/strata/runtime-cache-initial.json) **no son los resultados finales**.

Con la corrección, la pregunta sobre el filtro tiene 293 tokens de entrada y la misma respuesta en estas tres variantes:

| Consulta | Tokens reutilizados | Primer token emitido | Tiempo total |
|---|---:|---:|---:|
| Sin caché | 0 | 27,259 s | 30,253 s |
| Otra pregunta sobre la misma ficha | 256 | 4,568 s | 7,105 s |
| Repetición idéntica | 293 | 0,005 s | 3,013 s |

Se verificaron texto y huella de logits iniciales contra la ejecución sin reutilización. Cambiar F-28 por F-91 produjo F-91, igual que desde cero; en esa modificación no queda ningún bloque completo anterior al cambio que se pueda aprovechar. Pasaron **19 llamadas de control**, además del rechazo de una entrada excesiva y la transición al revisor. [Evidencia final](../evidence/0.7-before-outpost/strata/runtime-cache.json).

Si la recuperación devuelve otras fuentes u otro orden, puede reutilizarse poco o nada. Las latencias absolutas variaron durante la sesión: esta fase tiene otra latencia base que la medición del kernel. No se multiplican las aceleraciones entre sí ni se extrapolan a los Pixel.

## Memoria e interfaz

Las lecturas PSS posteriores de la pregunta compartida fueron **1496272 KiB con caché frente a 1184160 KiB sin ella**, unos **305 MiB adicionales**. Son muestras posteriores, no picos de inferencia. Los pesos no se duplican; se retienen KV y buffers entre consultas.

La app libera el contexto al pasar a segundo plano y ante su callback de reducción de memoria. Si Android informa `lowMemory` antes de una consulta, no retiene el contexto después. Se verificó el callback invocándolo desde la instrumentación; no se provocó presión real del sistema. Un único grafo se ejecuta por proceso y los parámetros del kernel permanecen fijos mientras sus trabajadores están activos.

En la interfaz, una respuesta de 105 tokens emitió el primero a los **26,362 s** desde cero y a los **5 ms** al repetirla. El total fue **56,098 s frente a 28,391 s**: escribir toda la respuesta sigue costando tiempo. Coincidieron texto y huella de logits. Tras liberar memoria volvió a empezar sin tokens reutilizados y conservó la respuesta.

[Respuesta inicial](../evidence/0.7-before-outpost/0.6/bonsai-bonsai4.png) · [repetición](../evidence/0.7-before-outpost/0.6/bonsai-warm-bonsai4.png) · [registro](../evidence/0.7-before-outpost/0.6/bonsai-warm-bonsai4.json) · [callback de memoria](../evidence/0.7-before-outpost/0.6/bonsai-trim-bonsai4.json) · [perfil seleccionado](../evidence/0.7-before-outpost/0.6/models.png).

## Controles orientados al campo: calidad separada de velocidad

Se compararon base y perfil optimizado sin caché, con muestreo de la app y semilla 42, sobre registros ficticios. Una pareja por caso; todos conservaron el texto.

La primera consulta de esa serie incluye la carga del modelo. Esta tabla complementa la comparación rotativa del kernel; por sí sola no aísla todos los efectos de cachés, orden y carga del host.

| Contexto | Base / optimizado, total | Revisión del contenido |
|---|---:|---|
| Viajero: equipaje antes del check-in | 22,45 / 19,34 s | **Falla:** confunde equipaje con una mención a restaurantes y se corta a 96 tokens. |
| Granjero: identificar un repuesto | 26,51 / 21,24 s | Identifica F-28 con la fuente. |
| Ingeniero: registro de autorización | 19,14 / 16,95 s | Reconoce que no hay autorización ni estado eléctrico confirmado. |
| Montañero: dos elevaciones | 19,50 / 17,12 s | Calcula 250 m; llama «desnivel» a las elevaciones absolutas. |
| Conductor: referencia del manual | 12,51 / 10,23 s | Indica la página 42 sin inventar diagnóstico. |

El marcador automático `10:00` aparece incluso en la respuesta incorrecta del viajero. `PASS runtime missions` certifica la ejecución y la paridad, **no cinco tareas resueltas correctamente**. Estas fichas no validan decisiones reales de campo. [Respuestas](../evidence/0.7-before-outpost/strata/runtime-missions.json) · [revisión de calidad](../evidence/0.7-before-outpost/strata/mission-review.json).

## Reproducción y alcance

Compilación y Android Lint sin incidencias. Pasaron las **64 comprobaciones de selección, 48 funcionales y 17 de generación con Qwen**, además de las verificaciones numéricas, de caché, interfaz y memoria descritas arriba. Bonsai 4B quedó seleccionado con el perfil medido. Se revisaron las capturas y se confirmó modo avión activado y Wi-Fi apagado. [Comprobaciones funcionales](../evidence/0.7-before-outpost/0.6/checks.json) · [generación](../evidence/0.7-before-outpost/0.6/generation-checks.json) · [selección de CPU](../evidence/0.7-before-outpost/optimization/kernel-dispatch.json).

Con modelos y dependencias preparados, ejecutar secuencialmente:

```powershell
pwsh -File scripts/build.ps1 -Offline
pwsh -File scripts/check-native-portability.ps1
pwsh -File scripts/test-kernel.ps1 -Phase batch
pwsh -File scripts/test-runtime.ps1 -Phase calibrate -SkipInstall
pwsh -File scripts/test-runtime.ps1 -Phase batch -SkipInstall
pwsh -File scripts/test-runtime.ps1 -Phase cache -SkipInstall
pwsh -File scripts/test-runtime.ps1 -Phase missions -SkipInstall
pwsh -File scripts/test-bonsai.ps1 -UiOnly -UiProfile bonsai4 -SkipInstall -KeepSelected
```

Los scripts rechazan teléfonos físicos. Para dejar seleccionado el modelo sin repetir generación: `scripts/test-bonsai.ps1 -SelectOnly -UiProfile bonsai4 -SkipInstall`. El perfil medido pertenece a esta instalación; no se distribuye como recomendación universal dentro del APK.

La especulación/MTP de Strata queda pendiente: necesita otro ensayo de aceptación, coste, memoria y paridad del muestreo. Este Bonsai no incluye la capa MTP de Strata. Tampoco se añadieron GPU/NPU, kernels ARM/VNNI, cartografía ni adaptadores de conocimiento. El fallo del viajero y las citas imperfectas son trabajo de calidad pendiente.

[APK 0.6 para emulador x86_64](../dist/brujula-0.6.0-emulator-debug.apk) · [SHA-256](../dist/brujula-0.6.0-emulator-debug.apk.sha256).
