## Versión 0.3.2 · paginación progresiva y fiabilidad · 2026-10-19

### Carpetas grandes y memoria
- La vista de carpetas carga lotes de 5.000 entradas. «Cargar siguientes 5.000» pide la siguiente página y conserva lo que ya estaba visible, sin reiniciar la pantalla ni mostrar otra pestaña por detrás.
- En el almacenamiento local, la enumeración se hace con `DirectoryStream`; en árboles SAF, con un cursor del proveedor. Ambas rutas saltan el desplazamiento ya mostrado y retienen solo el lote nuevo durante la consulta, en lugar de crear un array completo del directorio al abrirlo.
- La lista en pantalla acumula los lotes que el usuario solicita, de modo que el consumo crece con la exploración. Algunos proveedores ajenos a las rutas file/SAF-tree solo exponen `DocumentFile.listFiles()`; su fallback todavía puede materializar el resultado entero. Se documenta como limitación, no como soporte universal de paginación.
- El escáner recursivo mantiene por separado el límite de 100.000 elementos y profundidad 20, e informa resultados parciales. Cargar más elementos en una carpeta no convierte un análisis recursivo parcial en un inventario completo.
- La carga inicial ya no depende de un límite acumulativo de 100.000 entradas. Si una consulta se cancela al cambiar de carpeta, la cancelación se propaga y la respuesta obsoleta no reemplaza la ubicación nueva.

### Pruebas y versión
- Se añadieron pruebas unitarias para recorrer una carpeta en páginas sin repetir ni perder entradas y para rechazar desplazamientos negativos.
- Versión Android actualizada a `versionCode 5` y `versionName 0.3.2`.
- El workflow Android ejecuta `testDebugUnitTest`, `assembleDebug`, comprueba el APK y calcula su SHA-256. El estado final de CI debe corresponder al SHA final de `main`.
- La mascota vectorial anime ciberpunk queda visible también en la pantalla de bienvenida antes de conceder permisos, además de la tarjeta de Inicio. El lanzador usa iconos adaptativos de Android con fondo azul noche y el arte de la mascota como primer plano. No se generó una nueva ilustración rasterizada en esta iteración.
- Tras comparar los certificados de APK debug sucesivas, se confirmó que los runners habían generado claves distintas. El workflow prepara/verifica el keystore de depuración en `/home/runner/.android/debug.keystore` y lo conserva mediante caché de Actions; la ruta absoluta evita depender del valor de `HOME` durante la limpieza de Gradle.
- La primera APK con esta caché es una transición de firma: Android puede exigir desinstalar una APK debug anterior antes de instalarla, lo que borra las preferencias locales de Archivero (no los archivos del usuario). Las compilaciones posteriores deben conservar el certificado mientras la caché permanezca disponible.
- Esta es una firma de depuración, no una firma de publicación. No se incorporó ninguna clave de release ni se debe reutilizar el keystore debug como clave de distribución comercial.
- No se probó físicamente contra la biblioteca real de 200 GB ni contra todos los proveedores SAF. Esas validaciones siguen pendientes.

# Registro de cambios de Archivero

## Versión 0.3.1 · reproductor sin pantallas apiladas · 2026-10-19

### Navegación y música
- El reproductor de música ahora ocupa toda la superficie disponible con un fondo opaco. Se elimina el panel parcial que dejaba visible la pestaña anterior detrás y podía confundir los resultados con una segunda pantalla superpuesta.
- La flecha y el botón Atrás regresan a la lista desde la que se eligió la pista; la reproducción y los controles de la notificación continúan activos.
- Al estar en los resultados de una categoría, Atrás vuelve primero al selector de tipos en lugar de saltar directamente a Inicio.
- La lista por tipo conserva el recuento de archivos y el estado del análisis en una sola vista. Carpetas y búsquedas no necesitan mostrar dos pantallas simultáneas.

### Tema y recursos
- Se conserva el lenguaje Ciberpunk definido en 0.3.0: fondo azul noche sólido, paneles azul profundo, texto de alto contraste y acentos cian/magenta moderados. No se añade una retícula brillante ni un fondo decorativo que compita con los archivos.
- Se mantiene la mascota anime local y el icono de aplicación ya integrados en `main`; esta revisión se concentra en navegación y reproductor, sin duplicar esos recursos.

### Validación
- GitHub Actions ejecuta pruebas unitarias y `assembleDebug` para el SHA de esta actualización; el enlace de CI y el hash de la APK deben verificarse antes de declarar PASS.
- La compilación automatizada no sustituye la prueba física de Atrás, listas largas y reproducción en el dispositivo Android.


## Actualización de navegación y tema Ciberpunk · 2026-10-18

### Navegación por tipo sin pantallas superpuestas

- La pestaña «Tipos» abre un selector de categorías; al elegir una categoría, la vista cambia a la lista de resultados de ese tipo. El selector y los resultados ya no compiten por espacio en la misma pantalla.
- Los accesos de categoría del inicio llevan directamente a los archivos de ese tipo.
- Elegir «Música» muestra los archivos de audio encontrados. Ya no abre automáticamente el reproductor completo sobre la lista de resultados; el reproductor se abre cuando el usuario elige una pista, y el mini reproductor queda disponible mientras sigue navegando.
- La navegación por carpetas conserva la ruta de carpetas visitadas para que el botón Atrás del sistema o «Volver» regresen a la carpeta anterior sin saltar inmediatamente a Inicio.
- El acceso a la pestaña «Carpetas» comienza en la ubicación raíz autorizada.

### Contraste visual e identidad anime

- El tema Ciberpunk pasa a utilizar un fondo azul noche sólido, paneles azul profundo, texto de alto contraste y acentos cian/magenta moderados. Se elimina la retícula decorativa de pantalla completa para reducir ruido visual.
- La mascota vectorial local fue rediseñada con rostro anime, cabello oscuro, reflejos cian/magenta y accesorios inspirados en tecnología futurista. Se mantiene integrada en la tarjeta de bienvenida; no requiere red ni descarga de imágenes.
- La apariencia de tarjetas, navegación y mini reproductor respeta ahora las superficies y el contraste de la paleta activa.

### Verificación

- El workflow de Android ejecuta `testDebugUnitTest` y `assembleDebug`; el resultado del commit final y la disponibilidad de la APK deben confirmarse en [GitHub Actions](https://github.com/jonhararagi/archivero/actions/workflows/android.yml).
- La prueba física en el teléfono sigue siendo necesaria para validar navegación, bibliotecas grandes y reproducción en el dispositivo real.


## En preparación · 2026-10-18 · bibliotecas grandes, tema Ciberpunk y apertura de APK

### Problema: cierres al abrir o recorrer muchos archivos

- Se identificó un disparador concreto: la pantalla de limpieza podía iniciar un escaneo recursivo al restaurarse, aunque el usuario solo quisiera abrir la app. El escaneo ahora comienza únicamente cuando se pulsa «Analizar ubicación» o se solicita una acción que necesita el índice.
- La navegación y el escáner ya no dependen de `File.listFiles()` para enumerar carpetas del almacenamiento local. Se usa un iterador de directorio y, para árboles SAF, un cursor del proveedor; cada enumeración se limita a 5.000 elementos. Cuando se alcanza el límite, se muestra un aviso y la búsqueda recursiva comunica que el resultado es parcial.
- El límite global de escaneo continúa siendo 100.000 elementos y la profundidad máxima 20. Los elementos posteriores a un límite no se consideran analizados; no se presenta la lista parcial como un inventario completo.
- Se añadieron pruebas unitarias de directorios que superan el límite y de directorios que tienen exactamente el tamaño máximo.

### Apertura de APK

- Los archivos `.apk` se envían al selector/instalador de Android con `application/vnd.android.package-archive`, incluso cuando el proveedor devuelve un MIME genérico.
- Se declara `REQUEST_INSTALL_PACKAGES` para permitir que el sistema presente su flujo de instalación. Archivero no instala APK en silencio; Android conserva el consentimiento y puede requerir habilitar «Instalar apps desconocidas» para Archivero. Los formatos `.xapk` y `.apks` pueden requerir una aplicación instaladora compatible.

### Apariencia

- El tema Ciberpunk pasa a ser el predeterminado para instalaciones nuevas: fondo azul noche, retícula discreta cian y acentos magenta/cian. Se conservan las paletas anteriores y el fondo personalizado.

### Referencias técnicas públicas

- Android Developers · problemas de ANR y bloqueos de interfaz: https://developer.android.com/topic/performance/issues/anr
- Android Developers · consumo de memoria de bitmaps: https://developer.android.com/topic/performance/issues/bitmap-memory-usage
- Android Developers · archivos compartidos y Storage Access Framework: https://developer.android.com/training/data-storage/shared/documents-files
- Android API · permiso para solicitar instalación de paquetes: https://developer.android.com/reference/android/Manifest.permission#REQUEST_INSTALL_PACKAGES

### Validación pendiente

- GitHub Actions debe pasar pruebas unitarias y compilar la APK sobre el commit resultante antes de distribuirla.
- El límite de enumeración protege frente a carpetas con muchísimos hijos, pero una prueba en el Xiaomi con la biblioteca real y una captura de Logcat siguen siendo necesarias para atribuir cualquier cierre residual.
- No se afirma que los límites cubran absolutamente todos los proveedores de documentos; el fallback de proveedores no SAF sigue usando su propia implementación de `DocumentFile.listFiles()`.

## En preparación · 2026-10-18 · actualización de reproducción y fiabilidad


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
