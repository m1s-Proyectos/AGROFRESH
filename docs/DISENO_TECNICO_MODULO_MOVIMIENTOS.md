# Diseño técnico — Módulo Movimientos de Inventario (Fase 12.2)

**Fecha:** 2026-11-10  
**Proyecto:** `C:\Escritorio\BAD215\AgroFresh\AgroFresh2026-desarrollo`  
**Referencia principal:** `docs/ANALISIS_MODULO_MOVIMIENTOS_FASE_12_1.md`  
**Estado:** Solo diseño. Sin implementación, sin cambios en Supabase, código ni configuración local.

### Leyenda

| Marca | Significado |
|---|---|
| **VERIFICADO** | Respaldado por auditoría 12.1 o código existente inspeccionado. |
| **PROPUESTA** | Diseño coherente con el proyecto; requiere autorización antes de implementar. |
| **PENDIENTE** | Depende de decisión del usuario o de artefactos aún no existentes en BD. |
| **NO EXISTE** | Confirmado ausente en el ámbito inspeccionado. |

---

## 1. Resumen del diseño

El módulo **Movimientos** sustituye el placeholder `GET /movimientos` → `modulo-pendiente` por un **vertical slice MVC + JDBC** centrado en:

1. **Historial unificado** de filas en `movimiento` (todos los `tipo_movimiento` del CHECK).
2. **Registro de salidas** y **bajas** (`SALIDA`, `BAJA_DAÑO`, `BAJA_VENCIMIENTO`) sin duplicar recepción ni traslados.
3. **Identificación** de entradas por compra (`movimiento_compra`) y traslados (`movimiento_bodega` + enlaces a `/compras` y `/traslados`).

**Principio arquitectónico (VERIFICADO):** las mutaciones de stock en AGROFRESH se delegan a **procedimientos almacenados** (`RecepcionService`, `TrasladoService`). El diseño **no** replica en Java la lógica de `sp_recibir_*` / `sp_trasladar_*`.

**Bloqueo de diseño (VERIFICADO 12.1):** en el catálogo actual **NO EXISTE** `sp_salida`, `sp_baja_daño` ni equivalente. La fase **12.3** de backend de escritura queda **PENDIENTE** hasta autorizar procedimientos en Supabase (migración dedicada) o confirmar rutinas ocultas no listadas. La fase 12.3 puede iniciar **solo lectura** (historial + detalle) sin ese bloqueo.

```
Vista Thymeleaf → MovimientosController → MovimientoService → (JdbcTemplate CALL / SELECT)
                                                      ↘ sin acceso directo desde Controller
```

No se exponen entidades JPA a Thymeleaf. No se usa Lombok `@Data` en entidades si en el futuro se mapean tablas de solo lectura.

---

## 2. Análisis de las tres tablas principales

### 2.1 `inventario.movimiento` (VERIFICADO)

| Columna | Uso en diseño |
|---|---|
| `id_movimiento` | Identificador en listado, detalle y redirecciones POST. |
| `fecha` | Ordenación y filtro del historial (default `CURRENT_TIMESTAMP`). |
| `tipo_movimiento` | Filtro y etiquetas UI; dominio fijo de 5 valores. |
| `cantidad` | Siempre entero > 0 (`ck_movimiento_cantidad`). |
| `id_bodega` | Bodega de contexto (origen en salida/traslado; ámbito del lote en FK compuesta). |
| `id_usuario` | Responsable; resolución en servicio vía sesión (mismo patrón que `TrasladoService`). |
| `id_producto` | Nullable en esquema; **PROPUESTA:** exigir NOT NULL en validación de aplicación para salidas/bajas. |
| `num_lote` | Obligatorio cuando aplique FK a `lote` (perecederos); NULL en movimientos sin lote. |

**Triggers / hijos:** un movimiento de traslado tendrá fila en `movimiento_bodega`; una entrada por compra puede tener fila en `movimiento_compra`.

### 2.2 `inventario.movimiento_bodega` (VERIFICADO)

- Relación **1:1** con movimiento de tipo `TRASLADO` (PK = `id_movimiento`).
- `id_bodega_destino` es la bodega destino visible en historial.
- Trigger `trg_auditar_traslado` → `fn_auditar_traslado` (auditoría; no sustituye pantalla de historial).

### 2.3 `inventario.movimiento_compra` (VERIFICADO)

- Enlaza `id_movimiento` con `id_detalle_compra` (y vía este con `compra` y proveedor).
- Permite distinguir **ENTRADA** originada en recepción frente a otras entradas (si existieran) por ausencia/presencia de fila.

---

## 3. Reglas de negocio (diseño)

### 3.1 Transversales (VERIFICADO en esquema + patrones existentes)

| Regla | Fuente |
|---|---|
| `cantidad` > 0 en movimiento | `ck_movimiento_cantidad` |
| `cantidad_actual` ≥ 0 en `inventario` y `lote` | CHECK en tablas |
| Clasificación producto exclusiva perecedero XOR no perecedero | `TrasladoService` / `RecepcionService` |
| `id_usuario` activo desde sesión, no desde formulario | `TrasladoService`, `RecepcionService` |
| Transacción Spring alrededor de `CALL` | `@Transactional` en servicios de escritura |

### 3.2 Salida (`SALIDA`) — **PROPUESTA** (pendiente SP)

| Paso | Regla |
|---|---|
| 1 | Seleccionar `id_bodega` y `id_producto` con existencia en `inventario` (`cantidad_actual` > 0). |
| 2 | Determinar tipo: `EXISTS perecedero` → exigir `num_lote`; si no perecedero, `num_lote` NULL y validar solo inventario. |
| 3 | Si perecedero: lote debe existir en `(num_lote, id_bodega, id_producto)` y `lote.cantidad_actual` ≥ cantidad solicitada. |
| 4 | `cantidad` ≤ `inventario.cantidad_actual` y, si hay lote, ≤ `lote.cantidad_actual`. |
| 5 | Insertar movimiento `SALIDA` y decrementar existencias **solo** dentro del procedimiento almacenado autorizado (no SQL ad hoc en Java). |
| 6 | **NO EXISTE** campo motivo en `movimiento`; la UI no debe pedir texto libre de motivo almacenado en BD. |

### 3.3 Baja por daño (`BAJA_DAÑO`) — **PROPUESTA**

Mismas validaciones de stock que salida; `tipo_movimiento = 'BAJA_DAÑO'`. Sin columna de justificación en tabla (VERIFICADO). La interfaz puede mostrar texto explicativo fijo (“Baja por daño”) sin persistir observaciones adicionales.

### 3.4 Baja por vencimiento (`BAJA_VENCIMIENTO`) — **PROPUESTA**

| Regla | Detalle |
|---|---|
| Alcance | Solo productos **perecederos** con fila en `lote`. |
| Selección | **PROPUESTA:** lotes con `fech_vencimiento <= CURRENT_DATE` y `cantidad_actual > 0` (alineado a estados “VENCIDO” en `LotesController`). |
| Cantidad | ≤ `lote.cantidad_actual`; coherencia con `inventario` vía SP. |
| Operación automática masiva | **PENDIENTE** — no incluir en primera entrega salvo autorización. |

### 3.5 Historial — **PROPUESTA** (lectura JDBC, VERIFICADO tablas fuente)

Consulta paginable/ filtrable sobre `movimiento` con joins:

| Dato historial | Origen |
|---|---|
| Fecha | `m.fecha` |
| Tipo | `m.tipo_movimiento` |
| Producto (nombre, código) | `producto` |
| Bodega origen | `bodega` ← `m.id_bodega` |
| Bodega destino | `movimiento_bodega.id_bodega_destino` (solo TRASLADO) |
| Lote | `m.num_lote` (nullable) |
| Cantidad | `m.cantidad` |
| Responsable | `usuario` (nombre/apellido; no exponer credenciales) |
| Origen compra | LEFT JOIN `movimiento_compra` → `detalle_compra` → `compra` (ENTRADA con vínculo) |
| Enlace operativo | **PROPUESTA:** badge “Recepción” / “Traslado” con URL a módulos existentes, sin re-ejecutar operaciones |

**Alternativa (VERIFICADO):** reutilizar `fn_historial_traslados()` solo para subconjunto TRASLADO; el diseño **prefiere** consulta SQL unificada en servicio para una sola pantalla.

### 3.6 Operaciones fuera de alcance del módulo (VERIFICADO)

| Operación | Dónde queda |
|---|---|
| Entrada por compra | `RecepcionService` + `sp_crear_compra` / `sp_recibir_*` — aparecen en historial. |
| Traslado | `TrasladoService` + `sp_trasladar_*` — aparecen en historial. |
| Entrada manual | **NO EXISTE** flujo; no diseñar formulario de entrada en 12.4. |
| Edición / borrado de movimientos | FK RESTRICT y permiso `movimiento.UPDATE/DELETE` solo ADMIN; **PROPUESTA:** no exponer DELETE/UPDATE en UI (historial inmutable). |

---

## 4. Diseño de salidas y bajas (capa aplicación)

### 4.1 Formularios (PROPUESTA)

Campos mínimos alineados a columnas reales (sin motivo/observaciones):

| Formulario | Campos POST |
|---|---|
| Salida | `idBodega`, `idProducto`, `numLote` (condicional), `cantidad` |
| Baja daño | Igual que salida |
| Baja vencimiento | `idBodega`, `idProducto`, `numLote`, `cantidad` (lote precargado desde listado de vencidos) |

Catálogos en GET (mismo estilo que traslados):

- Bodegas: `SELECT id_bodega, nombre FROM bodega ORDER BY nombre`.
- Productos con stock: join `inventario` + `producto` filtrado por bodega.
- Lotes: `fn_lotes_disponibles(bodega, producto)` para salida/daño; para vencimiento, consulta análoga a `LotesController` con filtro `VENCIDO`.

### 4.2 Contrato de servicio de escritura (PENDIENTE nombres SP)

**PROPUESTA** — nombres orientativos a acordar en migración autorizada:

```text
CALL sp_registrar_salida(?, ?, ?, ?, ?, ?)        -- bodega, producto, lote|null, cantidad, usuario, OUT id_movimiento
CALL sp_registrar_baja_dano(...)                 -- misma firma lógica, tipo fijo en SP
CALL sp_registrar_baja_vencimiento(...)          -- idem
```

Hasta existir el SP, `MovimientoServiceImpl` debe rechazar con mensaje de negocio claro (`BusinessException`), no ejecutar INSERT directo.

### 4.3 Validación previa en Java (PROPUESTA)

Espejo de `TrasladoService` (argumentos, tipo producto, permisos) + comprobaciones de lectura (`inventario`, `fn_lotes_disponibles` / consulta lote). La **autoridad final** del stock sigue en PostgreSQL.

---

## 5. Integración con Recepción y Traslados

| Módulo | Recurso permiso | Escritura | Relación con Movimientos |
|---|---|---|---|
| Recepción `/compras` | `compra.INSERT` | `RecepcionService` | Genera `ENTRADA` + `movimiento_compra`; historial enlaza a compra. |
| Traslados `/traslados` | `traslado.INSERT` | `TrasladoService` | Genera `TRASLADO` + `movimiento_bodega`; historial muestra destino. |
| Movimientos `/movimientos` | `movimiento.SELECT/INSERT` | `MovimientoService` (nuevo) | Salidas/bajas; **no** llama a `sp_trasladar_*` ni `sp_recibir_*`. |

**VERIFICADO:** no modificar `RecepcionController`, `TrasladoService`, ni plantillas `recepcion.html` / `traslados.html` en 12.3 salvo enlaces cruzados opcionales desde historial.

**PROPUESTA:** en detalle de movimiento `ENTRADA` con `movimiento_compra`, mostrar `id_compra` y enlace read-only. En `TRASLADO`, mostrar bodega destino y enlace “Ver módulo Traslados” informativo.

---

## 6. Control de existencias

| Nivel | Mecanismo | Etiqueta |
|---|---|---|
| BD | CHECK ≥ 0; FK a `inventario`/`lote` | VERIFICADO |
| BD | `fn_validar_traslado`, `fn_validar_recepcion`, `fn_consultar_stock_bodega` | VERIFICADO (existencia) |
| App lectura | `InventarioController`, `LotesController` | VERIFICADO |
| App escritura movimientos | **PENDIENTE** SP; validación previa en servicio | PROPUESTA |
| Doble conteo | Evitar segunda ruta que INSERT en `movimiento` sin pasar por SP | PROPUESTA (regla de implementación) |

---

## 7. Arquitectura y componentes

### 7.1 Componentes recomendados

| Componente | ¿Crear? | Rol |
|---|---|---|
| `MovimientosController` | Sí | MVC: listado, formularios, POST, detalle; `PermisoService` en cada acción. |
| `MovimientoService` / `MovimientoServiceImpl` | Sí | Orquestación, validación previa, `CALL` SP, consultas historial. |
| `MovimientoRepository` | **PROPUESTA opcional** | Si se desea separar SQL: interfaz + impl JDBC. Alternativa: métodos privados en servicio (como hoy en varios controladores legacy). **Recomendación:** interfaz `MovimientoRepository` con impl JDBC para pruebas unitarias. |
| Entidades JPA `Movimiento`, etc. | **NO** para escritura | Evitar `@Transactional` JPA compitiendo con SP. |
| DTOs | Sí | Ver §8. |
| `MovimientoMapper` | **PROPUESTA** | De `Map`/`ResultSet` a DTO si el SQL vive en repository. |
| API REST separada | **NO** en v1 | Formularios server-side como Proveedores; si hiciera falta AJAX, subconjunto bajo `/api/movimientos/*` read-only (catálogos), espejo `TrasladoCatalogoController`. |

### 7.2 Flujo de capas

```
MovimientosController
    → exigirPermiso(movimiento, SELECT|INSERT)
    → MovimientoService
          → MovimientoRepository.consultarHistorial(Filtro)
          → MovimientoRepository.consultarDetalle(id)
          → MovimientoRepository.callRegistrarSalida(...)  // PENDIENTE SP
```

**VERIFICADO:** `ModuloController` dejará de mapear `/movimientos` (mismo patrón que Proveedores 11.2).

---

## 8. DTO y responsabilidades

| DTO | Campos principales | Uso |
|---|---|---|
| `MovimientoListadoDto` | id, fecha, tipoMovimiento, nombreProducto, codigoInterno, nombreBodegaOrigen, nombreBodegaDestino (nullable), numLote, cantidad, nombreResponsable, indicadorOrigen (COMPRA/TRASLADO/INTERNO) | Tabla historial |
| `MovimientoDetalleDto` | Listado + idUsuario (sin datos sensibles), idCompra opcional, idDetalleCompra opcional, fechVencimientoLote opcional | Vista detalle |
| `MovimientoSalidaForm` | idBodega, idProducto, numLote, cantidad | Salida y baja daño |
| `MovimientoBajaVencimientoForm` | idem | Baja vencimiento |
| `MovimientoFiltro` | tipo, idBodega, idProducto, fechaDesde, fechaHasta, texto busqueda | Query GET listado |

Records Java o POJOs sin Lombok `@Data` en “entidades”; en DTOs simples getters/setters o records son aceptables (patrón Proveedor).

---

## 9. Rutas propuestas

| Método | Ruta | Vista / acción | Permiso HTTP (PROPUESTA SecurityConfig) |
|---|---|---|---|
| GET | `/movimientos` | `movimientos.html` (historial) | `movimiento.ACCEDER` ∧ `movimiento.SELECT` |
| GET | `/movimientos/consultar/{id}` | `movimiento-detalle.html` | `movimiento.SELECT` |
| GET | `/movimientos/salida` | `movimiento-salida-form.html` | `movimiento.INSERT` |
| POST | `/movimientos/salida/guardar` | redirect detalle | `movimiento.INSERT` + CSRF |
| GET | `/movimientos/baja-dano` | `movimiento-baja-dano-form.html` | `movimiento.INSERT` |
| POST | `/movimientos/baja-dano/guardar` | redirect detalle | `movimiento.INSERT` + CSRF |
| GET | `/movimientos/baja-vencimiento` | `movimiento-baja-vencimiento-form.html` | `movimiento.INSERT` |
| POST | `/movimientos/baja-vencimiento/guardar` | redirect detalle | `movimiento.INSERT` + CSRF |

Catálogos AJAX opcionales (**PROPUESTA**): `GET /api/movimientos/bodegas`, `productos?bodega=`, `lotes?bodega=&producto=` (reutilizan consultas existentes; no duplicar lógica de negocio).

Parametros de filtro en listado: `tipo`, `bodega`, `producto`, `desde`, `hasta`, `q` (búsqueda por nombre/código producto).

---

## 10. Seguridad y permisos

### 10.1 Matriz Supabase (VERIFICADO 12.1)

| Acción | ADMINISTRADOR | OPERATIVO |
|---|---|---|
| ACCEDER | Sí | Sí |
| SELECT | Sí | Sí |
| INSERT | Sí | Sí |
| UPDATE | Sí | No |
| DELETE | Sí | No |

### 10.2 Matriz operacional propuesta (PROPUESTA)

| Operación UI | Permiso `movimiento` | Notas |
|---|---|---|
| Ver historial / detalle | ACCEDER + SELECT | OPERATIVO permitido |
| Registrar salida | INSERT | OPERATIVO permitido si negocio lo confirma |
| Registrar bajas | INSERT | **PENDIENTE:** ¿restringir bajas solo a ADMIN? |
| Editar / eliminar movimiento | — | **NO** exponer en UI |

**PROPUESTA SecurityConfig:** reglas finas por método HTTP (patrón Productos/Proveedores 11.3.1), sin debilitar reglas de `compra` ni `traslado`.

**VERIFICADO:** CSRF en todos los POST; `PermisoService.puede` en controlador además del filtro.

---

## 11. Diseño de interfaz (PROPUESTA)

Estilo: `categorias.css`, layout `header` / `sidebar('movimientos')` / `footer`, Bootstrap 5.3.3.

| # | Pantalla | Contenido |
|---|---|---|
| 1 | **Historial** (`movimientos.html`) | Filtros (tipo, bodega, fechas, búsqueda); tabla con columnas del §3.5; acciones: Consultar; botones “Registrar salida / baja daño / baja vencimiento” con `th:if` según INSERT; nota: entradas y traslados se registran en Recepción y Traslados. |
| 2 | **Salida** | Formulario compacto; cascada bodega → producto → lote (si perecedero) → cantidad. |
| 3 | **Baja daño** | Igual estructura; título y mensaje distintos. |
| 4 | **Baja vencimiento** | Tabla o select de lotes vencidos con stock; cantidad. |
| 5 | **Detalle** | Ficha read-only; bloques condicionales TRASLADO / COMPRA. |

Quitar `nav-pending` en sidebar al implementar 12.4 (**PROPUESTA**).

Sin gráficos ni exportación en v1.

---

## 12. Matriz de pruebas previstas

### 12.1 Unitarias (servicio + mapper, mocks JDBC)

| Caso | Tipo |
|---|---|
| Validación cantidad ≤ 0 rechazada | Unit |
| Perecedero sin lote rechazado | Unit |
| Cantidad > inventario rechazada (mock SP no invocado) | Unit |
| Mapper historial con/sin `movimiento_bodega` | Unit |

### 12.2 Integración (MockMvc + mock `MovimientoService` / SP)

| Caso | Tipo |
|---|---|
| Render historial y detalle | `ModuleViewsTest` |
| POST salida con CSRF | Integración |
| POST sin CSRF → 403 | Seguridad |
| OPERATIVO sin INSERT → 403 en formulario salida | Seguridad |
| Filtros `tipo`, `q` en listado | Integración |

### 12.3 Escenarios de negocio (cuando existan SP — PENDIENTE)

| Caso | Tipo |
|---|---|
| Stock insuficiente → error controlado | Integración / contrato SP |
| Lote inexistente | Integración |
| Baja vencimiento solo lotes vencidos | Integración |
| Tras salida, inventario coherente (lectura) | Integración autorizada en entorno aislado |

### 12.4 No regresión (VERIFICADO obligatorio en implementación)

- `RecepcionService` / `TrasladoService` tests existentes intactos.
- Permisos `compra` / `traslado` sin cambios.

---

## 13. Riesgos técnicos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Ausencia SP salida/baja | **BLOQUEA** escritura 12.3 | Migración BD autorizada; hasta entonces solo lectura |
| Lógica duplicada en Java | Stock inconsistente | Prohibir INSERT directo en diseño |
| `id_producto` NULL en filas legacy | UI incompleta | LEFT JOIN; ocultar acciones si datos incompletos |
| Permiso OPERATIVO INSERT en bajas | Riesgo operativo | Decisión §14 |
| Pooler Supabase | Auditorías intermitentes | Reintentar lectura antes de implementar |
| Historial pesado | Rendimiento | Filtros obligatorios + límite razonable (**PROPUESTA** 200 filas) |

---

## 14. Decisiones pendientes (requieren tu confirmación)

1. **¿Autorizar migración** con `sp_registrar_salida`, `sp_registrar_baja_dano`, `sp_registrar_baja_vencimiento` (nombres a definir con DBA)?
2. **¿OPERATIVO** puede registrar bajas o solo salidas?
3. **¿Historial** muestra todos los tipos por defecto o solo SALIDA/BAJA_*?
4. **¿Baja vencimiento** una línea por lote o permite cantidad parcial del lote?
5. **¿API REST** de catálogos bajo `/api/movimientos` o solo render server-side con recarga de página?
6. **¿Bitácora** (`bitacora`) para motivo de baja sin cambiar esquema `movimiento`? (opcional; hoy **NO EXISTE** integración en app).

---

## 15. Plan de implementación (PROPUESTA — no ejecutar sin autorización)

| Fase | Entregable | Dependencia |
|---|---|---|
| **12.2** | Este documento | — |
| **12.2-bis (opcional)** | Especificación SQL de SP (documento + migración autorizada) | Decisión 14.1 |
| **12.3** | Backend: quitar placeholder, `MovimientoService`, repository JDBC, `SecurityConfig`, tests listado/detalle/seguridad | 12.2 |
| **12.3b** | POST salida/bajas | SP en Supabase |
| **12.4** | Vistas Thymeleaf (5 pantallas) | 12.3 |
| **12.5** | Auditoría seguridad + `mvn verify` | 12.4 |

**Orden sugerido:** implementar **historial + detalle (solo lectura)** primero; luego escritura cuando existan procedimientos.

---

## 16. Confirmación de restricciones Fase 12.2

- **No** se modificó código, SQL, Supabase ni configuración local.
- **No** se ejecutaron comandos Git ni `mvn`.
- **Único artefacto creado:** `docs/DISENO_TECNICO_MODULO_MOVIMIENTOS.md`.

---

## Conclusión

El diseño es **coherente** con el esquema verificado en 12.1 y con el patrón JDBC + procedimientos almacenados del proyecto. La **implementación de salidas y bajas** queda condicionada a **procedimientos almacenados** aún **NO EXISTENTES** en el catálogo auditado; el **historial unificado** puede avanzar sin ese bloqueo.

**Fase 12.2 COMPLETADA (documentación).**  
**Detenido** — sin backend 12.3 hasta tu autorización explícita.
