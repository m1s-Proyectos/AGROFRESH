# Informe de implementación — Backend de Productos

**Fecha:** 2026-11-10  
**Etapa:** 10.3  
**Proyecto:** `C:\Escritorio\BAD215\AgroFresh\AgroFresh2026-desarrollo`

## 1. Alcance implementado

Se implementó exclusivamente el backend MVC/JPA del módulo Productos:

- entidades `Producto`, `Perecedero` y `NoPerecedero`;
- repositories JPA y consultas de existencia para dependencias;
- DTO de listado, DTO de detalle, formulario y enum de clasificación;
- mapper manual;
- catálogo Java cerrado de unidades de medida;
- servicio transaccional con reglas de alta, consulta y edición;
- controller MVC con las seis rutas solicitadas;
- autorización fina mediante los permisos existentes de `producto`;
- pruebas unitarias, JPA aisladas, controller y seguridad.

No se crearon vistas Thymeleaf ni se implementó eliminación.

`docs/DISENO_REGLAS_NEGOCIO_PRODUCTOS.md` no estaba presente. Se usaron como referencia `ANALISIS_MODULO_PRODUCTOS.md`, `DISENO_TECNICO_MODULO_PRODUCTOS.md` y las decisiones obligatorias del prompt de la Etapa 10.3.

## 2. Archivos creados

### Producción

- `src/main/java/com/agrofresh/model/Producto.java`
- `src/main/java/com/agrofresh/model/Perecedero.java`
- `src/main/java/com/agrofresh/model/NoPerecedero.java`
- `src/main/java/com/agrofresh/repository/ProductoRepository.java`
- `src/main/java/com/agrofresh/repository/PerecederoRepository.java`
- `src/main/java/com/agrofresh/repository/NoPerecederoRepository.java`
- `src/main/java/com/agrofresh/dto/ClasificacionProducto.java`
- `src/main/java/com/agrofresh/dto/ProductoListadoDto.java`
- `src/main/java/com/agrofresh/dto/ProductoDetalleDto.java`
- `src/main/java/com/agrofresh/dto/ProductoForm.java`
- `src/main/java/com/agrofresh/mapper/ProductoMapper.java`
- `src/main/java/com/agrofresh/util/UnidadMedidaCatalog.java`
- `src/main/java/com/agrofresh/service/ProductoService.java`
- `src/main/java/com/agrofresh/service/impl/ProductoServiceImpl.java`
- `src/main/java/com/agrofresh/controller/ProductosController.java`

### Pruebas

- `src/test/java/com/agrofresh/repository/ProductoRepositoryTest.java`
- `src/test/java/com/agrofresh/service/impl/ProductoServiceImplTest.java`
- `src/test/java/com/agrofresh/controller/ProductosControllerTest.java`

### Documentación

- `docs/INFORME_IMPLEMENTACION_BACKEND_PRODUCTOS.md`

## 3. Archivos modificados

- `src/main/java/com/agrofresh/security/SecurityConfig.java`: reglas específicas SELECT/INSERT/UPDATE para Productos.
- `src/main/java/com/agrofresh/controller/ModuloController.java`: se retiró únicamente `/productos` del placeholder.
- `src/test/java/com/agrofresh/HomePageTest.java`: mock del nuevo servicio y pruebas de permisos/CSRF.
- `src/test/java/com/agrofresh/controller/ModuleViewsTest.java`: mock del nuevo servicio para conservar el contexto existente.
- `src/test/java/com/agrofresh/security/SecurityLoginTest.java`: mock del nuevo servicio para conservar el contexto existente.

No se modificaron Categorías, Lotes, Inventario, Compras ni Traslados en código de producción.

## 4. Entidades y relaciones

### `Producto`

Mapea `inventario.producto`:

- `id_producto`: identity, PK.
- `id_categoria`: obligatorio.
- `codigo_interno`: obligatorio, `varchar(20)`, UNIQUE `uq_producto_codigo`.
- `nombre`: obligatorio, `varchar(150)`.
- `unidad_medida`: obligatoria, `varchar(20)`.

El identificador de categoría se mapea como escalar. Su existencia se valida reutilizando `CategoriaService`; no se exponen ni modifican entidades de Categorías.

### `Perecedero`

Mapea `inventario.perecedero` con PK compartida mediante `@MapsId`:

- `id_producto`: PK/FK a Producto.
- `temp_recomendad`: `BigDecimal`, precisión 5 y escala 2.
- `vida_util`: entero obligatorio y positivo.

### `NoPerecedero`

Mapea `inventario.no_perecedero` con PK compartida mediante `@MapsId`:

- `id_producto`: PK/FK a Producto.
- `fech_fabricacion`: `LocalDate`, obligatoria y no futura.
- `vida_util_anaquel`: entero obligatorio y positivo.

No se usó herencia JPA ni cascadas hacia Categorías.

## 5. Reglas de negocio aplicadas

- Código y nombre se recortan con `trim()`.
- Código obligatorio, máximo 20 y único; al editar puede conservarse el código propio.
- Nombre obligatorio y máximo 150.
- Categoría obligatoria y validada por `CategoriaService`.
- Altas aceptan únicamente: Unidad, Kilogramo, Gramo, Litro, Mililitro, Caja o Paquete.
- En edición se puede conservar exactamente la unidad histórica existente aunque esté fuera del catálogo; cualquier cambio debe elegir una opción válida.
- Clasificación obligatoria y exclusiva; se rechazan formularios que contengan campos de ambos subtipos.
- La temperatura no recibe límites funcionales inventados; solo se valida compatibilidad con `numeric(5,2)`.
- Fecha de fabricación futura rechazada.
- Alta y cambio de clasificación son transaccionales.
- Los productos persistidos sin subtipo o con ambos subtipos se reportan como inconsistencia controlada.
- No existe operación de eliminación o desactivación.

La restricción UNIQUE sigue siendo la protección final ante concurrencia. Las violaciones de integridad se traducen a `BusinessException` sin exponer detalles internos.

## 6. Dependencias verificadas antes del cambio de clasificación

`ProductoRepository` ejecuta consultas `EXISTS` de solo lectura sobre:

1. `lote`;
2. `movimiento`;
3. `inventario` (cualquier registro, incluida existencia cero, para preservar historia);
4. `detalle_compra`;
5. `catalogo`.

Si existe cualquiera, el cambio se bloquea con un mensaje específico. No se elimina, convierte ni transforma información asociada. Los cambios de datos descriptivos sin cambio de clasificación permanecen permitidos.

## 7. Rutas y seguridad

| Método | Ruta | Permiso |
|---|---|---|
| GET | `/productos` | ACCEDER + SELECT |
| GET | `/productos/nueva` | INSERT |
| POST | `/productos/guardar` | INSERT + CSRF |
| GET | `/productos/editar/{id}` | UPDATE |
| POST | `/productos/actualizar/{id}` | UPDATE + CSRF |
| GET | `/productos/consultar/{id}` | SELECT |

El controller vuelve a comprobar el permiso de negocio en servidor. No llama repositories ni expone entidades a los modelos MVC.

Las rutas devuelven los nombres de plantilla previstos (`productos`, `producto-form`, `producto-detalle`), pero esas vistas no se crean en esta etapa.

## 8. Pruebas

Cobertura añadida:

- altas perecedera y no perecedera;
- PK compartida y código UNIQUE en H2 aislado;
- código duplicado;
- nombre vacío y mayor de 150;
- categoría inexistente;
- unidad inválida;
- conservación de unidad histórica;
- cambio de clasificación permitido;
- bloqueo individual por lotes, movimientos, inventario, compras y catálogo;
- exclusividad de clasificación y detección de datos persistidos inconsistentes;
- fecha futura;
- DTOs y delegación del controller;
- permisos SELECT/INSERT/UPDATE y CSRF.

Las pruebas JPA usaron H2 en memoria con esquema aislado. No se ejecutaron escrituras contra Supabase.

## 9. Validación Maven real

Entorno:

- JDK: 21.0.10 seleccionado explícitamente mediante `JAVA_HOME`.
- Comando: `mvn -B clean verify`.
- Resultado: **BUILD SUCCESS**.
- Total: **83 pruebas contabilizadas**.
- Exitosas: **82**.
- Fallidas: **0**.
- Errores: **0**.
- Omitidas: **1** (`UsuarioRepositoryPostgresTest`, opt-in).
- Tiempo informado por Maven: **29.070 s**.

La primera ejecución de `mvn test` detectó fixtures de contexto sin mock para el nuevo `ProductoService`; se ajustaron `ModuleViewsTest` y `SecurityLoginTest`. La ejecución posterior y `clean verify` finalizaron correctamente.

## 10. Riesgos y limitaciones

- La exclusividad entre subtipos no está garantizada por PostgreSQL; depende del servicio para nuevas operaciones.
- Los datos históricos ya inconsistentes requieren revisión manual autorizada; el backend no los repara automáticamente.
- El listado consulta clasificación y categoría por producto; si el volumen crece, podría requerir una consulta proyectada optimizada en una etapa autorizada.
- Las vistas aún no existen; corresponde a la Etapa 10.4.
- No se validó una escritura real ni el arranque autenticado del módulo contra Supabase.
- Las consultas de dependencias se diseñaron según las tablas y columnas confirmadas en la Etapa 10.1; `ddl-auto=validate` deberá comprobar el mapeo en una validación local autorizada.

## 11. Confirmaciones

- Supabase no fue modificado.
- No se ejecutó SQL de escritura ni migraciones.
- `config/application-local.properties` no fue leído ni modificado.
- No se mostraron credenciales, contraseñas, hashes ni tokens.
- No se ejecutaron comandos Git.
- No se instalaron dependencias.
- No se implementó la interfaz ni se avanzó a la Etapa 10.4.
