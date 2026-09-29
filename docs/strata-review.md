# Ideas de Strata para Bonsai 4B y Brújula

Revisión de código y documentación del 29 de septiembre de 2026. Referencia fijada: [Niko1221/Strata, `3ce2523c2823687de5372be3af58534f56cbf286`](https://github.com/Niko1221/Strata/tree/3ce2523c2823687de5372be3af58534f56cbf286). Se consultaron archivos públicos; no se ejecutaron instaladores, modelos ni benchmarks de Strata. No se cambió el APK 0.5 ni la selección de Bonsai 4B en el emulador.

Actualización: las tres hipótesis principales se implementaron y probaron posteriormente en [Brújula 0.6](validation-0.6.md). Esta nota conserva el análisis inicial; el informe enlazado contiene los resultados aceptados, descartados y los fallos encontrados.

## Qué aporta y qué no se traslada

La [evaluación 0.7](validation-0.7.md) incorpora especulación de contexto opcional, control de coste y auditoría de los límites de MTP y de un auxiliar 1.7B.

Strata combina cómputo y almacenamiento entre GPU NVIDIA, RAM y SSD para Qwen3.8-Flash-Next, un modelo MoE. Conserva expertos frecuentes en GPU y resuelve otros con CPU. Su arquitectura aprovecha que solo se activan algunos expertos por token. Bonsai 4B utiliza una arquitectura densa: esa política de expertos no se puede trasladar directamente. Tampoco son trasladables las cifras publicadas de su equipo de escritorio. [Arquitectura y requisitos](https://github.com/Niko1221/Strata/blob/3ce2523c2823687de5372be3af58534f56cbf286/docs/DETAILS.md).

Su tabla n-gram/PLE almacenada en SSD pertenece al modelo; no equivale a una biblioteca de Wikipedia, OSM o documentos consultables y citables. La separación de inferencia y conocimiento propuesta para Brújula sigue siendo necesaria.

## 1. Reutilizar pesos entre varios tokens

El [kernel CPU Q2 de Strata](https://github.com/Niko1221/Strata/blob/3ce2523c2823687de5372be3af58534f56cbf286/src/kernels/cpu/q2_avx2.cpp) contiene `row_multi<NT>` y variantes para uno, dos, tres y cuatro tokens. Desempaqueta un bloque de pesos una vez y lo aplica a varias activaciones. Su [ruta de prefill en GPU](https://github.com/Niko1221/Strata/blob/3ce2523c2823687de5372be3af58534f56cbf286/src/prefill/moe_mmq.cu) también trata los prompts como productos matriciales cuantizados.

En Brújula, [q2_kernel.c](../app/src/main/cpp/q2_kernel.c) acelera un producto Q2_0 × Q8_0 por llamada. Aumentar `n_batch` por sí solo no implementa un kernel que reutilice el desempaquetado entre tokens. La ruta de GGML fijada anuncia `nrows=1` para Q2_0.

**Experimento propuesto:** microkernel de dos/cuatro tokens para prefill, conectado explícitamente a la multiplicación matricial y con fallback para dimensiones restantes. Comparar primero contra la referencia numérica y después en el modelo completo. Medir tiempo del prompt, memoria temporal y cancelación, sin cambiar a la vez el prompt ni el muestreo.

El código de Strata no es intercambiable sin adaptación: usa una estructura de activaciones `ActQ` con escalas y sumas precalculadas, además de FMA y otro orden de acumulación. Nuestra versión preserva igualdad de bits con el kernel escalar de llama.cpp. Una adaptación que cambie aritmética necesita pruebas de logits y calidad además de velocidad; no puede heredar el resultado de las 16.241 pruebas actuales.

## 2. Reutilizar el prefijo exacto de una consulta

Strata conserva estados de conversación, exige coincidencia del prefijo y mantiene el estado común mientras rota otros por uso reciente. Su [política de caché](https://github.com/Niko1221/Strata/blob/3ce2523c2823687de5372be3af58534f56cbf286/include/strata/program/conv_cache.hpp) corresponde a una cadena de prefijos sobre una sola historia activa; no es una caché arbitraria de todas las conversaciones.

En [engine.cpp](../app/src/main/cpp/engine.cpp), Brújula conserva los pesos pero crea y destruye el contexto por consulta. Por tanto vuelve a procesar todas las instrucciones y fuentes. [ResearchPrompt.java](../app/src/main/java/dev/outpost/app/ResearchPrompt.java) ya coloca las fuentes antes de la pregunta: consultas sucesivas con las mismas fuentes podrían compartir un prefijo largo.

**Experimento propuesto:** un contexto persistente acotado que reutilice únicamente tokens de prefijo idénticos. Caso de campo: un ingeniero consulta varias veces el mismo manual y revisión. Medir primera pregunta y siguientes por separado, frente a ejecución sin caché.

La selección y el orden de pasajes pueden cambiar entre preguntas; no basta con que pertenezcan al mismo documento. Invalidar o recortar el estado si cambian modelo, plantilla, fuentes, orden o configuración pertinente; reconstruir sampler por petición y probar cancelación, cambio de modelo y liberación de memoria. La caché nunca debe conservar evidencia vieja porque resulte más rápida. El beneficio queda por medir y será menor si apenas se comparte el sistema breve.

## 3. Calibrar y medir por fase

El [calibrador de Strata](https://github.com/Niko1221/Strata/blob/3ce2523c2823687de5372be3af58534f56cbf286/tools/calibrate.py) mide candidatos, confirma con pasadas alternadas y conserva cambios solo si superan un margen. Sus parámetros PCIe/especulación son propios de su arquitectura; el método de comparación sí es útil.

Brújula fija cuatro hilos y lotes de 128. Propuesta inicial: explorar dos, tres y cuatro hilos, y lotes 32/64/128/256, separando hilos de prefill y generación cuando exista evidencia de beneficio. Mantener pesos, entradas y política de salida constantes; repetir candidatos alternadamente y guardar configuración por modelo, versión del motor y capacidades del dispositivo. No extrapolar el ganador x86 al Pixel.

Añadir métricas de carga, preparación del contexto, prefill, generación, tokens reutilizados y memoria. Hoy `firstTokenMs` incluye varias de esas fases. En las [cuatro consultas 0.5](../evidence/0.7-before-outpost/bonsai-bonsai4.json), el 57–70 % del tiempo total transcurre antes del primer token. Eso orienta el trabajo hacia prefill y reutilización, pero aún no identifica por sí solo cada operación costosa.

## Ideas posteriores

El [controlador de especulación](https://github.com/Niko1221/Strata/blob/3ce2523c2823687de5372be3af58534f56cbf286/include/strata/spec/controller.hpp) decide según aceptación y coste si conviene proponer tokens o decodificar normalmente. Su MTP depende del modelo y no se incorpora sin más a nuestro Bonsai. Un borrador externo implica memoria y cómputo adicionales. La propuesta por repetición de texto del prompt podría estudiarse sin segundo modelo, pero requiere verificación correcta con el muestreo y no acelera la primera lectura del prompt.

La proyección experimental que Strata ofrece modifica el comportamiento del modelo y sus propias mediciones muestran divergencias. No es una optimización aritmética equivalente al kernel actual. [Descripción de esa opción](https://github.com/Niko1221/Strata/blob/3ce2523c2823687de5372be3af58534f56cbf286/data/experimental-speed-projection/README.md).

## Orden propuesto

1. Instrumentación por fase y calibración de hilos/lotes para establecer una base reproducible.
2. Kernel que reutilice pesos entre tokens, dirigido a reducir la espera de la primera consulta.
3. Caché de prefijos exactos para preguntas sucesivas sobre las mismas fuentes.
4. Especulación solo si las medidas muestran un beneficio adicional con memoria disponible.

Las pruebas de producto deben usar manuales, datos y preguntas de las [misiones de campo](field-use-cases.md). Las pruebas sintéticas quedan como controles de corrección y rendimiento. Calibración, kernel y caché tienen resultados en 0.6; especulación de contexto y límites de MTP se evaluaron en 0.7. La ejecución permanece limitada al emulador.
