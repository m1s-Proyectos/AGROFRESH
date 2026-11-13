# Migraciones — Módulo movimientos

| Archivo | Fase | Descripción |
|---|---|---|
| `MIG-12.5A_sp_salidas.sql` | 12.5A | Salidas normales (`sp_salida_*`, funciones auxiliares) |
| `_deploy_12_5a.py` | — | Aplica `MIG-12.5A_sp_salidas.sql` (usa `config/application-local.properties`) |
| `_test_12_5a_salidas.py` | — | Pruebas en transacción con `ROLLBACK` |
| `_test_12_5a_1_concurrency.py` | 12.5A.1 | Dos sesiones concurrentes + restauración |
| `_inspect_sp.py` | — | Inspección read-only de procedimientos existentes |

| `MIG-12.5B.1_sp_baja_dano.sql` | 12.5B.1 | Bajas por daño (`sp_baja_dano_*`, funciones auxiliares) |
| `_deploy_12_5b_1.py` | — | Aplica `MIG-12.5B.1_sp_baja_dano.sql` |
| `_test_12_5b_1_baja_dano.py` | 12.5B.1 | Pruebas con savepoints / sin commit persistente |
| `_audit_12_5b_readonly.py` | 12.5B.0 | Auditoría solo lectura |

| `MIG-12.5B.2_sp_baja_vencimiento.sql` | 12.5B.2 | Baja por vencimiento (`sp_baja_vencimiento`) |
| `_deploy_12_5b_2.py` | — | Aplica `MIG-12.5B.2_sp_baja_vencimiento.sql` |
| `_test_12_5b_2_baja_vencimiento.py` | 12.5B.2 | Pruebas aisladas con savepoints |
| `_test_12_5b_3_integral.py` | 12.5B.3 | Batería integral (funcional, seguridad, concurrencia) |
| `_test_12_5c_integral.py` | 12.5C | Validación global movimientos/inventario |

Contratos Java–PG (auditoría): `docs/ANALISIS_FASE_12_6_0.md`.
