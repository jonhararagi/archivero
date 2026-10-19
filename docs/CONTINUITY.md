## Estado de la rama

- Incremento visual previsto en la versión `0.3.5` / `versionCode 8`. El commit y la CI definitivos deben registrarse una vez que se publique la revisión.
- Se identifica como fuente visual de verdad `pngtuber-capibara.png`, añadida a la raíz de `main`. Se incorpora como `app/src/main/res/drawable-nodpi/mascota_capibara.png` y se muestra en bienvenida, Inicio y estados vacíos.
- La paleta `CAPIBARA` (cacao, crema, beige y coral) pasa a ser predeterminada; una instalación que guardaba el antiguo valor predeterminado `CIBERPUNK` se migra a `CAPIBARA`. Las otras paletas elegidas se conservan y Ciberpunk permanece disponible.
- Iconos de categorías, miniaturas, tarjeta de almacenamiento, icono adaptable y superficies visuales se armonizan con la mascota. Se busca una interfaz cute, cálida y legible, no neón en cada control.
- Estado de compilación de esta revisión: pendiente hasta vincular la ejecución de GitHub Actions al SHA de código y verificar pruebas, APK, hash y certificado.
- La revisión visual en el Xiaomi y las comprobaciones físicas de bibliotecas grandes/SAF siguen pendientes.

## Cambios recientes en código

- `DirectoryListing.kt`: la enumeración local usa `DirectoryStream`; el árbol SAF usa un cursor y desplazamiento por lotes de hasta 5.000 entradas. La revisión nueva resuelve el ID del documento actual para subcarpetas y conserva el URI/permiso del árbol original al construir URI de hijos.
- La corrección del recorrido SAF necesita CI sobre el SHA resultante y prueba funcional con el selector real de Android. No declarar validada la navegación física ni una biblioteca de 200 GB sin esa prueba.
- `MainActivity.kt`: «Cargar siguientes 5.000» agrega el lote solicitado sin reiniciar la pantalla. La carga se cancela al cambiar de ubicación y propaga `CancellationException` para no aplicar respuestas obsoletas.
- `FilePreviewActivity.kt`: los renders cancelados de imagen/PDF se repropagan sin modificar el estado de carga de una vista nueva; la lectura de texto cierra el flujo ante error. Incluye dos pruebas nuevas de límites de paginación.
- `FileOperationsTest.kt`: prueba páginas consecutivas, ausencia de elementos repetidos y rechazo de desplazamientos negativos.
- `app/build.gradle.kts`: versión `0.3.5`, código 8. Verificar la CI asociada al SHA más reciente antes de declarar PASS; la prueba física con Android/SAF sigue pendiente.
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
