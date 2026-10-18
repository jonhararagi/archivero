# Registro de cambios de Archivero

## En preparación · 2026-10-18 · actualización de reproducción y fiabilidad

### Dependencias multimedia

- Actualizados conjuntamente `media3-exoplayer`, `media3-session` y `media3-ui` de 1.5.1 a 1.10.0. Mantener los tres módulos en la misma versión evita mezclas de componentes de una misma biblioteca.
- La actualización se basa en las notas oficiales de Media3 1.10, que anuncian correcciones y mejoras de reproducción, mejoras de compatibilidad con formatos en ExoPlayer y ajustes de eficiencia. Esto no permite afirmar que un fallo concreto del Xiaomi esté solucionado hasta reproducirlo y probarlo en el dispositivo.
- Fuente del proveedor: https://developer.android.com/blog/posts/media3-1-10-is-out
- Arquitectura de reproducción en segundo plano: https://developer.android.com/media/media3/session/background-playback

### Almacenamiento, miniaturas y ANR

- Se conserva el acceso mediante Storage Access Framework y permisos persistentes para carpetas/documentos elegidos por el usuario; los URI `content://` no deben tratarse como rutas de archivo ordinarias.
- Se mantiene la decodificación muestreada y el trabajo de lectura/renderizado fuera del hilo principal. Android advierte que bitmaps grandes pueden causar jank, ANR y cierres por falta de memoria.
- Fuentes oficiales: https://developer.android.com/training/data-storage/shared/documents-files · https://developer.android.com/topic/performance/issues/bitmap-memory-usage · https://developer.android.com/topic/performance/issues/anr

### Verificación requerida

- La actualización de dependencias debe pasar pruebas unitarias y compilación CI antes de distribuir el APK.
- Después de CI, probar en el Xiaomi: reproducción y salto de pistas, música con pantalla bloqueada/cambio de aplicación, vídeo al salir, imágenes grandes, PDF multipágina y permisos SAF. El CI no simula todos los proveedores ni sustituye la prueba física.

## Sin publicar · 2026-10-18

### Cierre y reproducción de vídeo

- Al confirmar la salida desde Inicio, Archivero ahora solicita a Android retirar su tarea de Recientes con `finishAndRemoveTask()`. La música puede seguir en el servicio Media3 cuando está reproduciéndose.
- El reproductor de vídeo pausa al abandonar el primer plano; evita que un vídeo siga sonando si el usuario vuelve al inicio del teléfono o cambia de aplicación. Al destruirse la pantalla, ExoPlayer se libera como antes.
- Esto refuerza el ciclo de vida, pero no sustituye una prueba en el Xiaomi ni garantiza que cualquier cierre inesperado haya quedado resuelto.

### Interfaz anime y salida de la aplicación

- Se agregó una ilustración vectorial anime original, dibujada para Archivero y empaquetada localmente; no necesita conexión ni descarga de imágenes externas.
- La pantalla de inicio muestra un panel de bienvenida con la mascota, manteniendo los accesos y las herramientas de archivos existentes.
- El botón Atrás del sistema ahora vuelve desde las secciones internas a Inicio, cierra primero los paneles de apariencia/reproductor y, desde Inicio, pregunta si se desea salir.
- La confirmación de salida informa que la reproducción de música puede continuar en segundo plano. La liberación del reproductor sigue siendo responsabilidad del servicio Media3.

### Validación pendiente

- Los cambios de cierre/vídeo se han enviado directamente a `main`; verificar el resultado de CI y probar en Android real.
- El panel anime no demuestra por sí solo que se hayan corregido todos los cierres inesperados. Hace falta instalar la APK resultante y probar navegación, reproducción, permisos, miniaturas y operaciones largas en Android real.
- La confirmación de salida es navegación controlada; no fuerza la terminación del proceso ni mata servicios de Android.

## Sin publicar · 2026-10-17

Esta tanda nació de revisar el código y reforzar operaciones que podían fallar en archivos grandes o en ZIP creados por terceros.

### Miniaturas

- Las listas de archivos y la galería de limpieza muestran miniaturas de imágenes raster compatibles y de la primera página de PDF.
- Otros archivos conservan iconos por tipo y la extensión como fallback; no se afirma que todos los formatos binarios tengan una vista previa real.
- La generación de miniaturas se hace en Dispatchers.IO y usa decodificación muestreada con caché LRU de memoria de 8 MiB para limitar RAM y evitar trabajo repetido mientras se navega.

### Correcciones de fiabilidad

- Las operaciones de copiar, mover, comprimir y extraer ahora se ejecutan en Dispatchers.IO, para evitar bloquear la interfaz mientras se leen o escriben archivos.
- Si copiar o comprimir falla tras crear un destino, Archivero intenta eliminar el archivo parcial y comunica cuando Android tampoco permite limpiarlo.
- Si mover no puede eliminar el original tras copiar correctamente, se conserva la copia completa y se informa claramente para evitar pérdida de datos.
- Mover un archivo a la misma carpeta ya no crea una copia con otro nombre y elimina el original.
- Las entradas de directorio ZIP se procesan como rutas completas, sin volver a crear la última carpeta dentro de sí misma.
- La extracción rechaza rutas absolutas Unix/Windows, segmentos de recorrido .. y rutas excesivamente profundas. No sobrescribe archivos existentes.
- La extracción tiene límites de 5.000 entradas, 256 MiB por archivo y 512 MiB totales descomprimidos. Si falla, intenta retirar todos los archivos y carpetas creados durante esa operación.
- Se agregaron pruebas unitarias para nombres/rutas ZIP legítimos, separadores Windows, marcadores de directorio actual, rutas absolutas, traversal, NUL y profundidad.

### Validación

GitHub Actions ejecuta ahora testDebugUnitTest antes de construir el APK. El éxito de estas pruebas y de la compilación no sustituye las pruebas con proveedores reales de documentos ni en un teléfono Android.

### Límites que siguen abiertos

- El análisis recursivo de directorios, la reproducción en segundo plano y los permisos de borrado necesitan pruebas funcionales en dispositivos reales.
- Algunos proveedores de Android no permiten crear o borrar todos los elementos. Archivero muestra el error, pero la limpieza de una operación fallida sigue dependiendo de lo que permita ese proveedor.
- Los límites de extracción son deliberadamente conservadores para evitar archivos ZIP excesivos en dispositivos móviles.
