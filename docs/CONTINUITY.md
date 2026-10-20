## Continuidad · 2026-10-20 · metadatos de miniaturas fuera del hilo visual

### Entrega de código verificada
- Cambio de código: [commit `802e57952463a422567234c037bb3c8f906a14ce`](https://github.com/jonhararagi/archivero/commit/802e57952463a422567234c037bb3c8f906a14ce).
- Se retiraron las llamadas síncronas a `file.length()` y `file.lastModified()` de las claves Compose de las miniaturas de galería/limpieza. La consulta de metadatos, generación de la clave, determinación del tipo y decodificación se hacen desde `Dispatchers.IO`.
- La caché sigue limitada a 24 MiB. Los errores de metadatos del proveedor usan fallbacks seguros.
- CI de ese SHA: [run 39430152866](https://github.com/jonhararagi/archivero/actions/runs/39430152866), **PASS**. Pasaron `testDebugUnitTest`, `assembleDebug`, la verificación de certificado y la publicación del artefacto.
- APK debug de esa ejecución: 21.265.703 bytes; SHA-256 `2ab547b1480f05b1c87c8c2b097ee644d989b0565e202b1f93e3bc3429c35511`. Artefacto ID `12267263467`, ZIP SHA-256 `3c64c48662e077389a8d330c4ba7be5c1fc109e71e144287e38bc768faae9f65`.
- Certificado APK = keystore CI: `ca95ba2d2407d9eda63c0568c3ad080928db773997c467ad73013a69493875ba`.

### Pendiente, no marcar como PASS
1. Probar desplazamiento de galería y miniaturas de limpieza en el Xiaomi real, revisar jank/ANR y medir RAM.
2. Medir con conjuntos de 1.000 y 10.000 imágenes y proveedores SAF distintos.
3. Probar permisos revocados, imágenes/PDF dañados y cambios de archivo durante la sesión.
4. No afirmar validación sobre aproximadamente 200 GB sin medición física representativa.
5. Al finalizar esta actualización documental, validar de nuevo el SHA final de `main` y descargar el artefacto perteneciente a esa ejecución, no uno anterior.

**Estado:** cambio de código automatizado **PASS**; experiencia física/rendimiento a escala **PARTIAL**.

---

## Continuidad · 2026-10-20 · optimización de galería y revisión comparativa

### Evidencia de código y CI
- Cambio funcional de galería: [commit `90a9b771b974df61f5a111d95dba93f8edd2af75`](https://github.com/jonhararagi/archivero/commit/90a9b771b974df61f5a111d95dba93f8edd2af75).
- El filtrado de imágenes deduplica por URI y ordena dentro del trabajo de fondo; el visor reutiliza esa lista y no vuelve a ordenar al abrir una foto.
- CI del SHA funcional: [run 39414605657](https://github.com/jonhararagi/archivero/actions/runs/39414605657), estado `success`. Pasaron `testDebugUnitTest`, `assembleDebug`, validación del certificado y publicación de artefacto.
- Artefacto de ese run: ID `12260289796`, nombre `archivero-debug-apk`. APK: 21.265.703 bytes; SHA-256 `105c88e69b3519d0da04d1d5e8af19b73abe98c7bf68aa65f4e16dc3c4850571`. Certificado coincidente: `ca95ba2d2407d9eda63c0568c3ad080928db773997c467ad73013a69493875ba`.
- Los commits de documentación posteriores no cambian el código fuente Android. Antes de entregar un artefacto como correspondiente a la cabeza final de `main`, comprobar el run de CI ligado al SHA final y descargar su artefacto específico.

### Investigación comparativa
- Revisión añadida: [docs/COMPETITIVE_REVIEW.md](COMPETITIVE_REVIEW.md), creada en [commit e573c3d8](https://github.com/jonhararagi/archivero/commit/e573c3d8b8097ae1878c2e27ae66ea4d834cf6ec).
- Se consultaron fuentes públicas de Files by Google, Solid Explorer, X-plore, Total Commander Android y CX File Explorer, además de documentación oficial de Android sobre SAF, ANR y uso de memoria de bitmaps.
- El documento separa funciones documentadas, capacidades actuales de Archivero y lagunas. No proclama superioridad global sin benchmark medible.

### Pendiente, no marcar como PASS
- Prueba física en el Xiaomi: scroll de galerías amplias, tiempo de apertura de foto, zoom/pan, Atrás y retención de posición.
- Benchmark reproducible con carpetas/conjuntos de 1.000 y 10.000 imágenes, consumo pico de RAM y medida del tiempo de apertura.
- Verificación con proveedores SAF externos, permisos revocados y fallos de borrado.
- Precisión del hash perceptual en un conjunto de fotos etiquetadas; su resultado sigue siendo sugerencia de similitud, no identidad exacta.
- Rendimiento con la biblioteca real de aproximadamente 200 GB: no probado por la CI.
- Estado honesto de esta iteración: optimización de ruta caliente y comparativa documental PASS en cuanto a entrega de código/documento; rendimiento físico y auditoría integral PARTIAL.

---

## Estado en curso · 0.4.0 · galería fluida y limpieza inteligente · 2026-10-19

- Base verificada: `fd8dd7272406977153f9ae0143cc57d45b0605d3`, CI PASS [39336780741](https://github.com/jonhararagi/archivero/actions/runs/39336780741).
- Versión objetivo: `0.4.0` / `versionCode 13`.
- Imágenes: cuadrícula visual sin cantidad ni metadatos por foto; visor minimalista con zoom/pan y botones anterior/siguiente.
- Rendimiento: caché de miniaturas de 24 MiB y miniaturas de hasta 360 px.
- Limpiador: Capturas, Copias exactas, Fotos parecidas y Vídeos grandes. Copias exactas siguen usando SHA-256; fotos parecidas usan dHash de 64 bits y un límite de 2.500 imágenes por análisis.
- Seguridad: la app no elimina candidatos automáticamente; requiere selección y confirmación y evita marcar todos los elementos de un grupo.
- Capturas: sugerencia por nombre/ruta y antigüedad; la app no puede inferir si son innecesarias.
- CI del código `330ae222476e8627bfa748bc44f37b745ab52fd3`: **PASS** [39342202993](https://github.com/jonhararagi/archivero/actions/runs/39342202993).
- Unit tests, `assembleDebug`, verificación de firma y artefacto: PASS.
- Artefacto ID `12228822978`; ZIP SHA-256 `411a4b189a53da66e195554258b676cf1fb300d73d44f5c04b5b48c41197c0e9`; APK 21.265.703 bytes y SHA-256 `bf58b2dbfce0abae12b4a39bc1e7b5129b652d01e2a402ea0710c39e3761fc03`.
- Certificado APK = keystore CI: `ca95ba2d2407d9eda63c0568c3ad080928db773997c467ad73013a69493875ba`.
- Prueba física de scroll/zoom y rendimiento en Xiaomi pendiente.

## Historial anterior · identidad visual Capibara Kawaii · 2026-10-19

### Cambio de código verificado
- Commit de código: `222515f276c3b96f08325bf9ebe33efe64b46ba1` ([commit](https://github.com/jonhararagi/archivero/commit/222515f276c3b96f08325bf9ebe33efe64b46ba1)).
- CI del commit de código: **PASS**, ejecución [39296641336](https://github.com/jonhararagi/archivero/actions/runs/39296641336).
- `testDebugUnitTest`, `assembleDebug`, verificación de la firma y publicación del artefacto: PASS.
- Tema predeterminado basado en la mascota PNG: cacao `#674840`, coral rosado `#B65C61`, crema `#FFF7F1` y rosa suave `#F7DEDB`.
- Cambios confirmados en `MainActivity.kt`: mayor coherencia cromática, fondos suaves detrás de la mascota en bienvenida/Inicio/estados vacíos, etiqueta kawaii en cápsula, tarjetas de categorías más redondeadas y miniaturas coordinadas.
- Recurso original `pngtuber-capibara.png` conservado sin modificar. La paleta Ciberpunk sigue disponible para selección manual.
- Aplicación: `0.3.6` / `versionCode 9`.

### APK del cambio visual
- Artefacto CI `archivero-debug-apk`, ID `12207821973`.
- Tamaño APK: `21,181,924` bytes.
- SHA-256 APK: `af869eb763002c2db5c2cffeac43bbff27655943aab4fa05e5a3ffb89f723f8c`.
- SHA-256 del certificado APK/keystore CI: `ca95ba2d2407d9eda63c0568c3ad080928db773997c467ad73013a69493875ba`; coinciden.
- SHA-256 ZIP del artefacto: `f4baa7f855c7d7613a4a32b35b9200aad5b852d2054572194d04e511d20c2eb1`.
- Tipo: APK debug, no release. El ZIP se descargó y comprobó sin errores.

### Validación que sigue pendiente
- Revisar la apariencia en el Xiaomi real y confirmar que la imagen se recorta bien en diferentes tamaños de pantalla.
- Repetir navegación SAF, apertura de contenido y pruebas con almacenamiento grande en dispositivo real. CI no demuestra por sí sola estabilidad física ni rendimiento con 200 GB.

---

## Historial anterior · Archivero Android

### Versión y cambios de código

- Versión de la aplicación: 0.3.6 / versionCode 9.
- Commit de código validado: a71a3332b60b81af043118a69c277231777dec30 ([ver commit](https://github.com/jonhararagi/archivero/commit/a71a3332b60b81af043118a69c277231777dec30)).
- La identidad visual predeterminada sigue siendo Capibara Kawaii, con la imagen original subida por el usuario conservada sin editar en pngtuber-capibara.png y app/src/main/res/drawable-nodpi/mascota_capibara.png. Aparece en bienvenida, Inicio y estados vacíos. Ciberpunk permanece como paleta alternativa.
- La corrección de apertura asigna application/vnd.android.package-archive únicamente a .apk. Los contenedores .xapk y .apks se envían como application/zip para que el usuario pueda abrirlos con un visor compatible; no se presentan como instalables directamente desde Archivero. Los mensajes explican que requieren un instalador compatible.
- Se añadió FileOpenSupport.kt y cinco pruebas unitarias que cubren MIME para APK/XAPK/APKS, MIME específico del proveedor y fallback ante MIME genérico.

### CI y APK verificada

- **CI PASS para el commit de código a71a3332b60b81af043118a69c277231777dec30** en [GitHub Actions 39291438524](https://github.com/jonhararagi/archivero/actions/runs/39291438524).
- testDebugUnitTest: PASS, con BUILD SUCCESSFUL.
- assembleDebug: PASS, con BUILD SUCCESSFUL.
- La verificación de firma de Actions confirmó que el certificado de la APK coincide con el keystore configurado.
- APK debug: 21,181,924 bytes. SHA-256: 07a19cddb60de435f8fd4aa401ca8cbce3b2b1b7cbc48c41a168293e1673264b.
- Certificado SHA-256: ca95ba2d2407d9eda63c0568c3ad080928db773997c467ad73013a69493875ba.
- Artefacto archivero-debug-apk: ID 12206105817. ZIP SHA-256: dc75ad9d21f7066aa68c44382487fd26d95102f2b8132fe2f34af588b80f9aff.
- La APK descargada se extrajo correctamente, el ZIP pasó unzip -t y el hash de la APK coincidió con app-debug.apk.sha256. Es una APK de depuración, no una release comercial.

### Funciones y límites conocidos

- El listado local y los árboles SAF enumeran directorios mediante DirectoryStream o cursor de DocumentsContract con lotes de hasta 5.000 elementos. La lista visible acumula los lotes que el usuario solicita.
- Proveedores que no exponen una URI de árbol SAF pueden caer en DocumentFile.listFiles(), que podría materializar la lista completa. El orden global entre páginas también depende del proveedor.
- El análisis recursivo tiene límites de 100.000 elementos y profundidad 20. Un resultado limitado debe identificarse como parcial y no equivale a inventariar todos los datos del dispositivo.
- Las miniaturas se reducen y usan una caché limitada; las operaciones pesadas se ejecutan fuera del hilo principal. Las pruebas unitarias no garantizan que todos los proveedores funcionen igual.
- Sigue pendiente probar en un teléfono Android real: selector/instalador para .apk, visualización externa de XAPK/APKS, permisos SAF revocados, subcarpetas, páginas consecutivas, carpetas con muchos archivos, previsualización de imágenes/PDF, música/vídeo tras minimizar y navegación Atrás.
- No se ha validado el rendimiento con una biblioteca real de 200 GB ni la estabilidad en todos los proveedores SAF. No se declara PASS físico sin esa evidencia.

### Próximos pasos prioritarios

1. Probar la APK 0.3.6 en el Xiaomi y recopilar Logcat si se reproduce un cierre.
2. Reproducir una navegación con varias páginas de 5.000 entradas y comprobar que no se pierden ni repiten elementos.
3. Revisar proveedores no-tree donde DocumentFile.listFiles() puede cargar demasiadas entradas.
4. Seguir comparando funciones de Archivero con Files by Google, Solid Explorer, X-plore, Total Commander y CX File Explorer. No afirmar superioridad general sin mediciones directas y fuentes verificadas.

