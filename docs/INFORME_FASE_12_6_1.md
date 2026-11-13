# Informe Fase 12.6.1 — Integración backend Java (salidas y bajas)

**Fecha:** 2026-11-10  
**Proyecto:** AGROFRESH — `AgroFresh2026-desarrollo`  
**Alcance:** Integración de los cinco procedimientos almacenados de salida/baja vía Spring MVC + JDBC. **Sin** cambios en PostgreSQL ni en vistas Thymeleaf.

### Leyenda

| Marca | Significado |
|---|---|
| **VERIFICADO** | Confirmado en código, pruebas Maven o documentación 12.6.0 / migraciones 12.5. |
| **IMPLEMENTADO** | Entregado en esta fase. |
| **PENDIENTE** | Fuera de alcance 12.6.1 o sin entorno aislado. |
| **RIESGO** | Comportamiento a vigilar en 12.6.2 o producción. |

---

## 1. Resumen de implementación — **IMPLEMENTADO**

Se añadió una capa dedicada para **registrar salidas** (no perecedero / perecedero) y **bajas** (daño NP/PER, vencimiento) mediante `JdbcTemplate` y `CALL` a los SP existentes. La lógica de inventario, FEFO, bitácora y validación administrativa permanece en PostgreSQL.

Expuesto API REST JSON bajo `/api/movimientos/...` para consumo futuro desde formularios (Fase 12.6.2). El historial existente (`MovimientosController`) no se alteró.

---

## 2. Archivos creados — **IMPLEMENTADO**

| Archivo | Rol |
|---|---|
| `dto/MovimientoSalidaSolicitud.java` | Entrada salidas (bodega, producto, cantidad) |
| `dto/MovimientoBajaSolicitud.java` | Entrada bajas (+ motivo, numLote) |
| `repository/MovimientoOperacionRepository.java` | `CALL sp_*` con `queryForObject` |
| `service/MovimientoOperacionService.java` | Contrato de operaciones |
| `service/impl/MovimientoOperacionServiceImpl.java` | Validación, identidad, permisos, transacción |
| `controller/MovimientoOperacionController.java` | REST POST + manejo `P0001` |
| `test/.../MovimientoOperacionServiceImplTest.java` | Pruebas unitarias de servicio |
| `test/.../MovimientoOperacionControllerTest.java` | Seguridad API + respuesta JSON |
| `docs/INFORME_FASE_12_6_1.md` | Este informe |

---

## 3. Archivos modificados — **IMPLEMENTADO**

| Archivo | Cambio |
|---|---|
| `security/SecurityConfig.java` | Matchers POST salida (`movimiento.INSERT`); POST bajas (`ROLE_ADMIN`) |

**No modificados:** plantillas `movimientos.html`, `movimiento-detalle.html`, ni ningún SQL/SP en Supabase.

---

## 4. Procedimientos integrados — **VERIFICADO / IMPLEMENTADO**

| Procedimiento | Integración Java |
|---|---|
| `sp_salida_no_perecedero` | **IMPLEMENTADO** |
| `sp_salida_perecedero` | **IMPLEMENTADO** |
| `sp_baja_dano_no_perecedero` | **IMPLEMENTADO** |
| `sp_baja_dano_perecedero` | **IMPLEMENTADO** |
| `sp_baja_vencimiento` | **IMPLEMENTADO** |

Contrato alineado con `docs/ANALISIS_FASE_12_6_0.md` y catálogo documentado en fase 12.5C (sin re-desplegar ni alterar BD en 12.6.1).

---

## 5. Correspondencia parámetros SQL ↔ Java — **VERIFICADO**

Orden de placeholders en `MovimientoOperacionRepository` (solo IN; el OUT se pasa como `NULL` en el `CALL`, patrón `sp_crear_compra`):

| Procedimiento | Orden IN (Java → SP) |
|---|---|
| `sp_salida_no_perecedero` | `idBodega`, `idProducto`, `cantidad`, `idUsuario` |
| `sp_salida_perecedero` | idem |
| `sp_baja_dano_no_perecedero` | `idBodega`, `idProducto`, `cantidad`, `idUsuario`, `motivo` |
| `sp_baja_dano_perecedero` | `idBodega`, `idProducto`, `numLote`, `cantidad`, `idUsuario`, `motivo` |
| `sp_baja_vencimiento` | idem que daño perecedero |

El DTO **no** incluye `id_usuario` (prohibido por requisito de identidad).

---

## 6. Manejo de parámetros OUT (`p_id_movimiento`) — **IMPLEMENTADO**

**VERIFICADO** patrón existente en `RecepcionService`:

```sql
CALL sp_...(?, ..., NULL)
```

Spring `JdbcTemplate.queryForObject(..., Integer.class)` devuelve el valor del parámetro OUT/INOUT.  
`MovimientoOperacionServiceImpl.requerirMovimiento` exige `id > 0`.

**PENDIENTE:** prueba de integración real contra PostgreSQL para confirmar el mismo comportamiento del driver en los cinco SP (no ejecutada contra Supabase por restricción de no mutar inventario real).

---

## 7. Estrategia transaccional — **IMPLEMENTADO**

- `@Transactional(rollbackFor = Exception.class)` en cada método público de `MovimientoOperacionServiceImpl`.
- Un único `CALL` por operación; rollback delegado a Spring si el SP aborta o lanza excepción JDBC.
- Sin escrituras JPA sobre tablas de existencias.

---

## 8. Obtención de identidad del usuario — **VERIFICADO / IMPLEMENTADO**

Patrón **RecepcionService / TrasladoService**:

```text
SELECT id_usuario FROM usuario
WHERE username = :auth.getName() AND activo = TRUE AND bloqueado = FALSE
```

Rechazo con `ResponseStatusException` 403 si no hay fila. Nunca se lee `id_usuario` del cuerpo JSON.

---

## 9. Permisos implementados — **IMPLEMENTADO**

| Operación | Spring Security | Servicio |
|---|---|---|
| Salidas (ambas) | POST + `permisos.puede(..., "movimiento", "INSERT")` | Misma comprobación |
| Bajas (las tres) | POST + autoridad `ROLE_ADMIN` | `exigirAdministrador` |
| Acceso API base | `movimiento.ACCEDER` en `/api/movimientos/**` | — |

**VERIFICADO** en PostgreSQL: bajas invocan `fn_usuario_es_administrador` dentro del SP.

**RIESGO — criterio dual:** un usuario con `ROLE_ADMIN` en sesión pero sin rol `ADMINISTRADOR` en BD fallará en el SP aunque pase el filtro HTTP. Un operador con `movimiento.INSERT` no puede registrar bajas aunque tenga otros permisos amplios. **No se debilitó** ninguna capa.

---

## 10. Manejo de SQLSTATE — **IMPLEMENTADO**

| Origen | Tratamiento |
|---|---|
| Validación Java (`IllegalArgumentException`) | HTTP 400, mensaje de negocio en JSON |
| `ResponseStatusException` (403 permisos) | Código HTTP del exception |
| PostgreSQL `P0001` (`RAISE` negocio) | HTTP 400, texto de `getMessage()` del servidor (patrón `RecepcionController` / `TrasladoOperacionController`) |
| Otros `DataAccessException` | HTTP 500, mensaje genérico sin SQL ni credenciales |

No se añadió manejador global nuevo; se mantiene coherencia con APIs de compras/traslados.

**PENDIENTE:** prueba automatizada que simule `PSQLException` con `P0001` en el controlador (mock de servicio o test de integración).

---

## 11. Pruebas ejecutadas y resultados — **VERIFICADO**

| Suite | Resultado |
|---|---|
| `mvn test` (proyecto completo) | **OK** (exit 0) |
| `MovimientoOperacionServiceImplTest` | 5 tests: permisos, admin bajas, validación motivo |
| `MovimientoOperacionControllerTest` | 2 tests: operador sin bajas; salida con INSERT + CSRF |
| Resto de regresión (movimientos, seguridad, módulos) | **OK** |

---

## 12. Pruebas pendientes — **PENDIENTE**

1. Integración JDBC real contra entorno PostgreSQL **aislado** (cinco SP, rollback, `p_id_movimiento`, stock insuficiente, FEFO).
2. Test explícito de propagación `P0001` hasta JSON del controlador.
3. Concurrencia / bloqueos (solo documentado en fases PG 12.5A.1).

**No se ejecutaron** escrituras contra Supabase en esta fase.

---

## 13. Riesgos encontrados — **RIESGO**

1. **CSRF en POST:** las peticiones desde navegador deben incluir token CSRF (igual que compras/traslados). Las pruebas MockMvc lo confirman.
2. **API vs MVC:** rutas actuales son REST; 12.6.2 debe decidir si los formularios llaman a estos endpoints o se añaden controladores MVC con el mismo servicio.
3. **Salida perecedero:** el SP puede generar **varios** movimientos; OUT devuelve el primero. La UI debe comunicarlo si aplica.
4. **Doble autorización en bajas:** discrepancia posible entre `ROLE_ADMIN` (Java) y rol `ADMINISTRADOR` (BD) — comportamiento intencional defensivo.

---

## 14. Estado de preparación para Fase 12.6.2 — **IMPLEMENTADO**

| Elemento | Estado |
|---|---|
| Servicio + repositorio SP | Listo |
| DTO entrada | Listo |
| Seguridad HTTP | Listo |
| API JSON + mensajes usuario | Listo |
| Vistas / formularios | **PENDIENTE** (12.6.2) |
| Catálogos UI (bodega, producto, lotes) | Reutilizar patrones traslados/recepción |

### Recomendación alcance 12.6.2

- Formularios Thymeleaf (o páginas en módulo movimientos) que consuman los cinco endpoints con CSRF.
- Catálogo de lotes vía `fn_lotes_disponibles` / servicios existentes donde aplique.
- Enlaces desde historial al detalle ya implementado.
- Sin duplicar lógica de stock en Java.

---

## Cierre de fase

| Criterio | Cumplimiento |
|---|---|
| Cinco SP integrados | **Sí** |
| PostgreSQL sin cambios | **Sí** |
| Vistas sin cambios | **Sí** |
| Git / push | **No ejecutado** (por restricción) |

**DETENIDO** en 12.6.1 según instrucción. No avanzar a 12.6.2 sin autorización explícita.
