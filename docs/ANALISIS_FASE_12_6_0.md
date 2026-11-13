# Análisis Fase 12.6.0 — Contratos de integración Java ↔ PostgreSQL (Módulo Movimientos)

**Fecha:** 2026-11-10  
**Proyecto:** AGROFRESH — `AgroFresh2026-desarrollo`  
**Alcance:** Auditoría y documentación **exclusivamente**. Sin cambios en código, BD, configuración ni Git.

### Leyenda

| Marca | Significado |
|---|---|
| **VERIFICADO** | Confirmado en archivos del repositorio o catálogo PostgreSQL (evidencia 12.5C). |
| **PROPUESTA** | Diseño para fase 12.6.1; no implementado. |
| **PENDIENTE** | Requiere decisión en implementación. |
| **NO EXISTE** | Ausente en el código o catálogo actual. |

**Evidencia BD (solo lectura indirecta):** `docs/migrations/movimientos/_test_12_5c_output.txt`, migraciones `MIG-12.5A`, `MIG-12.5B.1`, `MIG-12.5B.2`, `docs/INFORME_FASE_12_5C.md`.  
**No se ejecutaron** migraciones ni modificaciones en esta fase.

---

## 1. Objetivo

Identificar cómo el módulo **Movimientos** (historial y futuras operaciones de salida/baja) se integrará con los procedimientos almacenados existentes: parámetros, resultados, seguridad, errores y vistas, sin alterar la fuente de verdad en Supabase.

---

## 2. Inspección del proyecto Java

### 2.1 Stack y dependencias — **VERIFICADO**

| Elemento | Valor |
|---|---|
| Java | 21 (`pom.xml` `java.version`) |
| Spring Boot | 3.5.6 |
| Web | `spring-boot-starter-web` |
| Seguridad | `spring-boot-starter-security` |
| Vistas | Thymeleaf + Bootstrap 5.3.3 (WebJars) |
| Persistencia | `spring-boot-starter-data-jpa` + driver PostgreSQL |
| Acceso datos movimientos | **JdbcTemplate** (no entidades JPA para `movimiento`) |

### 2.2 Configuración — **VERIFICADO**

| Archivo / propiedad | Hallazgo |
|---|---|
| `application.properties` | `ddl-auto=validate`, `open-in-view=false` |
| Conexión Supabase | Perfil local vía `config/application-local.properties` (no inspeccionado contenido — credenciales) |
| `PersistenceProfileEnvironmentPostProcessor` | Post-procesador de perfil existente |
| Esquema BD | Auditor de arranque (`DatabaseSchemaAuditor`) |

### 2.3 Organización de paquetes — **VERIFICADO**

Patrón por capas: `controller`, `service` / `service.impl`, `repository`, `dto`, `mapper`, `security`, `exception`, `config`.  
Mutaciones de stock existentes: **`RecepcionService`**, **`TrasladoService`** (CALL a SP).  
Módulo movimientos (lectura): **`MovimientosController`**, **`MovimientoServiceImpl`**, **`MovimientoRepository`**.

### 2.4 Autenticación y permisos — **VERIFICADO**

- Login por formulario (`SecurityConfig`).
- Roles mapeados a `ROLE_ADMIN` / `ROLE_OPERADOR` (`RoleAuthorityMapper`).
- `PermisoService.puede` → `fn_usuario_tiene_permiso(id_usuario, recurso, accion)` vía JDBC.
- Recurso historial: **`movimiento`** con `ACCEDER` + `SELECT` en rutas GET `/movimientos`.

### 2.5 Excepciones — **VERIFICADO**

| Mecanismo | Uso actual |
|---|---|
| `GlobalExceptionHandler` | `ResourceNotFoundException`, `BusinessException` → vista `error/error` |
| `ResponseStatusException` | 403 en controladores |
| `RecepcionController` | `DataAccessException` + `PSQLException` SQLState `P0001` → mensaje al cliente (JSON) |

**NO EXISTE** hoy un manejador MVC global para errores PostgreSQL en operaciones de movimientos (solo patrón en API recepción).

### 2.6 Módulo Movimientos en Java — **VERIFICADO**

| Componente | Responsabilidad actual |
|---|---|
| `MovimientosController` | GET listado y GET detalle |
| `MovimientoServiceImpl` | Filtros, paginación, catálogos para filtros |
| `MovimientoRepository` | SQL lectura sobre `movimiento` + joins |
| DTOs | `MovimientoFiltro`, `MovimientoListadoDto`, `MovimientoDetalleDto`, `MovimientoCatalogoDto` |

**NO EXISTE** en Java: `CALL` a `sp_salida_*` ni `sp_baja_*`.  
**NO EXISTE** POST/PUT en `MovimientosController` ni reglas HTTP dedicadas a salidas/bajas en `SecurityConfig`.

### 2.7 Vistas Thymeleaf — **VERIFICADO**

| Plantilla | Propósito |
|---|---|
| `movimientos.html` | Historial con filtros |
| `movimiento-detalle.html` | Detalle ENTRADA/TRASLADO/SALIDA/bajas (tipos en `TIPOS_VALIDOS`) |
| `layout/sidebar.html` | Ítem «Movimientos» si `accesos['movimiento']` |

**NO EXISTE** formulario de salida/baja en el módulo movimientos (recepción en flujo compras; traslados en módulo propio).

### 2.8 Patrones de integración SP referenciales — **VERIFICADO**

| Servicio | Llamada | Resolución `id_usuario` |
|---|---|---|
| `RecepcionService` | `CALL sp_crear_compra(?, NULL)` → `queryForObject` INOUT | `SELECT id_usuario FROM usuario WHERE username=?` |
| `RecepcionService` | `CALL sp_recibir_*` | `jdbc.update` |
| `TrasladoService` | `CALL sp_trasladar_*` | Mismo patrón username |
| `TrasladoService` | Validación perecedero / lote en servidor | Antes del CALL |

---

## 3. Inventario de rutinas PostgreSQL (`inventario`)

Firmas **VERIFICADAS** en `_test_12_5c_output.txt` (catálogo `pg_proc`). Todas listadas: **SECURITY INVOKER** (informes 12.5A–B).

### 3.1 Entradas (compras / recepción)

| Rutina | Parámetros IN | OUT / INOUT | Tablas tocadas (resumen) | Validaciones |
|---|---|---|---|---|
| `sp_crear_compra` | `p_id_proveedor` | INOUT `p_id_compra` | `compra` | — |
| `sp_recibir_no_perecedero` | compra, bodega, producto, cantidad, usuario | — | `compra`, `detalle_compra`, `inventario`, `movimiento`, `movimiento_compra` | `fn_validar_recepcion`, catálogo producto-proveedor, tipo NP |
| `sp_recibir_perecedero` | + `p_fecha_vencimiento` (date) | — | + `lote` | Idem + creación/actualización lote |

**Función:** `fn_validar_recepcion(p_id_compra, p_id_bodega, p_id_producto, p_cantidad, p_id_usuario)` → `boolean` / excepciones.

**Integración Java actual:** **VERIFICADO** en `RecepcionService` (módulo compras, no `MovimientosController`).

### 3.2 Salidas

| Rutina | Parámetros IN | OUT | Comportamiento |
|---|---|---|---|
| `sp_salida_no_perecedero` | bodega, producto, cantidad, usuario | `p_id_movimiento` | Descuenta inventario; un movimiento `SALIDA` |
| `sp_salida_perecedero` | bodega, producto, cantidad, usuario | `p_id_movimiento` | FEFO multilote; **varios** `movimiento` posibles; primer id en OUT |

**Funciones:** `fn_validar_salida`, `fn_stock_lotes_vigentes`, `fn_consultar_stock_bodega` (preexistente).

**Validaciones clave:** cantidad > 0; bodega/producto existen; stock; perecedero excluye lotes con `fech_vencimiento <= CURRENT_DATE`; usuario activo (no rol admin).

### 3.3 Bajas

| Rutina | Parámetros IN | OUT | Tipo movimiento | Bitácora |
|---|---|---|---|---|
| `sp_baja_dano_no_perecedero` | bodega, producto, cantidad, usuario, **`p_motivo` text** | `p_id_movimiento` | `BAJA_DAÑO` | Sí (`tipo_accion=BAJA_DAÑO`) |
| `sp_baja_dano_perecedero` | + **`p_num_lote`** | idem | idem | Sí; lote vigente o vencido |
| `sp_baja_vencimiento` | + lote | idem | `BAJA_VENCIMIENTO` | Sí; exige `fech_vencimiento <= CURRENT_DATE` |

**Funciones auxiliares:**

| Función | Contrato |
|---|---|
| `fn_usuario_es_administrador(p_id_usuario)` | `RETURNS void`; excepción si no `rol.nombre = 'ADMINISTRADOR'` |
| `fn_validar_motivo_baja(p_motivo)` | `RETURNS text`; obligatorio, máx. 500 chars |
| `fn_validar_salida` | Reutilizada en bajas para stock agregado |

**Integración Java:** **NO EXISTE** (pendiente 12.6.1).

### 3.4 Traslados

| Rutina | Parámetros IN |
|---|---|
| `sp_trasladar_no_perecedero` | origen, destino, producto, cantidad, usuario |
| `sp_trasladar_perecedero` | + `p_num_lote` |

**Función:** `fn_validar_traslado`.  
**Integración Java:** **VERIFICADO** `TrasladoService` (módulo traslados).

### 3.5 Otras funciones consulta (historial / UI futura)

| Función | Uso potencial |
|---|---|
| `fn_lotes_disponibles(bodega, producto)` | Catálogo lotes vigentes (bajas perecedero) |
| `fn_consultar_stock_bodega` | Validación previa en formulario |
| `fn_tipo_producto` | Clasificación NP/PER |
| `fn_reporte_movimientos` | Reportes (fuera alcance inmediato) |

---

## 4. Matriz de cobertura y dependencias

```text
fn_validar_recepcion ──► sp_recibir_*
fn_validar_salida ──────► sp_salida_* , sp_baja_* (stock agregado)
fn_stock_lotes_vigentes ► sp_salida_perecedero (solo vigentes)
fn_usuario_es_administrador , fn_validar_motivo_baja ──► sp_baja_*
fn_validar_traslado ────► sp_trasladar_*
```

| Rutina | En Supabase | Pruebas PG | En Java |
|---|---|---|---|
| `sp_recibir_*` / `sp_crear_compra` | VERIFICADO | 12.5C | VERIFICADO (`RecepcionService`) |
| `sp_salida_*` | VERIFICADO | 12.5A, 12.5C | **NO EXISTE** |
| `sp_baja_*` | VERIFICADO | 12.5B, 12.5C | **NO EXISTE** |
| `sp_trasladar_*` | VERIFICADO | 12.1 / app | VERIFICADO (`TrasladoService`) |

---

## 5. Contratos propuestos Java ↔ SP (12.6.1)

> **PROPUESTA** — no implementar en 12.6.0.

### 5.1 Resolución de identidad (común)

**VERIFICADO** patrón existente:

```text
id_usuario = SELECT id_usuario FROM usuario
             WHERE username = :username AND activo AND NOT bloqueado
```

Nunca aceptar `id_usuario` ni `tipo_movimiento` desde el cliente en operaciones de stock.

### 5.2 Salida no perecedero

| Campo formulario (PROPUESTA) | Parámetro SP | Tipo Java |
|---|---|---|
| `idBodega` | `p_id_bodega` | `Integer` |
| `idProducto` | `p_id_producto` | `Integer` |
| `cantidad` | `p_cantidad` | `Integer` |
| (sesión) | `p_id_usuario` | `Integer` |
| — | OUT `p_id_movimiento` | `Integer` vía `queryForObject` / fila CALL |

**Permiso Java (PROPUESTA):** `movimiento` + `INSERT`; OPERATIVO y ADMIN (coherente con 12.5C).

**SQL ejemplo (PROPUESTA):** `CALL inventario.sp_salida_no_perecedero(?, ?, ?, ?, NULL)` — espejo `RecepcionService` / pruebas Python.

### 5.3 Salida perecedero (FEFO)

Mismos IN sin `num_lote`. OUT: un id (primer movimiento si multilote).  
**UI (PROPUESTA):** no elegir lote; mostrar advertencia FEFO.  
Opcional previo: `fn_consultar_stock_bodega` y/o suma `fn_stock_lotes_vigentes`.

### 5.4 Baja daño no perecedero

| Campo | SP |
|---|---|
| bodega, producto, cantidad | IN estándar |
| `motivo` | `p_motivo` (`String`, trim en SP) |
| usuario sesión | `p_id_usuario` |
| OUT | `p_id_movimiento` |

**Permiso Java (PROPUESTA):** solo **ROLE_ADMIN** (`RoleAuthorityMapper` / consulta `rol.nombre`) **además** de validación en SP.

### 5.5 Baja daño perecedero

Añadir **`numLote`** obligatorio. Cargar lotes con `fn_lotes_disponibles` o consulta controlada (vigentes y vencidos permitidos en SP).

### 5.6 Baja por vencimiento

Igual que baja perecedero + **UI**: filtrar lotes con `fech_vencimiento <= CURRENT_DATE` (alineado a `LotesController` / SP).

### 5.7 Traslados

Contrato **ya integrado**; no duplicar en `MovimientoService` salvo redirección de navegación.

### 5.8 Entradas

Permanecen en **recepción/compras**; el historial de movimientos **ya muestra** `ENTRADA` vía `movimiento_compra`.

---

## 6. Manejo de errores y transacciones (PROPUESTA)

| Capa | Comportamiento |
|---|---|
| `@Transactional` en servicio de operación | Un CALL = una transacción Spring (como `TrasladoService`) |
| `DataAccessException` | Capturar; si `SQLState P0001`, exponer `getMessage()` amigable (patrón `RecepcionController`) |
| Otros SQLState | Mensaje genérico; log interno |
| Éxito salida/baja | Redirect a `/movimientos/consultar/{id}` o listado con flash |

**VERIFICADO:** SP garantizan atomicidad interna; Java debe evitar doble CALL en la misma petición.

---

## 7. Seguridad en capas

| Operación | `PermisoService` (PROPUESTA) | SP (VERIFICADO) |
|---|---|---|
| Historial | `movimiento` SELECT / ACCEDER | — |
| Salida | `movimiento` INSERT | Usuario activo |
| Baja | ROLE_ADMIN + opcional `movimiento` INSERT | `fn_usuario_es_administrador` |
| Recepción | `compra` INSERT | `fn_validar_recepcion` |
| Traslado | `traslado` INSERT | Usuario activo |

**PENDIENTE:** OPERATIVO tiene `movimiento.INSERT` en BD pero bajas bloqueadas en SP — la UI **no debe** exponer bajas a OPERATIVO aunque el permiso genérico exista.

---

## 8. Consistencia de datos y modelo (VERIFICADO en pruebas)

- `inventario.cantidad_actual` coherente con operaciones validadas en 12.5C.
- Perecedero: tras salidas/bajas probadas, inventario = suma de lotes en escenarios controlados.
- Bajas: motivo en **`bitacora.descripcion`**, no en `movimiento`.
- **NO EXISTE** FK `bitacora` → `movimiento`; enlace por texto `movimiento #id` (PROPUESTA consulta en detalle admin).

---

## 9. Vistas y navegación (PROPUESTA 12.6.1)

| Vista | Acción |
|---|---|
| `movimientos.html` | Botones «Registrar salida» / «Baja…» según rol (ocultos si sin permiso) |
| `movimiento-salida-form.html` (nueva) | Form NP o PER (clasificación vía `fn_tipo_producto` o tablas hijas) |
| `movimiento-baja-form.html` (nueva) | Subtipo daño/vencimiento; campo motivo obligatorio |
| `movimiento-detalle.html` | Mostrar motivo bitácora para `BAJA_*` (consulta adicional por id movimiento) |

Rutas HTTP **PROPUESTAS:**

- GET/POST `/movimientos/salida` — OPERATIVO+ con `movimiento.INSERT`
- GET/POST `/movimientos/baja/dano`, `/movimientos/baja/vencimiento` — solo ADMIN

**VERIFICADO:** CSRF habilitado en form login (Spring Security default); formularios POST con token.

---

## 10. Arquitectura de clases sugerida (PROPUESTA, sin crear aún)

| Pieza | Rol |
|---|---|
| `MovimientoOperacionService` (o extender service actual) | Orquestación CALL + permisos |
| Métodos en `MovimientoRepository` o clase `MovimientoSpGateway` | Encapsular SQL `CALL` |
| `MovimientoSalidaForm`, `MovimientoBajaForm` | DTO binding Thymeleaf |
| Ampliación `MovimientosController` | Rutas salida/baja |
| Tests | Espejo `MovimientosControllerTest` + mocks JDBC / integración |

**NO modificar** `RecepcionService` ni `TrasladoService` salvo reutilización de utilidades.

---

## 11. Riesgos y decisiones pendientes

| Tema | Estado |
|---|---|
| Mensajes PostgreSQL en español crudos al usuario | **PENDIENTE** — ¿mapear códigos o usar texto PG? |
| Salida perecedero múltiples `id_movimiento` | **PENDIENTE** — OUT solo devuelve el primero; documentar en UI |
| Bitácora en detalle | **PENDIENTE** — consulta LIKE o endpoint admin |
| `search_path` / prefijo `inventario.` en CALL | **VERIFICADO** JDBC usa conexión con esquema `inventario`; Java actual usa `sp_*` sin prefijo (igual que traslados) |
| Pool / concurrencia | Informes 12.5A.1 / 12.5C — sin cambio en Java |

---

## 12. Conclusión

- El backend **ya dispone** del patrón correcto (`JdbcTemplate` + `CALL` + usuario desde sesión) en recepción y traslados.
- El módulo **Movimientos** hoy es **solo lectura**; los SP de salida y baja están **desplegados y probados** en PostgreSQL pero **sin cliente Java**.
- La fase **12.6.1** puede implementar operaciones siguiendo los contratos anteriores **sin cambiar la base de datos**.

---

## 13. Confirmaciones de restricciones (12.6.0)

| Compromiso | Cumplido |
|---|---|
| Sin modificar SP, tablas, Java, vistas, Maven, Git | **Sí** |
| Sin migraciones ni datos alterados | **Sí** |
| Informe de auditoría generado | `docs/ANALISIS_FASE_12_6_0.md` |

**Fase 12.6.0 COMPLETADA.** Detenido — esperar autorización expresa para **12.6.1**.
