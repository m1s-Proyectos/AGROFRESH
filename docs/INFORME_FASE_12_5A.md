# Informe — Fase 12.5A Procedimientos de salida de mercadería (PostgreSQL)

**Fecha:** 2026-11-10  
**Proyecto:** AGROFRESH — `AgroFresh2026-desarrollo`  
**Referencia de diseño:** `docs/DISENO_TECNICO_SALIDAS_Y_BAJAS.md`

---

## 1. Objetivo

Crear en Supabase (esquema `inventario`) los procedimientos para **salidas normales** de mercadería, con FEFO multilote en perecederos, validación de existencias, bloqueos y registro en `movimiento`, **sin modificar Java** ni tablas existentes.

**Estado:** **IMPLEMENTADA** en base de datos.

---

## 2. Estado inicial de Supabase (VERIFICADO)

Inspección read-only (`docs/migrations/movimientos/_sp_inspect.txt`):

| Rutina | Existencia previa a 12.5A |
|---|---|
| `sp_trasladar_perecedero` / `sp_trasladar_no_perecedero` | **VERIFICADO** — patrón de referencia |
| `fn_validar_traslado`, `fn_lotes_disponibles`, `fn_consultar_stock_bodega` | **VERIFICADO** |
| `sp_salida_perecedero` / `sp_salida_no_perecedero` | **NO EXISTÍA** |

Catálogo de movimiento: tipos incluyen `SALIDA`; sin columna de motivo/observación (**VERIFICADO** 12.1).

---

## 3. Estructura real utilizada

| Tabla | Uso en salidas |
|---|---|
| `movimiento` | Uno o más registros `tipo_movimiento = 'SALIDA'` |
| `inventario` | Descuento agregado `(id_bodega, id_producto)` |
| `lote` | Descuento por lote (solo perecedero); FEFO por `fech_vencimiento`, desempate `num_lote` |
| `perecedero` / `no_perecedero` | Validación de clasificación exclusiva |
| `bodega`, `producto`, `usuario` | Existencia; usuario `activo = TRUE` |

No se crearon tablas ni columnas. No se modificaron `sp_trasladar_*` ni `sp_recibir_*`.

---

## 4. Procedimientos implementados

| Objeto | Tipo |
|---|---|
| `fn_validar_salida` | FUNCTION |
| `fn_stock_lotes_vigentes` | FUNCTION |
| `sp_salida_no_perecedero` | PROCEDURE |
| `sp_salida_perecedero` | PROCEDURE |

**Script completo auditable:** `docs/migrations/movimientos/MIG-12.5A_sp_salidas.sql`

---

## 5. Funciones auxiliares creadas

### `fn_validar_salida(p_id_bodega, p_id_producto, p_cantidad)`

- Cantidad > 0, bodega y producto existen.
- Stock vía `fn_consultar_stock_bodega` (mismo patrón que `fn_validar_traslado`).
- Mensajes en español coherentes con traslados (`La cantidad debe ser mayor que cero`, `Stock insuficiente...`).

### `fn_stock_lotes_vigentes(p_id_bodega, p_id_producto)`

- Suma `cantidad_actual` de lotes con `fech_vencimiento > CURRENT_DATE` y stock > 0 (**IMPLEMENTADA** — alineada a `fn_lotes_disponibles`).

---

## 6. Firmas de procedimientos

```sql
CALL inventario.sp_salida_no_perecedero(
  p_id_bodega integer,
  p_id_producto integer,
  p_cantidad integer,
  p_id_usuario integer,
  OUT p_id_movimiento integer
);

CALL inventario.sp_salida_perecedero(
  p_id_bodega integer,
  p_id_producto integer,
  p_cantidad integer,
  p_id_usuario integer,
  OUT p_id_movimiento integer
);
```

**Observación/referencia:** el esquema **no** tiene columna en `movimiento`; no se añadió parámetro (coherente con 12.4).

---

## 7. Validaciones

| Validación | Perecedero | No perecedero |
|---|---|---|
| Cantidad > 0 | sí | sí |
| Bodega / producto existen | sí | sí |
| Stock en `inventario` | sí | sí |
| Clasificación correcta | solo `perecedero` | solo `no_perecedero` |
| Usuario activo | sí | sí |
| Lotes vigentes (`> CURRENT_DATE`) | sí (FEFO) | n/a |
| Suma lotes vigentes ≥ cantidad | sí | n/a |
| Coherencia inventario vs lotes | sí (revalidación tras bloqueo) | n/a |
| Lotes vencidos excluidos | **IMPLEMENTADO** (`fech_vencimiento <= CURRENT_DATE` no se despacha) | n/a |

**Sin descuentos parciales:** si no puede completar la cantidad en lotes vigentes, `RAISE EXCEPTION` y rollback.

---

## 8. Implementación FEFO (`sp_salida_perecedero`)

1. Orden: `fech_vencimiento ASC`, `num_lote ASC` (**VERIFICADO** — mismo criterio que `fn_lotes_disponibles`).
2. Solo lotes con `cantidad_actual > 0` y `fech_vencimiento > CURRENT_DATE`.
3. Consumo iterativo con `FOR … FOR UPDATE` hasta cubrir `p_cantidad`.
4. Ejemplo validado en prueba (lotes 99901/99902/99903):

| Lote | Vencimiento | Inicial | Descontado | Final |
|---|---|---:|---:|---:|
| 99901 | 15/12/2026 | 10 | 10 | 0 |
| 99902 | 20/01/2027 | 20 | 15 | 5 |
| 99903 | 15/03/2027 | 30 | 0 | 30 |

Solicitud: **25** unidades.

---

## 9. Tratamiento multilote

- **Una fila en `movimiento` por cada porción** tomada de un lote (mismo `tipo_movimiento = 'SALIDA'`, misma bodega/producto/usuario, `fecha` por defecto del servidor en cada INSERT).
- **`p_id_movimiento` OUT:** devuelve el `id_movimiento` del **primer** registro creado (**IMPLEMENTADO**).
- **Reconstrucción en historial (12.3):** agrupar por proximidad de `fecha`, `id_usuario`, `id_bodega`, `id_producto` y tipo `SALIDA`, o sumar cantidades en reportes futuros; no existe tabla de cabecera/detalle (**VERIFICADO** modelo).

**Un solo** `UPDATE inventario` al final por la **cantidad total** solicitada (tras completar lotes).

---

## 10. Estrategia transaccional

- Procedimientos PL/pgSQL sin `COMMIT` interno.
- Cualquier `RAISE EXCEPTION` revierte la operación completa (lotes, inventario, movimientos).
- Orden: validar → bloquear bodega → revalidar → bloquear inventario → mutar lotes/movimientos → descontar inventario total.

---

## 11. Manejo de concurrencia

Patrón alineado a `sp_trasladar_*` (**VERIFICADO** en código fuente inspeccionado):

- `FOR UPDATE` en fila de `bodega`.
- `FOR UPDATE` en fila de `inventario`.
- `FOR UPDATE` en cada `lote` participante dentro del cursor FEFO.
- Revalidación de cantidades después del bloqueo.

**PENDIENTE:** prueba automatizada de dos sesiones concurrentes (no ejecutada por riesgo en pool compartido; diseño cubre el mismo patrón que traslados).

---

## 12. Registro de movimientos

```sql
INSERT INTO movimiento (tipo_movimiento, cantidad, id_bodega, id_usuario, id_producto, num_lote)
VALUES ('SALIDA', …, …, …, …, …);  -- num_lote NULL solo en no_perecedero (no usado aquí)
```

Perecedero: siempre `num_lote` informado. No perecedero: `num_lote` omitido (NULL), como en traslados no perecederos.

---

## 13. Seguridad y permisos

- El SP valida `id_usuario` existente y `activo = TRUE` (como `sp_trasladar_perecedero`).
- La autorización OPERATIVO/ADMINISTRADOR queda en **capa Java** (fase 12.5B); PostgreSQL no invoca `fn_usuario_tiene_permiso` dentro del SP (mismo estilo que traslados **VERIFICADO**).
- **IMPLEMENTADO:** `CREATE` ejecutado con el rol de aplicación; pruebas `CALL` exitosas sin `GRANT` adicional explícito (**no se revocaron** permisos existentes).

---

## 14. Pruebas ejecutadas

Ejecutor: `docs/migrations/movimientos/_test_12_5a_salidas.py`  
Salida: `docs/migrations/movimientos/_test_12_5a_output.txt`  
Método: transacción con `SAVEPOINT` / `ROLLBACK` final (**sin persistir** cambios en datos de negocio).

| # | Escenario del enunciado | Resultado |
|---|---|---|
| 1–3 / FEFO | Uno, dos y tres lotes (demo 99901–99903) | **OK** |
| 4 | Orden FEFO | **OK** (10 + 15 desde L001, L002) |
| 5 | Exclusión vencidos | **OK** (lógica `> CURRENT_DATE`; caso rechazo por lotes insuficientes) |
| 6 | Sin lotes vigentes suficientes | **OK** (rechazado) |
| 7 | Cantidad superior a existencias | **OK** (rechazado) |
| 8–9 | Cantidad cero / negativa | **OK** (rechazado) |
| 10–11 | Producto / bodega inválidos | **OK** (rechazado) |
| 12–13 | No perecedero suficiente / insuficiente | **OK** / **OK** |
| 14–15 | Historial y cantidades | **OK** (verificado en demo con consulta a `movimiento` y `lote` dentro del rollback) |
| 16 | Rollback | **OK** (transacción revertida) |
| 17 | Concurrencia dos salidas | **PENDIENTE** |
| 18 | Inventario no negativo | **OK** (CHECK + validación previa UPDATE) |

---

## 15. Resultados obtenidos

- Despliegue: `python docs/migrations/movimientos/_deploy_12_5a.py` → **deploy_ok**.
- Pruebas: `failed=0`.

---

## 16. Cambios realizados en Supabase (IMPLEMENTADO)

| Cambio |
|---|
| `CREATE OR REPLACE` de 4 rutinas en esquema `inventario` |
| Sin `ALTER TABLE`, sin triggers nuevos, sin modificación de rutinas existentes |

---

## 17. Confirmación — Java

**No se modificó** código en `src/main/java`, plantillas, `SecurityConfig`, Recepción, Traslados ni historial 12.3.

---

## 18. Pendientes para la Fase 12.5B

| Ítem |
|---|
| `MovimientoOperacionService` + `CALL sp_salida_*` desde Java |
| Formularios POST salida, permisos `movimiento.INSERT` |
| Mapeo de excepciones PostgreSQL → `BusinessException` |
| Prueba MockMvc / integración |
| Prueba manual de concurrencia (dos sesiones) |
| Procedimientos de **baja** (fases posteriores, no autorizadas en 12.5A) |

---

## Conclusión

La **Fase 12.5A** entrega salidas normales en PostgreSQL con **FEFO multilote**, consistencia transaccional y registro en `movimiento`, respaldada por script versionado y pruebas con rollback.

**Detenido** — sin Fase 12.5B hasta autorización expresa.
