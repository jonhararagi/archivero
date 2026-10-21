## Continuidad · papelera recuperable y esquema V3 · 2026-10-21

### Implementación en la rama
- La cabeza anterior validada era `64f940825204e20e5b744c5826dbfbb088694224`, [CI PASS run 39635997651](https://github.com/jonhararagi/archivero/actions/runs/39635997651), artefacto `archivero-debug-apk` ID `12361910740`, ZIP 20.590.366 bytes.
- Nuevo commit backend: `2d79cc468779ef36dff4b238dca7a06c240097e7`. Cambia `DigitalLibraryCatalog.kt` y amplía `DigitalLibraryCatalogTest.kt`; su CI aún debe validarse junto con la integración visual.
- El catálogo pasa a esquema V3, continúa leyendo V1 y V2, y guarda `records.count` y `trash.count` en la misma instantánea. Cada entrada de papelera conserva todo el `LibraryRecord` original y `deletedAtMillis`.
- `trashMetadata`, `restoreMetadata` y `purgeTrashedMetadata` modifican solo metadatos y los guardan en una sustitución atómica. No invocan operaciones del sistema de archivos referenciadas por la URI.
- Reingresar con `upsert` una URI ya puesta en papelera la devuelve a biblioteca activa y quita la copia de la papelera; no hay duplicado de identidad.
- Exportación e importación transportan tanto fichas activas como retiradas. Colisiones de URI entre cualquier estado siguen rechazándose por defecto; `replaceExisting=true` aplica explícitamente el estado importado.
- Tests nuevos cubren restauración después de reabrir, roundtrip de snapshot con ficha activa y retirada, reingreso de la URI y purga de metadatos mientras el archivo físico permanece intacto.
- La UI se está conectando en este commit: acción Mover a papelera, contador/diálogo de Papelera, restaurar y eliminar metadatos con doble confirmación. La CI de la cabeza integrada debe pasar antes de considerar PASS.

### Guardrails
- No hacer merge de PR #3 ni editar `main` directamente.
- No marcar validación de teléfono físico, permisos SAF, mediciones Xiaomi o pruebas a escala como realizadas si no hay evidencia.

---

