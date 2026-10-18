# Revisión técnica de Archivero

Fecha: 2026-10-18  
Rama autorizada: `main`  
Perfil de referencia: Android de gama media con 8 GB de RAM.

## Resumen ejecutivo

Archivero es un administrador de archivos Android en Kotlin/Jetpack Compose. Añadir un motor de juegos o un motor gráfico generalista no resolvería los problemas de URI, ciclo de vida, lectura de archivos ni reproducción y aumentaría el peso y la superficie de fallos. Mantener Kotlin + Compose + AndroidX Media3 es la ruta adecuada. Incorporar Room o una biblioteca de imágenes solo se justifica si una medición demuestra que el índice persistente o la gestión de miniaturas lo necesitan.

## Cambios ya enviados a main en esta tanda

- Panel de bienvenida anime kawaii e ilustración vectorial local, sin descarga de imágenes.
- Botón Atrás con navegación por pantallas y confirmación de salida.
- Salida confirmada usando `finishAndRemoveTask()` para retirar la tarea de Recientes.
- Pausa del reproductor de vídeo en `onStop()`, evitando que continúe sonando al abandonar Archivero. La música se mantiene separada en `MusicPlaybackService` para permitir reproducción de fondo.

Los commits y las compilaciones se verifican en GitHub Actions. Un CI verde valida compilación/pruebas automatizadas, no la experiencia en un Xiaomi físico. No declarar resueltos los fallos de miniaturas, PDF, selección de archivos, audio ni cierres inesperados hasta probarlos en dispositivo.

## Fuentes técnicas y aplicación al proyecto

1. **Android Developers: ANRs**  
   https://developer.android.com/topic/performance/issues/anr  
   Android explica que el hilo principal debe poder procesar entrada y dibujar; una tarea larga en UI puede provocar ANR. Aplicación: operaciones de archivos, lectura, hashes, miniaturas y renderizado deben permanecer fuera del hilo principal; revisar trazas y reproducir el fallo antes de atribuir una causa.

2. **Android Developers: uso de memoria de bitmaps**  
   https://developer.android.com/topic/performance/issues/bitmap-memory-usage  
   Los bitmaps pueden dominar el consumo de memoria; la documentación recomienda reducir dimensiones al contenedor, evitar duplicados y medir con Memory Profiler. Aplicación: mantener decodificación muestreada y caché acotada; comprobar imágenes de alta resolución y rotación/cambio de pantalla en dispositivo real.

3. **Android Developers: reproducción de fondo con MediaSessionService**  
   https://developer.android.com/media/media3/session/background-playback  
   Media3 recomienda mantener Player y MediaSession dentro de un servicio separado para reproducción de fondo. Aplicación: conservar `MusicPlaybackService`; no mover el reproductor de música a MainActivity. El vídeo es una sesión separada y se pausa al salir del primer plano.

## Riesgos pendientes que requieren reproducción y evidencia

- **Miniaturas / URI:** comprobar imágenes desde almacenamiento compartido, SAF (`content://`), proveedores de nube y archivos con permiso revocado. Evitar asumir que una ruta `file://` representa todos los documentos.
- **PDF:** probar archivos grandes, varias páginas y rotación; comprobar contraste y que el descriptor/PdfRenderer se cierre incluso cuando falla el renderizado.
- **Música:** probar siguiente/anterior, archivos no compatibles, pantalla bloqueada, notificación, cambio de aplicación y permisos persistentes. Recoger el error Media3 real si se detiene.
- **Vídeo:** probar codecs no compatibles, salir con Atrás, ir a Inicio y volver a la app. El nuevo `onStop()` pausa el vídeo, pero debe comprobarse el comportamiento real.
- **Cierre:** probar Atrás desde cada sección, paneles, reproductor y diálogo; confirmar que la tarea desaparece de Recientes. No usar `killProcess` ni finalizar el proceso a la fuerza.
- **Limpieza:** usar archivos de prueba prescindibles; comprobar proveedores que rechazan `delete()`. No borrar automáticamente sin revisión del usuario.
- **APK:** descargar el artefacto del workflow que corresponda exactamente al SHA de `main` y comprobar instalación/arranque en dispositivo.

## Criterio de aceptación para declarar PASS

1. CI de Android exitoso en el SHA final de `main`.
2. APK de ese mismo workflow disponible y descargable.
3. Prueba manual en dispositivo: navegar/volver/salir, abrir imagen y PDF, reproducir/pausar/saltar música, abrir y abandonar vídeo, cambiar de app y volver.
4. Sin ANR/cierre forzado durante la prueba; registrar modelo Android, pasos, resultado y logcat si falla.

## TIMER estimado para el siguiente ciclo de validación

- 15 min: revisar CI y descargar APK del SHA final.
- 30–45 min: pruebas manuales de navegación, imágenes, PDF y vídeo.
- 20–30 min: pruebas de música, permisos y cambio de aplicación.
- 20–40 min por fallo reproducible: capturar logs, corregir, probar y generar otro APK.

Estimación total de validación inicial: **85–130 min**, sin contar correcciones imprevistas. Las pruebas físicas requieren el teléfono y no pueden afirmarse desde CI.
