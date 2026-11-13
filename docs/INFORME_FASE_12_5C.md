# Informe Fase 12.5C — Validación integral de procedimientos almacenados

**Fecha:** 2026-11-10  
**Proyecto:** AGROFRESH  
**Alcance:** Auditoría y pruebas exclusivamente. **Sin** cambios en BD, SP, Java ni permisos.

**Evidencia:**  
- `docs/migrations/movimientos/_test_12_5c_integral.py`  
- `docs/migrations/movimientos/_test_12_5c_output.txt` (`failed=0`)

### Leyenda

| Marca | Significado |
|---|---|
| **VERIFICADO** | Observado en Supabase durante la ejecución. |
| **FALLIDO** | No cumplió criterio (ninguno en esta fase). |
| **PENDIENTE** | No ejecutado en 12.5C (cubierto en fase anterior o fuera de alcance acotado). |

---

## 1. Inventario de procedimientos y funciones

Consulta **VERIFICADA** a `pg_proc` (esquema `inventario`, filtro movimientos/inventario/lotes). Catálogo completo en `_test_12_5c_output.txt` líneas 1–20.

### Procedimientos principales

| Rutina | Tipo | Migración documentada |
|---|---|---|
| `sp_crear_compra` | PROCEDURE | Pre-existente |
| `sp_recibir_no_perecedero` | PROCEDURE | Pre-existente |
| `sp_recibir_perecedero` | PROCEDURE | Pre-existente |
| `sp_salida_no_perecedero` | PROCEDURE | `MIG-12.5A_sp_salidas.sql` |
| `sp_salida_perecedero` | PROCEDURE | `MIG-12.5A_sp_salidas.sql` |
| `sp_trasladar_*` | PROCEDURE | Pre-existente (sin cambios en 12.5A–B) |
| `sp_baja_dano_no_perecedero` | PROCEDURE | `MIG-12.5B.1_sp_baja_dano.sql` |
| `sp_baja_dano_perecedero` | PROCEDURE | `MIG-12.5B.1_sp_baja_dano.sql` |
| `sp_baja_vencimiento` | PROCEDURE | `MIG-12.5B.2_sp_baja_vencimiento.sql` |

### Funciones auxiliares relevantes

| Función | Migración / origen |
|---|---|
| `fn_validar_recepcion` | Pre-existente |
| `fn_validar_salida` | MIG-12.5A |
| `fn_stock_lotes_vigentes` | MIG-12.5A |
| `fn_validar_traslado` | Pre-existente |
| `fn_validar_motivo_baja` | MIG-12.5B.1 |
| `fn_usuario_es_administrador` | MIG-12.5B.1 (existe en BD; invocada vía bajas) |
| `fn_consultar_stock_bodega`, `fn_lotes_disponibles` | Pre-existentes |

**Dependencias observadas:** recepciones → `fn_validar_recepcion`; salidas/bajas daño NP → `fn_validar_salida`; salida perecedero → `fn_stock_lotes_vigentes`; bajas → `fn_usuario_es_administrador` + `fn_validar_motivo_baja` + `fn_validar_salida`.

---

## 2. Matriz de cobertura

| Rutina | Implementado (catálogo) | Prueba individual previa | Validación 12.5C |
|---|---|---|---|
| `sp_recibir_*` | VERIFICADO | Recepción Java / análisis 12.1 | **VERIFICADO** A01–A03, SEQ1–2 |
| `sp_salida_*` | VERIFICADO | 12.5A, 12.5A.1 | **VERIFICADO** B, C, CONC |
| `sp_baja_*` | VERIFICADO | 12.5B.1, 12.5B.2, 12.5B.3 | **VERIFICADO** D, SEQ, CONC |
| `sp_trasladar_*` | VERIFICADO | 12.5A.1 contexto traslados | **PENDIENTE** (no mutación en 12.5C; sin escenario dedicado) |
| `sp_crear_compra` | VERIFICADO | — | **VERIFICADO** vía entradas |

---

## 3. Pruebas funcionales

### A. Entradas — **VERIFICADO**

- Entrada NP válida incrementa inventario y registra `ENTRADA`.
- Cantidad cero rechazada.
- Entrada perecedera crea lote.

### B. Salidas — **VERIFICADO**

- Salida NP válida e insuficiente rechazada.

### C. FEFO — **VERIFICADO**

- Consumo multilote (22 u. sobre lotes 99601–99603): inventario = suma de lotes tras operación.
- Con todos los lotes vencidos, salida FEFO rechazada.

### D. Bajas — **VERIFICADO**

- ADMIN ejecuta baja NP; OPERATIVO rechazado; bitácora generada (smoke; detalle en 12.5B.3).

---

## 4. Pruebas consecutivas — **VERIFICADO**

| Secuencia | Resultado |
|---|---|
| Entrada → salida NP | OK `SEQ1` |
| Entrada → baja daño | OK `SEQ2` |
| FEFO → baja daño lote | OK `SEQ3` |
| Setup lote vencido → baja vencimiento | OK `SEQ4` |

Coherencia comprobada: inventario y lotes no negativos tras cada secuencia (savepoints).

---

## 5. Consistencia global

**VERIFICADO** en operaciones válidas:

- Perecedero: tras FEFO y bajas, `inventario.cantidad_actual` = `SUM(lote.cantidad_actual)` para producto/bodega de prueba.
- Movimientos con `tipo_movimiento` esperado (`ENTRADA`, `SALIDA`, `BAJA_DAÑO`, `BAJA_VENCIMIENTO`).
- Bajas con fila en `bitacora` enlazada por texto `movimiento #id`.
- Sin duplicados por id en movimientos creados en cada caso aislado.

---

## 6. Concurrencia cruzada — **VERIFICADO**

| Escenario | Descripción | Resultado |
|---|---|---|
| **A** | Recepción NP vs salida NP mismo producto | Ambas pueden completar en orden concurrente; sin stock negativo |
| **B** | Salida FEFO vs baja daño mismo lote (`99620`) | Una sesión gana; lote/inventario coherentes (18) |
| **C** | Baja daño vs baja vencimiento mismo lote (`99621`) | Exclusión mutua; lote final 4 |
| **E** | Dos salidas NP que excederían stock | Una falla; stock ≥ 0 (3) |

**Escenario D (baja daño vs salida normal sobre el mismo lote explícito):** **PENDIENTE** como caso dedicado — `sp_salida_perecedero` no recibe lote; el solapamiento práctico se observó en **B** (FEFO compite con baja por lote). No se detectó inconsistencia.

---

## 7. Seguridad — **VERIFICADO**

| Rol | Salida NP | Baja daño |
|---|---|---|
| OPERATIVO | Permitida (`SEC_operativo_salida_np`) | Rechazada (`D01`) |
| ADMINISTRADOR | Permitida | Permitida |

Validación de administrador vía `usuario.id_rol` + `rol.nombre` (no texto libre). Sin cambios de privilegios.

---

## 8. Rollback — **VERIFICADO**

- Salida con stock insuficiente: inventario sin cambio (`ROLL_salida_insuf_sin_parcial`).
- Motivo vacío en bajas: cubierto en 12.5B.3 (referencia cruzada).

---

## 9. Restauración — **VERIFICADO**

Tras pruebas concurrentes con commit:

- Eliminación de `movimiento` / `movimiento_compra` / `detalle_compra` / `compra` con `id` > máximos capturados al inicio.
- Bitácoras de bajas de prueba eliminadas.
- Inventario NP restaurado al valor inicial capturado.
- Lotes de prueba `99620`, `99621` eliminados tras borrar movimientos referenciadores.
- `RESTAURACION_completa`: `resid_mov=0`, `resid_compra=0`.

---

## 10. Incidencias

| ID | Estado |
|---|---|
| — | Sin defectos funcionales abiertos en SP |

**Notas de harness (no defecto de producción):**

- Entradas requieren par `producto–proveedor` en `catalogo` (validado en prueba).
- Fechas FEFO deben ser **posteriores** a `CURRENT_DATE` (ajuste de datos de prueba en script).

---

## 11. Conclusiones

- Los procedimientos de **entrada, salida (FEFO), y bajas** mantienen integridad en pruebas individuales, secuenciales y concurrentes acotadas.
- Controles de **administrador** en bajas permanecen efectivos; salidas siguen accesibles a usuario operativo activo.
- **Traslados** permanecen en catálogo sin regresión probada en esta fase (**PENDIENTE** smoke dedicado; comportamiento previo documentado en 12.1 / app).

**No se modificó** ningún objeto de base de datos ni código de aplicación.

---

## 12. Recomendaciones para integración Java (Fase posterior)

1. **Servicio de operaciones** con métodos `callSalida*`, `callBaja*` usando `JdbcTemplate` y resolución de `id_usuario` desde sesión (nunca desde formulario).
2. **Autorización:** `PermisoService` para salidas; rol ADMIN + motivo obligatorio para bajas (doble capa con validación ya existente en SP).
3. **Recepción:** mantener `RecepcionService` + `sp_recibir_*`; alinear catálogo proveedor–producto antes del `CALL`.
4. **Errores:** mapear mensajes `RAISE EXCEPTION` de PostgreSQL a `BusinessException` sin exponer detalles internos.
5. **Historial:** ampliar consulta de movimientos para enlazar bitácora de bajas (hoy sin FK directa).
6. **No otorgar** en UI operaciones de baja a OPERATIVO aunque exista `movimiento.INSERT` genérico en BD.

---

## 13. Archivos entregables

| Archivo |
|---|
| `docs/migrations/movimientos/_test_12_5c_integral.py` |
| `docs/migrations/movimientos/_test_12_5c_output.txt` |
| `docs/INFORME_FASE_12_5C.md` |

**Fase 12.5C COMPLETADA.** Detenido — sin integración Java (12.6) hasta autorización expresa.
