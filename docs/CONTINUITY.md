## Continuidad · integridad de exportación de Biblioteca · 2026-10-28

- Rama: feature/archivero-verified-library-export-001, basada en main inspeccionado en 9ff54f1f81da57f6c5c6c5131b80b1e911514b94. Main no se modifica directamente.
- Lote: la exportación calcula SHA-256 del flujo realmente escrito y, cuando se puede volver a abrir el destino, compara bytes y hash con la instantánea temporal original.
- El estado visual distingue exportación verificada, destino no releíble y contenido que no coincide. Un proveedor que no permita lectura de vuelta no se presenta como verificado.
- Helper separado de la interfaz, sin nuevas dependencias, con límite de 64 MiB y progreso ante un read(byte[]) que devuelve cero.
- Cobertura añadida para hash conocido, lectura de vuelta, flujo vacío, límite y lectura de bloque sin progreso.
- CI, artefacto APK y pruebas del HEAD de esta rama: pendientes hasta ejecutar el workflow de PR.

---

## Continuidad · instantánea para carga y refresco de Biblioteca · 2026-10-24

- PR #5 integrado en main mediante 93e70d016abbe044dbf38a6cd39117c3b6bc40a0. La rama feature/archivero-library-snapshot-load-001 ya no contiene cambios pendientes de integración.
- DigitalLibraryCatalog.recordsSnapshot() devuelve una copia desligada en orden de almacenamiento sin ordenar el catálogo. all() conserva el orden alfabético completo.
- Las dos lecturas usadas al iniciar y refrescar la pantalla Biblioteca pasan a recordsSnapshot(); el orden final visible continúa a cargo de filterLibraryRecords().
- Prueba nueva: comprueba el orden de almacenamiento y el orden alfabético antes y después de reabrir el índice.
- Sin cambios de esquema, persistencia o archivos físicos. **CI PASS del PR:** run 40062210605, con pruebas, compilación, firma/hash y artefacto verificados antes de la fusión. Sin benchmark en el Xiaomi.

---

## Auditoría de ramas y consolidación · 2026-10-24

- PR #3 está fusionado en main en c9da3c2339d26667d3862c8a7d03ae95281677e3; CI del PR y CI posterior de main terminaron PASS.
- PR #4 está fusionado en main en e492a474e5d8de1786467c69324bc3c93562f209; CI del PR y CI posterior de main terminaron PASS.
- La rama histórica feature/archivero-android-file-manager está divergida (28 commits exclusivos y 164 commits detrás de main al auditarla) y no tiene PR abierto. Se inspeccionaron los principales cambios de apertura externa, tipos MIME, procesamiento en segundo plano, vistas PDF/vídeo y operaciones ZIP/copia/movimiento; la versión actual de main contiene implementaciones evolucionadas de esas funciones.
- No se debe fusionar la rama histórica completa: puede reintroducir versiones antiguas de MainActivity.kt, AndroidManifest.xml y actividades multimedia. Si aparece una función concreta ausente, se rescata por separado con una prueba y un PR.
- Regla persistente: ninguna mejora aprobada se da por entregada hasta comprobar su integración en main y registrar su SHA y su CI.

---
## Continuidad · búsqueda acotada del catálogo · 2026-10-24

- PR #4 quedó fusionado en main en e492a474e5d8de1786467c69324bc3c93562f209. Su rama ya no contiene cambios pendientes de integración.
- `DigitalLibraryCatalog.search` ahora mantiene como máximo `limit` coincidencias en una cola de prioridad y ordena únicamente esa ventana. El recorrido de búsqueda sigue siendo lineal sobre los metadatos; la selección acotada reduce la memoria auxiliar y evita el ordenamiento completo.
- Se preservan favoritos primero, después nombre en minúsculas y URI como desempate estable; filtros por texto, categoría y etiquetas no cambian.
- Prueba añadida: 180 registros y comparación exacta entre la salida limitada y un ordenamiento completo de referencia, incluidos favoritos y filtros combinados.
- No se incorporan dependencias, no se modifica el esquema ni se tocan archivos físicos.
- CI PASS del PR en run 40061589163 y CI PASS posterior en main en run 40061745082. Sin benchmark en el Xiaomi; no atribuir una mejora porcentual hasta medirla.

---

## Continuidad · errores de escritura y rollback del catálogo · 2026-10-23

- PR #3 quedó fusionado en main en c9da3c2339d26667d3862c8a7d03ae95281677e3. La rama feature/archivero-digital-library-001 quedó detrás de main y no debe volver a integrarse como bloque.
- Se añade un punto de inyección opcional al escritor de instantáneas para simular fallos de almacenamiento determinísticamente en pruebas. El camino de producción conserva el escritor normal.
- Nueva prueba: el guardado falla al editar la ficha y al moverla a papelera; las operaciones propagan `IOException`, la instancia no publica cambios sin persistencia y una reapertura conserva el último catálogo válido.
- Sin migración de esquema, sin dependencias nuevas y sin operaciones sobre los archivos físicos.
- CI PASS del código en run 40024070823 y CI PASS posterior de main en run 40048558887. Sin migración ni dependencias nuevas.

---

## Continuidad · búsqueda tolerante a tildes · 2026-10-23

- Esta mejora se integró en main junto con el PR #3 mediante c9da3c2339d26667d3862c8a7d03ae95281677e3; se conserva la nota histórica para documentar la decisión técnica.
- Añadida `normalizeLibrarySearchText`: normaliza para buscar sin tildes y conserva la `ñ` como letra distinta de `n`.
- La regla se reutiliza en las consultas de metadatos y en los filtros visuales por categoría, tipo, proyecto y etiquetas. No modifica el texto almacenado, no cambia el esquema y no añade dependencias.
- Prueba: consulta `cancion personaje` contra distintos campos, filtros `musica/edicion/coleccion`, etiqueta con tilde, distinción `nino` vs. `niño` y comprobación de que el registro original conserva sus tildes.
- **CI PASS de código y pruebas:** [run 39960718647](https://github.com/jonhararagi/archivero/actions/runs/39960718647), SHA `8cb4e45583c8566d59381eeeef7ee249d56e1b6b`. Pasaron `testDebugUnitTest`, `assembleDebug`, la verificación de firma/hash y la subida. Artefacto `archivero-debug-apk`, ID `12505060873`, 20.666.763 bytes, `expired=false`, digest SHA-256 `bc743fa0019dd6b7b1b313058fc8a97163d2cf211cf37dbb571293d138001bae`. El cambio posterior en la rama solo reajustó comentarios/documentación de este comportamiento.
- TIMER estimado del lote: 30–60 minutos de agente; no representa una medición de rendimiento en dispositivo.

---

## Continuidad · reutilización del texto buscable · 2026-10-22

- Rama autorizada: `feature/archivero-digital-library-001`; base inspeccionada: `f9316f3d827df9f8c5500297a190a7cb063f58ad`. No tocar `main` ni fusionar PR #3.
- `LibraryRecord` tiene ahora una representación normalizada de búsqueda calculada de forma diferida y conservada en memoria por instancia. El texto incluye los mismos campos ya buscables, sin incorporar contenido binario ni una dependencia de indexación.
- El filtrado principal y `DigitalLibraryCatalog.search` reutilizan el texto. Las fichas son tratadas como valores inmutables; las ediciones crean copias nuevas, así que el texto derivado no se persiste ni debe quedar asociado a nuevos metadatos.
- Prueba nueva: la lectura repetida reutiliza el texto y una ficha copiada con nombre, etiquetas y notas distintos genera su propio texto sin conservar términos antiguos.
- **CI PASS:** [run 39882676213](https://github.com/jonhararagi/archivero/actions/runs/39882676213), SHA `364c0ec61f917815bd02c6394864f61be0182938`. Pasaron tests, compilación Android, firma/hash y subida. Artefacto `archivero-debug-apk`, ID `12471765139`, ZIP de 20.666.158 bytes, `expired=false`, digest SHA-256 `37869f682f2f6eefafee904e815a69253ef098c6cd3dd316444ebb918e33f542`.
- Se registra como evidencia anterior la paginación validada por [run 39882184901](https://github.com/jonhararagi/archivero/actions/runs/39882184901), SHA `f9316f3d827df9f8c5500297a190a7cb063f58ad`, con tests, APK firmado y artefacto no expirado. No se ha ejecutado benchmark en el Xiaomi.
- TIMER estimado: 30–60 minutos de agente; no representa tiempo de rendimiento medido.

---

## Continuidad · paginación progresiva en Biblioteca · 2026-10-22

- Rama autorizada: `feature/archivero-digital-library-001`; base inspeccionada: `e098d1efa454782cae85aa6e3fd74cf31569d5e0`. PR #3 permanece abierto en borrador y sin fusionar en `main`.
- Se sustituye el corte rígido de 500 resultados por carga progresiva en páginas de hasta 500. Cada cambio en consulta, filtros, favoritos o catálogo vuelve a la primera página para impedir que una página posterior se conserve sobre un conjunto nuevo.
- La lista/grid sigue siendo perezosa. La vista puede crecer hasta 10.000 fichas; alcanzado ese tope, se muestra una instrucción de reducir la búsqueda en vez de prometer paginación ilimitada.
- Se añade `nextLibraryResultLimit` y test de avance, saturación en el máximo e invalidación del límite cero.
- **Último cambio funcional ya validado antes de este lote:** [run 39881521173](https://github.com/jonhararagi/archivero/actions/runs/39881521173), SHA `f30edbe3847c7fd2b8f8222c59983b18e267d0ad`, PASS en tests, compilación, firma/hash y artefacto.
- **CI PASS de paginación:** [run 39882184901](https://github.com/jonhararagi/archivero/actions/runs/39882184901), SHA `f9316f3d827df9f8c5500297a190a7cb063f58ad`. Tests, compilación Android, firma/hash y subida completaron correctamente. Artefacto `archivero-debug-apk`, ID `12471520229`, ZIP 20.665.482 bytes, `expired=false`, digest SHA-256 `3b71728d7166d5722c2162b5b8dc2e0917ab43d766e3088d0be0864082365b41`.
- No se declara prueba en Xiaomi ni benchmark de una biblioteca real.
- TIMER estimado: 30–60 minutos de agente, no tiempo medido en dispositivo.

---

## Continuidad · búsqueda de varias palabras por metadatos · 2026-10-22

- Rama autorizada: `feature/archivero-digital-library-001`; commit base inspeccionado: `6465fe4d3ac1d1dd34452f0d32b927609f5ac0d4`. PR #3 sigue como borrador abierto; no modificar `main`, no fusionar ni crear ramas duplicadas.
- Se cambia la búsqueda libre de frase contigua a términos AND separados por espacios. Todos los términos deben aparecer en el texto de metadatos buscable, aunque estén repartidos entre nombre, etiquetas, notas, procedencia, tipo o proyecto. Los filtros de categoría, tipo, proyecto, favoritos, orden y límite no cambian.
- Se reutiliza una pequeña función pura para compartir la regla entre el filtrado de la pantalla y el catálogo; no se agrega ninguna dependencia ni indexación costosa.
- Pruebas nuevas en `DigitalLibraryCatalogTest`: coincidencia de términos entre campos y resultado vacío si falta un término; el conjunto de filtros visuales también tiene cobertura.
- **CI PASS del cambio funcional:** [run 39881521173](https://github.com/jonhararagi/archivero/actions/runs/39881521173), SHA `f30edbe3847c7fd2b8f8222c59983b18e267d0ad`. Pasaron pruebas unitarias, `assembleDebug`, verificación de firma/hash y subida. Artefacto `archivero-debug-apk`, ID `12470223376`, ZIP de 20.663.357 bytes, `expired=false`, digest SHA-256 `b8b75e8e7977a67e34de29e7759872db1c7a022b9560c1253133e2dd27435b6d`.
- La mejora no busca dentro del contenido real de PDF/documentos ni reemplaza un motor de indexación por contenido; solo busca metadatos catalogados.
- TIMER estimado para este lote: 30–60 minutos de agente; no es una medición de rendimiento en el dispositivo.

---

## Continuidad · evitar cadenas engañosas en similitud visual · 2026-10-21

- Rama autorizada: `feature/archivero-digital-library-001`; cabeza base `69993f34cdd979344db3e39aefa94681432aa200`. No modificar `main`, no crear ramas duplicadas ni fusionar PR #3.
- Hallazgo: `groupSimilarImageHashes` usaba componentes conectados (single-link); A podía agruparse con C solo porque ambas eran parecidas a B, aunque A y C estuvieran más lejos del umbral.
- Corrección: partición greedy deterministic complete-link: todos los pares de un grupo cumplen `maxDistance`. Ordena por hash y URI para que el resultado no dependa del orden de entrada. El algoritmo sigue siendo O(n²), como la implementación anterior; no se declara una mejora de velocidad.
- Pruebas: extremos no encadenados (0, 15, 255 con umbral 4), independencia frente al orden de entrada y validación del rango -1/65.
- Se evita capturar `CancellationException` dentro del hash de imagen y se garantiza `recycle()` de bitmaps ante fallo de escalado o recorrido de píxeles.
- Investigación de repos públicos: [Hydrus Network](https://github.com/hydrusnetwork/hydrus) (etiquetas/privacidad), [digiKam](https://github.com/KDE/digikam) (búsqueda con criterios combinables y metadatos), [Tropy](https://github.com/tropy/tropy) (descripción de fotos y recuperación de fuentes). Se toman patrones de producto, sin incorporar librerías o código externo.
- **CI PASS:** [run 39656468147](https://github.com/jonhararagi/archivero/actions/runs/39656468147), SHA `e619ba846b5f2b75ef2f4273868589125745e6df`. `testDebugUnitTest`, `assembleDebug`, firma/hash y subida completados correctamente. Artefacto `archivero-debug-apk`, ID `12372620604`, ZIP 20.662.905 bytes, no expirado; digest SHA-256 del ZIP `4fd7b2a0c061102c3bfe84ce21c4ffe46d027f37db755863bcc19f88f045f397`.
- TIMER estimado: 30–60 minutos de agente, no tiempo medido en dispositivo. Se conserva debajo todo el historial de continuidad.

---

## Continuidad · búsqueda perceptual dentro de Biblioteca · 2026-10-21

- **Último commit funcional verificado:** `81516cbed5ea1a473162f687c139db714841ddd2`.
- **CI PASS:** [run 39647800495](https://github.com/jonhararagi/archivero/actions/runs/39647800495), mismo SHA. Pruebas unitarias, APK de depuración, firma/hash y subida del artefacto terminaron correctamente.
- **Artefacto:** `archivero-debug-apk`, ID `12368157254`, ZIP 20.662.550 bytes, no expirado al consultar Actions.
- Se añade `LibrarySimilarImageScanner.kt`: analiza solo las fichas actualmente visibles tras aplicar filtros, utilizando `findSimilarImages` y la huella perceptual ya existente. No se duplicó el algoritmo ni se añadió ninguna dependencia.
- La interfaz se puede cancelar, ejecuta el trabajo de lectura en IO y muestra hasta 100 grupos y 20 nombres por grupo. Advierte que la semejanza es una pista de revisión, no confirmación de duplicado; ningún archivo ni ficha se elimina o modifica.
- `ImageSimilarity.kt` incluye un resumen de estado compartido y `ImageSimilarityTest.kt` cubre estados vacíos, resultados parecidos y aviso de límite/recursos ilegibles.
- `README.md` y `CHANGELOG.md` explican la nueva función y los límites. La continuidad histórica extensa se conserva debajo de esta entrada.
- PR #3 continúa abierto, en borrador y sin fusionar. No se escribió en `main`.
- TIMER estimado del lote: 30–60 minutos de agente, sin medición de rendimiento en dispositivo.

---

## Continuidad · asignación de proyecto/colección por lotes · 2026-10-21

- **Última cabeza funcional verificada:** SHA `215043d5a72295463e165a7064c65775c80f62d7`, [run 39642066619](https://github.com/jonhararagi/archivero/actions/runs/39642066619) **PASS**, artefacto `archivero-debug-apk` ID `12365273553`, ZIP 20.647.933 bytes.
- Base documental anterior: SHA `ec8fdb47f2f2dc9644a1a31e5dbfcc127b76dfa5`, [run 39640755743](https://github.com/jonhararagi/archivero/actions/runs/39640755743) **PASS**, artefacto ID `12364547619`, ZIP 20.631.102 bytes.
- Nuevo método de catálogo en `DigitalLibraryCatalog.kt`: `assignProjectToRecords`. Valida selección URI, existencia de todas las fichas y nombre destino antes de una sola escritura; preserva los otros campos.
- Nuevo editor Compose `LibraryBulkProjectEditor.kt`: limita la selección a resultados visibles (máximo 500), permite «Todas/Ninguna» y presenta vista previa del proyecto actual a destino antes de aplicar.
- Tests añadidos para guardado/reapertura, preservación de tags/notas/fuente/tipo/relaciones, idempotencia (cero cambios) y rechazo atómico de URI ausentes y nombres inválidos.
- CI [run 39642066619](https://github.com/jonhararagi/archivero/actions/runs/39642066619), SHA `215043d5a72295463e165a7064c65775c80f62d7`: **PASS**. Tests, compilación de APK, firma/hash y subida de artefacto terminaron correctamente.
- No hubo modificaciones directas a `main` ni merge del PR #3; el PR sigue en borrador.
- La continuidad histórica extensa se conserva debajo de esta entrada.

---

## Continuidad · auditoría manual de acceso a recursos · 2026-10-21

- Última cabeza funcional verificada: SHA `c21b4512f3058da56753f435d90e947329d548c1`, [run 39640308153](https://github.com/jonhararagi/archivero/actions/runs/39640308153) **PASS** (tests, APK debug, firma/hash y subida).
- Base de la iteración anterior: SHA `3e59e626a66a76067b419ce22e4ecfd5ee0f5324`, [run 39638828860](https://github.com/jonhararagi/archivero/actions/runs/39638828860) **PASS** (incluye la prueba de migración V2→V3).
- Artefacto de la última cabeza verificada: `archivero-debug-apk`, ID `12363234364`, ZIP de 20.631.102 bytes, no expirado en la comprobación.
- Artefacto de la base anterior: `archivero-debug-apk`, ID `12363882327`, ZIP de 20.614.651 bytes.
- Nuevo componente `LibraryResourceAccessAudit.kt`: acción manual, cancelable y fuera del hilo principal que recorre la biblioteca activa y consulta `DocumentFile.exists()`/`canRead()`; no abre ni copia contenido.
- Estados: accesible, no visible para el proveedor, y sin acceso/lectura. «No visible» es deliberadamente cauteloso: un proveedor puede ocultar una URI tras cambios de permisos, así que no se etiqueta como archivo borrado.
- Muestras separadas acotadas a 100 por defecto, contadores completos, progreso cada 50 fichas y cancelación cooperativa. El acumulador puro tiene pruebas para los tres estados, biblioteca vacía y límite de muestra inválido.
- Integración de UI: el nuevo botón comparte la fila con la auditoría de relaciones, usa el mismo resolver `libraryDocument` y no modifica ningún archivo ni ficha.
- Incidencia real: [run 39639788751](https://github.com/jonhararagi/archivero/actions/runs/39639788751), SHA `d78f61d7ba14af9fdcc2d2c648f7de20012504f5`, **FAIL** en `compileDebugKotlin`: `LibraryResourceAccessAudit.kt` tenía una referencia a `FolderSearch` no incluida en los iconos Compose. No se generó APK en esa ejecución. Se cambia a `Icons.Default.Search` y se vuelve a lanzar CI.
- La primera CI del lote falló por el icono `FolderSearch` no disponible: [run 39639788751](https://github.com/jonhararagi/archivero/actions/runs/39639788751), SHA `d78f61d7ba14af9fdcc2d2c648f7de20012504f5`, **FAIL** en `compileDebugKotlin`; no se publicó APK.
- Corrección comprobada: [run 39640308153](https://github.com/jonhararagi/archivero/actions/runs/39640308153), SHA `c21b4512f3058da56753f435d90e947329d548c1`, **PASS** en pruebas unitarias, compilación APK, firma/hash y subida. Artefacto `archivero-debug-apk` ID `12363234364`, ZIP de 20.631.102 bytes.

---

## Continuidad · papelera recuperable V3 y validación integrada · 2026-10-21

- **Cabeza funcional validada:** `a7ed2a03839d0effc596c3cb0719aa0fd879f733`.
- **CI PASS:** [run 39638194130](https://github.com/jonhararagi/archivero/actions/runs/39638194130). Terminaron correctamente tests unitarios, `assembleDebug`, verificación de firma/hash y subida de artefacto.
- **Artefacto:** `archivero-debug-apk`, ID `12365030210`, ZIP de 20.614.259 bytes, no expirado en la última comprobación.
- Se añade el esquema V3 a `DigitalLibraryCatalog.kt`, compatible con esquemas V1 y V2. Activos y papelera se conservan en la misma instantánea.
- La Biblioteca expone «Papelera (N)», «Mover a papelera», «Restaurar» y purga confirmada. Purga solo metadatos, nunca el archivo físico.
- La importación/exportación lleva también las fichas de papelera. Las colisiones de URI se rechazan por defecto; una reintroducción manual de la misma URI la hace activa y elimina su entrada anterior de papelera.
- Tests nuevos: recuperar tras reabrir, roundtrip de instantáneas con fichas retiradas, reingreso de URI, purga sin borrar el recurso y compatibilidad/migración V2→V3.
- **Corrección de documentación:** se restauró el registro extenso previo desde la última cabeza documental validada en vez de reemplazarlo por una nota breve. Este historial se conserva debajo de la entrada actual.
- PR #3 continúa abierto en borrador y apunta a `main`; no hubo merge ni cambios directos a `main`.
- Pendiente por evidencia externa: prueba física en Xiaomi, varios proveedores SAF, permisos revocados, rendimiento real con 1.000/10.000 imágenes y biblioteca cercana a 200 GB.

---

## Continuidad · auditoría de relaciones y URI sin ficha · 2026-10-21

- Nuevo componente `LibraryRelationshipAudit.kt` con la función pura `auditLibraryRelationships` y un reporte Compose invocado desde Biblioteca.
- Diferencia dos situaciones: relaciones dirigidas a URI que no están en el catálogo (posibles referencias sin ficha) y fichas que no reciben enlaces entrantes (informativo; pueden ser elementos independientes).
- El reporte trabaja con metadatos del catálogo solamente. No verifica existencia física ni permiso SAF de cada destino, no repara URI y no modifica fichas o archivos.
- Complejidad de análisis lineal en el número de fichas más relaciones; muestra acotada a 100 filas por apartado (total exacto calculado), ejecución en `Dispatchers.Default` y comprobación de cancelación por lotes.
- Tests añadidos para destinos ausentes, destino enlazado, registros independientes, URI duplicadas y límite de muestra. CI [run 39635472592](https://github.com/jonhararagi/archivero/actions/runs/39635472592), SHA `210e43570f39e38b1843844ec02b1ca9f43f9d9e`: **PASS** en tests, build de APK debug, verificación de firma/hash y subida del artefacto ID `12360483934` (20.590.366 bytes).
- Última base funcional validada antes del refresco documental: SHA `210e43570f39e38b1843844ec02b1ca9f43f9d9e`, [run 39635472592](https://github.com/jonhararagi/archivero/actions/runs/39635472592) **PASS**, artefacto `archivero-debug-apk` ID `12360483934`, ZIP de 20.590.366 bytes.
- Historial de auditoría conservado: [run 39635345291](https://github.com/jonhararagi/archivero/actions/runs/39635345291) falló en una aserción de orden alfabético en `LibraryRelationshipAuditTest`; el test se corrigió en SHA `210e43570f39e38b1843844ec02b1ca9f43f9d9e` y la nueva ejecución quedó **PASS**.
- Lotes previos con validación completa: escáner de duplicados [run 39632563122](https://github.com/jonhararagi/archivero/actions/runs/39632563122); etiquetas por lotes [run 39633408582](https://github.com/jonhararagi/archivero/actions/runs/39633408582); filtro de proyecto/colección [run 39634053321](https://github.com/jonhararagi/archivero/actions/runs/39634053321); búsqueda acotada [run 39634548741](https://github.com/jonhararagi/archivero/actions/runs/39634548741).
- PR #3 sigue abierto en borrador y `main` no ha sido modificado directamente.

---

## Continuidad · cola acotada para la búsqueda · 2026-10-21

- Se reemplazó `filtrar todas → ordenar todas → tomar N` por una cola de prioridad acotada al límite de resultados. La complejidad de ordenamiento pasa de ordenar todas las coincidencias a mantener los mejores N con coste logarítmico por coincidencia; la lista final de N resultados sí se ordena normalmente.
- Se preservan el orden de favoritos, las ordenaciones por Nombre/Recientes/Proyecto, todos los filtros, la ordenación estable por URI y el límite 10.000.
- Se añadió test con 5.000 registros generados que compara las páginas superiores de las tres ordenaciones con una ordenación completa de referencia, más el caso de filtro de proyecto.
- Esta prueba verifica equivalencia del resultado, no mide tiempo ni RAM en teléfono real. Benchmarks y jank en Xiaomi siguen pendientes.
- CI del SHA nuevo debe confirmar tests, compilación, firma/hash y subida de APK antes de marcar PASS.

### Última base validada
- SHA `a4cded20629d8800dec4d556841fde9df1dc8253`: **PASS** en [run 39634053321](https://github.com/jonhararagi/archivero/actions/runs/39634053321), con artefacto `archivero-debug-apk` ID `12360063059`, ZIP de 20.572.760 bytes.
- El PR #3 sigue en borrador, no fusionado. `main` no ha sido modificado directamente.

---

## Continuidad · filtro de proyecto/colección · 2026-10-21

- La biblioteca reutiliza `LibraryRecord.project` como colección/proyecto y ahora ofrece un selector en la fila de filtros.
- Las opciones disponibles se derivan de los valores ya guardados, eliminando duplicados por mayúsculas/minúsculas y ordenando sin un índice adicional. Las fichas con proyecto vacío no se inventan ni se asignan automáticamente.
- `filterLibraryRecords` acepta el filtro opcional `project`; compara el nombre sin distinguir mayúsculas/minúsculas y lo combina con texto, categoría, tipo de recurso y Solo favoritos.
- Pruebas unitarias cubren el filtro de proyecto independiente y una intersección con consulta, categoría y tipo. No requiere cambios del esquema V2.
- La CI del nuevo SHA debe pasar tests, compilación Android, verificación de firma/hash y publicación de artefacto antes de declarar PASS.

### Última base validada
- SHA `5880a532f0f7c87709c7f7828643c89ced478bc0`: **PASS** en [run 39633408582](https://github.com/jonhararagi/archivero/actions/runs/39633408582); tests, APK debug, firma/hash y subida.
- Artefacto `archivero-debug-apk`, ID `12360152672`, ZIP de 20.566.079 bytes.
- PR #3 sigue abierto en borrador, sin merge y sin cambios directos en `main`.

---

## Continuidad · etiquetado por lotes con vista previa · 2026-10-21

### Incremento en esta iteración
- El catálogo incorpora `addTagsToRecords`: valida todas las URI, verifica que cada ficha exista y prepara los cambios antes de reemplazar el archivo de metadatos. La escritura se realiza una sola vez, no una vez por cada ficha.
- El lote añade etiquetas sin borrar las anteriores. Los duplicados se comparan sin distinguir mayúsculas/minúsculas. Se devuelven cuántas fichas cambiaron; si todas ya tenían las etiquetas, no se escribe nada.
- La interfaz selecciona las fichas visibles del filtro actual (máximo 500), muestra una vista previa de hasta tres fichas y permite seleccionar todas o ninguna antes de aplicar.
- Pruebas unitarias verifican merge/deduplicación, persistencia, lote con URI inexistente y fallo por superar 100 etiquetas. Los dos últimos casos confirman que una validación fallida no persiste cambios parciales.
- La ejecución de CI para la cabeza actual debe confirmar que el nuevo editor Compose compila y que las pruebas pasan. No marcar el lote como PASS hasta ver CI completa.

### Incidentes de CI y corrección del escáner
- [Run 39631967956](https://github.com/jonhararagi/archivero/actions/runs/39631967956), SHA `e93995a91db85151ba00c6f898693fec672ba545`: **FAIL**. El primer commit de escáner contenía también un error sintáctico en un test; quedó corregido en el siguiente commit.
- [Run 39632036037](https://github.com/jonhararagi/archivero/actions/runs/39632036037), SHA `8074cadfb5e5d8ad8ad1db7904df55658afc0114): **FAIL** en `compileDebugKotlin`, selector de `potentialDuplicateCandidates` intentaba acceder a `value` de `LibraryRecord`.
- [Run 39632182856](https://github.com/jonhararagi/archivero/actions/runs/39632182856), SHA `cad339f76275c44ec42bfb986fabb86aab8d5613`: **FAIL** por el mismo selector incorrecto.
- Corrección comprobada: [run 39632563122](https://github.com/jonhararagi/archivero/actions/runs/39632563122), SHA `1844be99791f77372f63e4ef37f0199610727343): **PASS** en tests, compilación Android, verificación de firma/hash y subida del artefacto. ID del artefacto `12360830085`, ZIP de 20.539.989 bytes.

### Integración y límites
- PR #3 permanece abierto en borrador: https://github.com/jonhararagi/archivero/pull/3. No se modificó `main` ni se fusionó el PR.
- No se probó aún en un Xiaomi ni con proveedores SAF externos. Los benchmarks de 1.000/10.000 imágenes y biblioteca de unos 200 GB siguen pendientes.

---

## Incidencia de compilación · escáner de duplicados · 2026-10-21

- CI [run 39632036037](https://github.com/jonhararagi/archivero/actions/runs/39632036037), SHA `8074cadfb5e5d8ad8ad1db7904df55658afc0114`: **FAIL** en `Run local unit tests`, durante `compileDebugKotlin`; no se produjo APK en esa ejecución.
- Log observado: `LibraryDuplicateScanner.kt:102`, tipo del selector no inferible y referencia a `value` no resuelta. La función `potentialDuplicateCandidates` recibe el propio `LibraryRecord` en el selector, así que la corrección usa `it.uri`.
- La errata sintáctica del test del primer commit también fue detectada y corregida en el commit siguiente; el historial se conserva y no se presenta ninguno de esos commits como entrega validada.
- SHA que precede a esta corrección: `cad339f76275c44ec42bfb986fabb86aab8d5613`. La nueva cabeza y su CI deben verificarse antes de declarar PASS.

---

## Continuidad · escáner de duplicados exactos para Biblioteca · 2026-10-21

### Implementación preparada
- Se añade `LibraryDuplicateScanner.kt`, una acción manual y cancelable dentro de la pantalla Biblioteca.
- Solo se calculan hashes SHA-256 para entradas con tamaño positivo conocido y que comparten tamaño con otra URI del catálogo. La igualdad de tamaño no basta para producir un resultado.
- La lectura de contenido corre en `Dispatchers.IO`, comprueba cancelación y omite URI inaccesibles o recursos que cambien el tamaño durante la lectura.
- Los hashes no se persisten: los resultados no quedan obsoletos por un índice de huellas guardadas. La detección es de solo lectura y no ejecuta borrados.
- Pruebas unitarias cubren SHA-256 válido, URI repetidas, archivos con tamaños diferentes, huellas inválidas y orden de resultados.
- No está validado aún con carpetas SAF de distintos fabricantes, archivos modificados durante la lectura ni bibliotecas de 1.000/10.000 elementos. La CI del nuevo commit debe confirmar las pruebas y la compilación antes de considerar el incremento **PASS**.

### Última base verificada antes de esta iteración
- Cabeza de rama: `eb5e365f366f7bbbdec272b21a7096a191556e8d`.
- CI **PASS** en [run 39628363277](https://github.com/jonhararagi/archivero/actions/runs/39628363277): tests, `assembleDebug`, comprobación de firma/hash y subida de artefacto.
- Artefacto `archivero-debug-apk`, ID `12358925054`, ZIP de 20.517.818 bytes.
- PR #3 sigue en borrador; no se modificó `main` ni se integró el PR.

---

## Continuidad · filtro Solo favoritos · 2026-10-21

### Rama e integración
- Rama: `feature/archivero-digital-library-001`.
- PR #3: https://github.com/jonhararagi/archivero/pull/3. Continúa abierto en borrador, no fusionado; no se modificó `main` directamente.
- SHA de código validado: `3f8c725f08cb0d68309bcedd2138eeb36c7fd892`.
- GitHub Actions [run 39627829320](https://github.com/jonhararagi/archivero/actions/runs/39627829320): **PASS** para pruebas unitarias, compilación del APK, verificación de firma/hash y publicación del artefacto.
- Artefacto `archivero-debug-apk`, ID `12357562577`, ZIP de 20.517.818 bytes.

### Incremento nuevo
- El chip **Solo favoritos** agrega un filtro booleano combinable con la búsqueda, categoría y tipo de recurso.
- El filtro devuelve únicamente URI presentes en las preferencias locales de favoritos; desactivarlo restaura los resultados que satisfacen el resto de filtros.
- Pruebas de catálogo cubren el orden de favoritos, la selección solo de favoritos y el caso vacío cuando no hay favoritos.
- El catálogo visual ya acumula fichas V2, relaciones, cuadrícula/lista, metadatos técnicos, ordenación, últimas ocho búsquedas, favoritos y exportación/importación de metadatos limitadas a 64 MiB.

### Historial CI
- SHA `bfb02d5e...`: fallo de compilación por compatibilidad del buscador.
- SHA `ade41baa...`: compilación OK pero fallo de una expectativa de orden alfabético de la prueba.
- SHA `e0d5ea7b...`: PASS con filtros combinados/migración V1→V2 y artefacto.
- SHA `d89f6db6...`: PASS con favoritos de biblioteca y orden de prioridad.
- SHA `3f8c725f...`: PASS añadiendo `onlyFavorites`.
- Los fallos intermedios permanecen documentados; no se presentan como entregas válidas.

### Límites
- Sin prueba manual en el Xiaomi ni en proveedores SAF externos.
- Pendientes benchmarks reproducibles con 1.000/10.000 imágenes y biblioteca real de aproximadamente 200 GB; CI no demuestra jank, uso de memoria ni escalabilidad física.
- Quitar una ficha elimina solo metadatos; exportar la biblioteca no copia los bytes de recursos.
- **Estado:** código y pruebas automatizadas **PASS**; pruebas físicas/escalabilidad **PARTIAL**.
- **TIMER** del lote “Solo favoritos”: estimado 1–2 horas, no duración real medida.


---

## Continuidad · límites seguros de paginación · 2026-10-20

### Cambios sobre main
- Se añadió `DirectoryListingTest.kt` para comprobar lotes consecutivos del sistema de archivos local, ausencia de URI repetidas, detección del último lote, offset fuera del final y validación de parámetros.
- La primera ejecución de CI del test falló por una importación ausente de `java.io.File` en el test. Se corrigió en el commit `448d51743e5045f6fff436c3d010f63fdffc8f04`; la CI de ese SHA debe confirmarse por separado.
- `listDocumentChildrenBounded` ya no llama a `DocumentFile.listFiles()` en proveedores fuera del sistema de archivos y árboles SAF. Esa API materializa los hijos antes de limitar la página; en directorios grandes podría elevar la memoria sin control. Ahora devuelve un error explícito y la UI recomienda volver a elegir la carpeta por SAF.
- La pantalla muestra el mensaje real del proveedor para distinguir un árbol no compatible de un permiso revocado o un error genérico.
- La instalación nueva usa Ciberpunk por defecto, con fondo azul noche y acentos cian/magenta. Una preferencia de paleta guardada explícitamente se conserva.
- Commit que contiene el cambio de código y documentación: `2e4a1de69a39a4f5539a1f8f9a38de8d6d408506`; verificar pruebas y compilación desde la ejecución asociada a la cabeza definitiva de `main`.
- Los árboles SAF siguen usando un cursor con procesamiento acotado y el sistema de archivos local sigue usando `DirectoryStream`. El proveedor puede no ofrecer orden estable o soporte para saltos nativos; la implementación actual recorre los elementos previos para llegar al offset, sin mantenerlos en una lista en memoria.
- No se ha validado este cambio en proveedores SAF de terceros ni en el Xiaomi físico. No declarar verificado el inventario de aproximadamente 200 GB.
- TIMER de esta subiteración: la estimación original de 120–180 minutos es una previsión, no un tiempo real medido en el conector. Mantener el estado abierto hasta CI y pruebas físicas.

**Estado provisional:** mitigación de memoria de respaldo + pruebas añadidas; pendiente CI para el SHA consolidado y pruebas de proveedor/dispositivo (**PARTIAL**).

---

## Continuidad · verificación final de Archivero 0.4.2 · 2026-10-20

- La ejecución de CI para el SHA `f2e1ff41e05ce5a04860b72d7a537b3c5b45504c` quedó **PASS** en [run 39487033724](https://github.com/jonhararagi/archivero/actions/runs/39487033724).
- Pasaron `testDebugUnitTest`, `assembleDebug`, verificación de certificado y subida del artefacto.
- Artefacto: ID `12293698093`, `archivero-debug-apk`, ZIP 20.398.195 bytes, SHA-256 `b8ccb98f4db6c67234547483e290129be58c9bfa56743aa24af966ae81e4165a`.
- APK: 21.265.707 bytes, SHA-256 `6b47334b448ff1ee80fb47740cc5d1fc630b8eefb0c725e4fc19d6adc41bfbff`. SHA coincide con `.sha256` del artefacto y la prueba del ZIP pasó.
- Certificado del APK y keystore de CI: `ca95ba2d2407d9eda63c0568c3ad080928db773997c467ad73013a69493875ba`.
- Versión incluida en ese SHA: `0.4.2`, `versionCode 15`.
- La siguiente comprobación antes de entregar una nueva APK debe consultar la cabeza de `main` y descargar solo el artefacto de su ejecución exacta.

### Pendiente de validación física
1. Medir desplazamiento, uso de RAM y tiempos de escaneo en el Xiaomi real.
2. Probar carpetas SAF con miles de elementos, permisos revocados y operaciones canceladas.
3. Ejecutar un benchmark repetible con 1.000 y 10.000 imágenes, incluidos duplicados exactos y similares.
4. Probar el almacenamiento real de aproximadamente 200 GB. No declarar esa escala validada por CI.
5. Comparar las funciones prioritarias con Files by Google, Solid Explorer, X-plore, Total Commander y CX File Explorer usando el mismo conjunto de tareas.

**Estado:** automatización de versión 0.4.2 **PASS**; pruebas físicas y comparación de rendimiento **PARTIAL**.

---

## Continuidad · Archivero Android 0.4.2 · 2026-10-20

### Incremento de rendimiento
- Versión de entrega prevista: `versionName 0.4.2`, `versionCode 15`.
- Commit funcional anterior: `421db94b9d4446a3cea65dae32581dbe287827df`.
- En la carpeta actual, la resolución de `DocumentFile.isDirectory` se calcula en `Dispatchers.IO`, no durante la composición de Compose.
- Cuando `hasScanned` es verdadero, se reutiliza `indexed` directamente, ya que el recorrido recursivo solo añade a esa lista elementos que no son directorios. Así se evita copiar hasta 100.000 referencias y consultar sus metadatos otra vez.
- Se conserva la tolerancia a errores de proveedor: si no se puede determinar el tipo de un elemento del directorio actual, se mantiene visible como candidato en vez de ocultarlo silenciosamente.
- Esta iteración conserva las mejoras del limpiador de duplicados: metadatos/tamaños se obtienen en segundo plano, agrupación por tamaño positivo + URI sin duplicar, hash del contenido completo solo para candidatos del mismo tamaño, y escaneo de similares cancelable con límite de 2.500 imágenes.
- Pruebas existentes añadidas en `DuplicateDetectionTest.kt` comprueban tamaños compartidos, URI repetidas, tamaño no positivo, archivo único y entrada vacía.

### Criterio de entrega
- Ejecutar la CI para la cabeza final de `main` que contenga versión 0.4.2.
- Confirmar `testDebugUnitTest`, `assembleDebug`, certificado coincidente, subida del artefacto y SHA-256 del APK.
- Descargar y verificar el artefacto exacto de esa ejecución antes de entregar un enlace.
- No declarar medido el rendimiento en el Xiaomi ni el funcionamiento con 200 GB: faltan pruebas físicas representativas.
- No reclamar superioridad general sobre Files by Google, Solid Explorer, X-plore, Total Commander y CX File Explorer sin benchmark comparable.

**Estado al escribir esta entrada:** código de iteración 0.4.2 preparado; validación de CI del SHA final pendiente.

---

## Continuidad · versión de entrega Android 0.4.1 · 2026-10-20

- Versión Android incrementada en `app/build.gradle.kts`: `versionName 0.4.1`, `versionCode 14`.
- Propósito: que la APK de esta iteración se identifique como actualización respecto a la 0.4.0/versionCode 13.
- Código de limpieza incluido: escaneo de metadatos de duplicados y fotos similares en segundo plano, con cancelación y límite acotado en similitud; véanse el changelog y los commits funcionales registrados a continuación.
- Entregar únicamente el artefacto de la ejecución de CI asociada al SHA final de esta versión. Confirmar tests, `assembleDebug`, certificado, ZIP íntegro y SHA-256 antes de publicar.

---

## Continuidad · limpiador de archivos con escaneo fuera del hilo visual · 2026-10-20

### Cambio de código
- Cabeza de código: `abe285f4c467dd615d354583cf671d91a2818985`.
- La recopilación de metadatos para duplicados exactos ya no sucede antes de `scope.launch`: consulta `isDirectory` y `length()`, agrupa por tamaño y deduplica por URI en `Dispatchers.IO`.
- Se añaden comprobaciones de cancelación durante el recorrido. Si una ubicación cambia, los permisos fallan o la enumeración se interrumpe, la operación informa del fallo y no ejecuta ningún borrado.
- El escaneo de fotos similares ahora recibe la lista original y descubre candidatos en segundo plano, comprobando cancelación entre archivos y deteniéndose cuando identifica más de 2.500 imágenes compatibles. Los elementos visualmente parecidos siguen siendo sugerencias, no copias idénticas.
- Pruebas añadidas en `DuplicateDetectionTest.kt`: tamaños compartidos, URI repetidas, archivo vacío, tamaño no positivo, entrada única y entrada vacía.

### Validación automatizada observada
- GitHub Actions [run 39483773953](https://github.com/jonhararagi/archivero/actions/runs/39483773953), SHA `abe285f4c467dd615d354583cf671d91a2818985`.
- `testDebugUnitTest`: PASS; `assembleDebug`: PASS; verificación de certificado: PASS; paso de subida del artefacto: PASS.
- El job todavía estaba ejecutando sus acciones de limpieza al escribir esta entrada. Confirmar la conclusión final y el artefacto de la ejecución antes de entregar la APK.

### Pendiente, no marcar PASS
1. Benchmark de duplicados y fotos similares con conjuntos etiquetados y tamaños de 1.000/10.000 imágenes.
2. Medición de RAM/tiempo y desplazamiento en el Xiaomi real.
3. Pruebas de permisos SAF revocados, elementos inaccesibles y cancelación a mitad del escaneo.
4. Biblioteca física de aproximadamente 200 GB sin validar aún.

**Estado:** corrección de código y pasos principales de CI **PASS**; rendimiento a escala y dispositivo real **PARTIAL**.

---

## Continuidad · verificación de artefacto tras actualizar documentación · 2026-10-20

- SHA verificado de `main` en esa comprobación: `d0842575783646f21aebf3c9a7fb882901e1a7f7`.
- CI del SHA anterior: [run 39430717291](https://github.com/jonhararagi/archivero/actions/runs/39430717291), **PASS** para pruebas unitarias, `assembleDebug`, comprobación de firma y publicación del artefacto.
- La comparación con el commit funcional `802e57952463a422567234c037bb3c8f906a14ce` confirma que el commit `d084257` solo modifica `CHANGELOG.md` y este documento, sin cambios de código Android.
- Artefacto exacto de esa ejecución: ID `12267088842`, tamaño del ZIP 20.395.922 bytes, ZIP SHA-256 `7690580aa5c57eceac1ddc1b7d3f8196493d0de7413db8791620ca00db2209f3`.
- APK debug contenida: 21.265.703 bytes, SHA-256 `2ab547b1480f05b1c87c8c2b097ee644d989b0565e202b1f93e3bc3429c35511`. La suma coincidió con el archivo `.sha256` del artefacto y el ZIP pasó la prueba de integridad.
- Certificado de APK y keystore CI coincidentes: `ca95ba2d2407d9eda63c0568c3ad080928db773997c467ad73013a69493875ba`.
- Esta entrada registra una comprobación histórica específica. Antes de una próxima entrega se debe volver a verificar la cabeza de `main` y su run, porque cualquier commit posterior requiere consultar su propia ejecución.

---

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
5. Regla permanente para cada entrega: comprobar el SHA de `main` y utilizar únicamente el artefacto de la ejecución de CI asociada a ese mismo SHA; no reutilizar una APK anterior.

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

