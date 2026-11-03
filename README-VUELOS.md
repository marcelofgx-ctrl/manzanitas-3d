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
