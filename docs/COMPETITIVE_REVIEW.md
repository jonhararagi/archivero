# Revisión funcional comparativa · Archivero Android

**Fecha de consulta:** 2026-10-20.  
**Estado:** PARTIAL. Revisión documental de funciones públicas y del código de Archivero; todavía no es un benchmark físico en el Xiaomi.  
**Regla:** estas cinco aplicaciones son referencias de producto, no un ranking probado de calidad general. Las descripciones de funciones se atribuyen a sus páginas públicas y no implican que Archivero haya igualado su alcance.

## 1. Referentes y aprendizajes comprobables

### Files by Google
- **Fuente y actualización visible:** [Google Play](https://play.google.com/store/apps/details?id=com.google.android.apps.nbu.files), ficha consultada el 2026-10-20; la página indica actualización el 2026-09-30.
- **Funciones documentadas:** recomendaciones de limpieza; detección de archivos duplicados y fotos antiguas; análisis de espacio libre en dispositivo, SD y USB; búsqueda, navegación por tipo y ordenación por tamaño; reproducción multimedia offline y copia a Drive/SD.
- **Qué aprender:** presentar una recomendación de limpieza junto a una vista previa que permita comprobar el contenido; mostrar por qué se recomienda cada elemento y cuánto espacio se podría recuperar.
- **Estado Archivero:** PARTIAL. Ya presenta capturas antiguas, duplicados exactos, fotos similares y vídeos grandes como candidatos revisables. No se ha demostrado que recupere espacio con mayor precisión ni que cubra los mismos tipos de almacenamiento.
- **Mejora/propuesta verificable:** en pruebas guiadas, medir los pasos para revisar un candidato, abrirlo, volver a los resultados y confirmar una selección. No borrar candidatos sin confirmación.

### Solid Explorer
- **Fuente y actualización visible:** [Google Play](https://play.google.com/store/apps/details?id=pl.solidexplorer2), ficha consultada el 2026-10-20; actualización visible 2026-10-12. [Página del desarrollador](https://neatbytes.com/solidexplorer/).
- **Funciones documentadas:** panel doble y transferencia entre paneles; búsqueda indexada; renombrado por lotes; categorías de archivos; almacenamiento en nube y red; archivos cifrados; herramientas de limpieza y análisis de espacio.
- **Qué aprender:** reducir pasos en operaciones frecuentes, mantener visible el contexto de origen/destino y ofrecer filtros de búsqueda sin recargar la galería.
- **Estado Archivero:** PARTIAL. Tiene navegación por carpetas, categorías, búsqueda por nombre y operaciones locales, pero no ofrece el mismo alcance de nube/red ni una interfaz completa de dos paneles.
- **Mejora/propuesta verificable:** mejorar selección múltiple y acciones contextuales locales primero; medir pasos de copiar/mover/renombrar antes de plantear protocolos remotos. Las funciones de nube no se consideran implementadas.

### X-plore File Manager
- **Fuente y actualización visible:** [Google Play](https://play.google.com/store/apps/details?id=com.lonelycatgames.Xplore), ficha consultada el 2026-10-20; actualización visible 2026-10-06. [Manual/página del desarrollador](https://www.lonelycatgames.com/apps/xplore).
- **Funciones documentadas:** vista en dos paneles con árbol de carpetas; mapa de disco por ocupación; miniaturas y visor de imágenes con zoom y navegación anterior/siguiente; búsqueda, selección múltiple, papelera, archivos ZIP/RAR y conexiones de red/nube.
- **Qué aprender:** el visor debe ser ligero y preservar el contexto; el análisis de espacio debe ayudar a localizar ocupación, no forzar un escaneo profundo al arrancar.
- **Estado Archivero:** PARTIAL. Ya tiene visor de imágenes con gestos, miniaturas con caché acotada, exploración por lotes y análisis manual con límites visibles. No tiene el mapa de disco ni la gama de protocolos de X-plore; una papelera general no está garantizada para todas las URI/proveedores.
- **Mejora implementada en esta iteración:** preparar, deduplicar y ordenar la lista de imágenes durante el filtrado en segundo plano para evitar volver a ordenar toda la galería en el hilo de interfaz al abrir una foto. Véase el commit [90a9b771](https://github.com/jonhararagi/archivero/commit/90a9b771b974df61f5a111d95dba93f8edd2af75).
- **Prueba de aceptación pendiente:** medir respuesta al abrir una imagen en carpetas con 1.000 y 10.000 fotos y confirmar que la lista conserva el orden y el contexto al volver.

### Total Commander (Android)
- **Fuentes y versión/fecha visible:** [Página oficial Android](https://www.ghisler.com/android.htm), que indica Total Commander para Android 3.62 (15-02-2026); [Google Play](https://play.google.com/store/apps/details?id=com.ghisler.android.TotalCommander), ficha consultada el 2026-10-20 con actualización visible 2026-02-13.
- **Funciones documentadas:** paneles dobles, historial y favoritos de carpetas, búsqueda de archivos y texto, selección por grupos, copiar/mover directorios, ZIP/UNZIP, miniaturas, reproductor y extensiones de red.
- **Qué aprender:** operaciones por lotes predecibles, navegación atrás/historial estable y atajos claros; debe haber una respuesta de error por elemento si una operación parcial falla.
- **Estado Archivero:** PARTIAL. Cuenta con navegación Atrás, selección de varios elementos en la limpieza, operaciones locales y apertura de archivos; no se declara paridad con paneles dobles, plugins o búsqueda por contenido.
- **Mejora/propuesta verificable:** probar que una operación por lotes informa qué elementos se completaron y cuáles fallaron, sin ocultar resultados ni borrar contenido automáticamente.

### CX File Explorer
- **Fuente y actualización visible:** [Google Play](https://play.google.com/store/apps/details?id=com.cxinventor.file.explorer), ficha consultada el 2026-10-20; actualización visible 2026-10-03.
- **Funciones documentadas:** análisis visual del almacenamiento, exploración por categorías, limpieza de archivos basura y duplicados, papelera y acceso a recursos de nube/red.
- **Qué aprender:** simplificar Inicio alrededor de tareas principales y explicar el espacio ocupado con visualizaciones que no bloqueen la navegación.
- **Estado Archivero:** PARTIAL. Tiene secciones de Imágenes, Limpiar, Copias exactas, Fotos parecidas y Capturas, además de un indicador de almacenamiento; no se ha validado un mapa visual completo del espacio con bibliotecas de gran tamaño.
- **Mejora/propuesta verificable:** añadir análisis por carpetas solo con progreso, cancelación y etiqueta explícita de resultado parcial; probarlo con carpetas grandes y proveedores SAF reales.

## 2. Hallazgo técnico aplicado

En el flujo anterior de apertura, Archivero filtraba duplicados y ordenaba la lista de fotos cuando el usuario tocaba una miniatura. Para una galería extensa, ese trabajo de CPU en el hilo visual podía causar demora o bloqueo. La documentación Android recomienda sacar del hilo principal el filtrado/ordenamiento intensivo y la lectura de archivos.

- **Cambio:** el trabajo de filtrado, deduplicación por URI y ordenamiento de las imágenes se ejecuta en el contexto de fondo ya utilizado para filtrar los resultados. El visor recibe la lista preparada y no repite el ordenamiento al abrir una foto.
- **Commit:** [90a9b771b974df61f5a111d95dba93f8edd2af75](https://github.com/jonhararagi/archivero/commit/90a9b771b974df61f5a111d95dba93f8edd2af75).
- **CI:** [run 39414605657](https://github.com/jonhararagi/archivero/actions/runs/39414605657) completado correctamente; pasó `testDebugUnitTest`, `assembleDebug`, verificación de firma y publicación de artefacto.
- **Pendiente:** la compilación no mide jank, memoria ni tiempo de apertura en el Xiaomi real. La mejora de respuesta es razonada a partir del código, pero hace falta medir antes/después con una biblioteca representativa.

## 3. Criterios de comparación para una siguiente iteración

| Área | Medida | Condición para declarar PASS |
|---|---|---|
| Galería | Tiempo entre toque y visor; memoria pico | Medición en listas de 1.000 y 10.000 imágenes, sin trabajo de ordenamiento adicional en el hilo principal |
| Navegación | Pasos para abrir una imagen, pasar a la siguiente y volver | El usuario conserva su búsqueda y posición de lista |
| Miniaturas | Desplazamiento, errores de decodificación, crecimiento de caché | Caché dentro del límite, archivos dañados no cierran la app |
| Duplicados exactos | Coincidencia en muestras preparadas con nombres/rutas distintos | Coincidencia confirmada por contenido SHA-256; archivos ilegibles quedan fuera |
| Fotos similares | Precisión de candidatos en conjunto etiquetado | Se muestran como candidatos revisables, nunca como duplicados exactos confirmados |
| Limpieza | Elementos borrados frente a seleccionados; fallos del proveedor | Confirmación explícita, al menos una copia preservada por grupo y errores individuales informados |
| Bibliotecas grandes | Tiempo, memoria, cancelación y resultados parciales | Sin ANR reproducible; los límites se comunican y no se presenta un análisis parcial como completo |

El resultado de estos benchmarks debe guardarse con modelo de teléfono, versión de Android, tamaño del conjunto de datos, SHA de código, duración y pasos de reproducción. Hasta realizar esas medidas, no se afirma que Archivero sea globalmente superior a los cinco productos.

## 4. Referencias técnicas Android
- [Storage Access Framework: documentos y archivos compartidos](https://developer.android.com/training/data-storage/shared/documents-files), documentación consultada el 2026-10-20, actualizada por Android Developers el 2026-10-16.
- [Diagnóstico de ANR](https://developer.android.com/topic/performance/issues/anr), consultada el 2026-10-20; explica que trabajo extenso, E/S o cálculo en el hilo principal bloquea la entrada y el dibujo.
- [Uso de memoria de bitmaps](https://developer.android.com/topic/performance/issues/bitmap-memory-usage), consultada el 2026-10-20; recomienda reducir resolución al decodificar, evitar asignaciones duplicadas, configurar caché y liberar recursos.

## 5. Límites y siguientes pasos
1. **P0, dispositivo:** repetir desplazamiento, zoom, Atrás y cambio de búsqueda en el Xiaomi; registrar memoria y tiempos. No se han ejecutado esas pruebas físicas desde esta auditoría.
2. **P1, limpieza:** validar eliminación/revocación de permisos con árboles SAF y con almacenamiento local; no asumir que todos los proveedores aceptan las mismas operaciones.
3. **P2, medición:** preparar un conjunto de imágenes etiquetado para estimar precisión del dHash y medir el límite de 2.500 candidatos.
4. **P3, diferencias de producto:** valorar mapa de ocupación por carpetas, papelera compatible con el proveedor, selección por lotes más rápida y, solo después de estabilidad, soporte adicional de red/nube.

**Conclusión:** la galería visual, los duplicados SHA-256, los candidatos visualmente parecidos y la limpieza revisable son funciones presentes en el código consultado. El progreso de esta iteración es una optimización específica de ruta caliente y una base de comparación documentada. La auditoría global, la prueba con 200 GB y cualquier declaración de superioridad siguen en estado **PARTIAL**.
