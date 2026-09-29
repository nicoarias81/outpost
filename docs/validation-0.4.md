# Brújula 0.4 · generadores ternarios · solo emulador

## Hipótesis y compatibilidad

Los modelos [Ternary Bonsai de PrismML](https://prismml.com/news/ternary-bonsai) usan valores de peso escalados con tres estados. Esto reduce el tamaño de los pesos y permite considerar más parámetros bajo un presupuesto de memoria, pero no garantiza por sí solo menor latencia o mejores respuestas.

Se comprobaron los archivos oficiales y el código del motor instalado. La revisión `86ea01d05ec237f89b78b41c8c1ee0f908141ac7` de llama.cpp usa el tipo GGML 42/Q2_0 con **grupos de 64**, por lo que se eligieron los GGUF `Q2_0_g64`. No se usaron los antiguos Q2_0 g128 ni PQ2_0 de otros forks. No se cambió el motor por un fork ni se añadió un servidor de inferencia.

En este formato cada bloque contiene 64 códigos de 2 bits y una escala FP16: `(64 × 2 + 16) / 64 = 2,25 bits` efectivos por peso de matriz. El mínimo informativo `log2(3) ≈ 1,585` no es el tamaño del archivo real. Normas F32, vocabulario y metadatos añaden espacio.

| Perfil | Archivo de pesos verificado |
|---|---:|
| Qwen2.5 1.5B Q4_K_M | 1117320736 bytes |
| Ternary Bonsai 1.7B Q2_0 g64 | 490163968 bytes |
| Ternary Bonsai 4B Q2_0 g64 | 1137806656 bytes |

El Bonsai de 1.7B ocupa aproximadamente **56 % menos** que el Qwen de referencia. El 4B ocupa aproximadamente lo mismo que ese Qwen, aunque tiene más parámetros. Son comparaciones de archivos, no de RAM máxima.

## Cambios de la app

Selector de generador en Estado, persistencia de la elección y almacenamiento separado por perfil. Las importaciones están limitadas a los archivos fijados en `model-lock.json` y `bonsai-lock.json`, verificados mediante tamaño y SHA-256. Los modelos anteriores se conservan.

Las plantillas oficiales de ambos Bonsai incluyen un prefijo de asistente que abre y cierra un bloque `think` vacío. La API básica de formato ChatML no ejecuta esa parte de Jinja, por lo que la integración reproduce explícitamente ese sufijo. Se guardan las respuestas generadas completas y se registra si aparecen etiquetas de razonamiento.

Las pruebas se ejecutan en el mismo emulador AOSP Android 15 x86_64, 4 GiB configurados, cuatro hilos de CPU, sin red ni permiso INTERNET. Los pesos se descargan previamente al ordenador y se copian al emulador; no hay inferencia en el host. Se eliminaron únicamente copias temporales de instalación verificadas para liberar espacio, manteniendo los modelos instalados.

## Método de comparación

Cuatro consultas conocidas de desarrollo por modelo: diferencia kW/kWh, funcionamiento del GPS, comparación GPS/brújula y un porcentaje exacto ausente de las fuentes. Mismos textos y selección de pasajes, plantilla apropiada de cada modelo, greedy, contexto de 2048 tokens, máximo 192 tokens de salida y límite de 120 segundos. Una pasada por caso, no un benchmark estadístico ni una comparación con modelos de frontera.

`firstTokenMs` incluye carga/preparación y lectura del prompt. `stopReason=0` indica fin de generación, `1` límite de tokens, `2` cancelación y `3` timeout. Los resultados con cortes, errores o texto vacío permanecen en el registro. «PASS comparison» solo significa que el ejecutor terminó y guardó las pruebas; no que un modelo fuese correcto, rápido ni que completase todas las respuestas.

Se toman lecturas PSS al terminar cada caso; su máximo **no es el pico de memoria durante inferencia**. El orden de modelos y la caché del sistema pueden afectar a los tiempos.

## Resultados observados

Compilación correcta, Android Lint sin incidencias y **46 comprobaciones funcionales de biblioteca, UI, persistencia y perfiles superadas**. Se ejecutaron doce consultas comparativas, cuatro por modelo. Ocho terminaron y cuatro agotaron el presupuesto; no se cuentan esos cuatro cortes como éxitos de generación.

Además, se verificó el botón de generación de Bonsai 1.7B en la interfaz: produjo 120 tokens en 85,0 segundos. Se pulsó el selector visible para volver a Qwen y se comprobó la preferencia persistida `qwen15`. Esta prueba de UI se registra aparte de la comparación de cuatro consultas.

| Modelo | Tiempo hasta primer token | Tiempo total | Máximo de lecturas PSS posteriores | Consultas completas |
|---|---:|---:|---:|---:|
| Qwen 1.5B Q4_K_M | 18,6–25,5 s | 27,7–31,2 s | 1140 MiB | 4/4 |
| Bonsai 1.7B Q2_0 g64 | 54,8–87,5 s | 83,5–118,7 s | 546 MiB | 4/4 |
| Bonsai 4B Q2_0 g64 | No llegó al primero | Corte a ~120 s | 1158 MiB | 0/4 |

Las lecturas PSS de esta tabla no son máximos durante ejecución. Los archivos pesan 1,117 GB, 0,490 GB y 1,138 GB respectivamente; se usan GB decimales para almacenamiento y MiB binarios para PSS.

Qwen completó las cuatro consultas en unos 28–31 segundos cada una. Bonsai 1.7B las completó en 84, 115, 119 y 116 segundos. En la consulta de energía, Bonsai 1.7B llegó al primer token a los 54,8 segundos; solo 0,34 segundos correspondieron a la carga del modelo. La lectura/cálculo del prompt domina esa demora.

La menor memoria no solucionó los errores: Bonsai 1.7B asignó una afirmación sobre precisión GPS a un pasaje que no la respalda y atribuyó al GPS el uso del campo geomagnético y del acelerómetro que la fuente describe para orientación. En la pregunta sobre eficiencia exacta recitó definiciones sin dejar clara la ausencia del porcentaje solicitado. Qwen también tiene fallos documentados; estas pruebas no justifican etiquetar ninguno como fiable.

Bonsai 4B se cargó, pero las cuatro consultas finalizaron por timeout con cero tokens de salida. En la primera, la carga tomó 2,7 segundos; el resto del presupuesto se consumió antes de empezar a generar. **No se ha evaluado su calidad**, porque no hubo respuestas que revisar. No se amplió el tiempo límite para ocultar el fallo de usabilidad de esta configuración.

## Interpretación del rendimiento

La compilación actual prioriza una base x86_64 y desactiva AVX/AVX2/FMA/F16C. Además, el kernel Q2_0 examinado usa el cálculo genérico en esta ruta x86; no se incorporó una optimización específica de ternarios. Existe una [propuesta VNNI para Q2_0](https://github.com/ggml-org/llama.cpp/pull/26348), pero requiere instrucciones que nuestro emulador no anuncia. Sus cifras de otra CPU no se extrapolan aquí.

Estos resultados describen este emulador y esta compilación. No demuestran que un modelo ternario deba ser lento en ARM/NEON, GPU u otro motor, ni permiten predecir el rendimiento del Pixel. No se probó el 8B ni el 27B.

## Evidencia

- [Qwen de referencia](../evidence/0.7-before-outpost/0.4/bonsai-qwen15.json).
- [Bonsai 1.7B](../evidence/0.7-before-outpost/0.4/bonsai-bonsai17.json).
- [Bonsai 4B](../evidence/0.7-before-outpost/0.4/bonsai-bonsai4.json).
- [Resumen numérico de los tres perfiles](../evidence/0.7-before-outpost/0.4/bonsai-summary.json).
- [Comprobaciones funcionales](../evidence/0.7-before-outpost/0.4/checks.json).
- [Generación de Bonsai en la interfaz](../evidence/0.7-before-outpost/0.4/bonsai-bonsai17.png) y [registro](../evidence/0.7-before-outpost/0.4/bonsai-ui-bonsai17.json).
- [Selector de modelos con Qwen seleccionado](../evidence/0.7-before-outpost/0.4/models.png).
- Perfiles, URLs, revisiones y hashes: `bonsai-lock.json`.
- Preparación: `scripts/prepare-bonsai.py`; comparación: `scripts/test-bonsai.ps1`.

Los modelos siguen siendo experimentales. Qwen queda seleccionado para el uso habitual del prototipo; ambos Bonsai permanecen instalados y disponibles en Estado. El ahorro de memoria es medible, pero antes de proponer Bonsai como sustituto hay que mejorar el cálculo Q2_0 en esta plataforma y repetir pruebas de calidad. El selector no activa una aprobación automática del texto ni del revisor Kev.
