# Informe Fase 12.6.4 — Validación integral módulo Movimientos

**Fecha:** 2026-11-10  
**Proyecto:** AGROFRESH — `AgroFresh2026-desarrollo`  
**Alcance:** Auditoría y pruebas **sin** cambios de código, BD ni operaciones persistentes de inventario.

### Leyenda de hallazgos

| Marca | Significado |
|---|---|
| **VERIFICADO** | Confirmado en código o pruebas automatizadas de esta fase. |
| **CORRECTO** | Cumple el diseño documentado en 12.6.x / 12.6.0. |
| **LIMITACIÓN** | Funciona con restricciones conocidas. |
| **PENDIENTE** | No verificable sin entorno PostgreSQL aislado con rollback. |
| **NO VERIFICABLE** | Fuera del alcance de pruebas ejecutadas (p. ej. E2E escritura). |

---

## 1. Resumen ejecutivo

El módulo Movimientos integra **consulta** (historial y detalle), **salidas y bajas** (API + formularios Thymeleaf), y **navegación unificada** hacia **recepción** y **traslados** (módulos hermanos con SP propios).

| Área | Estado |
|---|---|
| Trazabilidad vista → SP (salidas/bajas) | **CORRECTO** |
| Seguridad y CSRF | **CORRECTO** (matiz en `GET /movimientos/registrar`) |
| Formularios vs DTO | **CORRECTO** |
| FEFO / multilote salida perecedera | **LIMITACIÓN** documentada en UI y API |
| Bitácora en detalle | **LIMITACIÓN** (consulta heurística) |
| Pruebas automatizadas | **VERIFICADO** — 142 tests, 0 fallos, 1 omitido |
| E2E PostgreSQL con escritura | **NO VERIFICABLE** / **PENDIENTE** |
| Modificación PostgreSQL en esta fase | **NINGUNA** |

**Conclusión del módulo:** **APTO CON LIMITACIONES** (véase §12).

---

## 2. Mapa de trazabilidad

Inspección de código actual (2026-11-10). Procedimientos según `MovimientoOperacionRepository`, `RecepcionService`, `TrasladoService`.

| Operación | Vista (GET) | Controlador API/MVC | Servicio | Procedimiento(s) PG |
|---|---|---|---|---|
| Entrada (recepción) | `recepcion.html` `/compras` | `RecepcionController` POST `/api/compras/confirmar` | `RecepcionService` | `sp_crear_compra`, `sp_recibir_no_perecedero`, `sp_recibir_perecedero` |
| Salida no perecedera | `movimiento-salida-no-perecedero.html` | `MovimientoOperacionController` POST `/api/movimientos/salida/no-perecedero` | `MovimientoOperacionServiceImpl` → `MovimientoOperacionRepository` | `sp_salida_no_perecedero` |
| Salida perecedera (FEFO) | `movimiento-salida-perecedero.html` | POST `/api/movimientos/salida/perecedero` | Idem | `sp_salida_perecedero` |
| Baja daño NP | `movimiento-baja-dano-no-perecedero.html` | POST `/api/movimientos/baja/dano/no-perecedero` | Idem | `sp_baja_dano_no_perecedero` |
| Baja daño PER | `movimiento-baja-dano-perecedero.html` | POST `/api/movimientos/baja/dano/perecedero` | Idem | `sp_baja_dano_perecedero` |
| Baja vencimiento | `movimiento-baja-vencimiento.html` | POST `/api/movimientos/baja/vencimiento` | Idem | `sp_baja_vencimiento` |
| Traslado | `traslados.html` `/traslados` | `TrasladoOperacionController` POST `/api/traslados/confirmar` | `TrasladoService` | `sp_trasladar_no_perecedero`, `sp_trasladar_perecedero` |
| Historial / consultar | `movimientos.html` `/movimientos` | `MovimientosController` | `MovimientoServiceImpl` | SQL lectura `movimiento` (+ joins) |
| Detalle | `movimiento-detalle.html` `/movimientos/consultar/{id}` | `MovimientosController` | `MovimientoServiceImpl` → `MovimientoRepository` | SQL lectura + subconsulta `bitacora` |
| Panel operaciones | `movimientos-registrar.html` `/movimientos/registrar` | `MovimientosController` | — (enlaces) | — |
| Catálogos salida/baja | JS → REST | `MovimientoCatalogoController` | JDBC lectura | `bodega`, `producto`, `inventario`, `fn_lotes_disponibles`, `lote` |

**Componentes adicionales:** `MovimientoSalidaSolicitud`, `MovimientoBajaSolicitud`, DTO listado/detalle, `movimiento-operacion.js`, `fragments/movimientos-submenu.html`, `SessionModelAdvice` (`puedeRegistrarSalida`, `esAdministrador`).

### Contraste con informes 12.6.1–12.6.3

| Afirmación previa | Estado actual |
|---|---|
| Cinco SP integrados vía CALL + OUT NULL | **VERIFICADO** — sin cambios en repository |
| CSRF en formularios | **VERIFICADO** — meta + header en `fetch` |
| Salida PER sin elegir lote; FEFO en PG | **VERIFICADO** — `data-lote-mod="info"`; payload sin `numLote` |
| `id_usuario` solo desde sesión | **VERIFICADO** — `resolverIdUsuario(auth.getName())`; DTO sin campo usuario |
| Panel `/movimientos/registrar` | **VERIFICADO** — presente en 12.6.3 |
| Bitácora en detalle | **VERIFICADO** — `descripcionBitacora` vía LIKE heurístico |

---

## 3. Seguridad

### 3.1 Permisos HTTP (`SecurityConfig`)

| Recurso | Regla |
|---|---|
| POST salidas API + GET formularios salida | `movimiento.INSERT` |
| POST bajas API + GET formularios baja | `ROLE_ADMIN` |
| GET `/movimientos` | `movimiento.ACCEDER` + `SELECT` |
| GET `/movimientos/consultar/**` | `movimiento.SELECT` |
| GET `/api/movimientos/**` (catálogos) | `movimiento.ACCEDER` (regla genérica `/api/movimientos/**`) |
| POST `/api/compras/confirmar` | `compra.INSERT` |
| POST `/api/traslados/confirmar` | `traslado.INSERT` |

**LIMITACIÓN:** `GET /movimientos/registrar` cae en `/movimientos/**` con solo `ACCEDER`; el controlador exige además `movimiento.SELECT`. Un usuario con ACCEDER sin SELECT recibiría 403 en el controlador, no en el filtro HTTP previo. Riesgo bajo.

### 3.2 Refuerzo en servicio

| Operación | Servicio |
|---|---|
| Salidas | `PermisoService.puede(..., "movimiento", "INSERT")` |
| Bajas | `ROLE_ADMIN` en Java + `fn_usuario_es_administrador` en SP |
| Entrada | `compra.INSERT` |
| Traslado | `traslado.INSERT` |

**CORRECTO:** doble capa en bajas (HTTP + servicio + PG).

### 3.3 Identidad

**VERIFICADO:** `MovimientoOperacionServiceImpl.resolverIdUsuario` — `SELECT id_usuario FROM usuario WHERE username = ? AND activo AND NOT bloqueado`. Mismo patrón en `RecepcionService` / `TrasladoService`. Los JSON de formularios **no** incluyen `id_usuario`.

### 3.4 CSRF

**VERIFICADO:** Spring Security CSRF activo; plantillas de operación incluyen `_csrf`; `movimiento-operacion.js` y `traslados.html` envían cabecera. `MovimientoOperacionControllerTest` usa `csrf()` en POST.

### 3.5 Submenú vs backend

**VERIFICADO:** enlaces de baja condicionados a `esAdministrador`; salidas a `puedeRegistrarSalida`; ocultar enlace no evita POST directo — API protegida.

### 3.6 Pruebas seguridad ejecutadas

- `MovimientosSecurityTest` (4)
- `MovimientoFormViewsTest` (operador/admin/INSERT)
- `MovimientoOperacionControllerTest` (403 bajas, CSRF salida)
- `MovimientoOperacionServiceImplTest` (permisos, admin, motivo)

---

## 4. Validación de operaciones

### 4.1 Entradas — **CORRECTO** (reutilización)

- Vista integrada al submenú movimientos.
- SP y permisos sin cambios en esta fase.
- **NO VERIFICABLE** en esta fase: POST real a Supabase.

### 4.2 Salidas — **CORRECTO**

| Aspecto | Evidencia |
|---|---|
| DTO | `bodega`, `producto`, `cantidad` |
| Validación Java | Cantidades > 0; permiso INSERT |
| SP params | Orden en `MovimientoOperacionRepository` alineado con 12.6.1 |
| OUT | `CALL ...(?, NULL)` + `queryForObject(Integer)` |
| Errores | `P0001` → 400 JSON en controlador |

### 4.3 Bajas — **CORRECTO**

| Aspecto | Evidencia |
|---|---|
| DTO | + `motivo`; `numLote` solo PER/vencimiento |
| Motivo | Normalización máx. 500; vacío rechazado antes del CALL |
| Lotes UI | `lotes-con-stock` (daño PER), `lotes-vencidos` (vencimiento) |
| Permisos | ADMIN en HTTP y servicio |

### 4.4 Traslados — **CORRECTO** (reutilización)

- Integración submenú; SP en `TrasladoService`.
- **NO VERIFICABLE:** ejecución real PG.

---

## 5. FEFO (salida perecedera)

| Criterio | Resultado |
|---|---|
| Java/JS no elige lote para la salida | **VERIFICADO** — `loteMod === 'info'`; sin `numLote` en payload |
| Listado de lotes vigentes | Solo consulta `fn_lotes_disponibles` |
| SP aplica FEFO | **CORRECTO** por diseño 12.6.0; **NO VERIFICABLE** ejecución real |
| Varios movimientos posibles | **LIMITACIÓN** — OUT devuelve primer `id_movimiento` |
| UI | Aviso en plantilla + JS (sin enlace detalle único multilote; nota en historial) |
| Agrupación en historial | **LIMITACIÓN** — no existe campo de correlación en esquema expuesto |

---

## 6. Bitácora (bajas)

| Criterio | Resultado |
|---|---|
| SP registra bitácora | Documentado en fases 12.5B; **NO VERIFICABLE** escritura real |
| Detalle Java | Subconsulta `bitacora.descripcion LIKE '%movimiento #<id>%'` |
| UI | Muestra `descripcionBitacora` o mensaje alternativo |
| **LIMITACIÓN** | Heurística LIKE; descripción truncada (varchar 200 en docs); motivo no está en tabla `movimiento` |

---

## 7. Historial y detalle

| Criterio | Resultado |
|---|---|
| Filtros tipo, bodega, producto, fechas, paginación | **VERIFICADO** — `MovimientoServiceImpl` / `MovimientoRepository` |
| Badges SALIDA / BAJA | **VERIFICADO** — `movimientos.html` |
| Detalle por tipo (ENTRADA compra, TRASLADO, SALIDA, BAJA) | **VERIFICADO** — `movimiento-detalle.html` |
| Campos nulos | Joins LEFT; Thymeleaf con condicionales |
| Navegación post-registro | Enlace detalle en JS si un solo id (no salida PER multilote) |
| Consultar = historial | **NO APLICABLE** vista duplicada |

---

## 8. Pruebas

### Comandos ejecutados (esta fase)

```text
mvn test          → exit 0
mvn -B clean verify → BUILD SUCCESS
```

### Resumen Surefire

| Métrica | Valor |
|---|---|
| Tests run | **142** |
| Failures | **0** |
| Errors | **0** |
| Skipped | **1** (`UsuarioRepositoryPostgresTest` — requiere `-Dagrofresh.postgres.it=true`) |

### Pruebas relacionadas con Movimientos

| Clase | Tests |
|---|---|
| `MovimientoFormViewsTest` | 5 |
| `MovimientoOperacionControllerTest` | 2 |
| `MovimientosControllerTest` | 4 |
| `MovimientosSecurityTest` | 4 |
| `MovimientoOperacionServiceImplTest` | 5 |
| `MovimientoServiceImplTest` | 5 |
| `ModuleViewsTest` (rutas movimientos) | incluido en 18 |

**NO VERIFICABLE:** integración JDBC real de CALL a los cinco SP (sin H2/Supabase aislado con rollback en CI).

### Entorno PostgreSQL

- Config local: `config/application-local.example.properties` (plantilla; no inspeccionado secreto).
- Tests de repositorio JPA usan **H2 en memoria**, no Supabase.
- **PENDIENTE:** batería IT con `agrofresh.postgres.it=true` y transacciones rollback para SP de movimientos (no implementada en el proyecto).

---

## 9. PostgreSQL

En la Fase 12.6.4:

- **No** se crearon ni alteraron tablas, columnas, índices, funciones ni procedimientos.
- **No** se ejecutaron CALL de escritura contra inventario.
- **No** se modificaron datos persistentes.

---

## 10. Hallazgos consolidados

| ID | Hallazgo | Clasificación |
|---|---|---|
| H1 | Trazabilidad salidas/bajas hasta SP correcta | **VERIFICADO** / **CORRECTO** |
| H2 | Permisos INSERT / ROLE_ADMIN coherentes | **CORRECTO** |
| H3 | CSRF en operaciones movimiento | **VERIFICADO** |
| H4 | FEFO solo en PG; UI no contradice | **CORRECTO** |
| H5 | Multilote salida PER sin agrupación en historial | **LIMITACIÓN** |
| H6 | Bitácora en detalle por LIKE | **LIMITACIÓN** |
| H7 | GET `/movimientos/registrar` vs SELECT | **LIMITACIÓN** menor |
| H8 | E2E SP con rollback | **PENDIENTE** |
| H9 | Suite Maven completa verde | **VERIFICADO** |

---

## 11. Riesgos

| Riesgo | Impacto |
|---|---|
| Operador confunde `idMovimiento` salida PER como operación única | Medio — mitigado por mensajes UI |
| Bitácora no encontrada en detalle si cambia formato descripción | Bajo — mensaje alternativo |
| Sin pruebas IT de SP en CI | Medio — regresiones PG no detectadas automáticamente |
| Dependencia de Supabase en catálogos en runtime | Operativo — igual que traslados/recepción |

---

## 12. Conclusión

**Estado: APTO CON LIMITACIONES**

El módulo está **funcionalmente completo** en capas Java, seguridad, formularios y consultas, alineado con la documentación 12.6.0–12.6.3. Las operaciones críticas de **escritura contra PostgreSQL** no se validaron de extremo a extremo en esta fase por **ausencia de entorno aislado con rollback** en la suite estándar.

**No se declara APTO pleno** hasta disponer de pruebas integración SP (opcional futuro) o validación manual en entorno de pruebas dedicado.

---

## Detención

Fase 12.6.4 completada. Sin cambios de código, sin Git, sin 12.6.5. Informe: `docs/INFORME_FASE_12_6_4.md`.
