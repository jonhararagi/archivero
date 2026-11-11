# Hoja de ruta de Archivero

**Objetivo:** evolucionar Archivero de administrador de archivos a biblioteca digital local, visual y fiable, manteniendo rapidez, sencillez y control del usuario.

**Principio de seguridad:** Archivero administra referencias, metadatos y vistas de los archivos. Ninguna operación de catálogo, clasificación, análisis, reparación o copia de seguridad del índice debe modificar el contenido de los archivos originales. Las acciones físicas de archivo deben ser explícitas, confirmadas y recuperables cuando el proveedor lo permita.

## Estado de la consolidación

- PR #8, accesos rápidos e historial atrás/adelante: integrada en `main` el 11 de noviembre de 2026. Commit de squash: `8b80f152c5af980213d6186ff7199c00e8f6aef7`.
- PR #9, reparación segura de relaciones; #10, hash de flujo verificado; #11, progreso de paginación; #12, vista previa de importación; #13, orden estable de páginas; y #14, exportación verificada: siguen como cambios pendientes de reconciliación. Sus HEAD tenían CI exitoso antes de la integración de #8, pero sus ramas quedaron con conflictos contra el nuevo `main`. Requieren resolución, nueva CI combinada y revisión antes de integrar.
- PR #15 propone una segunda implementación de verificación de exportación sobre `MainActivity.kt`. Se debe comparar con #14 y evitar integrar ambas implementaciones por duplicado. #14 aporta un helper separado, pruebas específicas y estados diferenciados `VERIFIED`, `UNAVAILABLE` y `MISMATCH`.
- PR #7, compatibilidad instrumentada Android API 26/35: no se considera aprobada. La ejecución del HEAD inspeccionado terminó `failure`; no atribuir el fallo a la aplicación sin diagnóstico reproducible.

Este documento es una hoja de ruta. Los elementos de las siguientes etapas son propuestas hasta que sus cambios estén implementados, probados y fusionados.

## Etapa 1. Reconciliación y fiabilidad del núcleo

1. Reconciliar las PR pendientes sobre una base actualizada, preservando todos los cambios útiles y resolviendo conflictos de forma explícita.
2. Mantener paginación progresiva y acotada para carpetas y catálogos grandes. Contar las entradas realmente leídas por el proveedor, no solo las URI visibles después de deduplicar.
3. Procesar hashes, copias y validaciones por bloques, con límites de tamaño, cancelación cooperativa y protección frente a lecturas de cero bytes.
4. Tratar los permisos SAF como revocables: validar URI y permisos antes de operar, conservar accesos recuperables y explicar cómo volver a conceder acceso.
5. Proteger el índice con escritura transaccional, snapshots, rollback ante errores, migraciones comprobables y recuperación tras cierre inesperado.
6. Añadir backup/restauración del índice y un historial de cambios que permita recuperar metadatos sin tocar los archivos físicos.

**Criterios de aceptación:** ningún resultado parcial se presenta como íntegro; una importación no reemplaza coincidencias sin revisión y confirmación; una vista previa caducada se rechaza; fallos de escritura conservan el último índice válido; los escaneos se pueden cancelar; los límites de memoria y tamaño están probados.

## Etapa 2. Biblioteca visual y centro de previsualización

1. Vista de cuadrícula y lista con fichas visuales: portada o miniatura, nombre, tipo, ubicación, tamaño y fecha cuando estén disponibles.
2. Identificación de formato mediante MIME y extensión, sin asumir que la extensión garantiza el contenido real.
3. Centro de previsualización para imágenes, documentos compatibles, PDF, audio, vídeo y metadatos de APK. Los formatos no compatibles deben ofrecer apertura externa sin fingir soporte integrado.
4. Miniaturas fuera del hilo principal, con caché de disco y memoria acotada, claves estables, invalidación y limpieza controlada.
5. Estados claros para contenido sin permiso, desconectado, ilegible, no compatible o todavía en carga.

**Criterios de aceptación:** listas fluidas con grandes bibliotecas; límite de memoria de caché comprobable; miniaturas no bloquean la interfaz; errores de proveedor no provocan cierres; la previsualización nunca modifica el original.

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
