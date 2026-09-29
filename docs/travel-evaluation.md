# Subcaso de viaje: recomendaciones locales sin internet

Este caso queda subordinado a la [visión de uso en el terreno](field-use-cases.md), revisada tras la aclaración del usuario. No es la prioridad global del producto. Viaje, campo, inspección de infraestructura, montaña y carretera definen ahora la evaluación principal.

## Contexto y estado

El usuario aportó un comentario atribuido a Vitalik en X: las apps offline están mejorando, pero siguen siendo lentas y fallan en consultas de viaje como recomendar restaurantes veganos en la ciudad actual. Se usa ese texto como orientación de producto; no se ha verificado de forma independiente la publicación ni se interpreta como una modificación formal de las bases del concurso.

Brújula 0.4 aún no cubre este caso: incluye seis notas didácticas, no un catálogo de restaurantes. Las pruebas anteriores midieron integración, comportamiento y tiempos de modelos. No demuestran utilidad en viajes. Este documento define la siguiente etapa; no describe una funcionalidad ya implementada.

## Flujo propuesto

1. Ciudad elegida o coordenadas simuladas dentro del emulador. Si falta ubicación, pedir ciudad; nunca deducir la posición real del usuario por idioma o zona horaria.
2. Consultar un catálogo geográfico local, filtrar establecimientos y recuperar los registros con sus fuentes.
3. Mostrar candidatos y datos disponibles inmediatamente, antes de cualquier generación. Objetivo provisional: primera lista útil en menos de un segundo, pendiente de medir.
4. Ordenar por criterios explícitos y explicar por qué encaja cada candidato. El generador puede resumir esos datos; no inventa nombres, direcciones, horarios, valoraciones ni atributos dietéticos.

«Mejores» exige criterios y evidencia. Cercanía o pertenencia a una categoría no demuestra calidad gastronómica. Cuando no haya reseñas o recomendaciones utilizables y atribuibles, presentar candidatos adecuados según criterios declarados, sin afirmar una clasificación global de calidad.

## Datos y procedencia

Paquetes de ciudad importables antes del uso offline: identificador de fuente, nombre, coordenadas, dirección, categoría, atributos dietéticos, estado conocido, horario si existe, fecha del paquete y fecha de comprobación cuando esté disponible. Conservar atribuciones y licencias de cada fuente. La fecha de extracción no equivale a la fecha de comprobación del negocio.

Fuentes candidatas verificadas documentalmente:

- [OpenStreetMap: diet:vegan](https://wiki.openstreetmap.org/wiki/Key:diet:vegan). `only` significa que todos o prácticamente todos los productos son veganos; `yes` indica oferta vegana habitual, `limited` oferta reducida y `no` ausencia de oferta. Preservar la distinción. Una etiqueta ausente es desconocida, y «vegetariano» no basta para afirmar «vegano». No convertir `only` en una certificación de un establecimiento 100 % vegano.
- [Overture Places](https://docs.overturemaps.org/guides/places/): candidato para geometría, direcciones y procedencia. No asumir que esos datos bastan para confirmar la dieta. Su documentación actual utiliza `taxonomy`; no construir un importador nuevo sobre el antiguo campo `categories`. La confianza del registro no es una valoración gastronómica.

La cobertura y actualidad de una primera ciudad deben inspeccionarse antes de elegir o mezclar fuentes. No se ha descargado un catálogo en esta etapa.

## Casos de evaluación

| Caso | Comportamiento esperado |
|---|---|
| Restaurantes veganos en una ciudad cubierta | Locales reales del paquete, ubicación correcta y evidencia del atributo dietético. |
| «En la ciudad en la que estoy», sin ubicación configurada | Pedir ciudad o ubicación simulada; no adivinar. |
| Local vegano frente a local con opciones | Categorías visibles y sin confundirlas. |
| Solo etiqueta de vegetariano o dieta ausente | No afirmar que es vegano. |
| Local cerrado, trasladado o fuentes contradictorias | Conservar estado/conflicto; no recomendarlo como confirmado sin resolverlo. |
| «Abierto ahora» con horario ausente o antiguo | Expresar que no puede confirmarse. Un horario almacenado no garantiza apertura real. |
| Ciudad fuera del paquete o sin candidatos | Informar de falta de cobertura; no inventar establecimientos ni rellenar cupos. |
| «Los mejores» sin datos comparables de calidad | Explicar criterios disponibles y límites de la ordenación. |
| Cambio de ciudad/radio | Recalcular candidatos y distancias, sin conservar recomendaciones de la ubicación anterior. |
| Mismas preguntas en español e inglés | Mantener filtros y atributos, sin degradar el significado de «vegano». |
| Registros duplicados o nombres parecidos | No duplicar recomendaciones ni fusionar locales distintos sin evidencia. |
| Modo avión desde el arranque | Obtener el mismo resultado local, sin depender de servicios de Google. |

## Medir antes de declarar éxito

Crear una referencia con establecimientos reales y evidencia conservada para una fecha concreta. Separar ciudades/casos de desarrollo de los reservados. Medir:

- Precisión y cobertura de los primeros cinco candidatos respecto a esa referencia.
- Locales inventados, fuera de zona o con atributo vegano no respaldado.
- Datos ausentes, obsoletos y conflictos correctamente señalados.
- Tiempo hasta lista útil y, por separado, hasta explicación; primera consulta y siguientes.
- Tamaño del paquete, memoria observada y errores de importación.
- Trazabilidad: cada dato mostrado debe poder abrir su registro fuente offline.

Una respuesta bonita o un revisor que la aprueba no prueba estos criterios. Las primeras listas deben ser útiles aunque no haya ningún LLM cargado. Bonsai se estudiará como opción de memoria y Kev como decisión auxiliar, cuando una prueba concreta justifique su uso.

## Orden de trabajo dentro de este subcaso

Primero catálogo de una ciudad y prueba de consulta geográfica offline; después filtrado/ordenación, ficha de evidencia y casos reservados; finalmente comparar generadores sobre los mismos candidatos. Todo permanece dentro del emulador en esta etapa. La investigación general sigue siendo objetivo del proyecto; este caso de viaje es la siguiente prueba de utilidad, no todo el alcance del concurso.
