# Vuelos Uruguay Conductor v1.4.1.1 · QA
Aplicación Android independiente (paquete uy.vuelos.uruguay). 
Fuentes: widget público de Avionio para Carrasco MVD, con atribución visible.
- Muestra vuelos por fecha seleccionada dentro del panel original (incluido mañana).
- Relee el panel al tocar el selector del origen y evita fijar siempre el día actual.
- Ordena por hora estimada cuando está disponible, agrupa códigos compartidos por origen/hora.
- Diferencia «la fuente no devuelve registros» de «no se pudieron recuperar los datos».
- Al consultar otros días, pausa el refresco automático para que no restablezca hoy.
- Las alertas opcionales de Android solo se programan con llegadas identificadas de hoy; no son push ni datos en tiempo real.
- El usuario puede cambiar fecha desde «Ver panel». Cuando la fuente no proporciona programación futura, no se inventan horarios.
QA solo Android: requiere validación física Samsung.
No se modifican Quick Trip, Supabase, Vercel ni la rama main de ningún proyecto.
Firma debug, puede ser necesario desinstalar v1.3.1 antes de instalar v1.4.1.

- Si Avionio responde sin registros, ofrece abrir el panel oficial de Carrasco para verificar una fecha futura.

## v1.5.0 — Dos modos sin interferencias
- **Hoy · En vivo:** actualización automática de Avionio cada tres minutos si la aplicación sigue en primer plano; vista nativa de arribos próximos y desplazamiento al próximo vuelo solamente si la lista no fue manipulada durante 45 segundos.
- **Consultar fecha:** modo manual visible. Detiene inmediatamente las recargas de WebView para no borrar fecha, hora ni posición seleccionadas en el widget de Avionio. Los toques en el panel también activan este bloqueo.
- El botón refrescar en modo manual **relee los datos ya visibles**; no recarga el widget porque eso resetearía el formulario. Para buscar otra fecha/hora se usa la flecha azul dentro de la fuente.
- Botón **Hoy · En vivo** vuelve explícitamente a hoy, con recarga controlada.
- Si Avionio dice "Sin vuelos" o los datos son ilegibles, la app no certifica que Carrasco esté sin vuelos; ofrece link al panel oficial para comprobar.
- La precisión/alcance de Avionio sobre fechas futuras es una limitación de esa fuente, no una garantía de la app.
- En primer plano y minimizado la consulta manual conserva estado mientras Android mantenga el proceso; reinicios o destrucción del proceso podrían reiniciar el widget.
- Sonidos y vibración permanecen como avisos locales basados en últimas llegadas verificadas, no push en tiempo real.
- Sin cambios a Quick Trip, main, Supabase ni producción.

## v1.5.1
Al pasar a modo manual se descarta la lista anterior hasta leer la nueva fecha, evitando presentar vuelos antiguos como si pertenecieran a otro día. La consulta a días futuros se mantiene pausada mientras el usuario trabaja con los filtros.

## v1.6.0 · interfaz conductor
- Cabecera más compacta, tarjetas más pequeñas y visibles de un vistazo.
- Horarios estimados vencidos sin estado de aterrizaje aparecen como SIN CONFIRMAR y nunca «EN 0 MIN».
- La lista nativa conserva la posición al actualizar en la misma fecha; respeta 45 segundos de inactividad antes de regresar al siguiente vuelo.
- La pantalla evita repetir el nombre del proveedor; mantiene acceso discreto «FUENTE ↗» al sitio original como acreditación y verificación.
- El widget externo mantiene sus elementos propios.
- La programación futura solo se muestra cuando el proveedor devuelve registros. Verificación alternativa con Carrasco oficial.
- Alertas locales según última consulta, no push en vivo.
- APK independiente; desarrollo temporal en rama QA aislada de manzanitas-3d (no hay repositorio exclusivo todavía); Quick Trip intacto.

## v1.7.0 — Consulta futura por fecha y hora
- Hoy mantiene seguimiento en vivo, estado estimado y alertas locales.
- Consultar fecha incluye selectores nativos de día y hora; desde 07:00 filtra 07:00 o posterior hasta las 23:59.
- Fuente web complementaria de horarios programados mediante WebView local, cargada únicamente al solicitar la consulta, sin APIs de pago.
- Un horario programado no confirma aterrizaje y no genera avisos de llegada.
- Si no puede comprobarse que la fuente aplicó la fecha seleccionada y actualizó resultados, la app NO enseña datos como si correspondieran a ese día.
- Si no obtiene datos confiables, permite verificar el panel de Carrasco y abrir la fuente complementaria.
- Datos externos sujetos a precisión/limitaciones; validación física Samsung pendiente.
- Rama QA aislada sin modificar ningún recurso de Quick Trip ni main.

## v1.8.0 — consulta de programación futura con fuente gratuita
- Algunas webs de horarios bloquearon las consultas automatizadas con HTTP 403; por eso se evita depender de filtros HTML no verificados.
- Nueva opción de API documentada de programación SkyLink por RapidAPI. El proveedor anuncia 1.000 consultas mensuales gratuitas, pero requiere clave personal.
- Se pega la clave en el Samsung y se guarda cifrada localmente mediante Android Keystore AES-GCM. No hay clave incluida en el código, ni se envía a GitHub o Quick Trip.
- La fecha y hora son controles propios; al elegir 04/11/2026 y 07:00, el filtro incluye las llegadas desde las 07:00 inclusive hasta acabar el día, si la API ofrece datos verificables para Carrasco.
- Valida aeropuerto SUMU, fecha exacta, zona horaria local Uruguay, horario UTC y respuesta completa; alerta claramente ante HTTP 403/429, cuenta sin acceso o cobertura ausente.
- No presenta vuelos ficticios ni afirma que el aeropuerto esté vacío si la API falla; conserva enlaces de verificación al panel oficial y una web pública de horarios.
- Modo Hoy y avisos locales permanecen intactos. El proveedor no ofrece seguimiento instantáneo en este modo.
- Es una versión QA: no se ha verificado todavía la cobertura de SUMU con una clave gratuita. Requiere instalación y comprobación por el usuario.
- No tocar Quick Trip, main, producción, Supabase ni GPS.

## v1.9.0 — alertas premium para conductores
- 40 min antes: **prepará tu traslado**; 20 min antes: **revisá tu salida a Carrasco**. Son tiempos sugeridos generales, no desplazamientos GPS calculados.
- Se agrupan vuelos con estimaciones en una misma franja de 15 minutos para evitar avisos duplicados; también se evita la repetición de una misma etapa tras actualizar datos.
- El aviso de aterrizaje solo se emite cuando en una consulta previa el vuelo estaba pendiente y una consulta posterior de la fuente lo marca como aterrizado. **No se confirma aterrizaje por el reloj**.
- Alertas locales del sistema Android: sonido+vibración, solo vibración, solo visual, o apagadas. Canales configurables por Android. En segundo plano se entregan avisos pendientes pero Android puede retrasarlos por ahorro de batería.
- Al recibir una alerta con la APK visible también se despliega durante nueve segundos una tarjeta blanca redondeada, con título y mensaje grande; hay cierre manual. Con la app minimizada se usa la notificación nativa de Android, cuya geometría depende del sistema.
- Configuración del aviso con **Vista previa** dentro de la APK (texto de ejemplo, no es un vuelo real).
- Hora en tarjeta de llegada: 23 sp; minutos restantes en cápsula: 16 sp; contraste reforzado. Después de aterrizaje confirmado se muestra la ventana orientativa de 25–60 min de desembarque/migraciones/equipaje, no una predicción individual.
- La aplicación no consulta vuelos en segundo plano y por tanto **no puede detectar nuevos aterrizajes estando cerrada**. Las notificaciones anticipadas quedan programadas según la última lectura, caducan si la información tiene más de 75 min.
- La fuente de vuelos futuros opcional de v1.8 se conserva sin cambios funcionales, con clave propia requerida y cobertura no verificada.
- APK QA independiente `uy.vuelos.uruguay`. Rama aislada `qa/vuelos-uruguay-alertas-premium-v19-20261103` dentro de `manzanitas-3d`; no se modifican Quick Trip, Mapa QT, producción, Supabase ni `main`.

## v1.10.0 — Pantalla encendida en primer plano
- Preferencia local «Pantalla encendida mientras usás la app», activada de forma predeterminada, con interruptor en el pie de pantalla.
- Mientras la actividad Android está visible (onResume), usa FLAG_KEEP_SCREEN_ON para impedir el apagado por inactividad.
- Al minimizar, bloquear el teléfono, cambiar de app o perder primer plano (onPause), elimina la bandera y vuelve al comportamiento normal del celular.
- No fuerza la app a primer plano, no bloquea el botón de apagado y no requiere servicio foreground, bloqueo de pantalla ni permisos especiales.
- La elección ON/OFF persiste en SharedPreferences; no afecta notificaciones, vuelos, ubicación ni Quick Trip.
- Mayor consumo de batería y posible retención de imagen si se deja la pantalla fija durante períodos prolongados; recomendamos apagar el interruptor cuando no sea necesario.
- Firma QA y validación física final en Samsung pendiente.

## v1.11.0 — Velocímetro y límite de calle (QA)
- Panel discreto: velocidad GPS en km/h y límite registrado en OpenStreetMap (etiqueta maxspeed) para el tramo identificado.
- El GPS está **apagado por defecto**. Su activación requiere consentimiento y permiso de ubicación precisa solo mientras la app esté visible; al minimizar o bloquear deja de solicitar ubicaciones.
- La consulta remota de límites envía la posición al servicio público Overpass de OpenStreetMap, con máximo una solicitud cada 90 s. Se conserva la geometría temporalmente en memoria para evitar consultas reiteradas.
- Sin límite registrado, sin cobertura de GPS o con calles paralelas ambiguas se muestra un guion «—», jamás un límite supuesto.
- Al pasar el límite en más de 7 km/h se resalta la velocidad, y el usuario puede habilitar una vibración breve tras cinco segundos sostenidos. Aviso vibratorio OFF por defecto.
- No se almacena recorrido, no hay rastreo en segundo plano y no se envía ubicación a Quick Trip. Atribución discreta © OpenStreetMap contributors.
- La señalización vial real prevalece sobre el valor de mapa y el velocímetro GPS no sustituye el del vehículo.
- Se conserva la función pantalla encendida de v1.10, los vuelos y sus avisos sin cambios funcionales.
- QA en rama aislada. Requiere comprobar cobertura real de maxspeed en Montevideo/Canelones y validar físicamente en Samsung.

## v1.12.0 — Solapa inferior contraída para priorizar los vuelos
- Controles de actualización, avisos de vuelos, pantalla encendida, fecha de la última consulta y atribución de fuente quedan organizados en una solapa inferior, **cerrada de forma predeterminada**.
- Solo permanece una pestaña inferior mínima «Más opciones» de 36 dp con indicador de agarre. Tocarla o deslizarla hacia arriba despliega los controles.
- «Ocultar opciones» o deslizar hacia abajo vuelve a cerrarla. Animación discreta de 190 ms y descripción accesible para lector de pantalla.
- El panel central de vuelos utiliza automáticamente toda la altura liberada. La solapa no flota encima de los vuelos: cambia el espacio de la lista sin tapar tarjetas.
- No borra ni modifica configuraciones ni datos operativos. GPS, seguimiento, alertas y modo consulta continúan con la misma funcionalidad.
- Se agregan pruebas unitarias de dirección del gesto y prevención de cierres accidentales.
- Solo rama QA de Vuelos Uruguay; sin cambios en `main`, Quick Trip, Mapa QT, Supabase ni producción.

## v1.13.0 — Velocímetro premium adaptable
- Rediseño del bloque de velocidad bajo el horario de Montevideo para un Samsung: dos tarjetas horizontales de tamaño flexible y controles estrechos GPS / VIB, con cifras grandes de **34 sp** y **32 sp**, sin ampliar la cabecera ni quitar espacio innecesario a los vuelos.
- La primera tarjeta muestra velocidad GPS y la segunda límite registrado para el tramo actual; cuando no hay cobertura GPS o datos OSM fiables, los campos muestran «—» sin inferencias.
- **Normal**: hasta el límite registrado, azul oscuro; **ámbar**: 1–6 km/h por encima; **rojo**: desde 7 km/h por encima. Fondo y texto cambian inmediatamente al actualizar velocidad o límite.
- La vibración continúa siendo voluntaria, desactivada inicialmente: se emite una vibración breve si el exceso rojo se mantiene 5 segundos, con espera mínima de 45 segundos entre avisos.
- Los umbrales de color son ayudas visuales definidas para el conductor y NO representan tolerancias legales; la señalización vial prevalece.
- Se conserva el permiso explícito de ubicación y la activación del GPS por consentimiento. Si ya se habilitó, se reactiva al volver al primer plano. No hay seguimiento con la app minimizada.
- Se mantiene atribución visible a OpenStreetMap. Se conserva la solapa inferior 1.12 y los motores de vuelos, ETA, GPS, mapas, alertas de vuelo, y pantalla encendida.
- Se agregan pruebas unitarias para límites normales, ámbar, rojo, cambios de tramo y límite desconocido.
- QA aislada en manzanitas-3d, sin modificar Quick Trip, Mapa QT, Supabase, producción ni main.

## v1.14.0 — Diagnóstico de «Límite del tramo: N/D»
- **0 km/h** significa vehículo detenido; **N/D** significa límite del tramo no verificable, no un límite de cero. El guion anterior se sustituyó por N/D.
- La app ahora solicita posiciones aun cuando la velocidad es cero para identificar la calle estando estacionado; el GPS funciona únicamente en primer plano cuando el usuario lo autorizó.
- Se consultan los tramos cercanos transitables de OpenStreetMap con y sin `maxspeed`, y se identifica la calle más cercana antes de decidir si existe un límite numérico. Esto impide atribuir a la calle actual el límite de otra calle cercana.
- Si existe una calle identificada pero no tiene límite numérico cargado, la pantalla dice «Sin límite cargado · [calle]»; para calles paralelas ambiguas o GPS impreciso, también explica la causa.
- Tocar «LÍMITE DEL TRAMO» abre detalles de por qué aparece N/D. La señalización física prevalece.
- Si falla el servicio público Overpass, no se guarda una respuesta fallida como mapa vacío por cinco minutos: reintenta de forma controlada, no más de una consulta cada 90 segundos.
- Si ambos atributos de límite direccional son numéricos y coinciden, puede utilizarlos; si difieren, no inventa un límite según una dirección desconocida.
- Sin rastreo continuo en segundo plano, sin permisos nuevos, sin histórico de recorridos. Mantiene la advertencia de velocidad y resto de funcionalidades de Vuelos Uruguay.
- La versión 1.13 se conserva. Todo el trabajo en QA aislada, sin modificar Quick Trip, Mapa QT, Supabase, producción ni main.

## v1.15.0 — Calle cercana arriba del velocímetro
- Se añadió una fila compacta «CALLE CERCANA · APROX.» arriba de los bloques de velocidad GPS y límite del tramo. Android Geocoder muestra el nombre de la vía cercana; si OSM devuelve un tramo identificable, su nombre tiene prioridad.
- Esta geocodificación no depende de Overpass; puede continuar funcionando aun cuando los servidores públicos de límites respondan con 502 o tiempo de espera, aunque la disponibilidad del proveedor de Android tampoco está garantizada.
- Las solicitudes se limitan: al cambiar de zona y tras 25 segundos como mínimo, reintento de 60 segundos si falla y refresco periódico de 180 segundos. Sin almacenamiento de recorridos, sin ubicación en segundo plano.
- La geocodificación puede enviar coordenadas al proveedor de Android. La explicación de permisos GPS informa de ello.
- El nombre es orientativo y no se utiliza para inferir límites legales. «N/D» continúa apareciendo cuando no hay un límite verificado de la vía.
- No se utilizan peticiones repetidas a la API central de OSM como sustituto de Overpass por la política de uso del servicio.
- Conserva el diseño 1.14, las alertas y listas de vuelos, pantalla encendida y solapa inferior plegable. QA aislada, sin modificar Quick Trip, Mapa QT, Supabase, producción ni main.
- Falta validación funcional física en Samsung.

## v1.16.0 — Nombre de calle discreto y centrado
- Reemplaza la franja anterior por una píldora de 28 dp de alto, centrada sobre las tarjetas «Velocidad actual» y «Límite del tramo», alineada sin invadir el ancho del botón de GPS.
- Solo aparece el nombre de la calle en negrita 12 sp, una línea y elipsis si excede 235 dp. Sin encabezado grande ni textos adicionales.
- Fondo tenue, borde sutil y mínimo espacio vertical: menos de la mitad del alto de las tarjetas de velocidad.
- Conserva información de accesibilidad que aclara que la calle es aproximada y no garantiza un límite de velocidad.
- Cambio solo visual, sin alterar Geocoder, GPS, límites OSM, avisos, vuelos, pantalla encendida o solapa inferior.
- Rama QA aislada; no se modifica main, producción, Supabase, Mapa QT ni Quick Trip.

## v1.17.0 — Límites de velocidad locales sin servidor en carretera (QA)
- Corrige la causa observada en Samsung: Overpass devolvía «Fuente cartográfica sin respuesta» y mantenía N/D aunque el nombre de la avenida funcionara.
- La compilación incorpora una **instantánea local** de tramos transitables de OpenStreetMap (descarga Geofabrik Uruguay) recortada a una zona que abarca Montevideo y buena parte de Canelones: latitud -35.15 a -34.05, longitud -57.3 a -55.2. No se anuncian límites nacionales fuera de esa cobertura.
- El archivo se genera durante GitHub Actions y se integra en la APK. Se divide en celdas de 0.01° (~1 km). La app abre únicamente la celda del GPS actual en un hilo secundario, con una caché mínima.
- Incluye calles con y sin etiqueta numérica de límite para impedir tomar el límite de una avenida vecina cuando la vía actual no tiene `maxspeed` registrado.
- Si la calle más cercana tiene `maxspeed` legible, aparece el valor. Si no hay límite etiquetado, las vías son ambiguas, el GPS es impreciso o la calle está fuera del área, sigue mostrando N/D con motivo detallado. Las señales reales prevalecen. Los datos requieren actualización mediante futuras versiones.
- Una vez instalada la APK, **consultar límites no requiere conexión ni envía GPS a Overpass**. El geocodificador Android que muestra el nombre de la calle sigue pudiendo requerir red, independientemente del límite.
- Se conserva la franja mínima con nombre de calle, el velocímetro, aviso rojo por exceso, vibración optativa, vuelos, pantalla encendida y la solapa inferior plegable.
- Fuente, licencia y atribución: © OpenStreetMap contributors; datos procesados a partir del extracto Geofabrik Uruguay, licencia ODbL 1.0.
- QA aislada; ningún cambio en Quick Trip, Mapa QT, Supabase, producción ni main.

## v1.18.0 — Estabilidad de calle y límite durante la conducción
- Problema reproducido en Samsung (capturas): Avenida Italia se mostró con velocidad GPS 52 km/h y límite 60 km/h; minutos antes/continuando por la misma avenida, «GPS entre calles con distintos límites» mostraba N/D y «Buscando nombre de calle…».
- El emparejador del mapa offline **agrupa segmentos OSM del mismo way ID** antes de decidir si realmente hay otra calle con distinto límite, evitando evaluar duplicados como vías distintas.
- Si dos calles con límites diferentes se cruzan, **solo con rumbo GPS suficientemente preciso y movimiento** (velocidad GPS >=5 m/s, precisión angular <=25°, precisión horizontal <=15 m) puede favorecer la vía alineada y descartar una calle perpendicular (>=55°). Sin rumbo fiable se mantiene la ambigüedad N/D.
- **Nunca resuelve calles paralelas con distinto límite mediante rumbo**, ni fuerza una velocidad en una transición real entre segmentos del mismo nombre que tienen valores distintos.
- El nombre de calle puede usar el segmento cartográfico cercano aunque el límite sea ambiguo, sin transformar un nombre aproximado en límite oficial.
- La etiqueta de calle evita parpadeos durante lecturas breves (máximo 4,5 s, desplazamiento menor a 55 m); esto no conserva un valor de límite previo ni mantiene activas alertas con datos inciertos.
- Cápsula de calle aumentada a 34 dp, tipografía «sans-serif-medium» 14 sp, máximo 275 dp de ancho, una sola línea y elipsis, manteniendo el panel compacto para Samsung.
- Pruebas unitarias: avenida con cruce perpendicular, rumbo norte/este, calles paralelas con distintos límites, cambio real de límite en una avenida y segmentos repetidos. No se modifican fuentes de vuelos, notificaciones, precios, GPS en segundo plano, Quick Trip, Mapa QT, Supabase, producción ni main.
- No garantiza coincidencia con señales oficiales ni cobertura OSM completa. Validación física final en Samsung pendiente; probar únicamente con un acompañante o el vehículo estacionado.

## v1.19.0 — Mejoras conservadoras para 75/N/D intermitentes en Avenida Italia
- Incidencia real en Samsung: conduciendo por Avenida Italia el límite cartográfico cambia reiteradamente entre 75 km/h y «N/D».
- Auditoría del extracto Geofabrik Uruguay: en 256 ways con nombre Avenida Italia se observaron etiquetas de límite 30 (18 ways), 45 (101), 60 (54), 75 (34), sin etiqueta (49). Por tanto, **75 existe realmente en OSM**, pero ninguna de esas etiquetas prueba por sí misma el límite oficial vigente en la posición del conductor.
- Se identificaron dos posibles causas técnicas de N/D intermitente: rumbo GPS descartado por falta de metadatos de precisión angular y mensaje de «N/D» emitido siempre que se cargaba la siguiente celda del mapa (~1 km).
- Se incorporó `MovingCourseFilter`: con rumbo GPS preciso se utiliza ese valor; si Android no comunica su precisión, se calcula un rumbo a partir de >=42 m de desplazamiento real a velocidad y precisión verificables. Se descarta parado, con precisión horizontal >12 m, saltos improbables o rumbo calculado de más de 5,5 s.
- En cambios de celda del mapa, **solamente** puede mantenerse un límite cuando los datos de geometría superpuestos de la celda anterior se vuelven a contrastar con la posición actual (hasta 11 m fuera de la celda y <=15 m de la misma calle). No se conserva una velocidad previa sin georreferenciarla.
- Se conservaron los límites en N/D cuando hay vías paralelas o tramos con límites diferentes verdaderamente indistinguibles. No se fija 75 ni se usa el nombre de la calle como prueba de su límite.
- Pruebas JUnit para velocidad nula, precisión mala, desplazamiento mínimo, rumbo disponible y caducidad, y cambios de celda con y sin margen de solapamiento.
- El usuario debe validar físicamente con vehículo estacionado o acompañante y contrastar señales. Vuelos, geocodificador y avisos intactos. Sin tocar main, Quick Trip, Mapa QT, Supabase ni producción.

## v1.20.0 — Corregido el salto involuntario de Hoy en vivo a Consultar fecha
- Captura del Samsung a las 22:27 del 03/11/2026: «HOY · EN VIVO» en gris, «CONSULTAR FECHA» seleccionado, fecha 04/11/2026 a partir de 07:00 y mensaje sin fuente conectada. El código original convertía automáticamente el modo en vivo en manual cuando la fecha mostrada por Avionio difería del día actual.
- Se eliminó ese cambio silencioso: la fecha publicada por el proveedor es metadato, **no autorización para cambiar la selección del usuario**.
- Si el proveedor pasa al día siguiente, la app **mantiene el modo en vivo**, muestra de forma explícita «AHORA · EN VIVO» y la fecha real de las filas, e informa que no se han confirmado los vuelos del día actual. Así no presenta vuelos de mañana como si fueran de hoy.
- La actualización automática continúa cada tres minutos mientras el modo sea en vivo; se suspende solo cuando el usuario selecciona explícitamente Consultar fecha, apaga Auto, o minimiza la aplicación.
- Al tocar Hoy desde una consulta manual o desde un tablero que la fuente avanzó al día siguiente, se ocultan controles de fecha, se cancela la consulta manual anterior y se vuelve a cargar la URL del proveedor.
- Los callbacks de la WebView ya no pisan la vista de fecha manual. No se generan notificaciones para vuelos de mañana mostrados en el panel en vivo.
- Se agregan tests de regresión para cambios automáticos de fecha en la fuente, bloqueo manual explícito y actualización en segundo plano.
- Esta corrección no crea datos de vuelos: si Avionio deja de publicar vuelos de hoy, no puede fabricar arribos y se informa la fecha real del tablero.
- Mantiene el velocímetro y las mejoras de mapa offline de v1.19; sin cambios en producción, main, Quick Trip, Mapa QT, Supabase ni otros motores.
- Rama QA aislada. Falta validación física en Samsung.

## v1.21.0 — Cápsula de calle de lado a lado
- Alineada con las tarjetas «Velocidad actual» y «Límite del tramo»: ocupa todo el ancho disponible de ambas tarjetas, no solo una pequeña pastilla centrada.
- Mantiene libre el control GPS de 70 dp y 7 dp de espacio; ancho flexible adaptable a Samsung.
- Nombre de calle a la izquierda, con 14 dp de margen interno, tipografía legible de 15 sp peso medio, una línea y elipsis si es largo.
- Conserva fondo claro, bordes sutiles y solo 34 dp de altura, sin restar protagonismo a la lista de vuelos.
- Solo layout visual, sin cambios en geocodificación, velocidades, cartografía offline, notificaciones, flujos de vuelos, pantalla encendida ni estados de reserva.
- QA aislada desde v1.20. Main, Supabase, Quick Trip, Mapa QT y producción no se modifican. Falta validar aspecto físico en Samsung.
