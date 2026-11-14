# Hoja de ruta de Archivero

**Objetivo:** evolucionar Archivero de administrador de archivos a biblioteca digital local, visual y fiable, manteniendo rapidez, sencillez y control del usuario.

**Principio de seguridad:** Archivero administra referencias, metadatos y vistas de los archivos. Ninguna operación de catálogo, clasificación, análisis, reparación o copia de seguridad del índice debe modificar el contenido de los archivos originales. Las acciones físicas de archivo deben ser explícitas, confirmadas y recuperables cuando el proveedor lo permita.

## Estado de la consolidación · actualizado 2026-11-14

- `main` está en el commit integrado `600ea5f415f2805a62fbde063ad538b26e533eaa`.
- PR #8 (accesos rápidos), #16 (hoja de ruta) y #17 (reconciliación de seguridad, importación/exportación y paginación) están fusionadas.
- Los cambios útiles de PR #9 a #14 quedaron incluidos y verificados dentro de la integración de PR #17. PR #15 se cerró como duplicada de la solución de exportación ya integrada. Las ramas de respaldo se conservaron.
- PR #7 sigue abierta y separada para validar compatibilidad instrumentada API 26/35. Su último intento conocido falló en API 35; no se atribuye esa falla a la aplicación sin evidencia reproducible.
- La CI posterior al merge de `main`, ejecución [#42975948337](https://github.com/jonhararagi/archivero/actions/runs/42975948337), terminó PASS: pruebas JVM, compilación, firma, SHA-256 y publicación de APK.
- La CI no sustituye las pruebas instrumentadas ni la validación física en el Xiaomi objetivo.

Este documento es una hoja de ruta. Los elementos de las siguientes etapas son propuestas hasta que sus cambios estén implementados, probados y fusionados.

## Etapa 1. Núcleo fiable e integrado

La base de esta etapa está integrada en `main`:

1. Paginación SAF progresiva y acotada, con avance correcto ante páginas solapadas y orden global estable.
2. Hash SHA-256 por streaming, validación de longitud/estabilidad y protección ante lecturas de cero bytes.
3. Reparación segura de relaciones con vista previa, confirmación, rollback y conservación de enlaces hacia fichas en papelera.
4. Importación de instantáneas con validación previa, detección de coincidencias, confirmación explícita y rechazo de vistas previas obsoletas.
5. Exportación con límite de tamaño, fingerprint SHA-256 y verificación de lectura de vuelta cuando el proveedor lo permite.
6. Catálogo local versionado, papelera de metadatos y persistencia transaccional.

**Pendiente de evolución:** un historial de auditoría de cambios del catálogo no debe confundirse con las instantáneas y la recuperación de escrituras que ya existen. La matriz instrumentada API 26/35 y las pruebas físicas siguen pendientes.

**Estado:** núcleo integrado y CI posterior al merge PASS; validación de dispositivo PARTIAL.

## Etapa 2. Biblioteca visual y centro de previsualización

1. Vista de cuadrícula y lista con fichas visuales: portada o miniatura, nombre, tipo, ubicación, tamaño y fecha cuando estén disponibles.
2. Identificación de formato mediante MIME y extensión, sin asumir que la extensión garantiza el contenido real.
3. Centro de previsualización para imágenes, documentos compatibles, PDF, audio, vídeo y metadatos de APK. Los formatos no compatibles deben ofrecer apertura externa sin fingir soporte integrado.
4. Miniaturas fuera del hilo principal, con caché de disco y memoria acotada, claves estables, invalidación y limpieza controlada.
5. Estados claros para contenido sin permiso, desconectado, ilegible, no compatible o todavía en carga.

**Criterios de aceptación:** listas fluidas con grandes bibliotecas; límite de memoria de caché comprobable; miniaturas no bloquean la interfaz; errores de proveedor no provocan cierres; la previsualización nunca modifica el original.

**Base ya existente:** cuadrícula/lista virtualizada, metadatos técnicos de las fichas, miniaturas de imágenes y primera página de PDF, caché LRU de memoria de 24 MiB y previsualización integrada de formatos compatibles. Esta etapa amplía esa base; no la reconstruye.

**Iteración 2A · caché persistente de miniaturas:**
- Añadir caché de disco dentro del directorio privado de caché de la aplicación, separada del catálogo de metadatos.
- Mantener 24 MiB de caché LRU en memoria y limitar el disco a 48 MiB, 2.048 entradas y 1 MiB por miniatura.
- Usar identidad versionada por URI, nombre, tamaño y fecha de modificación. Persistir solo cuando tamaño y fecha están disponibles; en proveedores que no los informan, usar únicamente memoria para evitar conservar miniaturas potencialmente obsoletas.
- Escribir mediante archivo temporal y reemplazo atómico; descartar entradas corruptas o fuera de límite y aplicar expulsión por antigüedad.
- La caché es regenerable y nunca se usa como fuente de verdad. La limpieza automática de caché no afecta archivos originales ni fichas del catálogo.

**Estado de iteración 2A:** en desarrollo en la rama `feature/archivero-visual-library-disk-cache-001`; los criterios siguen pendientes de CI y revisión.



## Etapa 3. Clasificación y búsqueda inteligente

1. Clasificación por formato, fecha, tamaño, carpeta, proyecto, etiquetas y colecciones.
2. Reglas personalizables que creen agrupaciones virtuales, sin mover archivos automáticamente.
3. Filtros combinables y búsqueda tolerante a tildes, con resultados paginados y límites explícitos.
4. Duplicados exactos mediante tamaño y SHA-256 completo; similitud visual como sugerencia independiente, sin confundirla con identidad.
5. Explicar por qué se agrupó un elemento y mostrar los metadatos que sustentan cada coincidencia.

**Criterios de aceptación:** clasificación reproducible y reversible; el usuario puede revisar las reglas antes de aplicarlas; ninguna coincidencia aproximada se elimina automáticamente; búsqueda y agrupación no cargan archivos completos en RAM.

## Etapa 4. Analizador de almacenamiento

1. Resumen por carpetas, formatos y rangos de tamaño.
2. Lista de archivos grandes y posibles duplicados, con ubicación y comparación.
3. Estimaciones de espacio recuperable separando duplicados exactos de similitudes.
4. Escaneo cancelable, incremental y con paginación; no recorrer todo el almacenamiento si el usuario no lo solicita.
5. Ninguna limpieza automática. Las operaciones de borrado requieren selección y confirmación por parte del usuario.

**Criterios de aceptación:** los totales indican si el análisis está completo o limitado por permisos; los archivos inaccesibles se identifican como tales; nunca se promete espacio recuperable sin confirmar que la acción se puede realizar.

## Etapa 5. Espacio de trabajo personalizable

1. Inicio con accesos rápidos, carpetas favoritas, colecciones y actividad reciente.
2. Vistas lista/cuadrícula y orden preferido por sección.
3. Fichas y colecciones con etiquetas, notas y relaciones.
4. Restauración de preferencias y accesos cuando los permisos continúan vigentes.
5. Interfaz móvil con objetivos táctiles cómodos, paneles simples y estados vacíos útiles.

**Criterios de aceptación:** preferencias persistentes y versionadas; cambios de vista no cambian archivos; la navegación sigue siendo usable con una mano y en pantallas pequeñas.

## Reglas técnicas transversales

- **Android SAF:** asumir que proveedores externos pueden ser lentos, incompletos, inconsistentes o revocar permisos.
- **Escalabilidad:** enumerar progresivamente; no materializar cientos de miles de entradas en una sola lista.
- **Memoria:** procesar streams y miniaturas por bloques; evitar cargar archivos enteros cuando no sea necesario.
- **Concurrencia:** mover I/O fuera del hilo principal, cancelar trabajo obsoleto y evitar publicar resultados de una carpeta anterior.
- **Integridad:** hashes solo sobre flujos completos; registrar límites, bytes leídos y resultado de verificación.
- **Persistencia:** validar snapshots, versionar formatos y conservar el índice anterior ante un fallo.
- **Privacidad:** procesamiento local por defecto; no subir archivos ni metadatos a servicios externos.
- **UX:** diferenciar claramente escrito, verificado, no verificable y discrepante.

## Matriz de validación obligatoria

1. Pruebas unitarias JVM para algoritmos puros, límites, rollback, paginación, clasificación y reglas de integridad.
2. Compilación Android independiente de las pruebas.
3. Lint independiente de compilación y pruebas.
4. Pruebas instrumentadas en emuladores API mínima y API actual, registrando diagnósticos si falla el entorno.
5. Pruebas reales en el Xiaomi objetivo `24116RACCG` (8 GB RAM) para SAF, permisos revocados, carpetas grandes, previsualización y consumo de memoria.
6. Prueba con bibliotecas grandes y proveedores lentos o con páginas solapadas.
7. Registrar SHA real, ejecución CI y artefacto solo si existe y se verifica. La compilación no sustituye a las pruebas unitarias, lint, instrumentadas ni físicas.

## Política de integración

- Trabajar en ramas y PR; no editar `main` directamente.
- No perder cambios de ramas complementarias ni integrar versiones duplicadas de una misma función.
- Resolver conflictos contra el `main` actual y ejecutar CI sobre el resultado combinado.
- No cerrar PR ni descartar ramas que contengan trabajo único hasta comprobar que cada cambio útil está conservado.
- No declarar una función lista por existir el código: debe tener pruebas, CI y, cuando dependa de SAF/OEM, validación en dispositivo real.
