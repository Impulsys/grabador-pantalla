# Grabador Impulsys

App nativa Android para **grabar la pantalla + el micrófono** del celular y hacer videos. Sin publicidad, sin marca de agua, sin cuenta.

## Cómo funciona
- Un botón grande: **Grabar / Detener**.
- Graba pantalla (H.264) + audio del **micrófono** (AAC) en un `.mp4`.
- El video queda en **Galería › Movies › Grabador**.

## Sonido
En Android 9 (ej. Moto G6) las apps solo pueden capturar el **micrófono**, no el audio interno.
Para que se escuche el sonido de una app/video: subí el volumen del parlante y el micrófono lo capta.
En Android 10+ el micrófono sigue siendo la fuente (v1).

## Compilar
El APK se compila solo en **GitHub Actions** en cada push a `main`.
Descarga directa: pestaña **Releases › latest › grabador-impulsys.apk**.

## Instalar en el celular
1. Abrí el link del `.apk` desde el navegador del celular.
2. Aceptá "instalar apps de orígenes desconocidos" si lo pide.
3. Abrí la app, dale permisos de micrófono y "grabar pantalla".

Stack: Kotlin · MediaProjection · MediaRecorder · minSdk 26 · targetSdk 33
