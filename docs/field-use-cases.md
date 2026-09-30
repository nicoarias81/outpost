# Brújula: resolver situaciones en el terreno sin conexión

> Historical record: observations, proposals and commands below describe the recorded release/session. For Outpost 0.12 use [current state](current-state.md), [handoff](handoff.md) and [current validation](validation-0.12.md). Original results and language are preserved.

## Decisión de producto

La orientación principal procede de los cinco contextos indicados por el usuario: viaje, trabajo agrícola, inspección de infraestructura eléctrica, montaña y desplazamiento por carretera. Las preguntas enciclopédicas usadas hasta ahora son comprobaciones técnicas de integración. Las recomendaciones de restaurantes son un subcaso de viaje, no el objetivo central.

Brújula debe ayudar a una persona a entender una situación, recuperar información pertinente y decidir su siguiente paso con los datos disponibles, aun sin cobertura. El valor se mide por lo que logra resolver, no por el número de parámetros, el estilo de la respuesta ni la aprobación de otro modelo.

**Estado:** definición de producto y evaluación. La versión 0.4 implementa búsqueda de una biblioteca de demostración y modelos locales; no implementa todavía los recorridos completos descritos aquí.

## Cinco situaciones, un núcleo compartido

| Contexto | Trabajo que la persona necesita resolver | Información que debe estar disponible | Evidencia de utilidad |
|---|---|---|---|
| Viajero sin datos | Llegar a una reserva, entender indicaciones, interpretar un billete o comparar alternativas locales. | Reserva y documentos personales aportados, plano de transporte, mapas de la zona, guía y vocabulario. | Entiende su documento, localiza el destino correcto y obtiene alternativas justificadas con límites de actualidad. |
| Granjero sin cobertura | Investigar una anomalía de riego, consultar una máquina o interpretar observaciones del cultivo. | Manual del modelo exacto, esquema de instalación, guía del cultivo, registros y lecturas aportados. | Identifica qué documentación aplica, separa causas posibles, pide una observación discriminante y evita un diagnóstico categórico sin evidencia. |
| Ingeniero que inspecciona una torre | Relacionar una observación con el activo, consultar criterios de inspección y registrar una incidencia. | Identificador del activo, planos, revisiones de manuales, procedimiento autorizado, historial y observaciones. | Encuentra el apartado aplicable, mantiene trazabilidad y prepara un registro con hechos, incertidumbres y datos faltantes. |
| Montañero aislado | Ubicarse, entender el terreno y valorar alternativas que figuren en su cartografía. | Mapa y relieve guardados, itinerario, puntos relevantes, ubicación/altitud y datos aportados de tiempo, material y batería. | Interpreta la posición respecto al itinerario y distingue rutas documentadas de terreno o condiciones desconocidos. |
| Persona en un coche sin señal | Entender una incidencia, consultar el manual y encontrar servicios relevantes en su recorrido. | Ruta/mapa guardados, manual del vehículo exacto, puntos de servicio, autonomía y observaciones aportadas. | Recupera el aviso correspondiente, calcula con datos explícitos y muestra alternativas sin inventar tráfico, disponibilidad o apertura actual. |

No se supone que una persona deba descargar todos los paquetes. El núcleo y la base general son comunes; la información regional, de actividad y de equipos se prepara según la salida.

## Recorrido de uso

### Antes de perder cobertura

- Elegir zona y actividad; cargar y comprobar los paquetes correspondientes.
- Incorporar manuales, planos, reservas, itinerarios y otra documentación propia que el usuario decida llevar.
- Mostrar tamaño, versión, fecha, cobertura y recursos ausentes. Fecha de descarga y fecha del contenido son campos distintos.
- Permitir probar que el paquete se abre y se consulta en modo avión.

### Durante la situación

1. La persona describe qué ocurre, qué intenta conseguir y qué tiene disponible. No necesita redactar una pregunta académica.
2. La app usa el contexto confirmado: ubicación, activo/modelo, documento, unidades y observaciones. Pregunta lo imprescindible cuando una diferencia cambie la respuesta.
3. Recupera la sección del manual, dato, mapa o registro pertinente y lo muestra antes de una redacción larga.
4. Utiliza búsqueda, cálculo y consulta geográfica local donde corresponda. El modelo interpreta y explica; no fabrica resultados de esas herramientas.
5. Ofrece una respuesta breve con los hechos conocidos, alternativas, siguiente dato que conviene obtener y fuente consultable. Se puede profundizar después.
6. Guarda, cuando se solicite, observaciones y un resumen de la consulta para retomar o comunicar el caso más tarde. Guardar un borrador no implica enviarlo.

La app debe seguir permitiendo leer y buscar aunque el modelo esté ocupado o no esté cargado. El contexto debe poder corregirse durante la conversación. Se debe conservar una distinción visible entre información del paquete, observación del usuario e inferencia del modelo.

### Cuando falta información

- No inventar el modelo de un equipo, una revisión documental, una ruta o una medición.
- No transformar una hipótesis en diagnóstico; indicar qué observación permitiría discriminarla.
- No confundir datos guardados con condiciones actuales: tráfico, meteorología, estado de una vía, funcionamiento de un servicio o estado eléctrico de un activo.
- En una inspección eléctrica, no inferir autorización, ausencia de tensión o seguridad de intervención a partir de una conversación o imagen. La ayuda documental debe respetar el procedimiento y las condiciones confirmadas del trabajo.
- En mapas, no convertir una línea geométrica o un trayecto fuera de cobertura cartográfica en una ruta transitable confirmada.

Estos límites son parte de los casos de uso, no un sustituto de prestar ayuda: la app puede localizar información, aclarar qué falta y preparar el registro aun cuando no pueda concluir una actuación.

## Qué construir como núcleo

Estos componentes se organizan en [dos dominios técnicos](two-domain-design.md): inferencia y conocimiento local, coordinados por la aplicación. Los casos de uso determinan qué capacidades hacen falta en cada uno.

1. **Gestor de paquetes y documentos.** Buscar por región, actividad, equipo y revisión; mostrar qué está realmente disponible offline.
2. **Consulta contextual.** Mantener ubicación elegida, objetivo, material, observaciones y equipo; permitir cambiarlos sin arrastrar supuestos anteriores.
3. **Recuperación con procedencia.** Abrir página/sección y distinguir un manual específico de una guía general. La importación TXT/Markdown actual no sustituye al lector de PDF/planos que estos escenarios necesitan.
4. **Herramientas locales.** Cálculos con unidades, consultas geográficas y lectura de datos estructurados, con entradas y resultados visibles.
5. **Modelo local.** Comprensión, preguntas de aclaración, síntesis, comparación y ayuda para organizar un diagnóstico; validado con situaciones completas.
6. **Respuesta y cuaderno de campo.** Lectura rápida, consulta ampliada y registro local. Voz y fotografía son capacidades a evaluar después, no capacidades existentes de la versión 0.4.

Bonsai es un candidato para encajar capacidad en memoria. Kev es un candidato para decisiones acotadas. La elección depende de si mejoran estos recorridos bajo las restricciones de tiempo, memoria y fiabilidad; ninguno resuelve por sí solo la falta de documentación ni garantiza conclusiones correctas.

## Evaluación basada en misiones

Cada misión necesita un paquete de evidencia congelado, contexto inicial, uno o más turnos del usuario, criterios de éxito y errores críticos. Los casos profesionales necesitan referencias revisadas por personas con conocimiento del dominio. No usar la aprobación de Kev como verdad de referencia.

| Misión inicial | Qué debe comprobar la evaluación |
|---|---|
| Viaje: reserva + plano de transporte + cambio de destino | Extraer el destino real, usar la versión del plano disponible, comparar alternativas y admitir que no conoce incidencias en vivo. |
| Viaje: indicación o documento en otro idioma | Conservar nombres, cantidades y condiciones; expresar ambigüedades que afecten a la interpretación. |
| Campo: caída de presión con manual y lecturas | Usar el modelo correcto, interpretar unidades, pedir datos que cambien las hipótesis y citar el procedimiento pertinente. |
| Campo: síntoma de cultivo con información incompleta | Pedir cultivo/etapa/observaciones relevantes y distinguir posibles causas sin convertirlas en una identificación definitiva. |
| Torre: observación + plano + procedimiento vigente | Relacionar el activo y la revisión correcta, localizar el criterio y producir un registro verificable. |
| Torre: dos manuales incompatibles o estado no confirmado | Detectar la discrepancia y no completar por su cuenta el dato operativo que falta. |
| Montaña: posición fuera del itinerario guardado | Mostrar la relación con la cartografía disponible, calcular sobre rutas existentes y hacer visibles los tramos o condiciones desconocidos. |
| Montaña: cambio de objetivo con batería reducida | Permitir una consulta breve de la información esencial y la lectura del mapa sin exigir generación prolongada. |
| Carretera: aviso del vehículo y manual específico | Encontrar el apartado correcto y no sustituirlo por una respuesta de otro modelo de vehículo. |
| Carretera: servicios en la ruta y autonomía aportada | Calcular distancias sobre datos disponibles, distinguir hechos de horarios/estado no confirmados y evitar candidatos fuera de zona. |

Todas las misiones tendrán variantes con paquete ausente, datos contradictorios, ubicación ambigua, error de unidades, documento de revisión equivocada y cambio de contexto. Las versiones de evaluación reservadas deben cambiar rutas, activos y documentos; no basta parafrasear las mismas preguntas.

## Métricas que importan

- ¿La persona logró la tarea o recibió una explicación precisa de qué dato impide continuar?
- ¿Se consultó la fuente y la revisión adecuadas, y se usaron correctamente?
- ¿Las preguntas de aclaración fueron necesarias y útiles?
- ¿Los cálculos y transformaciones mantuvieron unidades y supuestos?
- ¿Se distinguieron hipótesis, hechos y condiciones desconocidas?
- Tiempo hasta la primera información útil y hasta resolver la consulta; una referencia legible puede ser útil antes del primer token generado.
- Continuidad de lectura/búsqueda durante inferencia, funcionamiento con paquetes incompletos y estado tras cancelar o reiniciar.
- Memoria y almacenamiento. Batería, temperatura, uso exterior y trabajo con guantes no se pueden validar de forma representativa con nuestro emulador y quedan pendientes.

Las preguntas de kW, GPS y las comprobaciones de importación permanecen como pruebas técnicas. Sus aciertos no se contarán como validación de estas misiones.

## Próximo incremento

Definir un recorrido mínimo por cada uno de los cinco contextos, con documentos/mapas de prueba identificados y criterios de éxito explícitos. Implementar primero la preparación y recuperación de información que comparten. Elegir después qué capacidades de interacción añadir y qué modelos comparar sobre esas mismas misiones. No desarrollar un catálogo de restaurantes como prioridad global ni cinco aplicaciones separadas.

La restricción vigente permanece: ejecución y pruebas dentro del emulador; sin usar el Pixel ni captar ubicación o datos reales del usuario. Este documento cambia las prioridades, no afirma que estas funciones estén ya construidas.
