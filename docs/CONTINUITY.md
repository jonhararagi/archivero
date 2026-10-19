## Incremento en curso: apertura APK/XAPK/APKS

- Se corrigió la selección MIME en MainActivity.kt: solo .apk usa application/vnd.android.package-archive; .xapk y .apks se presentan como contenedores ZIP para abrirlos con un visor/instalador externo compatible.
- Se añadió FileOpenSupport.kt y cinco pruebas unitarias para los MIME relevantes.
- Versión de trabajo: 0.3.6 / versionCode 9.
- CI pendiente para este cambio. No reemplazar la APK entregable hasta comprobar que la ejecución asociada al SHA nuevo termina correctamente.
- En un dispositivo real aún debe comprobarse el selector de Android con un APK y el comportamiento al abrir archivos XAPK/APKS; estos últimos no se instalan directamente desde Archivero.

## Estado de la rama

- Versión Android: `0.3.5` / `versionCode 8`. El commit de código que introduce la identidad visual Capibara Kawaii es `ae1366b56711182f33f7562e3b78b2809f70c5a1`: [ver commit](https://github.com/jonhararagi/archivero/commit/ae1366b56711182f33f7562e3b78b2809f70c5a1).
- **CI PASS** para ese SHA en [GitHub Actions 39287800185](https://github.com/jonhararagi/archivero/actions/runs/39287800185). El run está completado con `success`; los logs muestran `testDebugUnitTest` y `assembleDebug` exitosos.
- APK debug generada: `21,181,924` bytes. SHA-256: `35fe976561903a024ae17347afda59f9752c7e54370f8159662032aee338adb0`. El hash volvió a comprobarse tras descargar el artefacto y coincide con el archivo `.sha256` empaquetado.
- Certificado firmante y keystore de CI: `ca95ba2d2407d9eda63c0568c3ad080928db773997c467ad73013a69493875ba`; ambos coinciden.
- Artefacto `archivero-debug-apk`, ID `12202864682`, digest ZIP `sha256:893b69759c05ffcf270283c8f8cd550f2b9c7eae8a38fd8cc92fec750150f9cc`. Es una APK debug, no una release.
- La imagen original subida como `pngtuber-capibara.png` se incluye también como recurso local `app/src/main/res/drawable-nodpi/mascota_capibara.png`. La paleta cacao/crema/coral es predeterminada, y Ciberpunk sigue disponible como alternativa.
- La CI y los hashes no sustituyen la prueba física. Continúan pendientes el recorte visual en el Xiaomi, navegación SAF y prueba con bibliotecas grandes/archivos reales.

## Cambios recientes en código

- `DirectoryListing.kt`: la enumeración local usa `DirectoryStream`; el árbol SAF usa un cursor y desplazamiento por lotes de hasta 5.000 entradas. La revisión nueva resuelve el ID del documento actual para subcarpetas y conserva el URI/permiso del árbol original al construir URI de hijos.
- La corrección del recorrido SAF necesita CI sobre el SHA resultante y prueba funcional con el selector real de Android. No declarar validada la navegación física ni una biblioteca de 200 GB sin esa prueba.
- `MainActivity.kt`: «Cargar siguientes 5.000» agrega el lote solicitado sin reiniciar la pantalla. La carga se cancela al cambiar de ubicación y propaga `CancellationException` para no aplicar respuestas obsoletas.
- `FilePreviewActivity.kt`: los renders cancelados de imagen/PDF se repropagan sin modificar el estado de carga de una vista nueva; la lectura de texto cierra el flujo ante error. Incluye dos pruebas nuevas de límites de paginación.
- `FileOperationsTest.kt`: prueba páginas consecutivas, ausencia de elementos repetidos y rechazo de desplazamientos negativos.
- `app/build.gradle.kts`: versión `0.3.5`, código 8. Los pasos automáticos y la firma han pasado para el commit de código indicado arriba; la prueba física con Android/SAF sigue pendiente.
- Identidad visual: desde 0.3.5, la imagen capibara kawaii subida por el usuario aparece en bienvenida, Inicio y estados vacíos; el icono adaptativo vectorial está recoloreado para armonizar. El recurso PNG original se conserva sin editar.
- El reproductor de música se abre como una vista completa y vuelve a la lista con Atrás; carpetas y tipos usan vistas navegables separadas.

## Limitaciones conocidas

- La exploración de carpeta acumula los lotes que el usuario solicita; la memoria aumenta con la cantidad acumulada.
- El fallback para algunos proveedores que no exponen un árbol SAF usa `DocumentFile.listFiles()`, que puede materializar el directorio completo.
- Las páginas se ordenan dentro de cada lote; el orden global puede variar según el proveedor.
- El análisis recursivo sigue limitado a 100.000 elementos y profundidad 20. Los resultados incompletos deben indicarse como parciales.
- No se ha probado físicamente con el Xiaomi del usuario, con una biblioteca real de 200 GB ni con todos los proveedores SAF.
- La primera APK con firma de CI explícita puede no instalarse encima de APK debug anteriores con otro certificado. Desinstalar borra preferencias y permisos guardados por Archivero, no los archivos personales. La caché de CI conserva el certificado únicamente mientras esa entrada siga disponible.

## Próximo ciclo sugerido · TIMER 30–60 min

1. Probar en Android real: entrar/salir de carpetas grandes, cargar varios lotes, búsqueda por tipo, revocar permisos, abrir APK externa y previsualizar imágenes/PDF; reproducir música/vídeo y verificar Atrás.
3. Recoger Logcat si se reproduce un cierre inesperado y atribuirlo al caso real antes de cambiar otras dependencias.
4. Seguir con la identidad visual; distinguir recursos vectoriales ya integrados de cualquier futura ilustración raster original.

No marcar PASS de dispositivo ni soporte completo de 200 GB sin esa evidencia.
