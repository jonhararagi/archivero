# Registro de cambios de Archivero

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
