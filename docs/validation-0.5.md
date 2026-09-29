# Brújula 0.5 · Bonsai 4B con kernel optimizado · solo emulador

Bonsai 4B completa ahora las cuatro consultas de control en **36,9–50,5 segundos**, con primer token en **21,2–33,7 segundos**. En 0.4 las cuatro agotaban 120 segundos antes del primer token. Se mantienen el archivo de pesos de 1.137.806.656 bytes, su SHA-256, contexto de 2048 tokens, máximo de 192 tokens, cuatro hilos y plazo de 120 segundos.

Toda inferencia se ejecutó en el proceso Android del emulador AOSP API 35 x86_64, con 4 GiB configurados, modo avión y Wi-Fi/datos desactivados. No se probó ningún teléfono ni se ejecutó el modelo en un servidor del ordenador. Las consultas anteriores se conservan como controles de rendimiento y regresión; **no sustituyen las misiones de viajero, granjero, ingeniero, montañero y conductor** definidas en [casos de campo](field-use-cases.md).

## Kernel Q2_0 × Q8_0

La ruta x86 utilizada anteriormente calculaba el producto escalar Q2_0 de forma genérica. El nuevo [q2_kernel.c](../app/src/main/cpp/q2_kernel.c) expande códigos de 2 bits mediante AVX2, multiplica por activaciones Q8_0 y aplica las escalas FP16 con F16C. La resta separada del término de activaciones reproduce el valor `código − 1`, incluso para activaciones −128. También reproduce el código reservado 3 si aparece. Se conserva el orden de escalado y acumulación del original y se desactiva la contracción de operaciones flotantes en ese archivo.

La selección comprueba CPUID y XCR0: AVX2, F16C, XSAVE/OSXSAVE y conservación de los registros XMM/YMM por el sistema. Si no están disponibles, llama a la función escalar original. Las instrucciones específicas se limitan a funciones con atributo `target`; no se exige AVX2 al APK completo. El emulador actual no anuncia VNNI; no se aplicó el [PR de VNNI](https://github.com/ggml-org/llama.cpp/pull/26348).

La integración usa `--wrap=ggml_vec_dot_q2_0_q8_0` al enlazar. Se comprobó que la tabla de funciones de GGML realmente pasa por esa ruta. La dependencia llama.cpp continúa en `86ea01d05ec237f89b78b41c8c1ee0f908141ac7`, sin cambios en sus archivos. Esta integración depende de ese contrato interno y requiere revalidación si cambia la dependencia.

## Verificación del cálculo y medidas aisladas

- **16.241 vectores, cero diferencias de bits:** 16.000 casos pseudoaleatorios, 240 casos extremos y un caso al borde de páginas protegidas. Longitudes 0, 64, 128, 576, 4096 y 12288, punteros sin alineación SIMD, activaciones de −128 a 127 y escalas FP16 finitas, incluidos ceros, subnormales y signos negativos.
- Verificación de selección de la ruta rápida y fallback forzado a través de la tabla real de GGML. Canarios de salida intactos y lectura hasta el último bloque sin invadir páginas protegidas.
- Microbenchmark de vectores de 4096 elementos: **2627,6 ns escalar frente a 440,7 ns AVX2**, aproximadamente **5,96×**. Mediana de tres rondas, 20.000 productos por ronda y caché caliente; no equivale a multiplicar por seis la velocidad de toda la app.

Además, se ejecutó el mismo prompt breve, mismos pesos y greedy con ambos kernels. Las dos rutas produjeron exactamente «La energía se mide en vatios hora.»:

| Modelo | Primer token escalar / AVX2 | Tiempo total escalar / AVX2 | Salida idéntica |
|---|---:|---:|---:|
| Bonsai 1.7B | 11,87 / 2,80 s | 15,08 / 3,47 s | 10 tokens |
| Bonsai 4B | 44,26 / 7,69 s | 51,56 / 9,10 s | 10 tokens |

Cada comparación es una sola pareja: escalar primero con carga del modelo, AVX2 después reutilizando los pesos. La carga inicial fue 368 ms y 694 ms respectivamente. Caché, orden y carga del host influyen; no se presenta como un benchmark estadístico ni como rendimiento de ARM. Se conserva toda la [evidencia numérica](../evidence/0.7-before-outpost/0.5/kernel-numeric.json), del [decoder 1.7B](../evidence/0.7-before-outpost/optimization/kernel-decoder.json) y del [decoder 4B](../evidence/0.7-before-outpost/0.5/kernel-decoder4.json).

## Prompt y generación

Con solo el kernel nuevo, el 4B dejó de agotar el plazo, pero las cuatro consultas con el prompt anterior y greedy devolvieron únicamente `[1]`, en 28,8–37,3 segundos. Ese [resultado desfavorable](../evidence/0.7-before-outpost/optimization/bonsai4-kernel-only.json) permanece guardado: terminar rápido no demuestra que se responda a la pregunta.

Se redujeron instrucciones repetidas del prompt, manteniendo las preguntas y pasajes originales, las citas numéricas y la indicación de reconocer información ausente. Bonsai usa ahora top-k 20, top-p 0,8, temperatura 0,7 y semilla 42. Los tres primeros parámetros siguen la [recomendación de Qwen3 para modo sin razonamiento](https://huggingface.co/Qwen/Qwen3-4B); no se afirma que sean óptimos para Bonsai. Qwen2.5 conserva greedy. Las salidas vacías o formadas solo por referencias se identifican en la interfaz como falta de explicación.

El [diagnóstico exploratorio](../evidence/0.7-before-outpost/optimization/kernel-sampling.json) se ejecutó antes de fijar el prompt compacto. No es una evaluación independiente de calidad; repetir el modo `sampling` con el código actual usa el prompt actual y no reproduce esa variante histórica.

## Consultas completas con la configuración final

| Consulta de control | Primer token | Tiempo total | Tokens | Finalización |
|---|---:|---:|---:|---|
| Potencia y energía | 21,15 s | 36,85 s | 105 | Normal |
| Funcionamiento del GPS | 27,99 s | 43,54 s | 81 | Normal |
| Comparación GPS/brújula | 33,70 s | 50,51 s | 92 | Normal |
| Porcentaje ausente de las fuentes | 33,37 s | 48,04 s | 78 | Normal |

Una pasada por caso. El primer token incluye carga, preparación y cálculo del prompt. La carga de la primera consulta fue 463 ms. El máximo de las lecturas PSS **posteriores** fue 1.186.065 KiB, unos 1158 MiB; no se midió el pico de RAM durante generación. La ruta rápida quedó registrada como utilizada en las cuatro consultas. [Preguntas, fuentes, respuestas y medidas completas](../evidence/0.7-before-outpost/0.5/bonsai-bonsai4.json).

El contraste 0.4 → 0.5 incluye **kernel, prompt y muestreo**; no atribuye todo el resultado al kernel. Los registros originales de 0.4 quedan en [evidence/0.4](../evidence/0.7-before-outpost/0.4/), incluido su resumen histórico de tres modelos. No se repitió aquí toda la matriz de tres modelos con la nueva política.

Persisten errores observables: el ejemplo numérico de energía cita `[1]` aunque aparece en `[2]`; GPS dice posición «exacta» y la comparación mezcla orientación con posición. La pregunta sin porcentaje sí declara que falta ese dato. Completar 4/4 significa generar texto dentro del plazo, **no acertar 4/4** ni verificar sus citas.

## App, interfaz y reproducción

Compilación correcta y Android Lint sin incidencias. Pasaron **48 comprobaciones funcionales** y **17 comprobaciones de generación con Qwen**, incluidas cancelación y generación posterior. La prueba visible del 4B produjo los mismos 105 tokens de la consulta de energía: primer token a 29,67 s y total de 52,14 s. Esa repetición muestra variabilidad respecto a la matriz. Se revisaron la captura del borrador y Estado; se accionó el selector y se confirmó la preferencia persistida `bonsai4`. Modo avión permaneció en 1 y Wi-Fi en 0.

- [Comprobaciones funcionales](../evidence/0.7-before-outpost/0.5/checks.json).
- [Borrador de Bonsai 4B](../evidence/0.7-before-outpost/0.5/bonsai-bonsai4.png) y [registro de interfaz](../evidence/0.7-before-outpost/0.5/bonsai-ui-bonsai4.json).
- [Estado con Bonsai 4B seleccionado](../evidence/0.7-before-outpost/0.5/models.png).
- [APK 0.5 para el emulador x86_64](../dist/brujula-0.5.0-emulator-debug.apk) y [SHA-256](../dist/brujula-0.5.0-emulator-debug.apk.sha256). Los pesos permanecen fuera del APK.

Con las dependencias preparadas y los modelos ya importados:

```powershell
pwsh -File scripts/build.ps1 -Offline
pwsh -File scripts/test-kernel.ps1 -Phase numeric
pwsh -File scripts/test-kernel.ps1 -Phase decoder4
pwsh -File scripts/test-emulator.ps1
pwsh -File scripts/test-generation.ps1
pwsh -File scripts/test-bonsai.ps1 -Profiles bonsai4 -SkipInstall
pwsh -File scripts/test-bonsai.ps1 -UiOnly -UiProfile bonsai4 -SkipInstall -KeepSelected
```

Los scripts rechazan teléfonos físicos. La optimización actual permite continuar desarrollando con 4B en este emulador. Un kernel ARM/NEON, rendimiento de los Pixel, autonomía, temperatura y calidad en misiones reales siguen sin medirse.
