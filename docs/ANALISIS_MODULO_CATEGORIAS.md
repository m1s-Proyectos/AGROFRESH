# Análisis técnico — Módulo Categorías

**Fecha:** 2026-11-10  
**Proyecto:** `C:\Escritorio\BAD215\AgroFresh\AgroFresh2026-desarrollo`

## 1. Inspección de Supabase

La conexión se realizó con la configuración local existente, sin mostrar ni guardar sus valores. Todas las consultas se ejecutaron en una transacción PostgreSQL con `transaction_read_only=on`. No se consultaron filas de categoría, producto o usuario.

- Base: `postgres`
- Esquema activo: `inventario`
- Transacción de inspección: solo lectura, cerrada con rollback

### Definición real de `inventario.categoria`

| Columna | Tipo | Longitud/precisión | NULL | Default/identity |
|---|---|---:|---|---|
| `id_categoria` | `integer` | precisión 32, escala 0 | No | identity `GENERATED ALWAYS`, inicio 1, incremento 1 |
| `nombre` | `character varying` | 150 | No | Sin default |
| `descripcion` | `character varying` | 200 | Sí | Sin default |

- PK: `pk_categoria (id_categoria)`.
- UNIQUE: `uq_categoria_nombre (nombre)`.
- CHECK: no se encontraron restricciones CHECK.
- FK salientes: ninguna.
- Índices: índice único de PK por `id_categoria` e índice único por `nombre`.
- Secuencia identity: `inventario.categoria_id_categoria_seq`.

### Relación con productos

`inventario.producto.id_categoria` tiene FK `fk_producto_categoria` a `inventario.categoria.id_categoria`, con `ON UPDATE RESTRICT` y `ON DELETE RESTRICT`. La categoría no se puede eliminar si hay productos que la referencian. La etapa solicitada no incluye eliminación.

## 2. Funcionalidades existentes en el proyecto

No existe implementación de Categorías en el código. La única referencia directa al nombre de la tabla está en la lista de tablas esperadas de `DatabaseSchemaAuditor`. No encontré entidad, repository, service, DTO, mapper, controller, plantilla, CSS ni prueba de categoría.

`Producto` tampoco está implementado como entidad/repository en este destino; los módulos de inventario consultan producto mediante JDBC. La relación se gestiona actualmente en Supabase.

## 3. Rutas y conflictos

- No existe mapping `/categorias` ni `/categoria`; no se encontró una colisión actual.
- `ModuloController` tiene placeholders para `/productos`, `/proveedores`, `/movimientos` y `/reportes`, pero no para Categorías.
- La página de productos del sidebar apunta al placeholder `/productos`; no debe reutilizarse esa ruta para Categorías.
- El sidebar no incluye Categorías.
- `SecurityConfig` protege rutas de módulos mediante `PermisoService`, pero no incluye `/categorias` en su lista. Una ruta nueva no declarada caería en `anyRequest`, que solo comprueba que la cuenta esté habilitada; por eso la futura integración deberá añadir controles explícitos.
- `SessionModelAdvice` no genera actualmente un atributo de acceso para Categorías.

## 4. Seguridad y permisos disponibles

La consulta de solo lectura encontró **cero** permisos para `recurso='categoria'` y, por tanto, cero asignaciones de rol a ese recurso.

Sí existen estos permisos para `recurso='producto'`:

- `ACCEDER`, `SELECT`, `INSERT`, `UPDATE`, `DELETE`.
- `ADMINISTRADOR` tiene asignadas esas acciones.
- `OPERATIVO` tiene `ACCEDER` y `SELECT`; no tiene `INSERT` ni `UPDATE`.

La aplicación mapea esos roles a `ROLE_ADMIN` y `ROLE_OPERADOR`; `PermisoService` invoca `fn_usuario_tiene_permiso` con el ID del usuario, recurso y acción.

### Alternativa sin cambios en Supabase

Puede reutilizarse `producto` como recurso de autorización para Categorías, previa aprobación funcional:

- Entrada al módulo: `producto.ACCEDER`, siguiendo el matcher actual.
- Lecturas: `producto.SELECT` si se desea control de lectura separado.
- Alta: `producto.INSERT`.
- Edición: `producto.UPDATE`.

Con los permisos actuales, `OPERATIVO` podría leer y `ADMINISTRADOR` registrar/editar. Esto acopla Categorías a los permisos de Productos. Si se requiere un recurso independiente `categoria`, faltan tanto los registros de permisos como sus asignaciones; no es posible habilitarlo con `fn_usuario_tiene_permiso` sin una configuración adicional autorizada.

## 5. Interfaz existente

Las vistas actuales reutilizan los fragments de header, sidebar y footer. Las páginas de Inventario y Lotes usan paneles, formularios GET con filtros, resúmenes y tablas (`inventario-panel`, `inventario-filtros`, `inventario-table`). La administración de usuarios usa alertas flash de éxito/error y formularios POST con CSRF. No encontré un patrón de modales necesario.

Para Categorías, el diseño coherente sería una lista/tablero con tabla, acción de alta, acciones de consultar/editar y formulario independiente; no hace falta introducir modal. Se reutilizarían los fragments y estilos existentes, con el enlace del sidebar condicionado por el permiso elegido.

## 6. Propuesta técnica para la Etapa 9.2

La tabla sencilla con identity y dos campos encaja con el patrón JPA existente de `Usuario`. Se propone una vertical slice independiente, sin reemplazar ni refactorizar módulos existentes:

1. **Entidad `Categoria`**: `@Entity`, tabla y esquema explícitos; `idCategoria` con `GenerationType.IDENTITY`; `nombre` `varchar(150)` NOT NULL; `descripcion` `varchar(200)` nullable; UNIQUE con el nombre real de la restricción.
2. **`CategoriaRepository`**: `JpaRepository<Categoria, Integer>`, listado ordenado por nombre y existencia por nombre (con variante que excluya ID para edición).
3. **DTOs**: `CategoriaDto` para lectura y `CategoriaForm` sin ID para registrar/editar.
4. **Mapper**: conversión manual, siguiendo el `UsuarioMapper` actual.
5. **`CategoriaService` / implementación**: transacciones de lectura y escritura; validación, normalización y traducción de errores de persistencia.
6. **`CategoriasController`**: los mappings solicitados, modelo MVC con DTOs y formularios Thymeleaf.
7. **Vistas**: lista, formulario y detalle, usando los fragments compartidos.

No hace falta mapear la relación inversa desde categoría a productos para las funciones solicitadas. No se propone incluir borrado ni desactivación.

## 7. Rutas propuestas

| Método | Ruta | Operación |
|---|---|---|
| GET | `/categorias` | Listar |
| GET | `/categorias/nueva` | Formulario de alta |
| POST | `/categorias/guardar` | Registrar |
| GET | `/categorias/editar/{id}` | Datos/formulario para editar |
| POST | `/categorias/actualizar/{id}` | Guardar edición |
| GET | `/categorias/consultar/{id}` | Consultar detalle |

Todas deben quedar bajo reglas servidoras de autenticación/autorización y CSRF. La plantilla no sustituye esos controles.

## 8. Validaciones y reglas de negocio

- `nombre`: no null, máximo 150 caracteres, aplicar `trim()` antes de comprobar duplicados y persistir; la UNIQUE real es la garantía final ante concurrencia.
- La base permite `nombre=''` porque solo exige NOT NULL. Rechazar vacío/espacios con `@NotBlank` es razonable, pero constituye una validación de aplicación que debe quedar aceptada como regla del módulo.
- `descripcion`: null o vacía permitidos; máximo 200 caracteres.
- Altas: no asignar `id_categoria`; PostgreSQL genera el identity.
- Ediciones: ID positivo y existente; mantener el mismo ID; permitir conservar el nombre propio sin tratarlo como duplicado; rechazar el nombre de otra categoría.
- Capturar violaciones de UNIQUE y presentarlas como duplicado sin exponer el detalle de PostgreSQL.
- No normalizar mayúsculas/minúsculas ni imponer unicidad case-insensitive: no existe esa regla en el esquema.
- Sin acción de eliminar o desactivar. La FK de productos usa `ON DELETE RESTRICT`.

El POM actual no declara explícitamente `spring-boot-starter-validation`. Si se desea usar Bean Validation (`@Valid`, `@NotBlank`, `@Size`), habrá que autorizar/justificar esa dependencia; alternativamente, las mismas reglas se pueden validar en el servicio sin añadirla.

## 9. Pruebas recomendadas

- Servicio con repository mock: alta, normalización, nombre vacío/null, longitudes, duplicados, descripción nullable, actualización, nombre propio, categoría inexistente.
- Repository en base PostgreSQL aislada: ID identity, orden alfabético, UNIQUE y existencia por nombre. No usar Supabase real para escrituras.
- MockMvc: rutas, DTO/modelo, validación, mensajes, CSRF y permisos.
- Seguridad: `ADMINISTRADOR` puede alta/edición; `OPERATIVO` solo leer; denied aun llamando el POST directamente.
- Restricción de producto/categoría documentada; no añadir pruebas de borrado.
- Pruebas de concurrencia de duplicados en base aislada si se necesita validar la carrera frente a UNIQUE.

No ejecuté pruebas porque esta etapa fue de análisis exclusivamente.

## 10. Archivos previsiblemente involucrados

**Nuevos en la futura Etapa 9.2:** entidad, repository, DTO/form, mapper, service/interfaz, implementación, controller, vistas y pruebas de Categorías.

**Ajustes puntuales previsibles:** `SecurityConfig` para las reglas del recurso elegido; `SessionModelAdvice` si se agrega una clave de acceso específica; sidebar para el enlace condicional; pruebas de vistas/rutas. `pom.xml` solo si se aprueba Bean Validation.

**Se conservan:** `UsuarioService`, su implementación, entidades y repositorios existentes, módulos de inventario/recepción/traslados/lotes, `application.properties` y configuración local.

## 11. Decisiones que requieren confirmación

1. Autorizar el uso compartido de permisos del recurso `producto` para Categorías, o posponer el módulo hasta autorizar configuración de permisos `categoria` en Supabase.
2. Confirmar que se rechazan nombres vacíos o compuestos solo por espacios (`@NotBlank`/equivalente).
3. Confirmar si la descripción debe conservar literalmente espacios exteriores o recortarse. El esquema solo especifica nullable y longitud.
4. Decidir si se añade `spring-boot-starter-validation` o se valida manualmente en el servicio.
5. Aceptar el orden del listado por nombre y los endpoints indicados.

## 12. Conclusión y alcance

La estructura real de `inventario.categoria` permite registrar, editar, listar y consultar categorías **sin cambiar el esquema de Supabase**. La relación con `producto` se mantiene intacta y la FK RESTRICT desaconseja agregar una operación de borrado.

Las reglas de alta/edición pueden hacerse cumplir en Java y la UNIQUE existente protege duplicados concurrentes. La autorización también puede implementarse sin cambiar la base si se aprueba reutilizar `producto` como recurso; no existen actualmente permisos `categoria`.

La conexión se cerró con transacción read-only. No se leyeron registros de categorías, usuarios ni datos personales; no se ejecutaron escrituras, migraciones o funciones de negocio. No se modificó código ni configuración. El único archivo creado es este análisis.
