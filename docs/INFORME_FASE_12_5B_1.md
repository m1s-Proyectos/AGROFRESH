# Informe Fase 12.5B.1 — Bajas por daño (PostgreSQL)

**Fecha:** 2026-11-10  
**Proyecto:** AGROFRESH  
**Alcance:** Implementación y prueba de `sp_baja_dano_perecedero` y `sp_baja_dano_no_perecedero`. Sin Java, sin bajas por vencimiento, sin cambios de esquema ni permisos globales.

**Referencia de diseño:** `docs/ANALISIS_FASE_12_5B.md`  
**Migración:** `docs/migrations/movimientos/MIG-12.5B.1_sp_baja_dano.sql`  
**Evidencia pruebas:** `docs/migrations/movimientos/_test_12_5b_1_output.txt` (`failed=0`)

---

## 1. Procedimientos creados

| Objeto | Tipo |
|---|---|
| `inventario.fn_usuario_es_administrador` | FUNCTION |
| `inventario.fn_validar_motivo_baja` | FUNCTION |
| `inventario.sp_baja_dano_no_perecedero` | PROCEDURE |
| `inventario.sp_baja_dano_perecedero` | PROCEDURE |

**SECURITY:** `INVOKER` (igual que salidas y traslados).  
**Despliegue:** `python docs/migrations/movimientos/_deploy_12_5b_1.py` → `deploy_ok: MIG-12.5B.1_sp_baja_dano.sql`.

---

## 2. Parámetros y tipos

### `sp_baja_dano_no_perecedero`

| Parámetro | Dirección | Tipo |
|---|---|---|
| `p_id_bodega` | IN | integer |
| `p_id_producto` | IN | integer |
| `p_cantidad` | IN | integer |
| `p_id_usuario` | IN | integer |
| `p_motivo` | IN | text |
| `p_id_movimiento` | OUT | integer |

### `sp_baja_dano_perecedero`

| Parámetro | Dirección | Tipo |
|---|---|---|
| `p_id_bodega` | IN | integer |
| `p_id_producto` | IN | integer |
| `p_num_lote` | IN | integer |
| `p_cantidad` | IN | integer |
| `p_id_usuario` | IN | integer |
| `p_motivo` | IN | text |
| `p_id_movimiento` | OUT | integer |

---

## 3. Validaciones

| Validación | Mecanismo |
|---|---|
| Cantidad > 0, bodega/producto existentes, stock agregado | `fn_validar_salida` (reutilizada) |
| Motivo obligatorio | `fn_validar_motivo_baja` (trim; vacío rechazado; máx. 500 caracteres) |
| Solo ADMINISTRADOR | `fn_usuario_es_administrador`: `usuario.id_rol` → `rol.nombre = 'ADMINISTRADOR'`, usuario activo, no bloqueado, rol activo |
| Clasificación producto | Mismas comprobaciones `perecedero` / `no_perecedero` que salidas |
| Lote perecedero | Existencia del triple `(num_lote, id_bodega, id_producto)`; cantidad en lote ≥ solicitada |
| Vencimiento en baja daño | **No** se excluyen lotes vencidos (diferencia con traslado/salida FEFO) |

---

## 4. Estrategia de seguridad

| Aspecto | Decisión |
|---|---|
| Identidad administrador | **VERIFICADO** vía FK `usuario.id_rol` y catálogo `rol.nombre`, no el campo texto legacy `usuario.rol` |
| Parámetro texto “ADMINISTRADOR” | **No usado** |
| Privilegios OPERATIVO | **Sin cambios** (`movimiento.INSERT` intacto) |
| Rechazo en PG | OPERATIVO recibe excepción al llamar el SP aunque tenga INSERT genérico |
| Java / Spring | Fuera de alcance; capa adicional en fase futura |

---

## 5. Bloqueos y concurrencia

Orden alineado a `sp_salida_*` / `sp_trasladar_perecedero`:

1. `FOR UPDATE` en fila de `bodega`.
2. Revalidación de stock.
3. `FOR UPDATE` en `inventario` (bodega, producto).
4. Perecedero: `SELECT … FROM lote … FOR UPDATE` del lote indicado.
5. `UPDATE lote` → `UPDATE inventario` → `INSERT movimiento` → `INSERT bitacora`.

Toda la secuencia corre en **una transacción** del `CALL`; fallo en bitácora o movimiento revierte descuentos.

---

## 6. Actualizaciones de datos

| Entidad | Operación |
|---|---|
| `inventario.cantidad_actual` | Decremento condicionado `cantidad_actual >= p_cantidad` |
| `lote.cantidad_actual` | Solo perecedero; mismo patrón condicionado |
| `movimiento` | `tipo_movimiento = 'BAJA_DAÑO'`; `num_lote` solo perecedero |
| `bitacora` | Fila nueva por operación |

---

## 7. Registro de movimientos

- Tipo fijo: **`BAJA_DAÑO`** (CHECK existente en tabla).
- `id_usuario`: administrador validado.
- `fecha`: default `CURRENT_TIMESTAMP` en tabla.
- `OUT p_id_movimiento`: primer/único id generado en la operación.

---

## 8. Registro de bitácora

| Campo | Valor |
|---|---|
| `id_usuario` | `p_id_usuario` (admin) |
| `tipo_accion` | `BAJA_DAÑO` |
| `tabla_afectada` | `movimiento` |
| `descripcion` | `Baja por dano movimiento #<id> [lote <n>:] <motivo>` truncado a 500 chars |
| `fecha_hora` | default BD |

**Motivo persistente:** sí, en `descripcion` (no hay columna en `movimiento`).

---

## 9. Pruebas ejecutadas y resultados

Script: `_test_12_5b_1_baja_dano.py`. Estrategia: `SAVEPOINT` por caso + **rollback final** de conexión; lotes de prueba `99871`/`99872` solo dentro de savepoint.

| # Requerimiento | Caso en log | Resultado |
|---|---|---|
| 1 Baja válida perecedero | `01_per_baja_valida_vigente` | OK |
| 2 Baja válida no perecedero | `01_np_baja_valida` | OK |
| 3 Stock insuficiente | `03_np_stock_insuficiente` | OK (rechazado) |
| 4 Cantidad cero | `04_cantidad_cero` | OK |
| 5 Cantidad negativa | `05_cantidad_negativa` | OK |
| 6 Producto inexistente | `06_producto_inexistente` | OK |
| 7 Bodega inexistente | `07_bodega_inexistente` | OK |
| 8 Lote vigente daño | `08_lote_vigente_dano` | OK |
| 9 Lote vencido daño | `09_lote_vencido_dano` | OK |
| 10 Movimiento correcto | Incluido en casos válidos (tipo `BAJA_DAÑO`) | OK |
| 11 Motivo en bitácora | `11_motivo_bitacora` | OK |
| 12 Rollback ante error | `12_rollback_motivo_vacio` (motivo vacío; stock sin cambio) | OK |
| 13 OPERATIVO rechazado | `13_operativo_rechazado_*` | OK |
| 14 ADMIN aceptado | `14_admin_aceptado_np` | OK |
| 5 Lote inexistente (perecedero) | `05_lote_inexistente` | OK |

**Resultado global:** `failed=0`.

---

## 10. Limitaciones

| Tema | Nota |
|---|---|
| EXECUTE en SP | Mismo esquema que salidas (`PUBLIC` puede ejecutar); la barrera funcional es la validación de rol interna |
| Motivo en UI historial | Java aún no enlaza bitácora con movimiento |
| Rollback post-`UPDATE` por fallo bitácora | No ejercitado con motivo inválido tardío; garantía por transacción única PL/pgSQL |
| Longitud `bitacora.descripcion` en catálogo | varchar sin límite explícito en auditoría; SP trunca a 500 |
| Baja vencimiento | **No implementada** (Fase 12.5B.2 pendiente) |

---

## 11. Evidencia de restauración

- Conexión de prueba termina con `conn.rollback()`.
- Cada caso usa `ROLLBACK TO SAVEPOINT sp_case`.
- Suite perecedero: `ROLLBACK TO SAVEPOINT sp_per_suite` tras lotes `99871`/`99872`.
- Log: `Restaurado savepoint lotes 99871/99872 (sin cambios persistentes)`.

---

## 12. Archivos entregables

| Archivo | Rol |
|---|---|
| `MIG-12.5B.1_sp_baja_dano.sql` | Definiciones BD |
| `_deploy_12_5b_1.py` | Despliegue |
| `_test_12_5b_1_baja_dano.py` | Pruebas |
| `_test_12_5b_1_output.txt` | Evidencia |
| `INFORME_FASE_12_5B_1.md` | Este informe |

---

## 13. Confirmaciones

| Compromiso | Estado |
|---|---|
| Sin tablas/columnas nuevas | Cumplido |
| Sin modificar `sp_salida_*` | Cumplido |
| Sin Java / UI | Cumplido |
| Sin revocar permisos OPERATIVO | Cumplido |
| Mecanismo ADMIN confiable en PG | Cumplido (`usuario` + `rol`) |

**Fase 12.5B.1 COMPLETADA.** Esperar autorización expresa para **12.5B.2** (baja por vencimiento).
