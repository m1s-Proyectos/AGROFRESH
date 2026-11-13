# Informe — Fase 12.3 Historial y consultas de movimientos (solo lectura)

**Fecha:** 2026-11-10  
**Proyecto:** AGROFRESH — `AgroFresh2026-desarrollo`  
**Referencia de diseño:** `docs/DISENO_TECNICO_MODULO_MOVIMIENTOS.md`

---

## 1. Objetivo de la fase

Implementar el **historial** y la **consulta de detalle** de movimientos de inventario mediante operaciones **exclusivamente de lectura**, con filtros combinables y interfaz alineada a Categorías / Productos / Proveedores, sin alterar Recepción, Traslados ni el esquema de Supabase.

**Estado:** **IMPLEMENTADA** (alcance autorizado 12.3).

---

## 2. Estructura de tablas consultadas

| Tabla | Uso en 12.3 | Etiqueta |
|---|---|---|
| `movimiento` | Listado, detalle, filtros | **VERIFICADO** (auditoría 12.1) |
| `producto` | Nombre y código en listado/detalle | **IMPLEMENTADO** |
| `bodega` | Origen y destino | **IMPLEMENTADO** |
| `usuario` | Responsable (nombre/apellido) | **IMPLEMENTADO** |
| `movimiento_bodega` | Bodega destino en traslados | **IMPLEMENTADO** |
| `movimiento_compra` | Identificación de entradas por recepción | **IMPLEMENTADO** |
| `detalle_compra` / `compra` / `proveedor` | Detalle de compra en vista de ENTRADA vinculada | **IMPLEMENTADO** |
| `lote` | `fech_vencimiento` en detalle cuando hay lote | **IMPLEMENTADO** |

No se consultan ni modifican `inventario`, procedimientos almacenados ni cantidades de stock.

---

## 3. Relaciones identificadas

- `movimiento` → `producto`, `bodega`, `usuario` (JOIN).
- `movimiento` → `lote` por triple `(num_lote, id_bodega, id_producto)` en detalle.
- `movimiento_bodega` 1:1 con movimiento `TRASLADO` (LEFT JOIN, sin duplicar filas en listado).
- `movimiento_compra` → `detalle_compra` → `compra` → `proveedor` vía subconsultas escalares en detalle; en listado solo `EXISTS` para badge «Recepción».

**Duplicados:** `COUNT(DISTINCT m.id_movimiento)` y ausencia de JOIN directo a `movimiento_compra` en el listado evitan filas repetidas.

---

## 4. Archivos creados

| Archivo |
|---|
| `src/main/java/com/agrofresh/dto/MovimientoFiltro.java` |
| `src/main/java/com/agrofresh/dto/MovimientoListadoDto.java` |
| `src/main/java/com/agrofresh/dto/MovimientoDetalleDto.java` |
| `src/main/java/com/agrofresh/dto/MovimientoCatalogoDto.java` |
| `src/main/java/com/agrofresh/mapper/MovimientoMapper.java` |
| `src/main/java/com/agrofresh/repository/MovimientoRepository.java` |
| `src/main/java/com/agrofresh/service/MovimientoService.java` |
| `src/main/java/com/agrofresh/service/impl/MovimientoServiceImpl.java` |
| `src/main/java/com/agrofresh/controller/MovimientosController.java` |
| `src/main/resources/templates/movimientos.html` |
| `src/main/resources/templates/movimiento-detalle.html` |
| `src/test/java/com/agrofresh/controller/MovimientosControllerTest.java` |
| `src/test/java/com/agrofresh/controller/MovimientosSecurityTest.java` |
| `src/test/java/com/agrofresh/service/impl/MovimientoServiceImplTest.java` |
| `docs/INFORME_FASE_12_3.md` |

---

## 5. Archivos modificados

| Archivo | Cambio |
|---|---|
| `ModuloController.java` | Retirado `GET /movimientos` del placeholder (queda `/reportes`). |
| `SecurityConfig.java` | Reglas finas `GET /movimientos` (ACCEDER ∧ SELECT) y `GET /movimientos/consultar/**` (SELECT). |
| `layout/sidebar.html` | Eliminada etiqueta «Pendiente» en Movimientos. |
| `ModuleViewsTest.java` | Vistas movimientos + `MockBean` `MovimientoService`. |
| `HomePageTest.java` | Prueba de permisos listado + `MockBean` `MovimientoService`. |

**No modificados:** `RecepcionService`, `TrasladoService`, plantillas `recepcion.html` / `traslados.html`, SQL de Supabase.

---

## 6. Implementación de consultas

- **Capa:** `MovimientoRepository` (JdbcTemplate) — justificación: mismo patrón que inventario/traslados para tablas sin entidad JPA de escritura; solo SELECT.
- **Listado:** JOINs a producto, bodega, `movimiento_bodega`, usuario; orden `fecha DESC, id_movimiento DESC`; paginación `LIMIT/OFFSET` (defecto 50, máx. 200).
- **Detalle:** consulta por `id_movimiento` con subconsultas para compra/proveedor y LEFT JOIN a `lote`.
- **Transacciones:** `@Transactional(readOnly = true)` en métodos de lectura del servicio.

---

## 7. Implementación de filtros

| Filtro | Comportamiento |
|---|---|
| Tipo | `tipo_movimiento` = valor si pertenece al CHECK conocido; valores inválidos ignorados. |
| Bodega | `id_bodega` origen **o** `id_bodega_destino` en traslado. |
| Producto | `id_producto`. |
| Fechas | `desde` ≥ inicio del día; `hasta` inclusive (límite exclusivo día siguiente). Si `hasta < desde`, se ajusta `hasta = desde`. |
| Combinación | Todos los criterios activos se aplican con AND. |
| Sin filtros | Todas las bodegas y movimientos (paginados). |

---

## 8. Implementación del detalle

- Ruta: `GET /movimientos/consultar/{id}`.
- Muestra solo datos presentes en BD (sin motivo/observaciones — **NO EXISTE** columna).
- **ENTRADA** con fila en `movimiento_compra`: compra, fecha, proveedor.
- **TRASLADO**: bodega destino + enlace informativo al módulo Traslados.
- Inexistente → `ResourceNotFoundException` (manejador global existente).

---

## 9. Consideraciones de seguridad

| Aspecto | Implementación |
|---|---|
| Listado | `movimiento.ACCEDER` ∧ `movimiento.SELECT` (filtro HTTP + `PermisoService` en controlador). |
| Detalle | `movimiento.SELECT`. |
| CSRF | Sin formularios POST en esta fase. |
| Datos sensibles | No se expone `username` ni credenciales; solo nombre y apellido del responsable. |
| Permisos Supabase | **Sin cambios**. |

---

## 10. Pruebas ejecutadas y resultados

| Suite | Enfoque |
|---|---|
| `MovimientosControllerTest` | Filtros, detalle, 403 sin permiso, propagación 404. |
| `MovimientoServiceImplTest` | Normalización filtro, delegación repositorio. |
| `MovimientosSecurityTest` | Login, ACCEDER+SELECT, SELECT en detalle. |
| `ModuleViewsTest` | Render `movimientos` / `movimiento-detalle`. |
| `HomePageTest` | Listado sin SELECT → 403. |
| Regresión | Recepción y Traslados (`ModuleViewsTest` existente). |

**Pruebas con datos reales de Supabase:** **PENDIENTE** (entorno local del usuario). Las consultas SQL se validaron por coherencia con esquema 12.1 y compilación Maven.

---

## 11. Resultado de Maven

```text
mvn -B clean verify
Tests run: 130, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS
```

---

## 12. Confirmación Supabase

- **No** se ejecutó SQL de escritura.
- **No** se crearon ni alteraron tablas, funciones ni procedimientos.
- **No** se modificó `config/application-local.properties`.

---

## 13. Confirmación Recepción y Traslados

- Código de `RecepcionService` / `TrasladoService` y rutas `/compras`, `/traslados` **sin cambios**.
- Prueba de regresión de vistas **OK** en `ModuleViewsTest`.

---

## 14. Funcionalidades pendientes (fases siguientes)

| Funcionalidad | Estado |
|---|---|
| Registrar salidas | **PENDIENTE** (requiere SP en BD) |
| Bajas por daño / vencimiento | **PENDIENTE** (requiere SP) |
| Formularios de escritura y POST | **PENDIENTE** (12.4+ / migración autorizada) |
| Auditoría seguridad dedicada movimientos | **PENDIENTE** (12.5) |
| Validación E2E contra datos productivos | **PENDIENTE** |

---

## Conclusión

La **Fase 12.3** cumple el alcance autorizado: historial con filtros, detalle, seguridad de lectura, interfaz integrada y suite Maven en verde. **Detenido** — sin avanzar a escritura de movimientos ni Fase 12.4 sin nueva autorización.
