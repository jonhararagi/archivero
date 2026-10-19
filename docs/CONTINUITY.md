## Estado actual · mascota chibi integrada en los recursos Android · 2026-10-19

- Commit de código: `f20f540721999767d7dfe5ec910938bdf836cde6`.
- Nuevos recursos WebP: `app/src/main/res/drawable-nodpi/mascota_chibi.webp` (512×686) y `mascota_chibi_launcher.webp` (256×256), ambos con transparencia.
- La mascota chibi mantiene cabello castaño, gafas redondas, sudadera marrón y camiseta crema; la capucha no lleva las formas que parecían orejas.
- Las tres apariciones de la mascota en `MainActivity.kt` usan el nuevo recurso. Los dos iconos adaptables apuntan al recorte chibi.
- Se retiró del script Gradle la tarea temporal de dibujo programático. No quedan imports de `java.awt`, `ImageIO` ni dependencia de red para formar la mascota.
- El original `pngtuber-capibara.png` y la copia `mascota_capibara.png` se conservaron.
- Versión: `0.3.7` / `versionCode 10`.
- Validación de CI de este SHA: pendiente hasta que se ejecute Actions y termine con PASS.
- Pendiente de dispositivo: revisar icono, composición, transparencia y tamaños táctiles en el Xiaomi, y pruebas funcionales con almacenamiento grande.

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

