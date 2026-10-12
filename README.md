# Manzanitas 3D — El bosque encantado

Primer prototipo gratuito e independiente, con representación 3D mediante WebGL nativo.

Jugar en navegador: https://marcelofgx-ctrl.github.io/manzanitas-3d/

## Objetivo
Recoger las manzanas que caen de los árboles, transportar hasta tres por viaje y entregarlas en el granero. Evitar al ogro y usar el poder de espantar. Completar el objetivo antes de agotar tiempo y vidas.

## Web
El juego es un único archivo `index.html` autónomo (excepto tipografías opcionales). Se publica mediante `.github/workflows/publicar-web.yml` en GitHub Pages. Para habilitar Pages por primera vez, entrar a Settings → Pages → Build and deployment → Source: GitHub Actions.

## APK
La acción `Compilar Manzanitas 3D APK` genera una APK debug instalable desde el artefacto `Manzanitas-3D-APK` de GitHub Actions. Incluye el juego como página WebView local, sin conexiones a Floot, servidores ni anuncios. No es una compilación de Google Play firmada para producción.

## Pendiente
Pruebas reales de WebGL y controles en Android, ajustes de dificultad, animaciones, y más adelante plan de monetización sujeto a cumplimiento normativo.
