# Análisis técnico — Módulo Productos

**Fecha:** 2026-11-10  
**Proyecto inspeccionado:** `C:\Escritorio\BAD215\AgroFresh\AgroFresh2026-desarrollo`

La consulta a Supabase se realizó con la configuración local existente, dentro de una transacción con `transaction_read_only=on`. Solo se consultaron metadatos, restricciones, índices, relaciones y permisos; no se leyeron filas de productos o usuarios.

## 1. Resumen del estado actual

- Base conectada: `postgres`; esquema activo: `inventario`.
- `producto`, `perecedero` y `no_perecedero` existen con el esquema descrito abajo.
- No hay un módulo de Productos implementado. `ModuloController` sirve el placeholder para `/productos`, y el sidebar marca Productos como pendiente.
- Inventario, recepción, traslados y Lotes consultan productos por JDBC; esas consultas no forman un CRUD de productos.
- Categorías sí tiene entidad, repository, servicio, DTO, mapper y controller. `CategoriaService.listar()` y `consultar(id)` son reutilizables.

## 2. Estructura real de `inventario.producto`

| Columna | Tipo | Longitud/precisión | NULL | Default/identidad |
|---|---|---:|---|---|
| `id_producto` | `integer` | precisión 32, escala 0 | No | identity `GENERATED ALWAYS`, inicio 1, incremento 1 |
| `id_categoria` | `integer` | precisión 32, escala 0 | No | Sin default |
| `codigo_interno` | `character varying` | 20 | No | Sin default |
| `nombre` | `character varying` | 150 | No | Sin default |
| `unidad_medida` | `character varying` | 20 | No | Sin default |

- PK: `pk_producto (id_producto)`.
- UNIQUE: `uq_producto_codigo (codigo_interno)`.
- FK: `fk_producto_categoria`, `id_categoria → categoria.id_categoria`, `ON UPDATE RESTRICT ON DELETE RESTRICT`.
- CHECK: no se encontró CHECK en `producto`.
- Índices: PK, UNIQUE del código y `pertenece_a_categoria_fk` sobre `id_categoria`.
- Secuencia identity: `inventario.producto_id_producto_seq`.

El código interno es obligatorio, único y de hasta 20 caracteres. El esquema no impone formato. Nombre es obligatorio y de hasta 150 caracteres, pero no es UNIQUE. La categoría es obligatoria por nulabilidad y FK. `unidad_medida` es obligatoria, `varchar(20)`.

## 3. Estructura de `inventario.perecedero`

| Columna | Tipo | NULL | Restricción relevante |
|---|---|---|---|
| `id_producto` | `integer` | No | PK y FK a `producto.id_producto` |
| `temp_recomendad` | `numeric(5,2)` | No | Sin default ni CHECK observado |
| `vida_util` | `integer` | No | CHECK `vida_util > 0` |

La PK/FK hace que exista como máximo una fila perecedera por producto. La FK usa `ON UPDATE RESTRICT ON DELETE RESTRICT`. El ID no es identity propio: reutiliza el ID del producto.

## 4. Estructura de `inventario.no_perecedero`

| Columna | Tipo | NULL | Restricción relevante |
|---|---|---|---|
| `id_producto` | `integer` | No | PK y FK a `producto.id_producto` |
| `fech_fabricacion` | `date` | No | Sin default observado |
| `vida_util_anaquel` | `integer` | No | CHECK `vida_util_anaquel > 0` |

La PK/FK hace que exista como máximo una fila no perecedera por producto. La FK usa `ON UPDATE RESTRICT ON DELETE RESTRICT`. El ID también se comparte con `producto`.

## 5. Relaciones e integridad de clasificación

Las FKs entrantes a `producto` que inspeccioné incluyen `catalogo`, `detalle_compra`, `inventario`, `movimiento`, `perecedero` y `no_perecedero`. Las relaciones encontradas usan restricciones de actualización/borrado; además, `lote.id_producto` referencia `perecedero.id_producto` con `ON DELETE RESTRICT`.

La clasificación se representa mediante la presencia de una fila en una tabla subtipo; `producto` no tiene columna de tipo/clasificación. Las dos tablas subtipo tienen PK/FK independientes. No encontré CHECK, constraint intertabla ni trigger no interno que exija exactamente una clasificación o excluya la otra.

Por estructura, un producto **puede no tener ninguna fila subtipo**, puede tener una en cada tabla y, por tanto, **puede pertenecer simultáneamente a ambas**. No consulté productos reales, así que no determiné si esas situaciones existen en los datos actuales.

Cambiar de perecedero a no perecedero implicaría retirar/crear filas en las tablas subtipo. Si el producto tiene lotes vinculados a `perecedero`, la FK RESTRICT puede impedir eliminar esa fila. Las operaciones de alta y cambio de clasificación requieren una regla de negocio explícita y pruebas en una base aislada.

Eliminar un producto puede ser impedido por FKs de catálogo, compras, inventario, movimientos y subtipos. Cambiar su clave tampoco es viable por identity/FKs restrictivas. No se deduce de esto una prohibición de editar los datos descriptivos.

## 6. Unidades de medida

No encontré tabla ni catálogo de unidades en `inventario`. El único campo relacionado es `producto.unidad_medida varchar(20) NOT NULL`; no hay CHECK ni FK que limite sus valores.

El código integrado solo lee `unidad_medida` en la consulta de inventario y no define una lista permitida. La interfaz futura necesita una decisión:

- campo libre de hasta 20 caracteres, que refleja el esquema actual; o
- lista cerrada mantenida por la aplicación, si se aprueba una lista funcional.

No se debe inventar ni agregar un catálogo a Supabase sin autorización.

## 7. Integración con Categorías

La entidad `Categoria` mapea `inventario.categoria`; tiene repository, DTO, mapper y `CategoriaService` con `listar()` y `consultar(id)`. El selector de Producto puede reutilizar `CategoriaService.listar()` y presentar los DTO, sin duplicar su implementación. El nuevo producto guardaría el `id_categoria` seleccionado.

La lista real viene ordenada por nombre. Debe manejarse una lista vacía y el caso de una categoría borrada/desconocida, aunque la FK impide referencias inválidas.

## 8. Permisos existentes

La tabla de permisos contiene para `producto`: `ACCEDER`, `SELECT`, `INSERT`, `UPDATE` y `DELETE`. Las asignaciones inspeccionadas son:

- `ADMINISTRADOR`: las cinco acciones.
- `OPERATIVO`: `ACCEDER` y `SELECT`.

La aplicación convierte esos roles a `ROLE_ADMIN` y `ROLE_OPERADOR`. `SecurityConfig` protege `/productos` por el recurso `producto` y `ACCEDER`; `PermisoService` usa `fn_usuario_tiene_permiso` para validar acciones. No se agregaron permisos. Para el futuro CRUD, alta debe exigir INSERT, edición UPDATE y lectura SELECT; DELETE existe en la tabla, pero no forma parte de las funcionalidades solicitadas.

## 9. Código y rutas existentes

- No existe `Producto`/`Perecedero`/`NoPerecedero` como entidad Java, ni `ProductoRepository`, `ProductoService`, DTO o mapper.
- `InventarioController` consulta producto/código/unidad junto con inventario y bodega para una pantalla de solo lectura.
- `RecepcionController` obtiene productos del catálogo proveedor, con precio y clasificación mediante joins a `perecedero`/`no_perecedero`.
- `TrasladoCatalogoController` filtra productos por tipo y usa inventario; sus consultas existentes no son CRUD de productos.
- `LotesController` une lote y producto para la vista de lotes.
- `ModuloController` tiene actualmente `GET /productos` como placeholder; una futura ruta de CRUD para `/productos` entra en conflicto y ese mapping deberá sustituirse puntualmente.
- El sidebar tiene el enlace `/productos` condicionado por `accesos['producto']` y lo marca pendiente. No existe otra ruta de Categorías en conflicto.
- No encontré pruebas de Producto.

## 10. Reglas respaldadas y decisiones pendientes

| Regla | Evidencia del esquema/código | Decisión pendiente |
|---|---|---|
| Código interno | `varchar(20) NOT NULL UNIQUE` | Formato/patrón; el esquema no lo restringe |
| Nombre | `varchar(150) NOT NULL`, sin UNIQUE | Confirmar si nombres repetidos se permiten |
| Categoría | `id_categoria integer NOT NULL` y FK | El selector debe ser obligatorio; no se definió comportamiento para lista vacía |
| Unidad | `varchar(20) NOT NULL`, sin catálogo ni CHECK | Campo libre o lista cerrada de unidades |
| Clasificación | Tablas subtipo separadas; no hay columna tipo | Si debe ser obligatoria y si exactamente una de las dos es requerida |
| Perecedero | `temp_recomendad numeric(5,2) NOT NULL`, `vida_util integer NOT NULL`, vida positiva | Rango permitido de temperatura |
| No perecedero | `fech_fabricacion date NOT NULL`, `vida_util_anaquel integer NOT NULL`, vida positiva | Si fabricación futura es aceptable |
| Cambio de clasificación | Lotes depende de perecedero con FK RESTRICT | Política cuando existen lotes/historial |
| Edición de código | UNIQUE sobre código | Confirmar que se permite cambiar código y cómo validar duplicado exceptuando el mismo producto |
| Edición con stock/historial | FKs preservan la fila de producto | Qué campos pueden editarse después de movimientos/recepciones |

No hay campo `activo` ni función de eliminación en el alcance. La eliminación no debe agregarse.

## 11. Propuesta de arquitectura

Para un CRUD pequeño y fuertemente asociado a la tabla existente, conviene usar JPA en una vertical slice sin refactorizar los módulos actuales:

- Entidad `Producto` con ID identity, columnas `codigo_interno`, `nombre`, `unidad_medida` y relación obligatoria a `Categoria` (`id_categoria`).
- Entidades/proyecciones de `Perecedero` y `NoPerecedero` con PK compartida `id_producto`, evitando herencia JPA hasta confirmar exclusividad de clasificación.
- `ProductoRepository` para búsqueda de código único y consultas necesarias.
- DTO/form para no exponer entidades; DTO con datos de categoría y clasificación seleccionada.
- Mapper manual siguiendo `CategoriaMapper`.
- `ProductoService` transaccional para alta/edición y regla de clasificación, usando `CategoriaService` para el selector.
- `ProductoController` MVC con vistas Thymeleaf, protegido por `PermisoService` y las reglas de Spring Security existentes.
- Sin nuevas dependencias salvo que una necesidad comprobada las justifique. Mantener `ddl-auto=validate`.

La alternativa de mantener consultas JDBC directas sigue el patrón de varios controladores operativos, pero JPA es preferible aquí por la relación FK, la UNIQUE del código y el patrón de entidad/repository/service ya existente. No se debe crear un Product CRUD por JDBC en el controller.

## 12. Pruebas que corresponderían a la Etapa 10.2

- Repository: código duplicado, búsqueda, consultas paginadas si se aprueban y vínculo de categoría.
- Service: alta/edición, código único, category required, subtype required/exclusivity, campos obligatorios por tipo, actualización del mismo producto y FK faltante.
- Clasificación: ninguna, solo perecedero, solo no perecedero, ambos, transición con lotes existentes; definir resultados con negocio antes de implementarlos.
- Controller/MockMvc: rutas, DTOs, validaciones, CSRF, permisos ADMIN/OPERADOR, categoría y unidad.
- Persistencia: base aislada que reproduzca el esquema; no usar Supabase para pruebas de escritura.

## 13. Riesgos técnicos

- La base no garantiza que cada producto tenga exactamente un subtipo ni que ambos subtipos sean excluyentes.
- Las tablas de clasificación tienen campos NOT NULL y CHECKs que la futura UI debe validar antes de persistir.
- Cambiar/eliminar una clasificación perecedera puede estar bloqueado por lotes existentes.
- No hay lista autorizada de unidades; aceptar cualquier texto o imponer opciones son decisiones funcionales.
- El placeholder `/productos` debe retirarse al implementar el controller real para evitar mappings duplicados.

## 14. Alcance de esta etapa

No se implementó el módulo ni se ejecutaron pruebas de Producto. La única consulta de datos fue a metadatos y permisos en transacción de solo lectura; no se consultaron registros de productos o usuarios. No se ejecutaron INSERT/UPDATE/DELETE, funciones ni procedimientos de negocio. No se modificó Supabase, configuración local, código fuente ni Git. El único archivo creado es este informe.
