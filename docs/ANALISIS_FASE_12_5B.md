# Análisis y diseño — Bajas por daño y vencimiento (Fase 12.5B.0)

**Fecha:** 2026-11-10  
**Proyecto:** AGROFRESH — `AgroFresh2026-desarrollo`  
**Alcance:** Auditoría **solo lectura** + diseño. **Sin** implementación de SP, sin cambios en BD/Java.

**Evidencia de consultas:** `docs/migrations/movimientos/_audit_12_5b_output.txt` (generado por `_audit_12_5b_readonly.py`).

### Leyenda

| Marca | Significado |
|---|---|
| **VERIFICADO** | Confirmado en Supabase o código desplegado inspeccionado. |
| **PROPUESTA** | Diseño para fase siguiente; no implementado. |
| **PENDIENTE** | Requiere decisión o autorización. |
| **NO EXISTE** | Ausente en el ámbito auditado. |

---

## 1. Objetivo y alcance

Determinar cómo implementar posteriormente:

1. **Baja por daño** (`BAJA_DAÑO`).
2. **Baja por vencimiento** (`BAJA_VENCIMIENTO`).

Contexto: salidas normales (`sp_salida_*`, FEFO, concurrencia) **verificadas** en 12.5A / 12.5A.1.

**Fuera de alcance:** crear SP, migraciones, Java, UI, cambios de permisos.

---

## 2. Tablas y estructuras inspeccionadas

**VERIFICADO** — esquema `inventario`, sesión `inventario_dev3`, `search_path=inventario, public`.

| Tabla | Inspeccionada |
|---|---|
| `inventario` | Sí |
| `lote` | Sí |
| `movimiento` | Sí |
| `movimiento_bodega` / `movimiento_compra` | Sí (no aplican a bajas) |
| `bitacora` | Sí |
| `producto`, `perecedero`, `no_perecedero` | Sí |
| `bodega`, `usuario`, `rol`, `permiso`, `rol_permiso` | Sí |

---

## 3. Columnas relevantes

### 3.1 `inventario` (VERIFICADO)

| Columna | Tipo | Notas |
|---|---|---|
| `id_bodega`, `id_producto` | int, PK compuesta | Existencia agregada |
| `cantidad_actual` | int NOT NULL DEFAULT 0 | CHECK `>= 0` |
| `stock_minimo` | int NOT NULL DEFAULT 0 | |

### 3.2 `lote` (VERIFICADO)

| Columna | Tipo | Notas |
|---|---|---|
| PK | `(num_lote, id_bodega, id_producto)` | |
| `fech_vencimiento` | date NOT NULL | Criterio vencimiento: `<= CURRENT_DATE` (app Lotes) |
| `cantidad_actual` | int NOT NULL DEFAULT 0 | CHECK `>= 0` |
| FK `id_producto` | → `perecedero` | **Solo perecederos** tienen lote |

### 3.3 `movimiento` (VERIFICADO)

| Columna | Tipo | Notas |
|---|---|---|
| `id_movimiento` | identity | |
| `fecha` | timestamptz DEFAULT now() | |
| `tipo_movimiento` | varchar(50) | CHECK: 5 valores incl. `BAJA_DAÑO`, `BAJA_VENCIMIENTO` |
| `cantidad` | int | CHECK `> 0` |
| `id_bodega`, `id_usuario` | int NOT NULL | |
| `id_producto`, `num_lote` | int nullable | FK lote compuesta si `num_lote` informado |

**NO EXISTE** columna motivo, observación ni referencia a bitácora.

### 3.4 `bitacora` (VERIFICADO)

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id_bitacora` | int | NO | identity |
| `id_usuario` | int | NO | FK → `usuario` |
| `fecha_hora` | timestamptz | NO | `CURRENT_TIMESTAMP` |
| `tipo_accion` | varchar | NO | (sin CHECK de dominio inspeccionado) |
| `tabla_afectada` | varchar | NO | ej. `movimiento_bodega`, `producto` |
| `descripcion` | varchar | NO | texto libre (muestras de traslado/producto) |

**NO EXISTE** `id_movimiento` ni FK a `movimiento`.  
**VERIFICADO:** 15 filas en bitácora; muestras recientes son `TRASLADO` / `INSERT` producto, **sin** entradas `BAJA_*` en la muestra.

### 3.5 Datos actuales en `movimiento` (VERIFICADO — conteos agregados)

| `tipo_movimiento` | n |
|---|---:|
| ENTRADA | 17 |
| SALIDA | 8 |
| TRASLADO | 9 |
| BAJA_DAÑO | 4 |
| BAJA_VENCIMIENTO | **0** (ausente en agregado) |

Existe historial `BAJA_DAÑO` sin SP publicado en catálogo → origen **PENDIENTE** (carga manual, script previo o entorno distinto).

---

## 4. Relaciones entre tablas

```text
producto ←─ perecedero / no_perecedero
inventario (bodega, producto) ── cantidad agregada
lote (num_lote, bodega, producto) ── solo perecedero
movimiento ── FK usuario, bodega, producto
movimiento ── FK opcional a lote (triple)
bitacora ── FK solo usuario (sin vínculo formal a movimiento)
```

---

## 5. Análisis de `movimiento`

- Los tipos de baja **ya están permitidos** por CHECK (**VERIFICADO**).
- Registro de baja = **INSERT** estándar; misma forma que salidas de 12.5A (sin tablas hijas).
- **Motivo en `movimiento`:** **NO EXISTE** → persistencia del motivo **debe** ir a `bitacora` u omitirse (**PROPUESTA:** bitácora obligatoria para bajas administrativas).

---

## 6. Análisis de `bitacora`

| Pregunta | Respuesta |
|---|---|
| ¿Registra motivo? | **Sí** — campo `descripcion` (varchar, NOT NULL). |
| ¿Quién realizó? | `id_usuario` (administrador que ejecuta el SP). |
| ¿Fecha/hora? | `fecha_hora` automática. |
| ¿Enlace a movimiento? | **NO EXISTE** FK; **PROPUESTA:** incluir `id_movimiento` en `descripcion` o `tabla_afectada='movimiento'`. |
| ¿Integración en Java? | **NO EXISTE** en app inspeccionada; trigger `fn_auditar_traslado` escribe bitácora solo en traslados (**VERIFICADO** rutina existe). |

Límite de longitud `descripcion`: tipo `varchar` sin longitud en catálogo `information_schema` → inspeccionar longitud real **PENDIENTE**; diseño 12.4 asumía **200** caracteres (confirmar con `character_maximum_length` en siguiente paso o migración).

---

## 7. Procedimientos existentes reutilizables

| Rutina | Reutilización para bajas |
|---|---|
| `fn_validar_salida` | **PROPUESTA** — base para cantidad/bodega/producto/stock (extender o `fn_validar_baja`). |
| `fn_consultar_stock_bodega` | **VERIFICADO** — usada en validadores. |
| `fn_stock_lotes_vigentes` | Solo salidas (excluye vencidos); **no** usar tal cual en baja por daño sobre lote vencido. |
| `fn_usuario_tiene_permiso` | Existe; **no** distingue bajas de salidas con mismo `movimiento.INSERT`. |
| `sp_salida_perecedero` / `no_perecedero` | Patrón bloqueo: bodega → inventario → lote; sin FEFO automático en bajas (**PROPUESTA**). |
| `sp_trasladar_perecedero` | Patrón lote explícito + `FOR UPDATE` en lote. |

**NO EXISTE** `sp_baja_*` en catálogo (**VERIFICADO** — 23 rutinas listadas, sin baja).

Todas las rutinas inspeccionadas: **SECURITY INVOKER** (**VERIFICADO**).

---

## 8. Análisis de concurrencia

**VERIFICADO** (12.5A.1 + código `sp_salida_*`):

- `FOR UPDATE` en `bodega` e `inventario`.
- Perecederos: bloqueo por lote en cursor o fila explícita.

**PROPUESTA** para bajas: mismo orden (**bodega → inventario → lote**), doble llamada a validador tras bloquear bodega (como salidas).  
Sin FEFO multilote en bajas: una operación = un lote identificado (o solo inventario en no perecedero).

---

## 9. Análisis de seguridad y privilegios

### 9.1 Aplicación (`rol_permiso`) — **VERIFICADO**

| Rol | `movimiento` | `inventario` / `lote` |
|---|---|---|
| ADMINISTRADOR | ACCEDER, SELECT, INSERT, UPDATE, DELETE | CRUD + ACCEDER |
| OPERATIVO | ACCEDER, SELECT, **INSERT** | SELECT + ACCEDER |

**OPERATIVO tiene `movimiento.INSERT`** — puede registrar tipos de movimiento si logra escribir en tablas o ejecutar un futuro SP.

### 9.2 PostgreSQL

| Tema | Hallazgo |
|---|---|
| RLS | **NO EXISTE** — `pg_policies` vacío para `inventario`. |
| `EXECUTE` en `sp_salida_*` | **VERIFICADO** — `PUBLIC` + rol de app. |
| `SECURITY DEFINER` | **NO** en rutinas de inventario inspeccionadas. |

### 9.3 Impedir bajas a OPERATIVO en PostgreSQL sin revocar privilegios

| Mecanismo | Viabilidad |
|---|---|
| `REVOKE EXECUTE` solo en futuros `sp_baja_*` | **PROPUESTA** — salidas siguen en `sp_salida_*` con EXECUTE actual. |
| Validación **dentro del SP** por rol | **PROPUESTA** — `JOIN usuario.id_rol → rol.nombre = 'ADMINISTRADOR'` + usuario activo. |
| `fn_usuario_tiene_permiso` con acción nueva | **PENDIENTE** — implicaría nuevo permiso en tabla `permiso` (cambio de esquema de datos). |

**Conclusión:** la exclusión ADMIN **debe** validarse en el **procedimiento** (y en Java 12.5C). No basta un parámetro texto “ADMINISTRADOR”.

Identificación del ejecutor: **`p_id_usuario`** resuelto en servidor (patrón traslados/salidas), no confiar en el cliente.

---

## 10. Diseño propuesto — Baja por daño (`sp_baja_dano_*`)

### 10.1 Alcance funcional (PROPUESTA)

| Aspecto | Diseño |
|---|---|
| Rol | Solo ADMINISTRADOR (validación en SP). |
| Perecedero | Parámetros: `p_id_bodega`, `p_id_producto`, `p_num_lote`, `p_cantidad`, `p_id_usuario`, `p_motivo varchar`, `OUT p_id_movimiento`. |
| No perecedero | Sin `num_lote`; solo `inventario`. |
| Lote | Vigente **o** vencido permitido (solo `cantidad_actual > 0`). |
| Vencimiento | **No** exigir vencido (diferencia con baja vencimiento). |

### 10.2 Flujo transaccional (PROPUESTA)

1. Validar administrador + usuario activo.
2. `fn_validar_salida` o variante (cantidad, existencias).
3. `FOR UPDATE` bodega → revalidar.
4. Clasificación perecedero / no perecedero.
5. `FOR UPDATE` inventario; si perecedero: `FOR UPDATE` lote por triple.
6. Validar `lote.cantidad_actual >= p_cantidad`.
7. `UPDATE lote` (si aplica) + `UPDATE inventario`.
8. `INSERT movimiento` tipo `BAJA_DAÑO`.
9. `INSERT bitacora` (`tipo_accion='BAJA_DAÑO'`, `tabla_afectada='movimiento'`, `descripcion` con motivo + `id_movimiento`).

Nombres sugeridos: `sp_baja_dano_perecedero`, `sp_baja_dano_no_perecedero` (simetría con salidas).

---

## 11. Diseño propuesto — Baja por vencimiento (`sp_baja_vencimiento`)

### 11.1 Alcance (PROPUESTA)

| Aspecto | Diseño |
|---|---|
| Rol | Solo ADMINISTRADOR. |
| Producto | **Solo perecedero** (lote obligatorio). |
| Vencimiento | **VERIFICADO** criterio UI: `l.fech_vencimiento <= CURRENT_DATE`. |
| Cantidad | `<= lote.cantidad_actual` y coherencia con `inventario`. |
| FEFO automático | **NO** — el administrador elige el **lote vencido** explícito. |

### 11.2 Flujo (PROPUESTA)

Igual a baja daño perecedero, con:

- Rechazo si `fech_vencimiento > CURRENT_DATE`.
- `tipo_movimiento = 'BAJA_VENCIMIENTO'`.
- Bitácora `tipo_accion='BAJA_VENCIMIENTO'`.

Firma sugerida: `sp_baja_vencimiento(p_id_bodega, p_id_producto, p_num_lote, p_cantidad, p_id_usuario, p_motivo, OUT p_id_movimiento)`.

**Función auxiliar PROPUESTA:** `fn_validar_baja_admin(p_id_usuario)` → bool / EXCEPTION.

---

## 12. Estrategia de registro de motivos

| Capa | Mecanismo |
|---|---|
| `movimiento` | **NO EXISTE** campo — no almacenar motivo aquí. |
| `bitacora` | **PROPUESTA obligatoria** — `p_motivo` NOT NULL tras trim; rechazar vacío. |
| Formato `descripcion` | Ej.: `Baja daño movimiento #123: {motivo}` (truncar si supera límite varchar). |
| Historial 12.3 | Sigue mostrando movimiento; motivo en consulta separada de bitácora **PENDIENTE** (Java futuro). |

Si `p_motivo` opcional: **PENDIENTE** decisión de negocio (recomendación: obligatorio para auditoría).

---

## 13. Casos de prueba propuestos (no ejecutados en 12.5B.0)

| # | Caso | Procedimiento |
|---|---|---|
| 1 | Baja válida daño / vencimiento | Ambos |
| 2 | Existencias insuficientes | Ambos |
| 3–4 | Cantidad 0 / negativa | Ambos |
| 5 | Lote inexistente | Daño / vencimiento perecedero |
| 6–7 | Producto / bodega inexistente | Ambos |
| 8 | Lote vencido en baja **daño** | Permitido |
| 9 | Lote vigente en baja **daño** | Permitido |
| 10 | Lote vigente en baja **vencimiento** | Rechazo |
| 11 | Vencimiento = `CURRENT_DATE` | Vencimiento permitido |
| 12 | OPERATIVO ejecuta SP | Rechazo en SP |
| 13 | ADMIN ejecuta SP | Éxito |
| 14 | Fallo tras `UPDATE` inventario | Rollback sin movimiento |
| 15 | Fallo al insertar bitácora | Rollback total (movimiento + stock) |
| 16 | Coherencia inventario/lotes post-op | Ambos |

---

## 14. Riesgos y decisiones pendientes

| Riesgo / decisión | Estado |
|---|---|
| OPERATIVO con `INSERT` en BD puede invocar futuros SP si se otorga EXECUTE a PUBLIC | **PROPUESTA** validación ADMIN en SP; valorar REVOKE a PUBLIC solo en `sp_baja_*`. |
| Sin FK bitácora–movimiento | Trazabilidad por texto; **PENDIENTE** índice/consulta por patrón. |
| `BAJA_DAÑO` históricas sin bitácora | No bloquea diseño. |
| Longitud exacta `descripcion` | **PENDIENTE** medición. |
| Baja daño en no perecedero | **PROPUESTA** soportada (solo inventario). |
| Motivo obligatorio | **PENDIENTE** confirmación usuario. |
| Una SP genérica vs. tres SP | **PROPUESTA** tres SP (claridad y permisos EXECUTE granulares). |

---

## 15. Conclusión y recomendación (Fase 12.5B.1)

1. **Implementar** en Supabase (tras autorización): `fn_validar_baja_admin`, `sp_baja_dano_perecedero`, `sp_baja_dano_no_perecedero`, `sp_baja_vencimiento`.
2. **Reutilizar** patrones de bloqueo y validación de `sp_salida_*` (sin FEFO multilote).
3. **Persistir motivo** en `bitacora` en la misma transacción que el movimiento.
4. **Exigir rol ADMINISTRADOR** dentro del SP; complementar con Spring Security en fase Java.
5. **Pruebas** análogas a 12.5A.1 (rollback / restauración) antes de integración Java.

---

## Confirmaciones finales

| Compromiso | Cumplido |
|---|---|
| Solo lectura en Supabase (agregados y metadatos) | **Sí** |
| Sin INSERT/UPDATE/DELETE de negocio en auditoría | **Sí** |
| Sin cambios Java / SP / tablas | **Sí** |
| Informe creado | `docs/ANALISIS_FASE_12_5B.md` |

**Fase 12.5B.0 COMPLETADA.**  
**Detenido** — sin implementación de SP hasta autorización expresa.
