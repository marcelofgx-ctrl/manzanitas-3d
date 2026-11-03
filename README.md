# Vuelos Uruguay Premium v1.2
Aplicación Android autónoma y sin relación con Quick Trip. Fuente de datos: widget embebible Avionio (Carrasco MVD) con crédito visible.

## Interfaz
Cabecera y controles premium, icono de avión y ruta verde menta, pestañas de llegadas y salidas, hora de Uruguay, estado de consulta, refresco manual y automático cada 3 minutos mientras la APK está abierta (toggable: AUTO 3 MIN / AUTO OFF).

## Límites
Esto no es una API de tiempo real ni una base de datos propia. Cada recarga vuelve a pedir el widget Avionio, cuya exactitud y velocidad de actualización son controladas por ese proveedor. Los vuelos de código compartido no son aviones adicionales.
La consulta requiere conexión a Internet. La APK no incorpora anuncios propios ni recopila datos de ubicación, ni incluye accesos a otras plataformas publicitarias.

## Instalación y aislamiento
ID Android: uy.vuelos.uruguay; versión 1.2, versión code 3. Al compilar con firma debug en CI, puede requerirse desinstalar la v1.1 antes de instalar v1.2.
Se compila en una rama QA aislada. No modifica ninguno de los repositorios, servidores, proyectos o aplicaciones Quick Trip, y no modifica la rama main.
