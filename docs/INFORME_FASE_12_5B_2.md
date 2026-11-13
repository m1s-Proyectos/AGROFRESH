# Informe Fase 12.5B.2 — Baja por vencimiento (PostgreSQL)

**Fecha:** 2026-11-10  
**Proyecto:** AGROFRESH  
**Alcance:** Implementación única de `sp_baja_vencimiento`. Sin cambios en salidas, bajas por daño, Java, UI ni permisos globales.

**Referencias:** `ANALISIS_FASE_12_5B.md`, `INFORME_FASE_12_5B_1.md`, `MIG-12.5B.1_sp_baja_dano.sql`  
**Migración:** `docs/migrations/movimientos/MIG-12.5B.2_sp_baja_vencimiento.sql`  
**Pruebas:** `docs/migrations/movimientos/_test_12_5b_2_output.txt` (`failed=0`)

---

## 1. Definición del procedimiento

`inventario.sp_baja_vencimiento` — baja administrativa de **perecedero** por lote **vencido** (`fech_vencimiento <= CURRENT_DATE`), con motivo obligatorio en bitácora.

**SECURITY:** `INVOKER` (sin `SECURITY DEFINER`).

**Despliegue:** `python docs/migrations/movimientos/_deploy_12_5b_2.py` → `deploy_ok`.

---

## 2. Parámetros y tipos

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

| Regla | Implementación |
|---|---|
| Motivo obligatorio | `fn_validar_motivo_baja` (12.5B.1, sin cambios) |
| Solo ADMINISTRADOR | `fn_usuario_es_administrador` → `usuario.id_rol` + `rol.nombre` |
| Cantidad / bodega / producto / stock agregado | `fn_validar_salida` + revalidación tras bloqueo |
| Solo perecedero | Igual que `sp_baja_dano_perecedero` |
| Lote requerido y existente | Triple `(num_lote, id_bodega, id_producto)` |
| **No vigente** | `fech_vencimiento > CURRENT_DATE` → excepción *El lote aun no esta vencido* |
| **Vencido o hoy** | `fech_vencimiento <= CURRENT_DATE` aceptado |
| Stock en lote e inventario | Condiciones `>= p_cantidad` en `UPDATE` |

---

## 4. Seguridad

- **Reutiliza** funciones de 12.5B.1; no acepta texto libre como rol.
- `p_id_usuario` debe ser un `id_usuario` válido con rol **ADMINISTRADOR** en catálogo `rol`.
- OPERATIVO recibe la misma excepción que en bajas por daño (mensaje genérico de administrador en `fn_usuario_es_administrador`).
- Sin cambios en `rol_permiso`, RLS ni grants.

---

## 5. Bloqueos (concurrencia)

Orden **idéntico a 12.5B.1**:

1. `FOR UPDATE` en `bodega`.
2. `FOR UPDATE` en fila de `inventario`.
3. `SELECT … FOR UPDATE` en `lote` (cantidad + `fech_vencimiento`).

Actualizaciones condicionadas evitan cantidades negativas; transacción única del `CALL`.

---

## 6. Actualizaciones

| Tabla | Acción |
|---|---|
| `lote` | `cantidad_actual -= p_cantidad` |
| `inventario` | `cantidad_actual -= p_cantidad` |
| `movimiento` | `INSERT` tipo `BAJA_VENCIMIENTO` |
| `bitacora` | `INSERT` con motivo |

---

## 7. Movimiento y bitácora

**Movimiento**

- `tipo_movimiento = 'BAJA_VENCIMIENTO'` (CHECK existente).
- `num_lote` informado.
- `OUT p_id_movimiento`.

**Bitácora**

| Campo | Valor |
|---|---|
| `tipo_accion` | `BAJA_VENCIMIENTO` |
| `tabla_afectada` | `movimiento` |
| `descripcion` | `Baja por vencimiento movimiento #<id> lote <n>: <motivo>` (máx. 500 chars) |

---

## 8. Casos de prueba y resultados

Script: `_test_12_5b_2_baja_vencimiento.py`. Lotes aislados `99881` (vigente), `99882` (vencido), `99883` (`CURRENT_DATE`). Savepoints + rollback final.

| # | Caso | Resultado |
|---|---|---|
| 1 | Baja válida vencido | OK |
| 2 | Rechazo vigente | OK (rechazado) |
| 3 | Vencido exactamente hoy | OK |
| 4 | Cantidad cero | OK |
| 5 | Cantidad negativa | OK |
| 6 | Existencias insuficientes | OK |
| 7 | Lote inexistente | OK |
| 8 | Producto inexistente | OK |
| 9 | Bodega inexistente | OK |
| 10 | OPERATIVO | OK (rechazado) |
| 11 | ADMINISTRADOR | OK |
| 12–13 | Movimiento + bitácora | OK |
| 14 | Rollback motivo vacío | OK |
| 15 | Consistencia inventario/lote | OK |

**Global:** `failed=0`.

---

## 9. Evidencia de restauración

- `ROLLBACK TO SAVEPOINT sp_suite` tras pruebas de lotes `99881–99883`.
- `conn.rollback()` al cerrar la conexión.
- Log: *Restaurado savepoint lotes 99881-99883 (sin cambios persistentes)*.

---

## 10. Limitaciones

| Tema | Nota |
|---|---|
| Solo perecedero con lote explícito | Coherente con diseño 12.5B |
| Mensaje `fn_usuario_es_administrador` | Texto orientado a “bajas por daño”; funcionalmente aplica a vencimiento |
| No perecedero | No aplica; sin SP dedicado |
| Integración Java | Fase posterior |
| Concurrencia multi-sesión | No repetida en 12.5B.2; patrón de bloqueo alineado a 12.5B.1 |

---

## 11. Archivos

| Archivo |
|---|
| `MIG-12.5B.2_sp_baja_vencimiento.sql` |
| `_deploy_12_5b_2.py` |
| `_test_12_5b_2_baja_vencimiento.py` |
| `_test_12_5b_2_output.txt` |
| `INFORME_FASE_12_5B_2.md` |

---

## 12. Confirmaciones

| Compromiso | Estado |
|---|---|
| Sin modificar `sp_baja_dano_*` ni salidas | Cumplido |
| Sin tablas/columnas nuevas | Cumplido |
| Sin Java / permisos globales | Cumplido |
| Motivo en bitácora | Cumplido |
| Integridad inventario/lote | Cumplido en pruebas |

**Fase 12.5B.2 COMPLETADA.** Detenido — esperar autorización para la fase siguiente.
