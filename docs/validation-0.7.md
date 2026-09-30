# Brújula 0.7 · especulación de contexto y evaluación de MTP

> Historical record: observations, proposals and commands below describe the recorded release/session. For Outpost 0.12 use [current state](current-state.md), [handoff](handoff.md) and [current validation](validation-0.12.md). Original results and language are preserved.

Se implementó decodificación especulativa mediante coincidencias de tokens en el contexto, sin descargar otro modelo. Es una opción **experimental, desactivada por defecto**, disponible en Estado para Bonsai 4B. Toda inferencia y prueba de muestreo se ejecuta en el emulador AOSP Android 15 x86_64, cuatro CPU lógicas y 4 GiB configurados. No se utilizó un teléfono ni se ejecutaron modelos en el ordenador anfitrión.

## Qué ocurre con MTP

La inspección del GGUF fijado y la API del motor confirman **36 capas del modelo y 0 capas MTP**. Los 398 tensores del 4B cubren sus bloques ordinarios y los tensores compartidos de embedding/norma; no se encontraron descriptores MTP, nextn o draft. El vocabulario activo que informa el motor tiene 151669 entradas. El 1.7B comparte los campos del tokenizador con el 4B, pero es otro modelo completo, no una cabeza MTP. [Auditoría de los archivos](../evidence/0.7-before-outpost/speculation/model-audit.json) · [confirmación dentro del motor](../evidence/0.7-before-outpost/speculation/initial-v1/speculation-pilot.json).

La [documentación oficial de Prism](https://github.com/PrismML-Eng/Bonsai-demo/blob/main/SPECULATIVE.md) describe drafters emparejados con otras familias de 27B; eso no establece compatibilidad con este checkpoint 4B. Un MTP/drafter de estados internos requiere pesos entrenados compatibles. No se fabricó una cabeza a partir de pesos aleatorios ni se etiquetó el procesamiento por lotes como MTP.

## Implementación

[speculation.cpp](../app/src/main/cpp/speculation.cpp) busca una continuación de un sufijo coincidente en los tokens del prompt y de la salida ya confirmada de la misma petición. No incorpora respuestas de otros documentos ni peticiones. La política final de la app espera al menos ocho tokens emitidos, exige una coincidencia de 8–16 tokens y propone hasta tres siguientes.

[engine.cpp](../app/src/main/cpp/engine.cpp) evalúa el token pendiente y las propuestas en un lote causal, obteniendo logits para todas las posiciones. El sampler de Bonsai decide cada token con los mismos parámetros de antes. Se conserva el prefijo de propuestas que coincide con lo muestreado por el objetivo; al primer desacuerdo, se descarta el resto del KV especulativo y se continúa con el token elegido por Bonsai. No se aceptan propuestas por parecido semántico ni por estar entre los candidatos más probables.

Se mantiene el límite de contexto de 2048, el máximo de 192 tokens de UI, el plazo de 120 segundos y la cancelación. El sampler se reinicia por consulta y la caché de prefijos conserva las reglas de 0.6. La especulación actúa en la generación, no acelera la primera lectura de las fuentes.

El controlador mide el coste de pasos normales y de ventanas especulativas. Después de tres ventanas desactiva la especulación para el resto de la respuesta si no estima una mejora superior al 5 %. La estimación no elimina todo riesgo de ralentización: hay que pagar las primeras pruebas. No requiere un segundo conjunto de pesos.

La [literatura de decodificación especulativa](https://arxiv.org/abs/2211.17192) distingue las propuestas del modelo objetivo que determina la distribución final. Aquí se usa un verificador que muestrea directamente del objetivo y compara con propuestas deterministas. La equivalencia depende también de los logits calculados: el comportamiento numérico real se comprobó por separado.

## Rendimiento y decisiones

Tres parejas alternadas, caché del prompt disponible, muestreo top-k 20, top-p 0,8, temperatura 0,7 y semilla 42. Se mide la fase de generación, excluyendo la lectura inicial. Los tiempos describen este emulador y carga del host.

| Control | Base, mediana | Política final, mediana | Interpretación |
|---|---:|---:|---|
| Salida larga que reproduce partes de una ficha | 21,664 s | 19,818 s | **8,5 % menos tiempo**, mismo texto en las tres parejas. |
| Respuesta breve de 16 tokens | 3,445 s | 3,034 s | No lanzó propuestas; usó la ruta normal. La diferencia temporal no es una aceleración especulativa. |

[Resultados finales](../evidence/0.7-before-outpost/speculation/speculation-benchmark.json). El control largo fija un máximo de 96 tokens: se corta por presupuesto y no demuestra éxito en una tarea de copia literal. Bonsai cambia mayúsculas, tildes y alguna palabra del original; esa limitación ya aparece sin especulación.

La política inicial admitía sufijos de cuatro tokens y probaba antes. Logró 20 % menos tiempo en el control largo, pero ralentizó 6 % la respuesta breve y perjudicó otro control de seis tokens. Se conservan esos [resultados iniciales](../evidence/0.7-before-outpost/speculation/initial-v1/speculation-benchmark.json). No se presentan como la mejora de la configuración final.

El kernel agrupado se volvió a comprobar en 0.7: mediana total 22,935 s con ancho uno frente a 18,465 s con ancho cuatro, mismo texto. Se guardó el perfil 4/4 hilos, lote 128 y agrupación cuatro para este dispositivo y modelo. [Perfil](../evidence/0.7-before-outpost/speculation/speculation-profile.json).

## Fidelidad numérica y límite de reproducibilidad

Pasaron **561 comprobaciones** del buscador, prefijos aceptados/rechazados, coste y muestreo. Incluyen 512 trazas sintéticas de 128 tokens, tanto con distribución simple como con la cadena top-k/top-p/temperatura usada por la app: los tokens coincidieron con el recorrido serial. [Pruebas](../evidence/0.7-before-outpost/speculation/speculation-unit.json).

En una continuación conocida se compararon logits de 24 posiciones con lotes de dos, cuatro y ocho: **72 posiciones con igualdad de bits**. [Auditoría](../evidence/0.7-before-outpost/speculation/speculation-audit.json).

Una prueba de UI de la política inicial sí cambió la redacción con la misma semilla. Se preservó el [fallo](../evidence/0.7-before-outpost/speculation/initial-v1/speculation-ui.json). En el diagnóstico de ese caso, evaluando la misma continuación sin muestreo, aparecieron diferencias entre logits seriales y por lotes: máximo absoluto 0,216; media absoluta 0,0249; KL media 0,000231 nats. El token más probable coincidió en las 64 posiciones, pero ninguna fue idéntica en bits. [Diagnóstico](../evidence/0.7-before-outpost/speculation/speculation-energy-audit.json).

Por tanto, **no se garantiza reproducción literal para toda entrada con la misma semilla**. La política más selectiva conservó los 105 tokens de la respuesta del diagnóstico, pero no elimina esa limitación general. La interfaz la indica. Esto no equivale a comprobar veracidad: los errores de contenido del modelo pueden mantenerse.

## Rechazo, cancelación y fuentes

- Las propuestas deliberadamente erróneas se rechazaron: 0 aceptadas de 183, con el mismo texto final que la base.
- El límite de tres tokens y EOS se respetaron.
- Cancelar después de seis tokens no publicó propuestas pendientes; la siguiente petición empezó sin reutilizar el contexto cancelado.
- Cancelar durante la verificación publicó únicamente el token ya confirmado.
- El controlador suspendió propuestas inútiles después de tres ventanas y siguió por el camino normal.
- La fuente revisada con F-91 produjo F-91, sin recuperar F-28 de la petición anterior.

[Ciclo de vida](../evidence/0.7-before-outpost/speculation/initial-v1/speculation-lifecycle.json) · [control de coste y cancelación de verificación](../evidence/0.7-before-outpost/speculation/speculation-guard.json). Las comprobaciones usan fichas ficticias y no validan procedimientos de campo.

Los cinco contextos de viajero, granjero, ingeniero, montañero y conductor se registran por separado en [los controles de campo](../evidence/0.7-before-outpost/speculation/speculation-missions.json). Presencia de un número, coincidencia de texto o finalización del ejecutor no se puntúan como éxito real de la misión.

En estos cinco pares el texto coincidió, pero viajero y montañero fallaron: el primero no resolvió el equipaje y se cortó; el segundo rechazó incorrectamente calcular 1450 − 1200 = 250 m. Ocurrió con y sin especulación, bajo las instrucciones de este ensayo. [Revisión de contenido](../evidence/0.7-before-outpost/speculation/mission-review.json). La mejora de ejecución no repara esos errores.

## Por qué no se añadió el 1.7B como borrador

Se midió su coste usando los pesos ya instalados: aproximadamente **121 ms por paso**, frente a **212 ms** del 4B en esa serie. Un ensayo con la continuación conocida del objetivo aceptó las 47 propuestas, pero ese «oráculo» usa una respuesta previa y no es un predictor desplegable.

Añadiendo al coste de esa verificación el mínimo estimado de generación del 1.7B, el recorrido sería **15,25 s frente a 13,34 s** equivalentes del 4B solo. Es una estimación optimista: supone acierto perfecto e ignora el prefill del auxiliar, sincronización, residencia simultánea y tráfico de memoria. No se implementó el sistema dual ni se afirma que ese resultado se aplique a otros equipos. En este ensayo no mostró un margen favorable para integrarlo. [Costes y cálculo](../evidence/0.7-before-outpost/speculation/speculation-draft-cost.json).

Una cabeza entrenada mucho más barata podría cambiar ese resultado; no se deduce que todo MTP sea lento ni que el 1.7B sea equivalente a MTP.

## Uso y reproducción

En **Estado → Especulación experimental**, activar o desactivar la opción para Bonsai 4B. La preferencia es específica de dispositivo, versión y modelo. Se entrega apagada por defecto por su beneficio desigual y la limitación de reproducibilidad. El modelo, las fuentes y la revisión Kev permanecen separados.

La prueba de interfaz activa y desactiva el botón real y compara una respuesta normal de la app con la opción habilitada. Usa el prompt de `ResearchPrompt`, no el sistema genérico de las fichas del ejecutor de experimentos. El total de una repetición incluye el beneficio de caché: no debe atribuirse entero a especulación; para esa comparación se registra `decodeMs`. [Registro de interfaz](../evidence/0.7-before-outpost/speculation/speculation-ui.json) · [opción en Estado](../evidence/0.7-before-outpost/speculation/speculation-state.png) · [respuesta](../evidence/0.7-before-outpost/speculation/speculation-answer.png).

```powershell
python scripts/audit-spec-models.py
pwsh -File scripts/build.ps1 -Offline
pwsh -File scripts/test-speculation.ps1 -Phase unit
pwsh -File scripts/test-speculation.ps1 -Phase benchmark -SkipInstall
pwsh -File scripts/test-speculation.ps1 -Phase lifecycle -SkipInstall
pwsh -File scripts/test-speculation.ps1 -Phase guard -SkipInstall
pwsh -File scripts/test-speculation.ps1 -Phase draft-cost -SkipInstall
pwsh -File scripts/test-speculation.ps1 -Phase profile -SkipInstall
pwsh -File scripts/test-speculation.ps1 -Phase audit -SkipInstall
pwsh -File scripts/test-speculation.ps1 -Phase energy-audit -SkipInstall
pwsh -File scripts/test-speculation.ps1 -Phase missions -SkipInstall
pwsh -File scripts/test-speculation.ps1 -Phase ui -SkipInstall
```

Los scripts rechazan teléfonos físicos. La inspección de cabeceras GGUF y las compilaciones se hacen en el host; toda ejecución de modelos y pruebas numéricas ocurre en el emulador. Los pesos, la revisión de llama.cpp y sus archivos fuente permanecen sin modificar. No se ejecutó Strata ni se importaron sus kernels.

La compilación final y Android Lint terminaron sin incidencias. Pasaron las **48 comprobaciones funcionales, 17 de generación con Qwen y 64 de selección de CPU**, además de las pruebas específicas de especulación. El controlador C++ y los cuatro módulos C se compilaron como objetos para Android x86_64 y ARM64; no se construyó ni ejecutó un APK ARM. Se revisaron las capturas. La UI final conservó la respuesta de 105 tokens, realizó tres verificaciones y aceptó siete de nueve propuestas. Se restauró la preferencia a desactivada y quedó Bonsai 4B seleccionado, con modo avión activado y Wi-Fi apagado.

[APK 0.7 para emulador x86_64](../dist/brujula-0.7.0-emulator-debug.apk) · [SHA-256](../dist/brujula-0.7.0-emulator-debug.apk.sha256).
