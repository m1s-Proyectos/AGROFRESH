# Informe — Fase 12.5A.1 Validación de concurrencia (salidas)

**Fecha:** 2026-11-10  
**Referencias:** `MIG-12.5A_sp_salidas.sql`, `INFORME_FASE_12_5A.md`  
**Sin cambios en Java, recepción, traslados ni procedimientos** (no se detectaron defectos).

---

## 1. Preparación de las pruebas

| Aspecto | Detalle |
|---|---|
| Entorno | Supabase `inventario` vía `config/application-local.properties` |
| Aislamiento | Stock fijado temporalmente (30 o 36); lotes de prueba `99820`/`99821`; **restauración** de inventario, lotes y borrado de `movimiento` SALIDA de prueba al finalizar cada escenario |
| Usuario de prueba | Primer `usuario` activo no bloqueado |
| Concurrencia | **Dos conexiones** `psycopg2` en hilos separados; `threading.Barrier(2)` para disparo simultáneo |
| No destructivo | No se dejaron salidas de prueba en producción (DELETE + UPDATE de restauración) |

**Herramienta:** `docs/migrations/movimientos/_test_12_5a_1_concurrency.py`  
**Evidencia bruta:** `docs/migrations/movimientos/_test_12_5a_1_output.txt`

---

## 2. Inspección de procedimientos desplegados (VERIFICADO)

Consulta `pg_get_functiondef('sp_salida_no_perecedero')` en la ejecución:

| Mecanismo | Presente |
|---|---|
| `fn_validar_salida` (pre y post bloqueo bodega) | **VERIFICADO** |
| `FOR UPDATE` sobre `bodega` | **VERIFICADO** |
| `FOR UPDATE` sobre `inventario` | **VERIFICADO** |
| `FOR UPDATE` en cursor FEFO de `lote` (`sp_salida_perecedero`) | **VERIFICADO** en script fuente |
| Sin `COMMIT` interno | **VERIFICADO** |

Orden de bloqueo **VERIFICADO** en código: bodega → inventario → lotes (perecedero).

---

## 3. Escenario A — no perecedero

| Campo | Valor |
|---|---|
| Producto / bodega | 17 / 1 |
| Stock preparado | 30 |
| Petición concurrente | 2 × 20 unidades |

| Sesión | Resultado |
|---|---|
| S1 | **Éxito** — `id_movimiento=57`, cantidad 20 |
| S2 | **Rechazo** — `Stock insuficiente. Disponible: 10, solicitado: 20` (`fn_validar_salida`) |

| Verificación | Esperado | Obtenido |
|---|---|---|
| Una éxito, una fallo | sí | **OK** |
| Stock final | 10 | **OK** |
| Movimientos nuevos | un SALIDA por 20 | **OK** `[(57, 20)]` |
| Inventario negativo | no | **OK** |
| Descuento duplicado (40) | no | **OK** |

**Nota:** S2 ejecutó la validación **después** del commit de S1; el stock visible ya era 10. El diseño también revalida tras bloquear `bodega` si ambas sesiones entran en paralelo al procedimiento.

---

## 4. Escenario B — perecedero FEFO

| Campo | Valor |
|---|---|
| Lotes | 99820: 18 uds (vence 2026-12-01); 99821: 18 uds (vence 2027-02-01) |
| Inventario preparado | 36 |
| Petición concurrente | 2 × 20 |

| Sesión | Resultado |
|---|---|
| S1 | **Éxito** — `id_movimiento=58` (primero del OUT) |
| S2 | **Rechazo** — `Disponible: 16, solicitado: 20` |

| Verificación | Obtenido |
|---|---|
| Inventario final | **16** (36 − 20) |
| Lotes finales | 99820 → **0**, 99821 → **16** (FEFO: 18 + 2) |
| Movimientos | `(58, lote 99820, 18)` + `(59, lote 99821, 2)` |
| Consumo duplicado | **No** — una sola salida efectiva de 20 uds |
| Coherencia inv vs lotes vigentes | **OK** (16 = suma lotes) |

---

## 5. Escenario C — rollback / error controlado

| Acción | Resultado |
|---|---|
| Stock preparado 30; `CALL` salida no perecedero cantidad **50** | `RaiseException` (stock insuficiente) |
| Stock tras rollback de la sesión | **30** |
| Movimientos SALIDA nuevos | **0** |

**VERIFICADO:** sin actualización parcial de inventario ni movimiento huérfano.

---

## 6. Evidencias de bloqueo y concurrencia

- Código desplegado confirma `FOR UPDATE` en bodega e inventario (**VERIFICADO** en prueba).
- Comportamiento observado: segunda sesión **no** consumió stock ya reducido por la primera (**VERIFICADO** escenarios A y B).
- Mensajes de error controlados en español, sin datos internos sensibles (**VERIFICADO**).

**PENDIENTE (opcional):** captura en vivo de `pg_locks` / `wait_event` durante espera en el mismo milisegundo; no requerida para concluir integridad.

---

## 7. Errores / defectos de procedimiento

Ninguno demostrado. **No se modificaron** `sp_salida_*` ni funciones auxiliares.

---

## 8. Conclusión sobre integridad transaccional

| Criterio | Estado |
|---|---|
| Una operación gana, la otra falla sin stock negativo | **CUMPLE** |
| FEFO bajo concurrencia | **CUMPLE** |
| Rollback total ante cantidad inválida | **CUMPLE** |
| Sin descuentos duplicados en pruebas | **CUMPLE** |
| Restauración del entorno tras prueba | **CUMPLE** |

**Fase 12.5A.1 COMPLETADA** — prueba de concurrencia pendiente de 12.5A **cerrada** (`failed=0`).

**Sin avance** a 12.5B u otras funcionalidades.

---

## 9. Confirmaciones

- Java, Recepción, Traslados: **sin cambios**.
- Procedimientos existentes de recepción/traslado: **sin cambios**.
- Supabase: solo lecturas/escrituras **temporales** de prueba con restauración documentada.
