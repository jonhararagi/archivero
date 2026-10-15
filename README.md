# Archivero 📁

Administrador de archivos Android sencillo, accesible y respetuoso con los permisos.

## Estado
Primera versión funcional en desarrollo. Esta versión usa el selector de carpetas de Android (Storage Access Framework): el usuario elige la ubicación a administrar y la aplicación no solicita acceso general a todo el almacenamiento.

## Incluido
- Interfaz Material 3 pensada para móvil.
- Selección explícita de una carpeta con permisos persistentes de Android.
- Navegación por carpetas y listado ordenado de archivos.
- Apertura de archivos mediante aplicaciones compatibles instaladas.
- Creación de carpetas.
- Eliminación de elementos mediante el proveedor de documentos de Android.
- Tamaño de archivo legible y estados vacíos.

## Próximos pasos
Búsqueda, ordenar/filtrar, selección múltiple, copiar/mover, renombrar, papelera cuando el proveedor lo permita, ZIP/extraer y pruebas instrumentadas en dispositivos reales.

## Compilar
Requiere JDK 17 y Android SDK 35. Abre el proyecto en Android Studio y sincroniza Gradle. También se incluye un workflow de GitHub Actions para compilar el APK de depuración y publicarlo como artefacto.
