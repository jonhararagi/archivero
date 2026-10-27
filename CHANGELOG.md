## [Unreleased] - 2026-10-27

### Añadido
- Vista previa de importación de instantáneas de Biblioteca con recuento de fichas activas, papelera y URI coincidentes antes de modificar el catálogo.
- Reemplazo de coincidencias solo con confirmación explícita; revisión del catálogo asociada a la vista previa para rechazar una confirmación obsoleta.
- Pruebas unitarias para colisiones entre fichas activas/papelera, ausencia de mutaciones durante la vista previa y protección contra cambios entre revisión y confirmación.

## Carga de Biblioteca sin ordenamiento duplicado · 2026-10-24

- Se añade recordsSnapshot(), que devuelve una copia desligada de las fichas en orden de almacenamiento sin ordenar el catálogo completo.
- La carga inicial y el refresco de Biblioteca usan esta instantánea porque los filtros visuales ya aplican su propio orden. all() conserva el contrato de devolver una lista alfabética completa.
- Prueba nueva: verifica el orden de ambas APIs antes y después de reabrir el catálogo.
- No cambia el esquema ni los datos persistidos. El ahorro esperado es evitar una ordenación O(N log N) durante carga/refresco; aún no hay benchmark de dispositivo.
- **CI PASS del PR:** [run 40062210605](https://github.com/jonhararagi/archivero/actions/runs/40062210605), SHA c59b3403165ff1f3d13188968f346cfbc4d8dc76. Pasaron pruebas unitarias, compilación Android, verificación de firma/hash y subida. Artefacto archivero-debug-apk, ID 12548266062, digest SHA-256 db2c094473ea2c3264c49adf375db1d25e5a3ee1f549a5c5380a397bab855de4. Integrado en main mediante 93e70d016abbe044dbf38a6cd39117c3b6bc40a0.

---
## Selección acotada en la búsqueda del catálogo · 2026-10-24

- La búsqueda interna del catálogo conserva solo los mejores N resultados en un heap acotado, en lugar de ordenar y materializar todas las coincidencias para después aplicar `take(limit)`.
- Se mantienen los criterios de coincidencia, la normalización de tildes, los filtros por categoría y etiquetas, la prioridad de favoritos y el orden determinista por nombre y URI.
- Prueba añadida con 180 fichas: compara la página acotada con un ordenamiento completo de referencia y valida filtros, favoritos, desempates y tamaño del resultado.
- La búsqueda sigue inspeccionando los metadatos del catálogo; este cambio no supone indexación de contenido ni se presenta como un benchmark de dispositivo.
- **CI PASS:** [run 40061589163](https://github.com/jonhararagi/archivero/actions/runs/40061589163), SHA 52c926f23a1f3492d15f31f787f7aea5f8050860. Pasaron pruebas unitarias, compilación Android, firma/hash y subida. Artefacto APK ID 12547498295, 20.666.176 bytes, digest SHA-256 aa2c68afc0c0c58f0fdbb5b7352e42be547b37b9cb3faad5a23c499ccc4d437c. PR #4 quedó integrado en main mediante e492a474e5d8de1786467c69324bc3c93562f209; CI posterior de main también PASS en [run 40061745082](https://github.com/jonhararagi/archivero/actions/runs/40061745082), artefacto ID 12547402669.

---

## Transacciones del catálogo frente a fallos de escritura · 2026-10-23

- Se introduce un punto de inyección interno y opcional para simular un error del escritor de instantáneas en pruebas; el comportamiento normal sigue usando el guardado temporal con reemplazo atómico cuando el sistema lo admite.
- Se añade una prueba que provoca fallos de escritura al editar una ficha y al moverla a papelera. Comprueba que el error se propaga, el estado en memoria no cambia y al reabrir se recupera la última instantánea válida del disco.
- No se alteran el esquema, las URI ni los recursos físicos. La prueba es determinista y no depende de permisos de archivos del sistema de CI.
- **CI PASS:** [run 40024070823](https://github.com/jonhararagi/archivero/actions/runs/40024070823), SHA 3fddcf9f0894d8f32844c8e63dce0da19f9f95d2; pasaron pruebas, compilación, firma/hash y subida. Artefacto ID 12531198333, 20.666.637 bytes, digest SHA-256 6f0f739bd30ca40a0ad98ee2cdcab9c47173c561ae53b0ed3f4498e497a743b9. PR #3 quedó integrado en main en c9da3c2339d26667d3862c8a7d03ae95281677e3; la CI posterior de main también terminó PASS en [run 40048558887](https://github.com/jonhararagi/archivero/actions/runs/40048558887), artefacto ID 12542302508.

---

## Búsqueda tolerante a tildes en Biblioteca · 2026-10-23

- La búsqueda por metadatos y los filtros de categoría, tipo, proyecto y etiquetas pueden encontrar términos aunque el usuario omita tildes, por ejemplo `cancion` para `Canción`.
- Se conserva la diferencia entre `ñ` y `n`, evitando que una consulta como `nino` se confunda con `niño`.
- El texto normalizado se deriva en memoria. Los nombres, etiquetas, notas y demás metadatos originales no se reescriben y el esquema del catálogo no cambia.
- Prueba añadida para búsqueda entre varios campos, filtros, etiquetas, distinción de la `ñ` y persistencia del texto original.
- **CI PASS de código y pruebas:** [run 39960718647](https://github.com/jonhararagi/archivero/actions/runs/39960718647), SHA `8cb4e45583c8566d59381eeeef7ee249d56e1b6b`. Pasaron `testDebugUnitTest`, `assembleDebug`, la verificación de firma/hash y la subida. Artefacto `archivero-debug-apk`, ID `12505060873`, 20.666.763 bytes, `expired=false`, digest SHA-256 `bc743fa0019dd6b7b1b313058fc8a97163d2cf211cf37dbb571293d138001bae`. El cambio posterior en la rama solo reajustó comentarios/documentación de este comportamiento.

---

## Texto buscable normalizado reutilizable · 2026-10-22

- Cada ficha inmutable calcula una sola vez su representación de texto en minúsculas para búsquedas posteriores. Los filtros y la búsqueda del catálogo reutilizan ese texto en vez de concatenar y normalizar otra vez todos los campos en cada consulta.
- El texto derivado permanece solo en memoria y no altera el esquema ni la exportación. Una ficha editada mediante `copy` obtiene una nueva caché acorde con sus nuevos metadatos.
- Se añade una prueba de reutilización y se verifica que una copia con nombre, etiquetas y notas modificadas no hereda texto buscable antiguo ni contamina la ficha anterior.
- No se declara un porcentaje de mejora: aún no hay benchmark representativo en un dispositivo.
- **CI PASS:** [run 39882676213](https://github.com/jonhararagi/archivero/actions/runs/39882676213), SHA `364c0ec61f917815bd02c6394864f61be0182938`. Pasaron pruebas unitarias, `assembleDebug`, verificación de firma/hash y subida. Artefacto `archivero-debug-apk`, ID `12471765139`, ZIP de 20.666.158 bytes, `expired=false`, digest SHA-256 `37869f682f2f6eefafee904e815a69253ef098c6cd3dd316444ebb918e33f542`.

---

## Paginación progresiva de resultados de Biblioteca · 2026-10-22

- Se reemplaza el corte fijo de 500 fichas por páginas progresivas de hasta 500 resultados adicionales. Los filtros nuevos y los cambios de catálogo reinician la vista a la primera página.
- La pantalla mantiene los componentes perezosos de cuadrícula/lista, muestra cuántas fichas están cargadas y permite continuar hasta el máximo seguro de vista de 10.000; al alcanzar ese techo pide reducir con filtros.
- Se añadió una función pura para avanzar páginas con tope y una prueba de límites, además de conservar el filtro previo de resultados acotados.
- **CI PASS:** [run 39882184901](https://github.com/jonhararagi/archivero/actions/runs/39882184901), SHA `f9316f3d827df9f8c5500297a190a7cb063f58ad`. Pasaron pruebas unitarias, `assembleDebug`, verificación de firma/hash y subida. Artefacto `archivero-debug-apk`, ID `12471520229`, ZIP de 20.665.482 bytes, `expired=false`, digest SHA-256 `3b71728d7166d5722c2162b5b8dc2e0917ab43d766e3088d0be0864082365b41`.

---

## Búsqueda de varias palabras por metadatos · 2026-10-22

- La búsqueda de la Biblioteca ahora interpreta las palabras separadas por espacios como términos que deben coincidir todos, aunque aparezcan en campos distintos: nombre, categoría, etiquetas, proyecto, tipo, notas, procedencia o relaciones.
- Se conserva el filtrado combinado, el orden de resultados y el límite acotado. No se indexa ni se lee el contenido binario de los archivos: la búsqueda sigue trabajando exclusivamente sobre metadatos ya catalogados.
- Se añadieron pruebas para términos que aparecen en campos diferentes y para una consulta en la que falta uno de los términos.
- **CI PASS:** [run 39881521173](https://github.com/jonhararagi/archivero/actions/runs/39881521173), SHA `f30edbe3847c7fd2b8f8222c59983b18e267d0ad`. Pasaron pruebas unitarias, `assembleDebug`, verificación de firma/hash y subida. Artefacto `archivero-debug-apk`, ID `12470223376`, ZIP de 20.663.357 bytes, `expired=false`, digest SHA-256 `b8b75e8e7977a67e34de29e7759872db1c7a022b9560c1253133e2dd27435b6d`.

---

## Agrupación visual más estricta y liberación segura de bitmaps · 2026-10-21

- El agrupador de similitud ya no trata cualquier cadena de coincidencias como un solo grupo: ahora cada pareja dentro de un grupo debe cumplir el umbral de distancia perceptual. Se ordenan previamente las huellas/URI para obtener una partición reproducible con la misma entrada, aunque llegue en otro orden.
- Se añaden pruebas para el caso engañoso A↔B↔C donde A y C exceden el umbral, para determinismo y umbrales fuera de rango.
- El análisis conserva la cancelación en vez de absorber una `CancellationException` como si fuera una imagen ilegible. La decodificación reduce el riesgo de retener bitmaps si el escalado o la lectura de píxeles fallan.
- Referencias de diseño consultadas: [Hydrus Network](https://github.com/hydrusnetwork/hydrus) destaca organización por etiquetas, colecciones grandes y privacidad local; [digiKam](https://github.com/KDE/digikam) combina etiquetas, metadatos y búsqueda por múltiples criterios; [Tropy](https://github.com/tropy/tropy) se orienta a describir fotos de investigación para reencontrar fuentes. Archivero adopta principios compatibles, no su código ni dependencias pesadas.
- **CI PASS:** [run 39656468147](https://github.com/jonhararagi/archivero/actions/runs/39656468147), SHA `e619ba846b5f2b75ef2f4273868589125745e6df`. Pasaron `testDebugUnitTest`, `assembleDebug`, la verificación de firma/hash y la subida del artefacto. `archivero-debug-apk`, ID `12372620604`, ZIP 20.662.905 bytes, no expirado; digest ZIP SHA-256 `4fd7b2a0c061102c3bfe84ce21c4ffe46d027f37db755863bcc19f88f045f397`.

---

## Búsqueda perceptual de imágenes dentro de Biblioteca · 2026-10-21

- Se integra en Biblioteca el acceso «Buscar fotos parecidas en fichas visibles», reutilizando el detector perceptual local existente.
- Analiza el conjunto filtrado actual, con límite de interfaz de 500 fichas visibles, trabajo fuera del hilo visual, cancelación y hasta 100 grupos/20 nombres por grupo.
- La interfaz explica que la similitud visual no confirma duplicación exacta. La operación solo lee miniaturas y metadatos necesarios; no modifica fichas ni archivos físicos. El resumen identifica análisis parciales y recursos no legibles.
- Se añadieron pruebas unitarias para resultados vacíos, grupos similares y estados parciales con recursos inaccesibles.
- **CI PASS del código funcional:** [run 39647800495](https://github.com/jonhararagi/archivero/actions/runs/39647800495), SHA `81516cbed5ea1a473162f687c139db714841ddd2`. Completaron correctamente `testDebugUnitTest`, `assembleDebug`, verificación de firma/hash y subida.
- **Artefacto verificado:** `archivero-debug-apk`, ID `12368157254`, ZIP de 20.662.550 bytes, `expired=false`.
- No se ha probado en el Xiaomi físico; CI no sustituye la comprobación de permisos/proveedores Android reales.
- TIMER estimado para este lote: 30–60 minutos de agente, no una medición del tiempo real.
- La rama sigue siendo `feature/archivero-digital-library-001`; PR #3 permanece abierto en borrador y sin fusionar en `main`.

---

## Asignación de proyecto/colección por lotes · 2026-10-21

- La Biblioteca incorpora una acción para asignar el mismo proyecto/colección a varias fichas visibles, con selección, vista previa de los valores anteriores y un único guardado del índice.
- Se conservan etiquetas, notas, procedencia, tipo de recurso y relaciones. Solo cambian el campo `project` y la marca temporal de las fichas cuya asignación varía.
- El catálogo valida nombre y todas las URI antes de escribir. Una URI inexistente o nombre inválido rechaza el lote completo, sin cambios parciales en memoria ni disco.
- Pruebas nuevas cubren asignación/persistencia, preservación de metadatos, operación sin cambios, URI inexistente y nombres vacíos, con controles o demasiado largos. CI [run 39642066619](https://github.com/jonhararagi/archivero/actions/runs/39642066619), SHA `215043d5a72295463e165a7064c65775c80f62d7`: **PASS** en tests, APK, firma/hash y subida. Artefacto `archivero-debug-apk` ID `12365273553`, ZIP de 20.647.933 bytes.

---

## Auditoría manual de acceso a recursos · 2026-10-21

- Se añadió una revisión explícita de todas las fichas activas para comprobar visibilidad y bandera de lectura del proveedor Android. La operación es cancelable, informa del progreso y conserva muestras acotadas para no retener un informe gigante en memoria.
- Se distinguen recursos legibles, URI que el proveedor no muestra y URI sin acceso o lectura. «No visible» se presenta con cautela porque un permiso revocado o un proveedor distinto puede ocultar un archivo sin que se haya borrado.
- El análisis consulta metadatos del proveedor; no lee el contenido de los archivos, no corrige URI, no elimina fichas y no borra recursos físicos.
- Se añadieron pruebas unitarias del acumulador, recuentos y límite de muestras. La primera CI [run 39639788751](https://github.com/jonhararagi/archivero/actions/runs/39639788751) falló al compilar porque `FolderSearch` no existe en los iconos Compose instalados; se reemplaza por `Search` antes de reintentar. No se publicó APK en el run fallido.
- Corrección validada: [run 39640308153](https://github.com/jonhararagi/archivero/actions/runs/39640308153), SHA `c21b4512f3058da56753f435d90e947329d548c1`: **PASS** en tests, APK debug, firma/hash y subida. Artefacto `archivero-debug-apk` ID `12363234364`, ZIP de 20.631.102 bytes.

---

## Papelera recuperable de la Biblioteca · 2026-10-21

- La acción «Mover a papelera» deja de quitar una ficha de forma irreversible del índice: guarda sus metadatos y fecha de retirada en el mismo archivo local, con opción de restaurar.
- El esquema pasa de V2 a V3. Sigue leyendo V1 y V2 y agrega `trash.count` más las fichas retiradas. Se agregó una prueba explícita para migrar V2→V3 conservando etiquetas, procedencia, proyecto, tipo de recurso y relaciones. Biblioteca y papelera se escriben en la misma instantánea temporal con reemplazo atómico cuando el sistema lo admite.
- La importación/exportación incluye elementos en papelera. Se mantienen los conflictos URI por defecto, se rechazan duplicados entre activos y papelera, y un reingreso manual de la misma URI devuelve esa ficha a la biblioteca activa.
- «Eliminar ficha» desde la papelera exige confirmación y elimina solamente metadatos. La ruta no borra ni mueve archivos físicos.
- Se añadieron pruebas de recuperación tras reabrir el catálogo, exportación/importación con elementos en papelera, reingreso de la misma URI y borrado permanente de metadatos sin tocar el archivo real. CI [run 39638194130](https://github.com/jonhararagi/archivero/actions/runs/39638194130), SHA `a7ed2a03839d0effc596c3cb0719aa0fd879f733`: **PASS** en tests, compilación Android, verificación de firma/hash y subida de APK. Artefacto ID `12365030210`, ZIP de 20.614.259 bytes.

---

## Auditoría de integridad de relaciones · 2026-10-21

- Se añadió un reporte local que busca relaciones cuyo destino no tiene ficha en el catálogo y fichas que no reciben enlaces de otras fichas.
- Los registros sin enlaces entrantes se marcan como informativos, porque pueden ser recursos independientes legítimos. No se eliminan ni alteran registros.
- La auditoría se ejecuta fuera del hilo visual, coopera con la cancelación y limita las muestras a 100 filas por categoría aunque el catálogo tenga muchos vínculos.
- Se añadieron pruebas para URI objetivo sin ficha, enlaces válidos, fichas independientes, URI duplicadas y límite de muestras. CI [run 39635472592](https://github.com/jonhararagi/archivero/actions/runs/39635472592), SHA `210e43570f39e38b1843844ec02b1ca9f43f9d9e`: **PASS** en tests, APK, firma/hash y artefacto ID `12360483934` (20.590.366 bytes). El run anterior [39635345291](https://github.com/jonhararagi/archivero/actions/runs/39635345291) falló en una expectativa de orden de las muestras; se ajustó la expectativa para que coincida con el orden alfabético del reporte.

---

## Búsqueda con selección acotada de resultados · 2026-10-21

- `filterLibraryRecords` ya no ordena toda la lista coincidente para luego tomar 500/501 elementos: mantiene solo los mejores N elementos en una cola de prioridad acotada y devuelve esos resultados en orden.
- Se mantiene la misma prioridad de favoritos, suborden por Nombre/Recientes/Proyecto, filtro de proyecto/colección y criterio determinista por URI.
- Se añadió una prueba con 5.000 fichas generadas que compara las primeras 37 entradas en las tres ordenaciones con el orden completo de referencia, además de un caso con filtro por proyecto.
- Es una optimización algorítmica verificada por tests, no un benchmark de rendimiento medido en un dispositivo. CI [run 39634548741](https://github.com/jonhararagi/archivero/actions/runs/39634548741), SHA `f7a720bda5f7d00c45eb926ce32d61cef620d850`: **PASS** en tests, APK, firma/hash y artefacto ID `12360583047` (20.573.162 bytes).

---

## Filtro de proyecto/colección · 2026-10-21

- La barra de filtros ahora permite elegir cualquiera de los proyectos/colecciones ya usados por las fichas; la lista de opciones se construye en memoria, se deduplica sin distinguir mayúsculas/minúsculas y se ordena de forma determinista.
- El filtro de proyecto se combina con texto, categoría, tipo y Solo favoritos. No introduce una migración ni inventa nuevas colecciones.
- Se añadieron pruebas para un filtro de proyecto aislado y para una combinación de proyecto, nombre/etiqueta, categoría y tipo.
- CI [run 39634053321](https://github.com/jonhararagi/archivero/actions/runs/39634053321), SHA `a4cded20629d8800dec4d556841fde9df1dc8253`: **PASS**, artefacto ID `12360063059` (20.572.760 bytes).

---

## Etiquetado por lotes con vista previa · 2026-10-21

- Se añadió una operación para seleccionar hasta 500 fichas visibles del filtro actual, revisar nombres/etiquetas que se van a cambiar y añadir varias etiquetas con una sola escritura del catálogo.
- Las etiquetas existentes se conservan; entradas repetidas se normalizan sin distinguir mayúsculas/minúsculas. Si una URI ya no existe, una etiqueta no es válida o alguna ficha superaría el límite de 100, se rechaza el lote completo antes de persistir.
- Los cambios se hacen solo en metadatos; los archivos físicos no se mueven ni se modifican.
- Se añadieron pruebas de persistencia, deduplicación de etiquetas, operación sin cambios, URI inexistente y límite que asegura rollback completo en memoria y disco. CI [run 39633408582](https://github.com/jonhararagi/archivero/actions/runs/39633408582), SHA `5880a532f0f7c87709c7f7828643c89ced478bc0`: **PASS**, artefacto ID `12360152672` (20.566.079 bytes).

---

## Corrección de selector del escáner · 2026-10-21

- La ejecución de CI [39632036037](https://github.com/jonhararagi/archivero/actions/runs/39632036037), para SHA `8074cadfb5e5d8ad8ad1db7904df55658afc0114`, falló en `compileDebugKotlin`.
- Causa observada en el log: el selector entregado a `potentialDuplicateCandidates` intentaba leer `value` de un `LibraryRecord`. Se cambia a `it.uri`, coherente con la firma real de la función reutilizada.
- El fallo permanece registrado. La corrección requiere pasar una nueva CI antes de marcar la función como válida.

---

## Duplicados exactos dentro de la Biblioteca · 2026-10-21

- Se añadió una acción independiente para analizar los recursos ya catalogados y detectar copias exactas por tamaño positivo + SHA-256 del contenido completo.
- El cálculo de hashes se limita a candidatos que comparten tamaño, se ejecuta fuera del hilo de interfaz y puede cancelarse. Los recursos inaccesibles o con tamaño no disponible se omiten de forma segura.
- No se guardan hashes persistentes, para evitar resultados antiguos si cambia un archivo. Se vuelve a comprobar el tamaño tras la lectura; un cambio detectado hace que ese recurso no participe del resultado.
- La lista muestra grupos, nombres de fichas y un prefijo del hash. No elimina, mueve, renombra ni altera los archivos.
- Se añadieron pruebas unitarias para digestos completos, URI repetidas, tamaños distintos, hash inválidos y orden determinista. El resultado todavía debe verificarse mediante CI y una prueba manual con proveedores SAF reales.

---

## Filtro de favoritos · 2026-10-21

- Se añadió el chip **Solo favoritos** a la barra de filtros de la Biblioteca. Es combinable con texto, categoría y tipo de recurso, y permite volver al conjunto completo al desactivarlo.
- La función pura `filterLibraryRecords` incorpora `onlyFavorites`; el test verifica selección vacía sin favoritos y el subconjunto esperado cuando hay fichas marcadas.
- GitHub Actions [run 39627829320](https://github.com/jonhararagi/archivero/actions/runs/39627829320), SHA `3f8c725f08cb0d68309bcedd2138eeb36c7fd892`: **PASS** en tests, APK, firma/hash y subida del artefacto.
- Artefacto `archivero-debug-apk`, ID `12357562577`, ZIP de 20.517.818 bytes. Aún no se ha probado visualmente en el Xiaomi.

---

## Favoritos de biblioteca · ordenación con prioridad · 2026-10-21

- Cada ficha de Biblioteca tiene una estrella para marcar/quitar el favorito desde cuadrícula o lista. Los favoritos son independientes del reproductor musical y se guardan localmente en preferencias.
- Los favoritos pasan al frente dentro de la ordenación elegida (Nombre, Recientes o Proyecto) y admiten hasta 5.000 URI para mantener acotado el almacenamiento de preferencias.
- Al recargar el catálogo se eliminan de la lista de favoritos las fichas que ya no existen, evitando referencias fantasma tras quitar metadatos. El archivo físico nunca se borra desde esta operación.
- Se añadió prueba unitaria para verificar que los favoritos se ordenan antes que las demás fichas sin perder el orden determinista.
- GitHub Actions [run 39627115115](https://github.com/jonhararagi/archivero/actions/runs/39627115115), SHA `d89f6db6dcc55354997cd5754bdccf37f1cf167a`: **PASS** en pruebas unitarias, compilación Android, verificación de firma/hash y subida del APK.
- Artefacto `archivero-debug-apk`, ID `12358276807`, ZIP de 20.516.295 bytes. El commit validado incluye los favoritos, la prueba nueva y el catálogo visual/filtros de la iteración anterior.
- Pendiente: todavía no hay prueba manual del favorito ni benchmark en el Xiaomi o con proveedores SAF reales.

---

## Biblioteca visual, relaciones y filtros combinados · 2026-10-21

- La sección Biblioteca ahora ofrece cuadrícula/lista, miniaturas reutilizadas de la caché existente y metadatos técnicos disponibles: MIME/extensión, tamaño, fecha de modificación y dimensiones de imágenes cuando Android puede consultarlas.
- El esquema del catálogo pasa de V1 a V2 con campos de proyecto/colección, tipo de recurso y hasta 100 URI relacionadas. La carga sigue siendo compatible con V1, conserva los campos antiguos y asigna valores predeterminados a los campos nuevos.
- Las fichas distinguen recursos originales, derivados, editados, generados, referencias y personajes sin inventar procedencias ni etiquetas. Las URI que ya no se pueden abrir se conservan y se muestran con un estado de acceso explícito.
- Se agregaron filtros combinables de texto, categoría y tipo de recurso, orden por nombre/recientes/proyecto e historial local de las últimas ocho búsquedas con opción para borrarlo.
- La búsqueda de fichas incorpora proyecto, tipo y relaciones; filtrado, consulta de metadatos y almacenamiento continúan fuera del hilo visual, y la pantalla limita la lista a 500 resultados.
- Pruebas: migración V1→V2 conservando notas y etiquetas; normalización de relaciones; filtros combinados, orden estable y límite de resultados. La expectativa de orden alfabético se corrigió tras la ejecución que la detectó.
- GitHub Actions [run 39625852877](https://github.com/jonhararagi/archivero/actions/runs/39625852877), SHA `e0d5ea7b173d795c2ecc7e5629efd8fc2f143614`: **PASS** en `testDebugUnitTest`, `assembleDebug`, comprobación de firma/hash y publicación del artefacto.
- Artefacto `archivero-debug-apk`, ID `12356454794`, ZIP de 20.513.480 bytes.
- Historial de fallos no ocultado: `bfb02d5e...` falló por compatibilidad de la firma de SearchBox; `ade41baa...` compiló pero tuvo una expectativa de ordenación de test incorrecta. Las correcciones están incluidas en la cabeza PASS.
- Pendiente: prueba visual/manual en el Xiaomi, permisos y proveedores SAF externos, benchmark con 1.000/10.000 imágenes y comprobación de la biblioteca real de aproximadamente 200 GB. CI no demuestra fluidez física ni escala real.

---

## Biblioteca digital local y búsqueda fuera del hilo visual · 2026-10-21

- Se añadió una sección de Biblioteca donde los recursos existentes pueden tener nombre visible, categoría, etiquetas, notas y procedencia sin copiar ni mover el archivo original.
- El catálogo local valida importaciones, rechaza URI en conflicto por defecto, limita instantáneas a 64 MiB y conserva el índice anterior ante errores de lectura/validación.
- Los selectores de documentos Android permiten importar/exportar una instantánea de metadatos.
- La carga y persistencia del catálogo se movieron a `Dispatchers.IO`; la búsqueda usa debounce y `Dispatchers.Default`, muestra como máximo 500 fichas y no ordena toda la biblioteca en el hilo visual en cada cambio.
- Pruebas de catálogo añadidas para persistencia, búsqueda por metadatos, etiquetas, exportación/importación, conflictos, archivos corruptos/sobredimensionados y no borrar el recurso al quitar su ficha.
- CI: [run 39620283829](https://github.com/jonhararagi/archivero/actions/runs/39620283829), SHA `f7daab88136ce21ea94477919e3bd2e119fe3246`, **PASS**: pruebas unitarias, compilación, firma/hash y publicación del artefacto `archivero-debug-apk` (ID `12354766201`).
- Limitación: todavía no se han realizado pruebas manuales en el Xiaomi, con proveedores SAF externos, ni benchmarks con 1.000/10.000 imágenes o la biblioteca de aproximadamente 200 GB. No declarar validada esa escala.

---

## Paginación y seguridad de proveedores · 2026-10-20

- La enumeración por lotes para el sistema de archivos y árboles SAF conserva un límite de memoria al leer entradas, mientras que los proveedores no compatibles ya no pasan por `DocumentFile.listFiles()`, que materializa una matriz completa antes de aplicar el límite.
- En ese caso, Archivero muestra el motivo y solicita volver a seleccionar la carpeta mediante el selector SAF, en lugar de arriesgar un consumo descontrolado de memoria o presentar una falsa paginación.
- La vista de carpeta muestra el motivo real del error de enumeración en lugar de una explicación genérica de permisos.
- En una instalación nueva, la paleta inicial ahora es Ciberpunk (azul noche con acentos cian/magenta); las preferencias de paleta guardadas por el usuario se conservan.
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
