## Paginación y seguridad de proveedores · 2026-10-20

- La enumeración por lotes para el sistema de archivos y árboles SAF conserva un límite de memoria al leer entradas, mientras que los proveedores no compatibles ya no pasan por `DocumentFile.listFiles()`, que materializa una matriz completa antes de aplicar el límite.
- En ese caso, Archivero muestra el motivo y solicita volver a seleccionar la carpeta mediante el selector SAF, en lugar de arriesgar un consumo descontrolado de memoria o presentar una falsa paginación.
- La vista de carpeta muestra el motivo real del error de enumeración en lugar de una explicación genérica de permisos.
- Se amplían las pruebas de paginación local con páginas consecutivas, ausencia de repetidos, página final, offset fuera de rango y rechazo de parámetros no válidos.
- La primera CI detectó una importación `java.io.File` ausente en la prueba nueva; se corrigió en [`448d517`](https://github.com/jonhararagi/archivero/commit/448d51743e5045f6fff436c3d010f63fdffc8f04). La validación debe hacerse contra la cabeza posterior que incluya el endurecimiento de proveedores.
- Pendiente: pruebas instrumentadas con proveedores SAF reales y carpetas masivas en el dispositivo objetivo.

---

## Evidencia de entrega 0.4.2 · 2026-10-20

- SHA de código validado: `f2e1ff41e05ce5a04860b72d7a537b3c5b45504c`.
- [GitHub Actions run 39487033724](https://github.com/jonhararagi/archivero/actions/runs/39487033724): **PASS**; pruebas unitarias, `assembleDebug`, verificación de firma y publicación del artefacto pasaron.
- Artefacto CI ID `12293698093`, nombre `archivero-debug-apk`, tamaño ZIP 20.398.195 bytes; SHA-256 ZIP `b8ccb98f4db6c67234547483e290129be58c9bfa56743aa24af966ae81e4165a`.
- APK extraída: 21.265.707 bytes; SHA-256 `6b47334b448ff1ee80fb47740cc5d1fc630b8eefb0c725e4fc19d6adc41bfbff`. La suma coincide con el archivo `.sha256` incluido; `unzip -t` no encontró errores.
- Certificado SHA-256 de la APK y del keystore CI coinciden: `ca95ba2d2407d9eda63c0568c3ad080928db773997c467ad73013a69493875ba`.
- Esta verificación automatizada no sustituye el benchmark en el Xiaomi ni las pruebas representativas con 200 GB.

---

## Archivero Android 0.4.2 · 2026-10-20

- Incrementados `versionName` a `0.4.2` y `versionCode` a `15` para entregar la siguiente iteración como actualización instalable.
- Se trasladó a `Dispatchers.IO` la preparación de la lista de archivos visibles para las vistas de categorías, galería y limpiador, evitando consultar `DocumentFile.isDirectory` durante la composición de Compose.
- Tras el escaneo recursivo se reutiliza la lista `indexed`, que ya contiene únicamente archivos, sin recorrer otra vez los proveedores ni copiar la lista grande.
- Se mantienen las mejoras anteriores: metadatos de miniaturas fuera del hilo visual; selección y agrupación de candidatos de duplicados exactos en segundo plano; búsqueda de imágenes similares cancelable y acotada a 2.500 candidatos; pruebas de la selección de duplicados.
- Esta versión debe entregarse solo tras PASS de tests, compilación, verificación de firma y artefacto para el SHA final de `main`.

---

## Versión Android 0.4.1 · 2026-10-20

- `versionName = 0.4.1`; `versionCode = 14), incrementados para distinguir la actualización de la 0.4.0 instalada anteriormente.
- Incluye la mejora del escaneo de candidatos a duplicados y fotos similares fuera del hilo visual documentada más abajo.
- La APK de entrega debe proceder de la CI del SHA que contenga esta versión, con pruebas, compilación y firma verificadas.

---

## Rendimiento del limpiador · escaneo de candidatos en segundo plano · 2026-10-20

- La detección de duplicados exactos ahora consulta metadatos y agrupa candidatos en `Dispatchers.IO`, con comprobaciones de cancelación durante el recorrido. El hash SHA-256 del contenido completo solo se calcula para elementos con tamaño compartido.
- La búsqueda de fotos parecidas mueve la enumeración de candidatos al escáner de fondo y se detiene tras encontrar evidencia de que supera el límite de 2.500 imágenes. La selección se mantiene acotada y el usuario recibe una advertencia cuando el resultado es parcial.
- La función pura `potentialDuplicateCandidates` cubre tamaño positivo compartido, URI repetidas, archivos vacíos y entradas únicas mediante pruebas unitarias.
- Commits relacionados: [`de01b9b`](https://github.com/jonhararagi/archivero/commit/de01b9bc788b3d6d4d969b0b53d48e9bad484c43), [`06fe849`](https://github.com/jonhararagi/archivero/commit/06fe84927f4e35b99a9ffc3960650cdc1a0ba05a), [`590f0c1`](https://github.com/jonhararagi/archivero/commit/590f0c1ae68b0ca9afa1bd2141b36cbcbd64f844), [`abe285f`](https://github.com/jonhararagi/archivero/commit/abe285f4c467dd615d354583cf671d91a2818985).
- La validación CI de la cabeza de código incluye tests unitarios, `assembleDebug`, comprobación de firma y publicación del artefacto: [run 39483773953](https://github.com/jonhararagi/archivero/actions/runs/39483773953). En el momento de este registro, esos pasos figuran en éxito; GitHub estaba terminando las tareas de limpieza del job.
- Validación física pendiente: medir tiempos/RAM en el Xiaomi, probar proveedores SAF variados y ensayar con una biblioteca real de gran tamaño. No se afirma que 200 GB estén verificados.

---

## Rendimiento de miniaturas SAF · 2026-10-20

### Cambio de código
- Commit: [`802e57952463a422567234c037bb3c8f906a14ce`](https://github.com/jonhararagi/archivero/commit/802e57952463a422567234c037bb3c8f906a14ce).
- `GalleryThumbnail` y `FileThumbnail` ya no consultan `length()` y `lastModified()` durante la composición de Compose. La clave de caché y la consulta de metadatos se preparan desde `Dispatchers.IO`.
- La lectura de nombre/tipo del documento en miniaturas del limpiador también se realiza en segundo plano. Los errores de proveedor al obtener nombre, tipo, longitud o modificación se manejan con valores seguros para evitar que un metadato inaccesible cierre la vista.
- Se mantiene la caché LRU limitada a 24 MiB y el tamaño reducido de las miniaturas.

### Validación CI del commit de código
- GitHub Actions: [run 39430152866](https://github.com/jonhararagi/archivero/actions/runs/39430152866), **PASS** para SHA `802e57952463a422567234c037bb3c8f906a14ce`.
- `testDebugUnitTest`: PASS. `assembleDebug`: PASS. Verificación de firma y publicación de artefacto: PASS.
- Artefacto `archivero-debug-apk`, ID `12267263467`; ZIP descargado e integridad comprobada. ZIP SHA-256: `3c64c48662e077389a8d330c4ba7be5c1fc109e71e144287e38bc768faae9f65`.
- APK debug: 21.265.703 bytes; SHA-256: `2ab547b1480f05b1c87c8c2b097ee644d989b0565e202b1f93e3bc3429c35511`.
- SHA-256 del certificado de la APK y del keystore de CI coinciden: `ca95ba2d2407d9eda63c0568c3ad080928db773997c467ad73013a69493875ba`.

### Límites que permanecen abiertos
- La CI no mide la fluidez real, los ANR ni el consumo de memoria en el Xiaomi.
- Sigue pendiente un benchmark con 1.000 y 10.000 imágenes, proveedores SAF externos y la biblioteca real de aproximadamente 200 GB.
- Estado: mejora de código + validación automatizada **PASS**; validación física y de escala **PARTIAL**.

---

## Iteración de rendimiento y comparación · 2026-10-20

### Galería
- El filtrado de imágenes deduplica por URI y ordena los resultados fuera del hilo visual. La apertura desde la cuadrícula reutiliza la lista que ya se preparó en segundo plano y evita volver a ordenar toda la galería al tocar una miniatura.
- Código: [90a9b771b974df61f5a111d95dba93f8edd2af75](https://github.com/jonhararagi/archivero/commit/90a9b771b974df61f5a111d95dba93f8edd2af75).
- CI de ese SHA: [run 39414605657](https://github.com/jonhararagi/archivero/actions/runs/39414605657), PASS para pruebas unitarias, `assembleDebug`, verificación de firma y publicación del artefacto.
- La APK debug de esa ejecución mide 21.265.703 bytes y su SHA-256 es `105c88e69b3519d0da04d1d5e8af19b73abe98c7bf68aa65f4e16dc3c4850571`. Certificado del APK y keystore CI: `ca95ba2d2407d9eda63c0568c3ad080928db773997c467ad73013a69493875ba`.
- Pendiente: prueba física de respuesta, desplazamiento, zoom y memoria en el Xiaomi; no se afirma validación con 200 GB solo por pasar CI.

### Comparativa funcional
- Se añadió [docs/COMPETITIVE_REVIEW.md](docs/COMPETITIVE_REVIEW.md) con fuentes públicas y fechas consultadas para Files by Google, Solid Explorer, X-plore, Total Commander Android y CX File Explorer.
- La comparativa separa funciones documentadas, funciones que Archivero ya tiene, lagunas actuales y criterios de benchmark. No declara superioridad general sin mediciones.
- Documentación Android consultada para el análisis: SAF, ANR y memoria de bitmaps. Las pruebas físicas y comparaciones con conjuntos de datos representativos permanecen pendientes.

---

## Versión 0.4.0 · galería visual y limpieza inteligente · 2026-10-19

### Navegación visual
- La sección Imágenes usa una cuadrícula de miniaturas, sin conteos de archivos, extensiones, tamaños ni ubicación bajo cada foto.
- El visor de fotos reduce la interfaz a cerrar y abrir con otra aplicación. Añade zoom y arrastre táctil, navegación anterior/siguiente y fondo negro para dar más espacio a la imagen.
- La caché de miniaturas aumenta a 24 MiB y las miniaturas suben de 192 a 360 píxeles para hacer el desplazamiento más fluido sin decodificar cada foto a resolución completa.

### Limpieza
- El limpiador ofrece Capturas antiguas, Copias exactas, Fotos parecidas y Vídeos grandes.
- La búsqueda de similitud usa diferencia de luminancia (dHash) sobre miniaturas pequeñas, con un máximo de 2.500 imágenes por análisis para mantener el trabajo acotado en móviles. Si hay más candidatos, la interfaz indica que el análisis fue limitado.
- Las listas de limpieza priorizan miniatura y nombre; ocultan hashes, rutas, fechas, tamaños y cantidades de elementos.
- Las capturas antiguas se sugieren para revisión. Archivero no puede saber si son inútiles y no las elimina automáticamente.
- Se exige selección y confirmación manual para borrar; el limpiador bloquea la selección de todos los miembros de un mismo grupo para evitar borrar todas las copias/fotos agrupadas.

### Validación automatizada PASS
- Código verificado: [commit 330ae222](https://github.com/jonhararagi/archivero/commit/330ae222476e8627bfa748bc44f37b745ab52fd3).
- CI: **PASS** [39342202993](https://github.com/jonhararagi/archivero/actions/runs/39342202993): pruebas unitarias, compilación Android, firma y publicación de artefacto.
- Artefacto `archivero-debug-apk`, ID `12228822978`, ZIP 20.392.711 bytes; SHA-256 ZIP `411a4b189a53da66e195554258b676cf1fb300d73d44f5c04b5b48c41197c0e9`.
- APK debug extraída: 21.265.703 bytes; SHA-256 `bf58b2dbfce0abae12b4a39bc1e7b5129b652d01e2a402ea0710c39e3761fc03`.
- SHA-256 de certificado APK y keystore CI coinciden: `ca95ba2d2407d9eda63c0568c3ad080928db773997c467ad73013a69493875ba`.
- Inspección del APK confirma las clases `ImageGalleryGrid`, `findSimilarImages` y recursos de la interfaz de limpieza.
- Pruebas físicas pendientes en Xiaomi: desplazamiento por galería grande, zoom, navegación entre fotos, limpieza segura y tiempos de análisis.

---

## Pulido visual · Capibara Kawaii inspirado en la mascota · 2026-10-19

### Cambios de interfaz
- Se ajustó la paleta predeterminada para acercarla a los colores dominantes de la ilustración PNG original subida a `main`: cacao profundo (`#674840`), coral rosado (`#B65C61`), crema cálida (`#FFF7F1`) y rosa suave (`#F7DEDB`).
- Se amplió la armonía del tema claro con colores explícitos para fondo, superficies, contenedores, texto secundario y bordes suaves; Ciberpunk y las otras paletas continúan disponibles.
- Se incorporaron fondos rosa suave detrás de la mascota en bienvenida, Inicio y estados vacíos, sin editar el archivo original.
- La etiqueta «♡ MODO CAPIBARA KAWAII ♡» ahora se muestra como una cápsula pastel. Las tarjetas de categorías tienen contornos discretos y radios más redondeados.
- Las miniaturas de carpetas/archivos se coordinaron con tonos tierra y crema. No se cambió la lógica de navegación, permisos ni operaciones de archivos.

### Verificación CI del cambio de código
- Commit de código: [222515f276c3b96f08325bf9ebe33efe64b46ba1](https://github.com/jonhararagi/archivero/commit/222515f276c3b96f08325bf9ebe33efe64b46ba1).
- **CI PASS**: [GitHub Actions 39296641336](https://github.com/jonhararagi/archivero/actions/runs/39296641336), asociada exactamente al SHA anterior.
- `testDebugUnitTest`: PASS. `assembleDebug`: PASS. Verificación del firmante y publicación del artefacto: PASS.
- APK debug: 21.181.924 bytes. SHA-256: `af869eb763002c2db5c2cffeac43bbff27655943aab4fa05e5a3ffb89f723f8c`.
- SHA-256 del certificado de la APK y del keystore de CI, coincidentes: `ca95ba2d2407d9eda63c0568c3ad080928db773997c467ad73013a69493875ba`.
- Artefacto `archivero-debug-apk`, ID `12207821973`; SHA-256 del ZIP: `f4baa7f855c7d7613a4a32b35b9200aad5b852d2054572194d04e511d20c2eb1`.
- La APK conserva versión `0.3.6` / `versionCode 9`; este incremento es de pulido visual y no cambia el número de versión Android.
- Es una APK debug, no una release. No se ha probado físicamente esta apariencia en el Xiaomi ni se declara validado el rendimiento con una biblioteca real de 200 GB.

---

## Versión 0.3.6 · apertura correcta de APK/XAPK/APKS · 2026-10-19

### MIME y mensajes de instalación
- Los archivos .apk conservan el MIME de paquete Android y se envían al flujo del instalador del sistema, que exige confirmación del usuario.
- Los archivos .xapk y .apks se tratan como contenedores ZIP al solicitar una aplicación externa, en lugar de etiquetar ambos como un APK único. Cuando no hay visor compatible, el mensaje explica que hace falta un instalador compatible con esos formatos.
- Se añadieron cinco pruebas unitarias: MIME de APK, XAPK, APKS, tipo MIME específico del proveedor y fallback cuando el proveedor informa un tipo genérico.
- Se incrementó la versión Android a 0.3.6 / versionCode 9.

### Verificación y artefacto
- Commit de código validado: a71a3332b60b81af043118a69c277231777dec30.
- **CI PASS**: [GitHub Actions 39291438524](https://github.com/jonhararagi/archivero/actions/runs/39291438524). Los logs muestran BUILD SUCCESSFUL en testDebugUnitTest y assembleDebug; también pasaron la comprobación de firma y la publicación del artefacto.
- APK debug: 21,181,924 bytes. SHA-256: 07a19cddb60de435f8fd4aa401ca8cbce3b2b1b7cbc48c41a168293e1673264b.
- SHA-256 del certificado de la APK y del keystore de CI, coincidentes: ca95ba2d2407d9eda63c0568c3ad080928db773997c467ad73013a69493875ba.
- Artefacto archivero-debug-apk, ID 12206105817; ZIP SHA-256: dc75ad9d21f7066aa68c44382487fd26d95102f2b8132fe2f34af588b80f9aff.
- Es una APK debug, no una release. Las pruebas físicas de instalación/apertura en Android real siguen pendientes.


## Versión 0.3.5 · identidad visual inspirada en la mascota capibara · 2026-10-19

### Cambios visuales
- La imagen original subida a `main` como `pngtuber-capibara.png` se integra como recurso Android local `mascota_capibara.png`; se muestra en la bienvenida, la tarjeta de Inicio y los estados vacíos.
- Nueva paleta predeterminada **Capibara kawaii**: cacao y marrón cálido, crema, beige suave y coral rosado, extraída visualmente de la sudadera, el cabello, la camiseta y los detalles rosados de la mascota.
- Las superficies claras, el texto cacao, las tarjetas crema y los acentos coral forman una familia visual consistente sin cubrir ni competir con los nombres de archivo.
- Los iconos vectoriales adaptativos existentes se recolorean en tonos cálidos para que no desentonen con la ilustración principal.
- La preferencia predeterminada anterior Ciberpunk se migra a Capibara kawaii; las demás paletas que el usuario ya eligió se conservan. Ciberpunk continúa disponible en Personalizar apariencia.
- Los iconos de tipos de archivo y las miniaturas usan tonos tierra coordinados con la paleta principal.

### Versión y verificación
- Versión Android: `0.3.5`, `versionCode 8`.
- **CI PASS** para el SHA de código `ae1366b56711182f33f7562e3b78b2809f70c5a1`: [GitHub Actions 39287800185](https://github.com/jonhararagi/archivero/actions/runs/39287800185). El run terminó con `success`; `testDebugUnitTest` y `assembleDebug` registraron `BUILD SUCCESSFUL`.
- APK debug: `21,181,924` bytes. SHA-256 verificado también tras descargar y extraer el artefacto: `35fe976561903a024ae17347afda59f9752c7e54370f8159662032aee338adb0`.
- La huella SHA-256 del certificado de la APK y del keystore de CI coincide: `ca95ba2d2407d9eda63c0568c3ad080928db773997c467ad73013a69493875ba`.
- Artefacto `archivero-debug-apk`, ID `12202864682`, digest del ZIP `sha256:893b69759c05ffcf270283c8f8cd550f2b9c7eae8a38fd8cc92fec750150f9cc`. [Descargar desde GitHub Actions](https://github.com/jonhararagi/archivero/actions/runs/39287800185).
- Esta APK es de depuración, no una release comercial. Las pruebas físicas de la apariencia en el Xiaomi, navegación SAF y bibliotecas grandes siguen pendientes.

## Versión 0.3.3 · navegación SAF de subcarpetas · 2026-10-19

### Corrección y paquete
- Corrige la identificación de la carpeta actual al enumerar un árbol SAF y conserva la URI del árbol original al crear URI para subcarpetas. Así, navegar a un directorio hijo no depende de una concesión de acceso artificial para ese hijo.
- Incrementa `versionCode` a `6` y `versionName` a `0.3.3` para distinguir esta actualización de la APK anterior.
- CI de la versión `0.3.3`/`versionCode 6`: PASS para el commit `c6962787184514ee611cb372cd3d96a78f516f53` en [la ejecución 39260226420](https://github.com/jonhararagi/archivero/actions/runs/39260226420). Pasaron `testDebugUnitTest`, `assembleDebug`, verificación de certificado, cálculo SHA-256 y publicación del artefacto. SHA-256 de la APK: `609b072552b41f6a7c8ec5e5958d6fcf9c31da8e3ea62c3b865b9c09928b1191`; certificado firmante: `ca95ba2d2407d9eda63c0568c3ad080928db773997c467ad73013a69493875ba`. El artefacto descargable es `archivero-debug-apk` (ID `12189856826`); es una APK debug, no release.
- Pendiente de prueba física: seleccionar una carpeta mediante el selector Android, entrar en subcarpetas, cargar varias páginas y repetirlo con almacenamiento externo. No se declara validado con una biblioteca real de 200 GB.

## Versión 0.3.2 · paginación progresiva y fiabilidad · 2026-10-19

### Carpetas grandes y memoria
- La vista de carpetas carga lotes de 5.000 entradas. «Cargar siguientes 5.000» pide la siguiente página y conserva lo que ya estaba visible, sin reiniciar la pantalla ni mostrar otra pestaña por detrás.
- En el almacenamiento local, la enumeración se hace con `DirectoryStream`; en árboles SAF, con un cursor del proveedor. Ambas rutas saltan el desplazamiento ya mostrado y retienen solo el lote nuevo durante la consulta, en lugar de crear un array completo del directorio al abrirlo.
- La lista en pantalla acumula los lotes que el usuario solicita, de modo que el consumo crece con la exploración. Algunos proveedores ajenos a las rutas file/SAF-tree solo exponen `DocumentFile.listFiles()`; su fallback todavía puede materializar el resultado entero. Se documenta como limitación, no como soporte universal de paginación.
- El escáner recursivo mantiene por separado el límite de 100.000 elementos y profundidad 20, e informa resultados parciales. Cargar más elementos en una carpeta no convierte un análisis recursivo parcial en un inventario completo.
- La carga inicial ya no depende de un límite acumulativo de 100.000 entradas. Si una consulta se cancela al cambiar de carpeta, la cancelación se propaga y la respuesta obsoleta no reemplaza la ubicación nueva.
- Corrección de navegación SAF: cada consulta usa el ID del documento que representa la carpeta actual, no siempre el ID de la raíz del árbol. Las URI de los hijos se construyen con `DocumentsContract.buildDocumentUriUsingTree()` para conservar la concesión persistente de la carpeta elegida al entrar en subcarpetas. La validación automática confirma compilación/pruebas unitarias, pero la navegación debe probarse también con el selector SAF real.

### Pruebas y versión
- Se añadieron pruebas unitarias para recorrer una carpeta en páginas sin repetir ni perder entradas y para rechazar desplazamientos negativos.
- Versión Android actualizada a `versionCode 5` y `versionName 0.3.2`.
- El workflow Android ejecuta `testDebugUnitTest`, `assembleDebug`, comprueba el APK y calcula su SHA-256. El estado final de CI debe corresponder al SHA final de `main`.
- La mascota vectorial anime ciberpunk queda visible también en la pantalla de bienvenida antes de conceder permisos, además de la tarjeta de Inicio. El lanzador usa iconos adaptativos de Android con fondo azul noche y el arte de la mascota como primer plano. No se generó una nueva ilustración rasterizada en esta iteración.
- Tras comparar los certificados de APK debug sucesivas, se confirmó que guardar el keystore en caché no bastaba por sí solo. Ahora el workflow pasa la ruta del keystore explícitamente a Gradle, verifica que la APK esté firmada con ese certificado y falla si las huellas no coinciden; la caché de Actions conserva la misma clave entre compilaciones.
- La primera APK que usa explícitamente la clave estable de CI es la revisión `c7b2ad22aa76925a6aa587838b1d2cf6cfa0d622`. Su certificado SHA-256 es `ca95ba2d2407d9eda63c0568c3ad080928db773997c467ad73013a69493875ba`; CI compara esa huella con el keystore antes de publicar el artefacto. Las APK debug anteriores tienen certificados distintos y Android puede exigir desinstalarlas antes de instalar esta revisión, lo que borra preferencias y permisos locales de Archivero (no los archivos personales).
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
