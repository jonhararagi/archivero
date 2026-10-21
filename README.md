# Archivero 📁

Administrador de archivos Android visual y sencillo, pensado para móvil y con operaciones de limpieza que requieren revisión del usuario.

## Estado actual

La aplicación combina dos modos de acceso:

- **Acceso a todo el almacenamiento:** en Android 11 o posterior, se solicita habilitar el permiso especial desde Ajustes para recorrer las carpetas compartidas del teléfono.
- **Carpeta específica:** se puede elegir una ubicación con el selector del sistema Android sin conceder acceso global. El análisis queda limitado a esa ubicación.

Los archivos se procesan localmente. La aplicación no tiene una función de subida de archivos a un servidor.

## Funciones incluidas

- Pantalla de bienvenida, Inicio y estados vacíos con la mascota capibara kawaii subida a `main` (`pngtuber-capibara.png`), integrada como recurso local. No necesita red para mostrarse.
- Categorías de imágenes, vídeos, música, documentos, APK, ZIP y otros formatos; al abrir un tipo se muestra solo su lista de resultados, con búsqueda por nombre y opción clara para cambiar de categoría.
- Tema predeterminado **Capibara kawaii**, inspirado en la mascota: cacao y marrón suave, crema, beige cálido y coral rosado; tipografía oscura y superficies claras para facilitar la lectura. La paleta Ciberpunk y las demás alternativas siguen disponibles; el icono adaptativo también se armonizó con tonos cálidos. Hay siete paletas y fondo personalizado.
- Apertura de `.apk` con el MIME de paquete Android y el flujo de confirmación del sistema. Los contenedores `.xapk` y `.apks` se abren como archivos comprimidos; necesitan un instalador compatible y Archivero no afirma instalarlos directamente.
- Selección de varios archivos de audio para crear una cola de reproducción.
- Reproductor Media3 con reproducción en segundo plano, notificación de controles, anterior/siguiente, posición, aleatorio, repetición, favoritos y temporizador.
- La categoría Música abre primero la lista de archivos y muestra el estado del análisis y el recuento de resultados en esa misma vista. Al elegir una pista, el reproductor se abre a pantalla completa con fondo opaco, sin dejar la pestaña anterior visible detrás; Atrás o la flecha devuelve a la lista sin detener la música. El mini reproductor permite reabrirlo y los controles de Android siguen disponibles en la notificación.
- Miniaturas reales para imágenes compatibles y la primera página de PDF en las listas de archivos y limpieza; otros formatos muestran iconos por tipo y extensión.
- Las miniaturas se decodifican fuera del hilo principal, se reducen a un tamaño pequeño y usan una caché en memoria limitada para controlar el uso de RAM.
- Vista previa integrada para formatos compatibles, además de la opción de abrir un archivo con otra aplicación.
- Siete paletas visuales, incluido Capibara kawaii como predeterminado y Ciberpunk como alternativa, además de fondo local persistente. La paleta Capibara usa tonos cálidos de la ilustración y superficies contrastantes.
- Búsqueda recursiva manual con progreso, límite de 100.000 elementos y profundidad máxima 20. Entrar a Limpieza no inicia un escaneo automático; debe tocarse «Analizar ubicación». La vista de carpetas carga lotes de 5.000 elementos y permite continuar sin sustituir toda la lista por un array ilimitado. En rutas de almacenamiento local usa `DirectoryStream` y en árboles SAF usa un cursor con desplazamiento; cada lote se acumula solo cuando el usuario lo solicita. Si un proveedor no permite una enumeración acotada, Archivero muestra el motivo y pide volver a elegir la carpeta mediante el selector SAF, en lugar de materializar una lista potencialmente ilimitada.
- Limpieza revisable de capturas identificables de más de 90 días, vídeos de 100 MB o más y duplicados idénticos.
- La detección de duplicados agrupa primero por tamaño y confirma el contenido mediante SHA-256; no usa el nombre como prueba.
- La Biblioteca incluye un análisis manual de duplicados exactos: compara por SHA-256 completo solo recursos del mismo tamaño, permite cancelar el análisis y presenta resultados sin modificar ni eliminar archivos. Los recursos inaccesibles o sin tamaño conocido quedan fuera de la comparación.
- La limpieza no elimina archivos automáticamente. El usuario revisa y marca elementos y confirma la acción. En los grupos de duplicados, se exige conservar al menos una copia.
- Mensajes de error cuando Android o un proveedor de documentos no permite leer, crear o eliminar un elemento.

## Biblioteca digital local

La sección **Biblioteca** crea fichas locales para recursos existentes sin copiar, mover ni eliminar el archivo original. Cada ficha puede guardar nombre visible, categoría, etiquetas, notas, procedencia, proyecto/colección, tipo de recurso y relaciones con otras fichas.

- Abre el menú contextual de un archivo y elige **Añadir a biblioteca** para crear o editar su ficha.
- Busca por nombre, categoría, etiquetas, notas, procedencia, proyecto, tipo o identificadores de recursos relacionados.
- Usa vistas de **cuadrícula** y **lista**, miniaturas de la caché compartida y metadatos técnicos disponibles: formato/MIME, tamaño, fecha de modificación y dimensiones de imágenes cuando Android puede leerlas.
- Los tipos de recurso distinguen explícitamente entre **Sin clasificar**, **Original**, **Derivado**, **Editado**, **Generado**, **Referencia** y **Personaje**. No se inventan datos cuando un proveedor no devuelve metadatos.
- Combina búsqueda con filtros de categoría, tipo de recurso y proyecto/colección (valores existentes en las fichas). Ordena por **Nombre**, **Recientes** o **Proyecto**. Las últimas ocho búsquedas se guardan en preferencias locales y pueden borrarse desde la pantalla Biblioteca.
- El chip **Solo favoritos** reduce los resultados a las fichas marcadas sin perder los filtros de texto, categoría o tipo; al desactivarlo vuelve la búsqueda completa.
- El etiquetado por lotes permite seleccionar fichas visibles, previsualizar las etiquetas nuevas y aplicarlas en una sola escritura del índice. Conserva las etiquetas anteriores, ignora duplicados por mayúsculas/minúsculas y rechaza operaciones que dejarían cambios parciales.
- **Asignar colección** permite seleccionar fichas visibles, previsualizar el cambio de proyecto y aplicarlo en una sola escritura del catálogo. Si alguna ficha dejó de existir o el nombre no es válido, se rechaza todo el lote.
- **Revisar relaciones** detecta URI vinculadas que no tienen ficha en el catálogo y enumera de forma limitada las fichas sin enlaces entrantes. Estas últimas son orientativas: no se consideran errores automáticamente.
- **Revisar acceso** comprueba manualmente los recursos activos de la Biblioteca y muestra ejemplos de los que no son visibles para el proveedor o no se pueden leer. Se limita a metadatos y permisos; «no visible» no significa necesariamente que el archivo esté borrado.
- **Buscar fotos parecidas en fichas visibles** analiza un máximo de 500 resultados actualmente filtrados mediante huellas perceptuales de miniaturas. Es una sugerencia para revisión, no una prueba de duplicación; muestra hasta 100 grupos y no modifica archivos ni metadatos.
- Las fichas pueden vincular hasta 100 recursos. Las referencias solo guardan URI/identificadores: no duplican archivos. Las relaciones cuyo recurso ya no está indexado se indican como pendientes, y la ficha se conserva.
- **Quitar ficha** elimina solo los metadatos; no borra el archivo físico. Si una URI está ausente o Android ya no permite acceder, se avisa sin borrar el registro.
- La vista presenta hasta 500 resultados a la vez para mantener controlada la carga visual. El filtrado se ejecuta fuera del hilo de interfaz y ahora mantiene una cola acotada de los mejores resultados, evitando ordenar toda la coincidencia para mostrar solo la primera página.
- Marca fichas como **favoritas** con la estrella; los favoritos quedan por delante de los demás dentro del orden elegido. Es independiente de los favoritos musicales. Los favoritos se guardan localmente, se limitan a 5.000 y se limpian del historial si se quita la ficha del catálogo.

Las fichas retiradas con «Mover a papelera» quedan recuperables tras cerrar y volver a abrir la aplicación. Desde **Papelera** puedes restaurarlas o eliminar permanentemente solo sus metadatos con confirmación. Esta acción nunca borra el archivo físico. Las instantáneas de biblioteca incluyen los elementos de la papelera para que las exportaciones conserven esa información.

El índice local está versionado y se actualiza mediante un archivo temporal con reemplazo atómico cuando el sistema de archivos lo admite. Las instantáneas se pueden importar/exportar desde los selectores de Android y tienen un límite de 64 MiB. Una importación inválida o con URI en conflicto se rechaza por defecto, sin sobrescribir silenciosamente la biblioteca activa.

El esquema V3 conserva proyecto, tipo de recurso y relaciones, añade la Papelera recuperable de metadatos y sigue leyendo fichas de los esquemas V1/V2: las fichas antiguas conservan sus datos y reciben valores predeterminados para los campos nuevos; se convierten al esquema actual cuando se guardan.

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
