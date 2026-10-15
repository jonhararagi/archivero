# Archivero 📁

Administrador de archivos Android sencillo, accesible y respetuoso con los permisos.

## Estado
Primera versión funcional en desarrollo. Esta versión usa el selector de carpetas de Android (Storage Access Framework): el usuario elige la ubicación a administrar y la aplicación no solicita acceso general a todo el almacenamiento.

## Incluido
- Interfaz Material 3 pensada para móvil.
- Selección explícita de una carpeta con permisos persistentes de Android.
- Navegación por carpetas y listado ordenado de archivos.
- Búsqueda rápida dentro de la carpeta actual, con estado vacío y limpieza de búsqueda.
- Apertura de archivos mediante aplicaciones compatibles instaladas.
- Reproductor integrado para audio y video, con cierre explícito.
- Recuperación de la carpeta seleccionada si Android recrea el proceso, con aviso seguro si el permiso dejó de estar disponible.
- Creación de carpetas.
- Eliminación de elementos mediante el proveedor de documentos de Android.
- Tamaño de archivo legible y estados vacíos.

## Validación y límites conocidos
La compilación de depuración se valida en GitHub Actions y se publica como artefacto descargable. Todavía hace falta probar el flujo en un teléfono real, especialmente permisos revocados, proveedores de documentos de terceros, archivos grandes y formatos sin aplicación compatible. Android puede limitar las operaciones que cada proveedor permite; la aplicación debe mostrar un error y no asumir que borrar o crear siempre tendrá éxito.

## Próximos pasos
Ordenar/filtrar, selección múltiple, copiar/mover, renombrar, papelera cuando el proveedor lo permita, ZIP/extraer y pruebas instrumentadas en dispositivos reales.

## Compilar
Requiere JDK 17 y Android SDK 35. Abre el proyecto en Android Studio y sincroniza Gradle. También se incluye un workflow de GitHub Actions para compilar el APK de depuración y publicarlo como artefacto.
