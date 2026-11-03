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
