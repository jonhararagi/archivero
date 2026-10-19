# Registro de continuidad de Archivero

Última revisión: 2026-10-19  
Repositorio: `jonhararagi/archivero`  
Rama autorizada: `main`  
Versión Android: `versionName 0.3.2`, `versionCode 5`

## Estado verificado

- Revisión verificada de la app y firma estable: `c7b2ad22aa76925a6aa587838b1d2cf6cfa0d622`.
- GitHub Actions: [ejecución 39237724749](https://github.com/jonhararagi/archivero/actions/runs/39237724749), PASS para `testDebugUnitTest`, `assembleDebug`, verificación de firma, SHA-256 y publicación de la APK.
- La ejecución restauró la caché `archivero-ci-debug-keystore-v1`. El SHA-256 del certificado del firmante de la APK coincide con el del keystore: `ca95ba2d2407d9eda63c0568c3ad080928db773997c467ad73013a69493875ba`.
- SHA-256 de la APK verificada: `8d04e66e02af2881a2dcc6007b8e25309aa59ecac4b0f1a01822244bb38424c3`.
- La siguiente compilación (esta revisión documental) sirve para confirmar que la clave restaurada produce la misma huella de firma. La APK es `debug`, no una release comercial firmada.

## Cambios recientes en código

- `DirectoryListing.kt`: la enumeración local usa `DirectoryStream`; el árbol SAF usa un cursor y desplazamiento. La vista carga lotes de hasta 5.000 entradas.
- `MainActivity.kt`: «Cargar siguientes 5.000» agrega el lote solicitado sin reiniciar la pantalla. La carga se cancela al cambiar de ubicación y propaga `CancellationException` para no aplicar respuestas obsoletas.
- `FileOperationsTest.kt`: prueba páginas consecutivas, ausencia de elementos repetidos y rechazo de desplazamientos negativos.
- `app/build.gradle.kts`: versión 0.3.2, código 5.
- Identidad visual: el recurso anime vectorial aparece en la bienvenida antes de los permisos y en Inicio; el icono Android es adaptable con fondo azul noche y primer plano de la mascota. No se generó una nueva ilustración rasterizada.
- El reproductor de música se abre como una vista completa y vuelve a la lista con Atrás; carpetas y tipos usan vistas navegables separadas.

## Limitaciones conocidas

- La exploración de carpeta acumula los lotes que el usuario solicita; la memoria aumenta con la cantidad acumulada.
- El fallback para algunos proveedores que no exponen un árbol SAF usa `DocumentFile.listFiles()`, que puede materializar el directorio completo.
- Las páginas se ordenan dentro de cada lote; el orden global puede variar según el proveedor.
- El análisis recursivo sigue limitado a 100.000 elementos y profundidad 20. Los resultados incompletos deben indicarse como parciales.
- No se ha probado físicamente con el Xiaomi del usuario, con una biblioteca real de 200 GB ni con todos los proveedores SAF.
- La primera APK con firma de CI explícita puede no instalarse encima de APK debug anteriores con otro certificado. Desinstalar borra preferencias y permisos guardados por Archivero, no los archivos personales. La caché de CI conserva el certificado únicamente mientras esa entrada siga disponible.

## Próximo ciclo sugerido · TIMER 30–60 min

1. Confirmar que el siguiente workflow documental restaura la caché y produce la misma huella de firma `ca95ba2d2407d9eda63c0568c3ad080928db773997c467ad73013a69493875ba`.
2. Probar en Android real: entrar/salir de carpetas grandes, cargar varios lotes, búsqueda por tipo, revocar permisos, abrir APK externa y previsualizar imágenes/PDF; reproducir música/vídeo y verificar Atrás.
3. Recoger Logcat si se reproduce un cierre inesperado y atribuirlo al caso real antes de cambiar otras dependencias.
4. Seguir con la identidad visual; distinguir recursos vectoriales ya integrados de cualquier futura ilustración raster original.

No marcar PASS de dispositivo ni soporte completo de 200 GB sin esa evidencia.
