# Brújula 0.3 · revisión local con Kev · 29 de septiembre de 2026

> Historical record: observations, proposals and commands below describe the recorded release/session. For Outpost 0.12 use [current state](current-state.md), [handoff](handoff.md) and [current validation](validation-0.12.md). Original results and language are preserved.

## Qué aporta un modelo tipo Jev

La [documentación oficial de TypeSafe](https://docs.typesafe.ai/introduction) describe Jev como un modelo de decisiones estructuradas: elegir, puntuar o evaluar proposiciones. Su integración pública es una API remota, que no sirve para el funcionamiento offline exigido aquí.

Se eligió [Kev 0.8B](https://github.com/jaredpalmer/kev), alternativa comunitaria de pesos abiertos, en la [conversión Q8_0 de DreamBlooms](https://huggingface.co/DreamBlooms/kev-0.8b-GGUF). Es un backbone Qwen3.5 con adaptación LoRA fusionada y un clasificador bilineal adicional. **No es Jev ni hereda su rendimiento.**

La app conserva el generador Qwen2.5 1.5B y añade revisión opcional de la primera frase del borrador. Kev recibe evidencia, una afirmación y tres opciones: apoyo, contradicción o información no establecida. No genera texto; la interfaz explica sus puntuaciones. No corrige, filtra ni publica automáticamente nada.

## Implementación y alcance

- Inferencia exclusivamente en el emulador AOSP Android 15 x86_64, 4 GiB configurados, modo avión y sin permiso INTERNET.
- GGUF de Kev: 811843040 bytes; cabeza FP32: 2099200 bytes. Configuración, revisión del publicador y SHA-256 fijados en `judge-lock.json`.
- Adaptación JNI del formato de tokens y lectura del pointer head de dohnuts.cpp, revisión `63374ff55a66c50b266adfef422e1fc4b0ee5717`, con licencia Apache-2.0 incluida. Sin servidor HTTP, Python de inferencia, visión o herramientas remotas.
- Una pregunta por llamada: secuencia causal, embeddings en todos los tokens, proyecciones q/k de 256 dimensiones sobre estados de 1024, producto escalar escalado y softmax con la temperatura publicada 2.406050072164233. No hay generación de tokens para decidir.
- Entrada limitada a 1536 tokens totales; la evidencia no se trunca silenciosamente si excede el presupuesto. Timeout/cancelación comparten el mecanismo del generador.
- Generador y revisor se cargan alternativamente, liberando el modelo anterior. Se probó pasar de Kev al generador y de la generación de la UI a Kev.
- La temperatura del autor no implica calibración validada para este corpus o para español. La UI indica esa limitación.
- No se ha ejecutado la referencia PyTorch fuera del emulador. La adaptación sigue el port publicado, pero **no se afirma paridad numérica con PyTorch, con Jev o con otros tamaños de Kev**.

## Prueba funcional y evaluación de decisiones

Compilación y Android Lint correctos, sin incidencias. Se superaron **25 comprobaciones funcionales del revisor**, además de **42 de recuperación/UI y 17 de generación** sobre la versión 0.3: 84 comprobaciones funcionales en total. Este resultado es distinto de acertar las decisiones: los desaciertos se registran y no se convierten en fallos de instalación.

La prueba sencilla contiene doce pares evidencia/afirmación: seis en inglés y seis en español; cuatro ejemplos apoyados, cuatro contradichos y cuatro no establecidos. Son ejemplos escritos durante el desarrollo y conocidos por el integrador, no un benchmark independiente ni evidencia de ausencia de contaminación de entrenamiento.

Resultado: **12/12** en los ejemplos sencillos; **6/6** por idioma. Una permutación de opciones en un ejemplo conservó la decisión. No demuestra invariancia general al orden.

En la última ejecución, las decisiones sencillas tardaron entre **1,2 y 3,1 segundos** incluyendo una primera carga; la revisión con pasajes completos en la UI tardó **5,1 segundos**. PSS del proceso al terminar: **898447 KiB** (aproximadamente 877 MiB), una lectura puntual, no pico de RAM ni memoria de todo Android.

## Fallos relevantes para nuestra aplicación

Los casos adicionales usan las fuentes reales y el error observado en nuestro generador. Se conservan separados de los doce casos sencillos para no esconder el problema en un promedio.

| Afirmación / evidencia | Esperado | Kev | Puntuación de apoyo |
|---|---|---|---:|
| «La potencia y energía se mide en vatios…», pasajes completos | Contradicción | Apoyo | 75,7 % |
| «La potencia se mide en vatios…», pasajes completos | Apoyo | Apoyo | 80,6 % |
| «La energía se mide en vatios…», pasajes completos | Contradicción | Apoyo | 71,2 % |
| «La energía se mide en vatios», evidencia corta que distingue W y Wh | Contradicción | No establecido | 32,9 % |

Hay **dos falsos apoyos en los diagnósticos**; separar la frase en partes no corrigió el falso apoyo con la evidencia completa. La formulación de la afirmación y el contexto alteran los resultados.

La primera frase imprecisa de Qwen1.5B sobre energía y tiempo también recibió apoyo sugerido en la UI, aproximadamente 79,5 %. Esta frase es lingüísticamente ambigua y no se incluyó en el contador de exactitud. El ejemplo confirma que el revisor no convierte un borrador en texto fiable.

**Decisión de implementación:** mantenerlo como experimento opcional y atribuir explícitamente cada evaluación a Kev. No se activa ningún filtro de aprobación automática ni se etiqueta el texto como «verificado». No se han ajustado umbrales para hacer pasar estos ejemplos.

## Evidencias y reproducción

- [Resultados completos, puntuaciones y casos](../evidence/0.7-before-outpost/0.3/review-checks.json).
- [Primera ejecución](../evidence/0.7-before-outpost/review-initial.json).
- [Captura de la UI revisada](../evidence/0.7-before-outpost/0.3/review.png).
- [Regresión de búsqueda/UI](../evidence/0.7-before-outpost/0.3/checks.json) y [regresión de generación](../evidence/0.7-before-outpost/0.3/generation-checks.json).

Preparar con `scripts/prepare-native.py` y `scripts/prepare-judge.py`, compilar con `scripts/build.ps1 -Offline`, y ejecutar `scripts/test-emulator.ps1`, `scripts/test-generation.ps1` y `scripts/test-review.ps1`. Las dos últimas pruebas importan los modelos fijados desde archivos locales previamente descargados. Ninguna conecta el emulador a internet ni usa el Pixel.

## Qué queda pendiente

Corpus mayor y preguntas reservadas, verificación específica de citas, calibración en datos propios, afirmaciones compuestas y adversarias, casos de negación/números/unidades, paridad contra el port de referencia y evaluación de otras arquitecturas de inferencia textual. No se extrapolan los resultados al Jev de TypeSafe, a Kev 4B, a OpenJev ni a un teléfono real.

