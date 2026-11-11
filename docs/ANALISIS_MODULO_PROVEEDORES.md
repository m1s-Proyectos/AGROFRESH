# Análisis técnico — Módulo Proveedores (Etapa 11.1)

**Fecha:** 2026-11-10  
**Proyecto:** `C:\Escritorio\BAD215\AgroFresh\AgroFresh2026-desarrollo`  
**Alcance:** Solo lectura. Sin cambios en Supabase, código de producción ni Git.

### Fuentes de evidencia

| Fuente | Qué aporta |
|---|---|
| Arranque local con perfil `local` + `DatabaseSchemaAuditor` (2026-11-10) | Metadatos reales de `inventario.proveedor` y FKs entrantes contra Supabase |
| Auditoría Etapa 10.6 (`docs/INFORME_AUDITORIA_SUPABASE_PRODUCTOS_10_6.md`, salida de restricciones) | Coherencia de FK `catalogo` y `lote` |
| Código actual (Categorías, Productos, Recepción, Lotes, seguridad) | Patrones arquitectónicos y conflictos de rutas |
| Script preparado `docs/_audit_proveedor_11_1.py` | Consultas agregadas y permisos (no ejecutado en esta sesión: pooler Supabase `EMAXCONNSESSION`) |

No se consultaron filas identificables de proveedores ni usuarios. No se imprimieron credenciales.

---

## 1. Resumen ejecutivo

- La tabla **`inventario.proveedor` existe** y encaja con un CRUD JPA al estilo Categorías/Productos.
- **No hay** entidad, repository, servicio, controller ni vistas de mantenimiento; `/proveedores` sigue en **`ModuloController`** (placeholder).
- **Recepción** y **Lotes** ya consumen proveedores por JDBC (`id_proveedor`, `nombre`).
- El recurso de seguridad **`proveedor`** ya está cableado en `SecurityConfig`, `SessionModelAdvice` y sidebar (`accesos['proveedor']`).
- **Tres tablas** referencian `proveedor` con `ON DELETE/UPDATE RESTRICT`: **`compra`**, **`catalogo`**, **`lote`**. No hay FK directa desde `detalle_compra` (solo vía `compra`).
- **Migraciones:** no se requieren para el CRUD previsto (sin cambios de estructura).
- **Consulta en vivo** de tabla `permiso` y conteos agregados de datos: **pendiente** por límite de conexiones del pooler; script listo para reintentar.

---

## 2. Estructura real de `inventario.proveedor`

### 2.1 Columnas (PostgreSQL / Supabase)

| Columna física | Tipo | Longitud | NULL | Campo funcional |
|---|---|---:|---|---|
| `id_proveedor` | `integer` (`int4`) | — | **NO** | Identificador interno |
| `nombre` | `character varying` | 150 | **NO** | Nombre |
| `dui` | `character varying` | 10 | **SÍ** | DUI (opcional en BD) |
| `nit` | `character varying` | 17 | **NO** | NIT |
| `correo_electronico` | `character varying` | 100 | **NO** | Correo |
| `direccion` | `character varying` | 200 | **NO** | Dirección |
| `telefono` | `character varying` | 9 | **NO** | Teléfono |
| `iva` | `character varying` | 50 | **NO** | Tipo de IVA |

- **PK:** `pk_proveedor (id_proveedor)`.
- **Identificador:** columna entera NOT NULL con generación en servidor (patrón **IDENTITY** del catálogo remoto, alineado al resto de tablas `SERIAL`/`IDENTITY` del proyecto). Secuencia coherente con el modelo AGROFRESH (`inventario.proveedor_id_proveedor_seq` según documentación de persistencia).
- **DEFAULT explícito en columnas de negocio:** no observado en auditoría (salvo mecanismo de identidad).

### 2.2 Restricciones

| Nombre | Tipo | Definición / efecto |
|---|---|---|
| `pk_proveedor` | PRIMARY KEY | `id_proveedor` |
| `uq_proveedor_dui` | UNIQUE | `dui` — en PostgreSQL permite **varias filas con `dui` NULL** |
| `uq_proveedor_nit` | UNIQUE | `nit` |
| `ck_proveedor_correo` | CHECK | Regex de correo: `^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,63}$` (insensible a mayúsculas) |

- **CHECK** sobre DUI, NIT, teléfono, dirección o valores de IVA: **no encontrados** en el catálogo auditado.
- **UNIQUE** sobre `correo_electronico`: **no**.

### 2.3 Índices

- PK e índices de soporte de PK/UNIQUE (`uq_proveedor_dui`, `uq_proveedor_nit`) según `pg_indexes` (nombres inferidos del patrón del esquema; p. ej. índice sobre FKs en tablas hijas no aplica en `proveedor` mismo).

### 2.4 Mapeo funcional → físico

| Funcional | Columna real | Notas |
|---|---|---|
| Nombre | `nombre` | |
| DUI | `dui` | Nullable; UNIQUE si tiene valor |
| NIT | `nit` | **Obligatorio en BD** (`NOT NULL`) |
| Correo | `correo_electronico` | No usar `correo` |
| Dirección | `direccion` | |
| Teléfono | `telefono` | Longitud **9** (no 8+separadores salvo que el negocio acote formato) |
| Tipo de IVA | `iva` | Texto libre hasta 50 caracteres; sin CHECK de catálogo en BD |

---

## 3. Relaciones con otras tablas

### 3.1 FKs entrantes (hacia `proveedor.id_proveedor`)

| Tabla origen | Constraint (nombre auditado) | ON UPDATE / DELETE |
|---|---|---|
| `compra` | `fk_compra_realiza_proveedo` | RESTRICT / RESTRICT |
| `catalogo` | `fk_catalogo_ofrece_proveedo` | RESTRICT / RESTRICT |
| `lote` | `fk_lote_provee_proveedo` | RESTRICT / RESTRICT |

- **`detalle_compra`:** no referencia `id_proveedor`; la relación con proveedor es **indirecta** mediante `compra`.
- **`movimiento`**, **`inventario`**, **`producto`:** sin FK directa a proveedor.

### 3.2 Uso en el código actual

| Componente | Uso |
|---|---|
| `RecepcionController` | `GET /api/compras/proveedores` → `SELECT id_proveedor, nombre FROM proveedor` |
| `RecepcionController` / `RecepcionService` | Alta de compra con `id_proveedor`; catálogo por proveedor |
| `LotesController` | `JOIN proveedor` para mostrar nombre en listado de lotes |
| `ModuloController` | Placeholder `GET /proveedores` |

### 3.3 Consecuencias de editar / eliminar

| Operación | Efecto técnico |
|---|---|
| **Editar** `nombre`, `correo_electronico`, `direccion`, `telefono`, `iva`, `dui`, `nit` (si UNIQUE/CHECK lo permiten) | **Permitido** por el esquema; el `id_proveedor` de compras, catálogo y lotes **no cambia**; pantallas operativas mostrarán datos actualizados en joins por `id`. |
| **Cambiar** `id_proveedor` | **No viable** (PK + FKs). |
| **Eliminar** proveedor con compras, filas en `catalogo` o `lote` | **Bloqueado** por FK `RESTRICT`. |
| **Editar NIT/DUI** con historial de compras | **No bloqueado por BD**; decisión de negocio si deben ser inmutables tras el primer uso (ver § 6.3). |

No se ejecutaron consultas de filas reales; los conteos agregados (proveedores con compra, etc.) quedan para el script `docs/_audit_proveedor_11_1.py` cuando el pooler admita conexión.

---

## 4. Compatibilidad con arquitectura Java

| Aspecto | Compatibilidad |
|---|---|
| Entidad JPA `@Table(name = "proveedor", schema = "inventario")` | **Alta** — tabla plana, sin subtipos |
| `GenerationType.IDENTITY` en `id_proveedor` | **Alta** — coherente con catálogo |
| Campos `String` con `length` según `varchar` | **Alta** |
| Sin `@ManyToOne` obligatorio en v1 del CRUD | **Alta** — Categorías no expone relación inversa con productos |
| Validación de correo | Debe **alinearse** con `ck_proveedor_correo` o la BD rechazará el INSERT/UPDATE |
| `ddl-auto=validate` | Esperable **OK** si el mapeo reproduce nulabilidad y longitudes |

Patrón de referencia: **Categorías** (tabla simple) más **Productos** (permisos finos en `SecurityConfig` + `PermisoService` en controller).

---

## 5. Reglas de negocio identificadas (respaldadas por el esquema)

| Regla | Evidencia PostgreSQL | Tipo |
|---|---|---|
| Nombre obligatorio | `nombre NOT NULL`, max 150 | **BD** |
| NIT obligatorio en persistencia | `nit NOT NULL`, max 17, UNIQUE | **BD** |
| DUI opcional; único si se informa | `dui` nullable, UNIQUE `uq_proveedor_dui` | **BD** |
| Correo obligatorio y con formato | `correo_electronico NOT NULL` + `ck_proveedor_correo` | **BD** |
| Dirección obligatoria | `direccion NOT NULL`, max 200 | **BD** |
| Teléfono obligatorio | `telefono NOT NULL`, max 9 | **BD** |
| Tipo IVA obligatorio | `iva NOT NULL`, max 50 | **BD** |
| Sin duplicado de DUI/NIT | UNIQUE | **BD** |
| Sin eliminación silenciosa con dependencias | FK RESTRICT en compra/catálogo/lote | **BD** |

**No respaldado por BD:**

- Regla funcional “**DUI o NIT**” como alternativa excluyente: el esquema exige **NIT siempre** y DUI opcional.
- Unicidad de correo.
- Catálogo cerrado de tipos de IVA.
- Formato de DUI/NIT/teléfono salvadoreño (dígitos, guiones).
- Inmutabilidad de identificación fiscal tras primera compra.

---

## 6. Reglas propuestas (capa Java / UI)

### 6.1 Validación en aplicación (recomendadas, no sustituyen la BD)

| Campo | Propuesta | Clasificación |
|---|---|---|
| `nombre` | `trim()`; rechazar vacío; máx. 150 | **Java** (como Categorías) |
| `nit` | Obligatorio, máx. 17; comprobar duplicado con `existsByNit` / `…AndIdProveedorNot` en edición | **Java** + UNIQUE **BD** |
| `dui` | Opcional; si se envía, máx. 10 y duplicado | **Java** + UNIQUE **BD** |
| `correo_electronico` | Validar regex **compatible** con CHECK de BD antes de persistir | **Java** |
| `direccion`, `telefono`, `iva` | Obligatorios, longitudes máximas; `trim()` | **Java** |
| Correo duplicado | Solo si negocio lo confirma (hoy **no** hay UNIQUE) | **Decisión usuario** |
| IVA | Lista cerrada en UI (p. ej. `Exento`, `Gravado`) **solo si** el negocio confirma valores permitidos | **Decisión usuario** |
| Teléfono | P. ej. 8 dígitos locales o 9 con prefijo; hasta 9 chars en columna | **Decisión usuario** |

### 6.2 Duplicidad

- **DUI / NIT:** manejar `DataIntegrityViolationException` → mensaje de negocio (patrón `CategoriaServiceImpl`).
- **Correo:** sin restricción BD; validar en servicio solo si el usuario autoriza unicidad.

### 6.3 Edición e historial

- **Conservar valores en formulario** tras error (flash `proveedorForm`, patrón Productos/Categorías).
- **No ofrecer DELETE** en UI (como Categorías/Productos); la BD ya impide borrado con dependencias.
- **Propuesta opcional:** bloquear en servicio la edición de `nit`/`dui` si existen filas en `compra`, `catalogo` o `lote` — **no está en el esquema**; requiere confirmación (equivalente espiritual a dependencias en Productos).

---

## 7. Permisos existentes

### 7.1 Mecanismo actual

- Tabla `permiso` + `rol_permiso` + función `fn_usuario_tiene_permiso` vía `PermisoService`.
- Recurso lógico **`proveedor`** ya usado para:
  - Menú (`SessionModelAdvice`: `ACCEDER` para mostrar enlace).
  - Matcher genérico en `SecurityConfig` para rutas `/proveedores/**` → `proveedor.ACCEDER`.

### 7.2 Consulta en vivo (Etapa 11.1)

La consulta `SELECT … FROM permiso WHERE recurso = 'proveedor'` **no se ejecutó** en esta sesión por **límite de conexiones del pooler** (`EMAXCONNSESSION`). Debe repetirse con `docs/_audit_proveedor_11_1.py` o transacción `READ ONLY` antes de codificar.

### 7.3 Matriz propuesta (hipótesis alineada a Productos/Categorías)

| Operación | Permiso esperado |
|---|---|
| Entrar al módulo / listado | `proveedor.ACCEDER` + `proveedor.SELECT` |
| Consultar ficha | `proveedor.SELECT` |
| Registrar | `proveedor.INSERT` |
| Editar | `proveedor.UPDATE` |

| Rol | Comportamiento típico AGROFRESH (confirmar en BD) |
|---|---|
| ADMINISTRADOR | ACCEDER, SELECT, INSERT, UPDATE (DELETE existe en modelo de permisos pero **fuera de alcance UI**) |
| OPERATIVO | ACCEDER, SELECT |

**No crear** permisos ni modificar roles en 11.1.

### 7.4 Gaps de seguridad a resolver en 11.2

Hoy **no** hay reglas finas en `SecurityConfig` para `POST /proveedores/guardar` ni `…/actualizar/{id}` (sí existen para Productos/Categorías). El CRUD debe añadir matchers por método HTTP y **revalidar** en el controller.

---

## 8. Arquitectura y patrones (Categorías / Productos)

### 8.1 Capas sugeridas (solo documentación; sin implementar)

| Capa | Patrón de referencia |
|---|---|
| `Proveedor` entity | `@Entity`, `schema = "inventario"`, sin Lombok `@Data` |
| `ProveedorRepository` | `JpaRepository`, `findAllByOrderByNombreAsc`, `existsByDui…`, `existsByNit…` |
| `ProveedorDto` / `ProveedorForm` / `ProveedorDetalleDto` | Como `CategoriaDto` / `ProductoForm` |
| `ProveedorMapper` | Manual |
| `ProveedorService` / `ProveedorServiceImpl` | `@Transactional`, `BusinessException`, `DataIntegrityViolationException` |
| `ProveedoresController` | MVC, `PermisoService`, flash éxitos/errores |
| Vistas | `proveedores.html`, `proveedor-form.html`, `proveedor-detalle.html`; reutilizar `categorias.css` / layout |
| Pruebas | Unit servicio, MockMvc vistas, `HomePageTest` permisos/CSRF |

### 8.2 Conflictos de rutas

| Conflicto | Acción en 11.2 |
|---|---|
| `ModuloController` `@GetMapping("/proveedores")` | **Retirar** mapping (igual que Productos) |
| Sidebar “Pendiente” | Quitar etiqueta al implementar |
| `/api/compras/proveedores` | **Mantener**; distinto propósito (combo recepción, permiso `compra`) |

### 8.3 JDBC existente

- Consultas en Recepción/Lotes **sin prefijo** `inventario.` dependen de `search_path` (hallazgo Etapa 10.6). El nuevo módulo JPA usará esquema explícito en la entidad; no es obligatorio refactorizar JDBC ajeno en 11.2.

---

## 9. Rutas propuestas

| Método | Ruta | Operación |
|---|---|---|
| GET | `/proveedores` | Listar |
| GET | `/proveedores/nueva` | Formulario alta |
| POST | `/proveedores/guardar` | Registrar |
| GET | `/proveedores/editar/{id}` | Formulario edición |
| POST | `/proveedores/actualizar/{id}` | Actualizar |
| GET | `/proveedores/consultar/{id}` | Detalle |

Todas bajo autenticación, reglas `SecurityConfig`, CSRF en POST y `PermisoService` en el controller.

---

## 10. Riesgos técnicos

| Riesgo | Severidad | Comentario |
|---|---|---|
| Requisito “DUI **o** NIT” vs `nit NOT NULL` | **Alto (funcional)** | Alinear requisito con datos reales (p. ej. NIT placeholder) o autorizar cambio de esquema (fuera de alcance actual) |
| Sin UNIQUE en correo | **Medio** | Duplicados permitidos por BD |
| `iva` sin CHECK | **Medio** | Valores inconsistentes si la UI es texto libre |
| Pooler Supabase saturado al auditar | **Bajo** | Reejecutar script de lectura antes de 11.2 |
| Placeholder `/proveedores` vs CRUD | **Medio** | Colisión hasta quitar `ModuloController` |
| Recepción depende de listado JDBC de proveedores | **Bajo** | CRUD JPA no debe romper API existente |
| Edición de identificación con historial | **Medio** | Solo política de negocio |

---

## 11. Decisiones pendientes de confirmación del usuario

1. ¿**NIT siempre obligatorio** o se permitirá proveedor solo con DUI (requeriría cambio de esquema o valor convencional de NIT)?
2. ¿**Valores permitidos de IVA** (lista cerrada)?
3. ¿**Formato** de DUI, NIT y teléfono (solo dígitos, máscaras)?
4. ¿**Correo único** entre proveedores?
5. ¿**Inmutabilidad** de DUI/NIT tras primera compra o fila en catálogo/lote?
6. Confirmar en Supabase la **matriz real** de `permiso` / `rol_permiso` para `recurso = 'proveedor'`.
7. ¿Incluir **búsqueda** en listado (cliente, como Productos/Categorías) o filtro servidor?

---

## 12. Recomendaciones para la Etapa 11.2 (diseño / implementación)

1. Ejecutar `docs/_audit_proveedor_11_1.py` con el pool libre; volcar permisos y conteos agregados al diseño.
2. Redactar **`DISENO_TECNICO_MODULO_PROVEEDORES.md`** con matriz de permisos confirmada y reglas DUI/NIT/IVA acordadas.
3. Implementar vertical slice JPA siguiendo **Productos** (seguridad fina) + **Categorías** (tabla simple).
4. Quitar placeholder en `ModuloController`; actualizar sidebar.
5. Pruebas: `ProveedorServiceImplTest`, `ProveedoresControllerTest`, `ModuleViewsTest`; H2 con esquema `inventario`; sin escrituras en Supabase.
6. No añadir DELETE ni migraciones sin autorización.

---

## 13. Condición de cierre Etapa 11.1

- **No** se implementó código de producción.
- **No** se modificó Supabase.
- **No** se ejecutaron operaciones de escritura.
- Archivos auxiliares de análisis: `docs/_audit_proveedor_11_1.py` (reutilizable, solo lectura).
- Entregable principal: **este informe**.

**Detenido — esperar autorización para iniciar Etapa 11.2.**
