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
