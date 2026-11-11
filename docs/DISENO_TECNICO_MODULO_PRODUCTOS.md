# Diseño técnico — Módulo Productos (Etapa 10.2)

**Fecha:** 2026-11-10  
**Proyecto:** `C:\Escritorio\BAD215\AgroFresh\AgroFresh2026-desarrollo`  
**Fuente principal:** `docs/ANALISIS_MODULO_PRODUCTOS.md` (Etapa 10.1)  
**Estado:** Solo diseño. No se implementó código, no se modificó Supabase ni configuración local.

Leyenda de madurez de cada regla:

| Marca | Significado |
|---|---|
| **Confirmada** | Decisión aprobada por enunciado o evidencia del esquema/código actual. |
| **Propuesta** | Diseño recomendado coherente con el proyecto; puede implementarse salvo objeción. |
| **Pendiente** | Requiere autorización explícita antes de codificar. |

---

## 1. Resumen del diseño

El módulo Productos será un vertical slice JPA + MVC alineado con Categorías:

`Vista Thymeleaf → ProductosController → ProductoService → Repositories → tablas inventario.producto / perecedero / no_perecedero`

Objetivos funcionales del alcance:

- Listar, registrar, editar y consultar productos.
- Reutilizar `CategoriaService` para el selector de categoría.
- Validar unidad de medida con un catálogo cerrado en Java (sin tabla nueva).
- Exigir exactamente una clasificación (perecedero **o** no perecedero) usando solo las columnas reales de las tablas subtipo.
- No eliminar productos.
- No alterar módulos ajenos ni el esquema de Supabase.

Al implementar, habrá que retirar puntualmente el placeholder `GET /productos` de `ModuloController` y quitar la etiqueta “Pendiente” del sidebar. No se refactorizarán Inventario, Lotes, Recepción ni Traslados.

---

## 2. Reglas de negocio

### 2.1 Código interno — **Confirmada**

| Regla | Detalle |
|---|---|
| Obligatorio | Rechazar `null`, vacío o solo espacios. |
| Longitud | Máximo 20 caracteres (`varchar(20)`). |
| Unicidad | Debe respetar `uq_producto_codigo`. |
| Normalización | Aplicar `trim()` antes de validar y persistir. |
| Edición | Permitir conservar el código actual del mismo producto. |
| Protección concurrente | Comprobación previa en servicio + capturar violación UNIQUE de PostgreSQL como `BusinessException`. |

**Pendiente:** no se impone formato (prefijos, solo dígitos, mayúsculas, etc.). Si negocio necesita un patrón, debe autorizarse aparte.

### 2.2 Nombre — **Confirmada**

| Regla | Detalle |
|---|---|
| Obligatorio | Rechazar `null`, vacío o solo espacios. |
| Longitud | Máximo 150 caracteres. |
| Normalización | `trim()` al inicio y al final. |
| Unicidad | El esquema **no** exige UNIQUE; se permiten nombres repetidos. |
| Edición | Permitir conservar el nombre actual. |

### 2.3 Categoría — **Confirmada**

| Regla | Detalle |
|---|---|
| Obligatoria | `id_categoria` NOT NULL + FK `fk_producto_categoria`. |
| Origen | Selector alimentado por `CategoriaService.listar()`. |
| Validación | Antes de guardar, confirmar existencia con `CategoriaService.consultar(id)` (o equivalente del servicio). |
| Prohibido | Duplicar catálogo de categorías o saltarse el módulo existente. |

**Propuesta UX:** si no hay categorías, el formulario de alta muestra aviso y deshabilita el guardado hasta que exista al menos una.

### 2.4 Unidad de medida — **Confirmada** (catálogo app) + detalle en §3

| Regla | Detalle |
|---|---|
| Obligatoria | `unidad_medida` NOT NULL, máximo 20. |
| Origen de valores | Catálogo controlado en Java; no existe tabla de unidades en Supabase. |
| Entrada | Rechazar cualquier valor que no esté en el catálogo. |
| Persistencia | Guardar el texto canónico del catálogo en `producto.unidad_medida`. |

### 2.5 Clasificación — **Confirmada**

Cada producto debe tener **exactamente una** clasificación tras una operación completada:

- Perecedero → fila en `inventario.perecedero`.
- No perecedero → fila en `inventario.no_perecedero`.

La aplicación debe impedir:

- Quedar sin subtipo.
- Quedar en ambas tablas a la vez.

La BD **no** garantiza esta exclusividad; la regla vive en el servicio, dentro de una transacción.

### 2.6 Edición de datos generales — **Confirmada** / **Propuesta**

| Campo | Comportamiento |
|---|---|
| Código, nombre, categoría, unidad | Editables si el usuario tiene `producto.UPDATE`. |
| `id_producto` | Inmutable (identity). |
| Clasificación | Editable solo si no hay dependencias bloqueantes (§6). |

**Propuesta:** inventario, compras o movimientos existentes **no** bloquean la edición descriptiva (código, nombre, categoría, unidad) porque las FKs apuntan a `producto` y no impiden UPDATE de columnas descriptivas.

### 2.7 Eliminación — **Confirmada**

Fuera de alcance. Sin botones, rutas, servicios ni permisos de UI para DELETE, aunque el permiso `producto.DELETE` exista en BD.

---

## 3. Diseño de unidades de medida

### 3.1 Catálogo inicial — **Confirmada** como propuesta de opciones del enunciado

Valores canónicos a persistir (coinciden con longitud ≤ 20):

1. `Unidad`
2. `Kilogramo`
3. `Gramo`
4. `Litro`
5. `Mililitro`
6. `Caja`
7. `Paquete`

**Evaluación de suficiencia:** para el alcance del enunciado (catálogo cerrado sin tocar Supabase) es suficiente. Cubre conteo, masa, volumen y empaque genérico. No cubre unidades agrícolas adicionales (por ejemplo “Racimo”, “Docena”, “Saco”) si el negocio las necesita después.

### 3.2 Componente propuesto — **Propuesta**

`com.agrofresh.util.UnidadMedidaCatalog` (o enum `UnidadMedida`):

- Lista ordenada de valores permitidos.
- Método `esValida(String valor)`.
- Método `requerirValida(String valor)` que lanza `BusinessException` si no pertenece al catálogo.
- Sin dependencia de BD.

El formulario solo ofrece `<option>` de este catálogo. El servicio revalida en servidor (nunca confiar solo en el cliente).

### 3.3 Ampliación futura sin modificar Supabase — **Propuesta**

1. Agregar constantes/entradas al catálogo Java.
2. Actualizar pruebas del catálogo y del servicio.
3. Opcional: externalizar la lista a `application.properties` (`agrofresh.unidades=...`) si se autoriza, aún sin tabla nueva.

No crear tabla `unidad_medida` ni CHECK en PostgreSQL sin autorización.

### 3.4 Datos históricos fuera del catálogo — **Pendiente** (política de edición)

Pueden existir productos previos con textos de unidad no listados. Opciones:

| Opción | Descripción |
|---|---|
| A (recomendada) | En edición, si el valor actual no está en el catálogo, mostrarlo como opción temporal “solo lectura/migración” y obligar a elegir una del catálogo para guardar. |
| B | Rechazar la carga del formulario hasta corrección manual en BD (más rígido; no preferible). |
| C | Permitir conservar el valor histórico sin permitir otros valores libres. |

**Pendiente de autorización:** adoptar A salvo indicación contraria.

---

## 4. Diseño de clasificación

### 4.1 Modelo conceptual — **Confirmada**

`producto` no tiene columna de tipo. La clasificación es la presencia de una fila subtipo:

```
producto 1 ──0..1── perecedero
producto 1 ──0..1── no_perecedero
```

Regla de aplicación tras commit exitoso:

```
(existe perecedero XOR existe no_perecedero) == true
```

### 4.2 Alta — **Propuesta de secuencia transaccional**

1. Validar datos generales + unidad del catálogo + categoría existente.
2. Validar campos del subtipo según clasificación elegida.
3. Persistir `producto`.
4. Persistir exactamente una fila en `perecedero` **o** `no_perecedero` con el mismo `id_producto`.
5. Si falla el subtipo, rollback completo (no dejar producto huérfano sin clasificación).

### 4.3 Cambio de clasificación — **Confirmada** (bloqueo por dependencias)

Secuencia propuesta **solo si** la validación de dependencias lo permite:

1. Comprobar dependencias (§6). Si hay bloqueo → `BusinessException` con mensaje claro; no borrar ni transformar lotes/movimientos.
2. Eliminar la fila del subtipo actual.
3. Insertar la fila del nuevo subtipo con los campos capturados.
4. Verificar exclusividad al final de la transacción.

Si el cambio no es posible, el usuario mantiene la clasificación actual y puede seguir editando datos generales.

### 4.4 Representación en formulario — **Propuesta**

Un solo formulario con selector de clasificación (`PERECEDERO` | `NO_PERECEDERO`) y campos condicionales. No hace falta un formulario HTML distinto por tipo; sí validación de servidor distinta por rama.

---

## 5. Diferencias entre perecederos y no perecederos

Basado exclusivamente en columnas reales de la Etapa 10.1.

### 5.1 `inventario.perecedero`

| Aspecto | Detalle |
|---|---|
| Qué almacena | Condiciones de conservación del producto perecedero. |
| Relación | `id_producto` es PK y FK a `producto.id_producto` (1:0..1). |
| Campos | `temp_recomendad numeric(5,2) NOT NULL`, `vida_util integer NOT NULL` con CHECK `vida_util > 0`. |
| Validaciones app | Temperatura obligatoria; vida útil entera y > 0; precisión acorde a `numeric(5,2)`. |

**Propuesta de captura en UI:**

- Temperatura recomendada (°C), número decimal.
- Vida útil (días u otra unidad de negocio no modelada en BD), entero positivo.

**Pendiente:** rango permitido de `temp_recomendad` (mín/máx). El esquema no define CHECK de rango.

### 5.2 `inventario.no_perecedero`

| Aspecto | Detalle |
|---|---|
| Qué almacena | Datos de fabricación/anaquel del producto no perecedero. |
| Relación | `id_producto` es PK y FK a `producto.id_producto` (1:0..1). |
| Campos | `fech_fabricacion date NOT NULL`, `vida_util_anaquel integer NOT NULL` con CHECK `vida_util_anaquel > 0`. |
| Validaciones app | Fecha obligatoria; vida en anaquel entera y > 0. |

**Propuesta de captura en UI:**

- Fecha de fabricación.
- Vida útil en anaquel (entero positivo).

**Pendiente:** si se acepta `fech_fabricacion` futura.

### 5.3 Diferencias funcionales relevantes

| Tema | Perecedero | No perecedero |
|---|---|---|
| Campos | Temperatura + vida útil | Fecha fabricación + vida anaquel |
| Integración operativa | Recepción/traslado exigen lote; `lote.id_producto` referencia `perecedero` | Operan sin el mismo vínculo de lote a subtipo |
| Cambio de tipo | Salir de perecedero puede estar bloqueado por lotes | Entrar/salir depende de otras FKs y de la política §6 |
| Formulario | Campos numéricos de conservación | Fecha + entero de anaquel |

**Decisión de UI — Propuesta:** formulario común con campos condicionales (no dos pantallas separadas).

---

## 6. Tratamiento de relaciones y dependencias

### 6.1 FKs entrantes a `producto` (Etapa 10.1)

| Tabla | Efecto sobre edición descriptiva | Efecto sobre cambio de clasificación / borrado de subtipo |
|---|---|---|
| `catalogo` | No bloquea UPDATE descriptivo | No referencia subtipo; no bloquea por sí sola |
| `detalle_compra` | No bloquea | No referencia subtipo |
| `inventario` | No bloquea | No referencia subtipo |
| `movimiento` | No bloquea | No referencia subtipo |
| `perecedero` / `no_perecedero` | N/A | Son el propio subtipo |
| `lote` | No bloquea UPDATE de `producto` | **`lote.id_producto` → `perecedero.id_producto` (ON DELETE RESTRICT)** |

### 6.2 Dependencias a comprobar antes de cambiar clasificación — **Propuesta**

Mecanismo: consultas `EXISTS` de solo lectura dentro de `ProductoService`, antes de borrar/insertar subtipos.

| # | Comprobación | Bloquea cuando | Mensaje orientativo |
|---|---|---|---|
| 1 | `EXISTS` en `lote` para el `id_producto` | Hay lotes | “No se puede cambiar la clasificación porque el producto tiene lotes asociados.” |
| 2 | `EXISTS` en `movimiento` para el `id_producto` | Hay movimientos | “No se puede cambiar la clasificación porque existen movimientos registrados.” |
| 3 | `EXISTS` en `inventario` con `cantidad_actual > 0` | Hay existencia | “No se puede cambiar la clasificación mientras existan existencias.” |
| 4 | `EXISTS` en `detalle_compra` / `catalogo` | Hay historial comercial | Opcional; ver pendiente |

**Confirmada la intención del enunciado:** no eliminar ni transformar automáticamente lotes, movimientos ni inventarios.

**Propuesta operativa mínima a implementar primero:**

- Bloquear **cualquier** cambio de clasificación si existe al menos un lote del producto (cubre el caso crítico perecedero → no perecedero ante FK RESTRICT).
- Bloquear también si hay movimientos o existencias > 0 (previene inconsistencias operativas con Recepción/Traslados, que ya exigen clasificación exclusiva).

**Pendiente:** si negocio autoriza cambios cuando solo hay catálogo/compras históricas sin lotes ni stock.

### 6.3 Detección de datos inconsistentes preexistentes — **Propuesta**

Al consultar/editar, si el producto aparece en ambas tablas o en ninguna:

- Detalle: mostrar advertencia de inconsistencia.
- Edición: exigir elegir una clasificación válida y, si las dependencias lo permiten, corregir a exactamente una; si no, solo lectura de clasificación con mensaje.

No se diseñan scripts masivos de reparación en esta etapa.

---

## 7. Arquitectura propuesta

### 7.1 Flujo

```
Thymeleaf (productos*.html)
        ↓
ProductosController          ← PermisoService + CSRF
        ↓
ProductoService / Impl       ← CategoriaService, UnidadMedidaCatalog, validación de dependencias
        ↓
ProductoRepository
PerecederoRepository
NoPerecederoRepository
        ↓
Supabase (inventario.*)
```

### 7.2 Componentes

| Componente | Paquete | Responsabilidad |
|---|---|---|
| `Producto` | `model` | Entidad JPA de `inventario.producto`. Sin Lombok `@Data`. Getters; mutación controlada. |
| `Perecedero` | `model` | Entidad JPA; PK/FK compartida `id_producto`; `temp_recomendad`, `vida_util`. |
| `NoPerecedero` | `model` | Entidad JPA; PK/FK compartida; `fech_fabricacion`, `vida_util_anaquel`. |
| `ProductoRepository` | `repository` | CRUD JPA, `existsByCodigoInterno`, `existsByCodigoInternoAndIdProductoNot`, listados con categoría. |
| `PerecederoRepository` | `repository` | Alta/baja/consulta por `id_producto`. |
| `NoPerecederoRepository` | `repository` | Alta/baja/consulta por `id_producto`. |
| `UnidadMedidaCatalog` | `util` | Catálogo cerrado y validación. |
| `ProductoMapper` | `mapper` | Entity/subtipo → DTO; sin exponer entidades a la vista. |
| `ProductoService` | `service` | Orquestación transaccional de alta/edición/consulta/listado y reglas. |
| `ProductoServiceImpl` | `service.impl` | Implementación; usa `CategoriaService` para validar categoría. |
| `ProductosController` | `controller` | MVC; permisos; flash messages; no llama repositories. |

Relación JPA con `Categoria`: preferir `@ManyToOne(optional = false)` / `@JoinColumn(name = "id_categoria")` **sin cascadas** que alteren categorías. Alternativa equivalente: mapear solo `idCategoria` y validar vía servicio; la primera opción es más alineada al selector y al listado.

**No usar herencia JPA** (`JOINED`/`SINGLE_TABLE`) mientras la exclusividad no esté en BD: dos entidades satélite + servicio es más seguro y explícito.

### 7.3 Ajustes puntuales al integrar (etapas posteriores, no ahora)

| Archivo | Cambio mínimo esperado |
|---|---|
| `ModuloController` | Quitar `/productos` del mapping de pendientes. |
| `SecurityConfig` | Reglas finas por ruta (como Categorías) para SELECT/INSERT/UPDATE además del ACCEDER genérico. |
| `sidebar.html` | Quitar badge “Pendiente” de Productos. |
| Módulos Inventario/Lotes/Compras/Traslados | **Sin cambios** de comportamiento. |

### 7.4 Condicionamiento de beans — **Propuesta**

Seguir el patrón de Categorías: servicios/controllers de persistencia condicionados a `spring.datasource.url` cuando el proyecto ya lo aplica, para no romper arranques/pruebas sin datasource.

---

## 8. DTO y responsabilidades

### 8.1 `ProductoListadoDto` — **Propuesta**

Para la grilla:

- `idProducto`
- `codigoInterno`
- `nombre`
- `nombreCategoria` (y opcionalmente `idCategoria`)
- `unidadMedida`
- `clasificacion` (`PERECEDERO` | `NO_PERECEDERO` | `INCONSISTENTE` si aplica)

### 8.2 `ProductoDetalleDto` — **Propuesta**

Extiende la información de listado con campos de subtipo:

- Datos generales + categoría (id y nombre).
- `clasificacion`
- Si perecedero: `tempRecomendad`, `vidaUtil`
- Si no perecedero: `fechFabricacion`, `vidaUtilAnaquel`
- Flags de UI: `puedeEditar`, `puedeCambiarClasificacion` (calculados en servicio/controller según permisos y dependencias)

Si se prefiere simplicidad máxima como en Categorías, un único `ProductoDto` puede cubrir listado y detalle; el listado simplemente no usa los campos de subtipo.

### 8.3 `ProductoForm` — **Propuesta**

Clase mutable (como `CategoriaForm`) con:

- `codigoInterno`, `nombre`, `idCategoria`, `unidadMedida`
- `clasificacion` (`PERECEDERO` / `NO_PERECEDERO`)
- Campos condicionales: `tempRecomendad`, `vidaUtil`, `fechFabricacion`, `vidaUtilAnaquel`

No incluye `idProducto` en el alta; en edición el id viaja por path.

### 8.4 Mapper

`ProductoMapper`:

- `toListadoDto(...)`
- `toDetalleDto(...)`
- Construcción de subentidades desde el form en el servicio (o métodos `nuevoPerecedero` / `nuevoNoPerecedero`).

Las entidades **no** se envían a Thymeleaf.

---

## 9. Rutas propuestas

### 9.1 Colisiones comprobadas

| Mapping actual | Impacto |
|---|---|
| `ModuloController` → `GET /productos` (placeholder) | **Conflicto directo.** Debe eliminarse al registrar `ProductosController`. |
| `TrasladoCatalogoController` → `GET /api/traslados/productos` | Sin colisión (prefijo `/api/traslados`). |
| `RecepcionController` → `GET /api/compras/productos` | Sin colisión. |
| Rutas `/categorias/**` | Sin colisión. |

### 9.2 Rutas MVC del módulo — **Propuesta** (espejo de Categorías)

| Método | Ruta | Permiso | Vista |
|---|---|---|---|
| GET | `/productos` | ACCEDER + SELECT | `productos` |
| GET | `/productos/nueva` | INSERT | `producto-form` |
| POST | `/productos/guardar` | INSERT | redirect a detalle |
| GET | `/productos/editar/{id}` | UPDATE | `producto-form` |
| POST | `/productos/actualizar/{id}` | UPDATE | redirect a detalle |
| GET | `/productos/consultar/{id}` | SELECT | `producto-detalle` |

Sin rutas de eliminación.

CSRF requerido en POST, igual que el resto de la aplicación.

---

## 10. Seguridad y permisos

### 10.1 Matriz — **Confirmada**

| Operación | Permiso |
|---|---|
| Entrar al módulo | `producto.ACCEDER` |
| Listar | `producto.SELECT` |
| Consultar | `producto.SELECT` |
| Registrar | `producto.INSERT` |
| Editar | `producto.UPDATE` |

Asignaciones actuales (Etapa 10.1):

- `ADMINISTRADOR`: ACCEDER, SELECT, INSERT, UPDATE, DELETE (DELETE no se usará en UI).
- `OPERATIVO`: ACCEDER, SELECT.

### 10.2 Aplicación — **Propuesta** (igual que Categorías)

1. Reglas en `SecurityConfig` por ruta/método.
2. Revalidación en métodos del controller con `PermisoService.puede(...)`.
3. UI: botones “Nuevo” / “Editar” visibles solo con INSERT/UPDATE.
4. No crear permisos nuevos ni alterar filas en Supabase.
5. Mantener CSRF habilitado.
6. Denegación → `/acceso-denegado`.

---

## 11. Diseño de interfaz

Estilo: Thymeleaf + Bootstrap 5.3.3 + layout actual (sidebar/header/footer).

### 11.1 Listado (`productos.html`)

- Columnas: código interno, nombre, categoría, unidad, clasificación.
- Acciones: Consultar; Editar (si permiso).
- CTA “Registrar producto” si INSERT.
- Estado vacío cuando no hay filas.
- Sin acción de eliminar.

### 11.2 Formulario registro/edición (`producto-form.html`)

Secciones:

1. Datos generales: código, nombre, categoría (`select` desde `CategoriaDto`), unidad (`select` desde catálogo).
2. Clasificación: radio/select Perecedero | No perecedero.
3. Campos condicionales:
   - Perecedero → temperatura recomendada, vida útil.
   - No perecedero → fecha de fabricación, vida útil en anaquel.
4. En edición: si el cambio de clasificación está bloqueado, deshabilitar el selector y mostrar el mensaje de dependencia; permitir guardar el resto.

Misma plantilla para alta y edición (como Categorías).

### 11.3 Detalle (`producto-detalle.html`)

- Ficha con datos generales, categoría, unidad y clasificación.
- Bloque específico del subtipo.
- Acciones: Volver al listado; Editar (si permiso).

### 11.4 Navegación

- Enlace existente `/productos` en sidebar (`accesos['producto']`).
- Quitar “Pendiente” en la etapa de interfaz.

---

## 12. Matriz de pruebas (etapas posteriores)

Leyenda: **U** unitaria (servicio/catálogo/mapper), **I** integración (H2 aislado con esquema equivalente), **S** seguridad/MockMvc, **R** solo lectura contra Supabase (opt-in, sin escrituras).

| # | Caso | U | I | S | R |
|---|---|---|---|---|---|
| 1 | Código interno obligatorio / solo espacios | ✓ | | | |
| 2 | Código > 20 caracteres | ✓ | | | |
| 3 | Código duplicado (previo y UNIQUE concurrente) | ✓ | ✓ | | |
| 4 | Conservar código al editar el mismo producto | ✓ | ✓ | | |
| 5 | Nombre obligatorio / solo espacios / trim | ✓ | | | |
| 6 | Nombre > 150 | ✓ | | | |
| 7 | Categoría inexistente | ✓ | ✓ | | |
| 8 | Categoría obligatoria | ✓ | | | |
| 9 | Unidad fuera del catálogo | ✓ | | | |
| 10 | Unidad válida del catálogo | ✓ | ✓ | | |
| 11 | Clasificación ausente | ✓ | | | |
| 12 | Intento de ambas clasificaciones | ✓ | ✓ | | |
| 13 | Alta perecedero con campos válidos | ✓ | ✓ | | |
| 14 | Alta perecedero con `vida_util <= 0` | ✓ | ✓ | | |
| 15 | Alta no perecedero con campos válidos | ✓ | ✓ | | |
| 16 | Alta no perecedero con `vida_util_anaquel <= 0` | ✓ | ✓ | | |
| 17 | Edición de datos generales | ✓ | ✓ | | |
| 18 | Cambio de clasificación sin dependencias | ✓ | ✓ | | |
| 19 | Cambio de clasificación bloqueado por lotes | ✓ | ✓ | | |
| 20 | Cambio bloqueado por movimientos / stock (>0) | ✓ | ✓ | | |
| 21 | GET listado/detalle con SELECT | | | ✓ | |
| 22 | POST guardar exige INSERT + CSRF | | | ✓ | |
| 23 | POST actualizar exige UPDATE + CSRF | | | ✓ | |
| 24 | OPERATIVO no puede INSERT/UPDATE | | | ✓ | |
| 25 | ADMINISTRADOR con permisos de escritura | | | ✓ | |
| 26 | Metamodelo/columnas reales producto+subtipos | | | | ✓ |
| 27 | No existen rutas DELETE de producto | | | ✓ | |

**Restricción:** pruebas contra Supabase real = solo lectura hasta autorización explícita de escritura. Escrituras de prueba en H2 (o base aislada), nunca en el proyecto Supabase compartido sin permiso.

---

## 13. Riesgos técnicos

1. La BD permite 0 o 2 subtipos; la exclusividad depende del servicio y de transacciones correctas.
2. `lote → perecedero` con `ON DELETE RESTRICT` puede hacer fallar un cambio de clasificación si no se valida antes.
3. Recepción y Traslados ya asumen clasificación exclusiva; productos inconsistentes heredados romperían esos flujos.
4. Valores históricos de `unidad_medida` fuera del catálogo Java.
5. Colisión de mapping con el placeholder `/productos`.
6. `temp_recomendad` sin rango CHECK: valores extremos podrían aceptarse si no se define política.
7. Hibernate `ddl-auto=validate` exigirá mapeo exacto de columnas/tipos (`numeric(5,2)`, `date`, identity).
8. No refactorizar módulos JDBC existentes evita regresiones, pero coexistirán dos estilos de acceso a `producto` (lectura JDBC operativa vs CRUD JPA).

---

## 14. Decisiones pendientes

| # | Tema | Opciones / nota | Bloquea implementación |
|---|---|---|---|
| 1 | Formato de `codigo_interno` | Sin patrón (recomendado) vs patrón autorizado | No, si se adopta “sin patrón” |
| 2 | Rango de `temp_recomendad` | Libre dentro de `numeric(5,2)` vs mín/máx de negocio | Parcial (se puede validar solo NOT NULL + escala) |
| 3 | `fech_fabricacion` futura | Permitir / rechazar | Parcial |
| 4 | Unidades históricas fuera de catálogo | Política A/B/C (§3.4) | Sí para edición robusta |
| 5 | Bloqueo por catálogo/compras sin stock | Incluir o no en dependencias | No si se adopta bloqueo por lote+movimiento+stock |
| 6 | Ampliación de unidades del enunciado | ¿Agregar Racimo/Docena/etc.? | No para el MVP del catálogo dado |
| 7 | Un `ProductoDto` vs listado+detalle separados | Preferencia de simplicidad | No |
| 8 | Corrección de productos ya inconsistentes (0 o 2 subtipos) | Solo aviso vs forzar reparación en edición | Parcial |

**Confirmadas y listas para codificar** cuando se autorice la Etapa 10.3+: reglas A–G del enunciado, catálogo de 7 unidades, exclusividad de clasificación, reuso de Categorías, permisos `producto.*`, sin eliminación, sin cambios a Supabase.

---

## 15. Plan de implementación para las siguientes etapas

Orden recomendado (no ejecutar ahora):

### Etapa 10.3 — Backend (propuesta)

1. Entidades `Producto`, `Perecedero`, `NoPerecedero` + repositories.
2. `UnidadMedidaCatalog`, DTOs, `ProductoForm`, `ProductoMapper`.
3. `ProductoService` / `Impl` con reglas y transacción de alta/edición.
4. `ProductosController` + reglas en `SecurityConfig`.
5. Retirar placeholder `/productos` de `ModuloController`.
6. Pruebas U/I/S con H2; sin escritura a Supabase.
7. `mvn -B clean verify` con JDK 21.

### Etapa 10.4 — Interfaz (propuesta)

1. Plantillas `productos.html`, `producto-form.html`, `producto-detalle.html` + CSS alineado al dashboard.
2. Sidebar sin “Pendiente”.
3. Campos condicionales de clasificación.
4. Pruebas de vistas/MockMvc y verificación visual autenticada si se autoriza.

### Etapa 10.5 — Validación controlada (propuesta)

1. Arranque local con `ddl-auto=validate`.
2. Pruebas de lectura opt-in contra metamodelo real.
3. Escrituras reales a Supabase **solo** con autorización explícita y cuentas de prueba.

### Fuera de alcance explícito

- Eliminación / desactivación de productos.
- Nuevas tablas o migraciones.
- Refactor de Inventario, Lotes, Recepción o Traslados.
- Proveedores, Movimientos, Reportes.

---

## 16. Criterio de cierre de la Etapa 10.2

Esta etapa queda completa con el presente documento, coherente con:

- el enunciado y las reglas A–G;
- las tablas reales `producto`, `perecedero` y `no_perecedero`;
- el módulo Categorías existente;
- los permisos actuales del recurso `producto`;
- la arquitectura por capas del proyecto;
- la restricción de no modificar Supabase.

**No se implementó código. No se inició la Etapa 10.3.**
