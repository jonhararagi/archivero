# Archivero 📁

Administrador de archivos Android visual y sencillo, pensado para móvil y con operaciones de limpieza que requieren revisión del usuario.

## Estado actual

La aplicación combina dos modos de acceso:

- **Acceso a todo el almacenamiento:** en Android 11 o posterior, se solicita habilitar el permiso especial desde Ajustes para recorrer las carpetas compartidas del teléfono.
- **Carpeta específica:** se puede elegir una ubicación con el selector del sistema Android sin conceder acceso global. El análisis queda limitado a esa ubicación.

Los archivos se procesan localmente. La aplicación no tiene una función de subida de archivos a un servidor.

## Funciones incluidas

- Pantalla de bienvenida con la mascota anime ciberpunk visible antes de otorgar permisos, tarjeta de Inicio con personaje y accesos directos por categoría; icono adaptativo de Android con fondo azul noche y el arte vectorial de Archivero como primer plano.
- Categorías de imágenes, vídeos, música, documentos, APK, ZIP y otros formatos; al abrir un tipo se muestra solo su lista de resultados, con búsqueda por nombre y opción clara para cambiar de categoría.
- Tema Ciberpunk con fondo azul noche liso, paneles azul profundo, texto claro y acentos cian/magenta usados con moderación; las seis paletas y el fondo personalizado se mantienen.
- Apertura de `.apk` con el instalador externo de Android y MIME de paquete explícito. Archivero no instala automáticamente: el sistema puede pedir habilitar «Instalar apps desconocidas» y siempre muestra su propia confirmación.
- Selección de varios archivos de audio para crear una cola de reproducción.
- Reproductor Media3 con reproducción en segundo plano, notificación de controles, anterior/siguiente, posición, aleatorio, repetición, favoritos y temporizador.
- La categoría Música abre primero la lista de archivos y muestra el estado del análisis y el recuento de resultados en esa misma vista. Al elegir una pista, el reproductor se abre a pantalla completa con fondo opaco, sin dejar la pestaña anterior visible detrás; Atrás o la flecha devuelve a la lista sin detener la música. El mini reproductor permite reabrirlo y los controles de Android siguen disponibles en la notificación.
- Miniaturas reales para imágenes compatibles y la primera página de PDF en las listas de archivos y limpieza; otros formatos muestran iconos por tipo y extensión.
- Las miniaturas se decodifican fuera del hilo principal, se reducen a un tamaño pequeño y usan una caché en memoria limitada para controlar el uso de RAM.
- Vista previa integrada para formatos compatibles, además de la opción de abrir un archivo con otra aplicación.
- Seis paletas visuales, incluido Ciberpunk, y fondo local persistente. La identidad Ciberpunk usa un fondo principal definido y superficies contrastantes, no una rejilla brillante detrás de cada elemento.
- Búsqueda recursiva manual con progreso, límite de 100.000 elementos y profundidad máxima 20. Entrar a Limpieza no inicia un escaneo automático; debe tocarse «Analizar ubicación». La vista de carpetas carga lotes de 5.000 elementos y permite continuar sin sustituir toda la lista por un array ilimitado. En rutas de almacenamiento local usa `DirectoryStream` y en árboles SAF usa un cursor con desplazamiento; cada lote se acumula solo cuando el usuario lo solicita. Los proveedores ajenos a estos dos caminos pueden obligar a `DocumentFile.listFiles()`, que sí puede materializar todos sus resultados.
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

## Firma de los APK generados por CI

Las compilaciones `debug` de GitHub Actions conservan la clave de depuración mediante la caché del workflow para que futuras APK de CI puedan actualizarse unas sobre otras. La primera versión tras activar esta caché usa una clave nueva con respecto a los artefactos anteriores: Android puede requerir desinstalar la versión debug anterior antes de instalarla, lo que restablece la configuración local de Archivero. Los archivos guardados por el usuario no se borran. Este mecanismo no convierte el APK en una versión release firmada para distribución pública.

## Compilar

Requiere JDK 17 y Android SDK 36 (el workflow instala `platforms;android-36` y `build-tools;36.0.0`). Abre el proyecto en Android Studio y sincroniza Gradle.

[Ver compilaciones y descargar el APK generado por GitHub Actions](https://github.com/jonhararagi/archivero/actions/workflows/android.yml)
