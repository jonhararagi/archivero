# Registro de continuidad de Archivero

Última revisión: 2026-10-19  
Repositorio: `jonhararagi/archivero`  
Rama autorizada: `main`  
Versión Android en `main`: `versionName 0.3.4`, `versionCode 7`

## Estado de la rama

- Versión en `main`: `0.3.4` / `versionCode 7`; commit de código `6dc0d4ef2b00b1f5126599abbb496c3cf193e277`: [ver commit](https://github.com/jonhararagi/archivero/commit/6dc0d4ef2b00b1f5126599abbb496c3cf193e277).
- Incremento de código: las vistas previas de imagen/PDF propagan `CancellationException`; los trabajos cancelados no deben cambiar el indicador de carga de una vista nueva; la lectura de texto cierra el flujo aunque falle la lectura.
- Añadidas pruebas unitarias de desplazamiento más allá del final y de límite de página no positivo.
- El workflow que corresponde al commit de código es [GitHub Actions 39285287357](https://github.com/jonhararagi/archivero/actions/runs/39285287357). En la última consulta, su estado era `queued` y todavía no había artefactos. No declarar PASS ni reutilizar la APK 0.3.3 hasta comprobar CI y artefacto del SHA final de `main`.
- Último artefacto previamente verificado de `0.3.3`: [ejecución 39260712891](https://github.com/jonhararagi/archivero/actions/runs/39260712891), ID `12191750763`, APK debug del código anterior, SHA-256 `609b072552b41f6a7c8ec5e5958d6fcf9c31da8e3ea62c3b865b9c09928b1191`. No contiene las correcciones de `0.3.4`.
- La firma de CI registrada para la APK anterior es `ca95ba2d2407d9eda63c0568c3ad080928db773997c467ad73013a69493875ba`. Debe volver a comprobarse en el artefacto nuevo, no suponerse.

## Cambios recientes en código

- `DirectoryListing.kt`: la enumeración local usa `DirectoryStream`; el árbol SAF usa un cursor y desplazamiento por lotes de hasta 5.000 entradas. La revisión nueva resuelve el ID del documento actual para subcarpetas y conserva el URI/permiso del árbol original al construir URI de hijos.
- La corrección del recorrido SAF necesita CI sobre el SHA resultante y prueba funcional con el selector real de Android. No declarar validada la navegación física ni una biblioteca de 200 GB sin esa prueba.
- `MainActivity.kt`: «Cargar siguientes 5.000» agrega el lote solicitado sin reiniciar la pantalla. La carga se cancela al cambiar de ubicación y propaga `CancellationException` para no aplicar respuestas obsoletas.
- `FileOperationsTest.kt`: prueba páginas consecutivas, ausencia de elementos repetidos y rechazo de desplazamientos negativos.
- `app/build.gradle.kts`: versión actual `0.3.4`, código 7. Esperar CI y artefacto para el SHA final de `main`; la prueba física con Android/SAF sigue pendiente.
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
