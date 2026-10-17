# Registro de cambios de Archivero

## Sin publicar · 2026-10-17

Esta tanda nació de revisar el código y reforzar operaciones que podían fallar en archivos grandes o en ZIP creados por terceros.

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
