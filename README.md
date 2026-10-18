# Archivero 📁

Administrador de archivos Android visual y sencillo, pensado para móvil y con operaciones de limpieza que requieren revisión del usuario.

## Estado actual

La aplicación combina dos modos de acceso:

- **Acceso a todo el almacenamiento:** en Android 11 o posterior, se solicita habilitar el permiso especial desde Ajustes para recorrer las carpetas compartidas del teléfono.
- **Carpeta específica:** se puede elegir una ubicación con el selector del sistema Android sin conceder acceso global. El análisis queda limitado a esa ubicación.

Los archivos se procesan localmente. La aplicación no tiene una función de subida de archivos a un servidor.

## Funciones incluidas

- Inicio visual con información de almacenamiento, mascota anime cyberpunk original de cabello oscuro con detalles cian/magenta y accesos que llevan directamente a la categoría elegida.
- Categorías de imágenes, vídeos, música, documentos, APK, ZIP y otros formatos; al abrir un tipo se muestra solo su lista de resultados, con búsqueda por nombre y opción clara para cambiar de categoría.
- Tema Ciberpunk con fondo azul noche liso, paneles azul profundo, texto claro y acentos cian/magenta usados con moderación; las seis paletas y el fondo personalizado se mantienen.
- Apertura de `.apk` con el instalador externo de Android y MIME de paquete explícito. Archivero no instala automáticamente: el sistema puede pedir habilitar «Instalar apps desconocidas» y siempre muestra su propia confirmación.
- Selección de varios archivos de audio para crear una cola de reproducción.
- Reproductor Media3 con reproducción en segundo plano, notificación de controles, anterior/siguiente, posición, aleatorio, repetición, favoritos y temporizador.
- Mini reproductor inferior disponible solo después de iniciar música: abrir la categoría Música muestra primero los archivos de audio, sin superponer automáticamente el reproductor sobre los resultados. Al reproducir una pista, el mini reproductor permite volver al reproductor completo sin detenerla.
- Miniaturas reales para imágenes compatibles y la primera página de PDF en las listas de archivos y limpieza; otros formatos muestran iconos por tipo y extensión.
- Las miniaturas se decodifican fuera del hilo principal, se reducen a un tamaño pequeño y usan una caché en memoria limitada para controlar el uso de RAM.
- Vista previa integrada para formatos compatibles, además de la opción de abrir un archivo con otra aplicación.
- Seis paletas visuales, incluido Ciberpunk, y fondo local persistente. La identidad Ciberpunk usa un fondo principal definido y superficies contrastantes, no una rejilla brillante detrás de cada elemento.
- Búsqueda recursiva con progreso, límite de 100.000 elementos y profundidad máxima 20. Entrar a Limpieza no inicia un escaneo automático; debe tocarse «Analizar ubicación». Cada carpeta se enumera con un máximo de 5.000 elementos para contener uso de memoria y se indica cuando los resultados son parciales.
- Limpieza revisable de capturas identificables de más de 90 días, vídeos de 100 MB o más y duplicados idénticos.
- La detección de duplicados agrupa primero por tamaño y confirma el contenido mediante SHA-256; no usa el nombre como prueba.
- La limpieza no elimina archivos automáticamente. El usuario revisa y marca elementos y confirma la acción. En los grupos de duplicados, se exige conservar al menos una copia.
- Mensajes de error cuando Android o un proveedor de documentos no permite leer, crear o eliminar un elemento.

## Correcciones de fiabilidad recientes

La revisión encontró y corrigió errores en las operaciones con archivos: ahora copiar/mover/comprimir/extraer se realizan fuera del hilo visual, las copias parciales se limpian cuando es posible, y la extracción ZIP valida rutas, impide sobrescrituras y limita el tamaño descomprimido. Mover un archivo a la carpeta donde ya está se rechaza para evitar duplicados confusos.

El workflow incorpora pruebas unitarias de seguridad para rutas ZIP antes de generar el APK. Consulta el [registro de cambios](CHANGELOG.md) para conocer los límites y los casos cubiertos.

## Límites y validación

El workflow de GitHub Actions ejecuta pruebas unitarias de seguridad para rutas ZIP, compila el APK de depuración y lo publica como artefacto descargable. Estas pruebas **no equivalen a una prueba funcional en un teléfono**; todavía no hay pruebas instrumentadas que cubran los permisos y proveedores reales de Android.

Aún se deben comprobar en Android real:
- reproducción tras minimizar la app, bloquear la pantalla y cambiar entre Wi-Fi/datos; algunos ajustes de batería de Android/MIUI pueden restringir servicios en segundo plano;
- selección y eliminación con proveedores de documentos distintos, permisos revocados y tarjetas SD;
- lectura de directorios muy grandes y comportamiento cuando se alcanza el límite de escaneo;
- archivos dañados, formatos no compatibles, rendimiento de miniaturas en carpetas con muchos elementos y operaciones sobre almacenamiento externo.

El análisis de capturas depende de que Android informe una fecha y de que el nombre o la ruta permita reconocerlas. Algunos archivos no son legibles mediante el proveedor elegido; no se deben clasificar como duplicados si no se puede leer su contenido. En dispositivos con muchos archivos, comparar SHA-256 puede consumir tiempo, batería y almacenamiento de E/S.

## Compilar

Requiere JDK 17 y Android SDK 35. Abre el proyecto en Android Studio y sincroniza Gradle.

[Ver compilaciones y descargar el APK generado por GitHub Actions](https://github.com/jonhararagi/archivero/actions/workflows/android.yml)
