# Informe Fase 12.6.3 — Consolidación pantallas módulo Movimientos

**Fecha:** 2026-11-10  
**Proyecto:** AGROFRESH

### Leyenda

| Marca | Significado |
|---|---|
| **VERIFICADO** | Existía o comprobado en código/pruebas. |
| **IMPLEMENTADO** | Añadido o mejorado en 12.6.3. |
| **PENDIENTE** | Fuera de alcance o sin entorno BD aislado. |
| **NO APLICABLE** | No requiere pantalla aparte. |

---

## Pantallas del módulo

| # | Pantalla | Ruta / vista | Estado |
|---|---|---|---|
| 1 | Historial | `GET /movimientos` → `movimientos.html` | **VERIFICADO** + **IMPLEMENTADO** (submenú, badges SALIDA/BAJA, texto consulta) |
| 2 | Registrar entrada | `GET /compras` → `recepcion.html` | **VERIFICADO** + **IMPLEMENTADO** (submenú movimientos) |
| 3 | Registrar salida NP/PER | `GET /movimientos/salida/*` | **VERIFICADO** (12.6.2) |
| 4 | Registrar baja (3) | `GET /movimientos/baja/*` | **VERIFICADO** (12.6.2) |
| 5 | Traslados | `GET /traslados` → `traslados.html` | **VERIFICADO** + **IMPLEMENTADO** (submenú) |
| 6 | Consultar movimientos | Mismo historial + filtros | **NO APLICABLE** (no duplicada; submenú renombrado) |
| 7 | Detalle | `GET /movimientos/consultar/{id}` → `movimiento-detalle.html` | **VERIFICADO** + **IMPLEMENTADO** (submenú, bloques SALIDA/BAJA, bitácora) |
| — | Panel registrar | `GET /movimientos/registrar` → `movimientos-registrar.html` | **IMPLEMENTADO** (índice de operaciones) |

---

## Pantallas nuevas

- `movimientos-registrar.html` — hub de operaciones del módulo.

## Pantallas modificadas

- `movimientos.html`, `movimiento-detalle.html`
- `recepcion.html`, `traslados.html`
- `fragments/movimientos-submenu.html`
- `static/js/movimiento-operacion.js`, `static/css/inventario.css`

## Archivos Java modificados

| Archivo | Cambio |
|---|---|
| `SessionModelAdvice` | `puedeRegistrarSalida`, `esAdministrador` globales en vistas |
| `MovimientosController` | `GET /movimientos/registrar` |
| `MovimientoDetalleDto` | Campo `descripcionBitacora` |
| `MovimientoRepository` | Lectura opcional de bitácora en detalle |
| `MovimientoMapper` | Mapeo `descripcion_bitacora` |

**Sin cambios** en `MovimientoOperacionService`, SP ni PostgreSQL estructural.

---

## Rutas verificadas

### GET (vistas)

| Ruta | Permiso |
|---|---|
| `/movimientos` | `movimiento.SELECT` + `ACCEDER` |
| `/movimientos/registrar` | `movimiento.SELECT` |
| `/movimientos/consultar/{id}` | `movimiento.SELECT` |
| `/movimientos/salida/*` | `movimiento.INSERT` |
| `/movimientos/baja/*` | `ROLE_ADMIN` |
| `/compras`, `/traslados` | Sin cambio (módulos existentes) |

### POST (API, sin cambio 12.6.1)

- `/api/movimientos/salida/no-perecedero`
- `/api/movimientos/salida/perecedero`
- `/api/movimientos/baja/dano/no-perecedero`
- `/api/movimientos/baja/dano/perecedero`
- `/api/movimientos/baja/vencimiento`
- `/api/compras/confirmar`, `/api/traslados/confirmar` (recepción/traslados)

### Catálogos GET

- `/api/movimientos/bodegas`, `productos`, `lotes-disponibles`, `lotes-con-stock`, `lotes-vencidos`

---

## Servicios y endpoints reutilizados

- **IMPLEMENTADO** integración UI con API 12.6.1 y catálogos `MovimientoCatalogoController`.
- **VERIFICADO** recepción y traslados sin duplicar servicios.
- **VERIFICADO** historial vía `MovimientoService` / `MovimientoRepository`.

---

## CSRF, DTO y permisos — **VERIFICADO**

- Meta CSRF en formularios de operación; headers en `fetch`.
- JSON alineado con `MovimientoSalidaSolicitud` y `MovimientoBajaSolicitud`.
- Salida perecedera sin selector de lote (modo `info` solo lectura).
- Doble capa salidas (`INSERT`) y bajas (`ROLE_ADMIN` + SP).

---

## Pruebas ejecutadas

| Comando | Resultado |
|---|---|
| `mvn -B clean verify` | **OK** (exit 0) |
| `MovimientoFormViewsTest` | OK + panel registrar |
| `ModuleViewsTest` | Historial, detalle, registrar, salida NP |
| Regresión 12.6.1 | OK |

**PENDIENTE:** E2E manual con Supabase aislado; POST real de inventario.

---

## Problemas / limitaciones

| Tema | Clasificación |
|---|---|
| Salida perecedera multilote | **RIESGO** — sin vínculo único en historial; UI lo documenta |
| Motivo baja vía bitácora | **IMPLEMENTADO** lectura heurística por `descripcion`; puede no coincidir si el texto de bitácora cambia |
| Consultar vs historial | **NO APLICABLE** — una sola pantalla |

---

## PostgreSQL

**VERIFICADO:** no se modificaron tablas, columnas, SP ni datos de producción. Solo consulta adicional de lectura sobre `bitacora` en detalle.

---

## Funcionalidades pendientes (post 12.6.3)

- Agrupación visual de salidas FEFO multilote (diseño, sin cambiar SP).
- Prueba de integración UI con BD aislada.

---

## Detención

Fase 12.6.3 cerrada. Sin avance a 12.6.4. Sin Git.
