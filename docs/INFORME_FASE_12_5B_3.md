# Informe Fase 12.5B.3 — Pruebas integrales de bajas

**Fecha:** 2026-11-10  
**Proyecto:** AGROFRESH  
**Alcance:** Solo pruebas y auditoría. **Sin** cambios en SP, funciones, tablas, permisos ni Java.

**Evidencia:** `docs/migrations/movimientos/_test_12_5b_3_output.txt` (`failed=0`, `incidencias=0`)

### Leyenda

| Marca | Significado |
|---|---|
| **VERIFICADO** | Comportamiento observado en Supabase durante la ejecución. |
| **FALLIDO** | Prueba que no cumplió criterio (ninguno en esta fase). |
| **PENDIENTE** | No ejecutado por ausencia de datos en BD (ninguno residual). |

---

## 1. Objetivo

Validar de forma integral:

- `sp_baja_dano_no_perecedero`
- `sp_baja_dano_perecedero`
- `sp_baja_vencimiento`

Incluyendo consistencia, transaccionalidad, seguridad (rol administrador) y concurrencia con dos sesiones PostgreSQL.

---

## 2. Entorno de pruebas

| Elemento | Valor observado |
|---|---|
| Conexión | `config/application-local.properties` (rol JDBC de aplicación) |
| Esquema | `inventario` |
| Script | `_test_12_5b_3_integral.py` |
| Usuario ADMIN | `id_usuario=5` (rol `ADMINISTRADOR` vía `usuario.id_rol`) |
| Usuario OPERATIVO | `id_usuario=3` |
| Lotes aislados concurrencia | `99710`, `99711` |
| Lotes aislados funcionales (savepoint) | `99701`, `99702`, `99703` |

Suite funcional: **transacción única** con `SAVEPOINT` + `ROLLBACK` final (sin persistencia).  
Concurrencia: **commit** controlado + restauración explícita de movimientos, bitácoras, inventario y lotes de prueba.

---

## 3. Procedimientos evaluados

| Procedimiento | Auxiliares usados |
|---|---|
| `sp_baja_dano_no_perecedero` | `fn_validar_salida`, `fn_validar_motivo_baja`, `fn_usuario_es_administrador` |
| `sp_baja_dano_perecedero` | Idem + lote explícito |
| `sp_baja_vencimiento` | Idem + validación `fech_vencimiento <= CURRENT_DATE` |

**No se modificó** ningún objeto de BD.

---

## 4. Casos funcionales

### A. Baja daño no perecedero — **VERIFICADO**

| Caso | Resultado |
|---|---|
| Baja válida + consistencia | OK `A01_valida` |
| Existencias insuficientes | OK rechazo |
| Cantidad 0 / negativa | OK |
| Motivo vacío | OK |
| Motivo > 500 caracteres | OK |

### B. Baja daño perecedero — **VERIFICADO**

| Caso | Resultado |
|---|---|
| Lote vigente / vencido | OK `B01`, `B02` |
| Parcial / total del lote | OK `B03`, `B04` |
| Lote inexistente | OK |
| Lote con producto incorrecto | OK `B06` |
| Insuficiente | OK |

### C. Baja vencimiento — **VERIFICADO**

| Caso | Resultado |
|---|---|
| Lote vencido / hoy (`CURRENT_DATE`) | OK `C01`, `C02` |
| Lote vigente rechazado | OK |
| Parcial / total | OK `C04`, `C05` |
| Insuficiente | OK |

---

## 5. Consistencia de datos

Tras cada operación válida en `A01` y `B01`, el script comprobó **VERIFICADO**:

1. Inventario disminuye exactamente `p_cantidad`.
2. Lote disminuye cuando aplica (perecedero).
3. Sin cantidades negativas en `inventario` / `lote`.
4. Un solo `movimiento` por id generado.
5. `tipo_movimiento` correcto (`BAJA_DAÑO` / `BAJA_VENCIMIENTO`).
6. Bitácora con fragmento del motivo y `tabla_afectada = 'movimiento'`.
7. `bitacora.id_usuario` = administrador ejecutor.
8. `fecha_hora` dentro de tolerancia (±120 s).
9. Sin duplicados de movimiento por id.
10. Sin registros parciales en escenarios de rechazo temprano (motivo vacío).

---

## 6. Seguridad

| Prueba | Resultado |
|---|---|
| ADMINISTRADOR ejecuta baja | **VERIFICADO** OK |
| OPERATIVO | **VERIFICADO** rechazo (`fn_usuario_es_administrador`) |
| `id_usuario` inexistente | **VERIFICADO** rechazo |
| Usuario inactivo (simulado con savepoint) | **VERIFICADO** rechazo |
| Usuario bloqueado (simulado con savepoint) | **VERIFICADO** rechazo |

La validación de rol consulta **`usuario.id_rol` → `rol.nombre`**, no un texto de rol enviado por el cliente. No se alteraron privilegios globales.

---

## 7. Rollback

| Escenario | Resultado |
|---|---|
| Motivo vacío en baja daño perecedero (fallo antes de commits efectivos en savepoint) | **VERIFICADO** inventario y lote sin cambios (`ROLL_sin_parciales_motivo_vacio`) |

Atomicidad global de cada `CALL` confirmada por comportamiento de rechazo sin descuentos parciales.

---

## 8. Concurrencia (dos sesiones)

| Escenario | Observado | Criterio |
|---|---|---|
| **A** — dos bajas NP sobre stock 25 (15+15) | Una sesión OK, otra `Stock insuficiente`; stock final **10**; **1** movimiento nuevo | **VERIFICADO** |
| **B** — dos bajas daño mismo lote (16, toman 10) | Una gana; lote e inventario **6** | **VERIFICADO** |
| **C** — daño vs vencimiento mismo lote vencido (12, toman 8) | Una gana (`S2` vencimiento); otra falla en revalidación; lote **4** | **VERIFICADO** |
| **D** — dos retiros de 8 con stock 12 | Máximo una éxito; stock **4** ≥ 0 | **VERIFICADO** |

Bloqueos `FOR UPDATE` impidieron doble descuento y cantidades negativas en los escenarios ejecutados.

---

## 9. Resultados individuales

Resumen en `_test_12_5b_3_output.txt`: **55 líneas de OK**, `failed=0`.

---

## 10. Incidencias encontradas

| ID | Severidad | Descripción | Acción en 12.5B.3 |
|---|---|---|---|
| — | — | Ninguna incidencia funcional abierta | — |

**Nota de harness (no defecto de SP):** la restauración requiere borrar `movimiento` antes que `lote` por FK `fk_movimiento_afecta_lote`; el script fue ajustado solo en el archivo de prueba.

---

## 11. Evidencia de restauración

Tras concurrencia:

- Movimientos de prueba eliminados (`movs_conc_eliminados=4`).
- `resid_mov=0`, `resid_bit=0`.
- Inventario no perecedero restaurado a `cantidad_actual=104` (valor capturado al inicio).
- Lotes `99710`, `99711` eliminados tras quitar movimientos referenciadores.
- Bloque `finally` del script repite limpieza de seguridad.

**VERIFICADO:** sin residuos de bitácora/movimiento de la suite concurrente.

---

## 12. Limitaciones de la fase

- No se repitió prueba de concurrencia triple simultánea.
- Usuario bloqueado probado por **simulación** en savepoint (no había fila bloqueada permanente en BD).
- Rollback tras fallo **posterior** a `INSERT movimiento` no se forzó sin alterar esquema (garantía estructural PL/pgSQL).

---

## 13. Conclusión

Los tres procedimientos de baja **superan** la batería integral 12.5B.3: validaciones funcionales, consistencia movimiento/bitácora, exclusión de OPERATIVO/inactivos, rollback ante error temprano y escenarios concurrentes sin inconsistencias detectadas.

**No se realizaron modificaciones** a procedimientos, funciones, tablas, permisos ni código Java.

**Fase 12.5B.3 COMPLETADA.** Detenido — esperar autorización expresa para **Fase 12.5C** (integración Java).

---

## 14. Archivos

| Archivo |
|---|
| `docs/migrations/movimientos/_test_12_5b_3_integral.py` |
| `docs/migrations/movimientos/_test_12_5b_3_output.txt` |
| `docs/INFORME_FASE_12_5B_3.md` |
