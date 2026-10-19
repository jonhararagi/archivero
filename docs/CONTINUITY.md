# Registro de continuidad de Archivero

Última revisión: 2026-10-19  
Repositorio: `jonhararagi/archivero`  
Rama autorizada: `main`  
Versión Android: `versionName 0.3.3`, `versionCode 6`

## Estado verificado

- Código `0.3.3`/`versionCode 6`, commit `c6962787184514ee611cb372cd3d96a78f516f53`: [GitHub Actions 39260226420](https://github.com/jonhararagi/archivero/actions/runs/39260226420), PASS en pruebas unitarias, `assembleDebug`, verificación de firma, SHA-256 y publicación de artefacto.
- SHA-256 de la APK: `609b072552b41f6a7c8ec5e5958d6fcf9c31da8e3ea62c3b865b9c09928b1191`.
- El certificado de la APK coincide con el keystore de CI: `ca95ba2d2407d9eda63c0568c3ad080928db773997c467ad73013a69493875ba`.
- Artefacto `archivero-debug-apk`, ID `12189856826`, digest ZIP `sha256:9d9c56710523cbb77e4873b1319171447e0cd6907f16c73782959aab68a14f6d`. Es una APK de depuración, no una release comercial firmada; el enlace de Actions permite descargar el ZIP que incluye la APK y su fichero SHA-256.

## Cambios recientes en código

- `DirectoryListing.kt`: la enumeración local usa `DirectoryStream`; el árbol SAF usa un cursor y desplazamiento por lotes de hasta 5.000 entradas. La revisión nueva resuelve el ID del documento actual para subcarpetas y conserva el URI/permiso del árbol original al construir URI de hijos.
- La corrección del recorrido SAF necesita CI sobre el SHA resultante y prueba funcional con el selector real de Android. No declarar validada la navegación física ni una biblioteca de 200 GB sin esa prueba.
- `MainActivity.kt`: «Cargar siguientes 5.000» agrega el lote solicitado sin reiniciar la pantalla. La carga se cancela al cambiar de ubicación y propaga `CancellationException` para no aplicar respuestas obsoletas.
- `FileOperationsTest.kt`: prueba páginas consecutivas, ausencia de elementos repetidos y rechazo de desplazamientos negativos.
- `app/build.gradle.kts`: versión 0.3.3, código 6. CI de la versión pasa en el commit y ejecución registrados arriba; la prueba física con Android/SAF sigue pendiente.
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

1. Probar en Android real: entrar/salir de carpetas grandes, cargar varios lotes, búsqueda por tipo, revocar permisos, abrir APK externa y previsualizar imágenes/PDF; reproducir música/vídeo y verificar Atrás.
3. Recoger Logcat si se reproduce un cierre inesperado y atribuirlo al caso real antes de cambiar otras dependencias.
4. Seguir con la identidad visual; distinguir recursos vectoriales ya integrados de cualquier futura ilustración raster original.

No marcar PASS de dispositivo ni soporte completo de 200 GB sin esa evidencia.
