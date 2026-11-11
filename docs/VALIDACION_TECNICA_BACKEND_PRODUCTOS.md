# Validación técnica — Backend Productos (Etapa 10.3)

**Fecha:** 2026-11-10  
**Tipo:** Auditoría de solo lectura (sin modificación de código ni Supabase).  
**Fuentes:** código en `src/main/java`, pruebas en `src/test`, `docs/ANALISIS_MODULO_PRODUCTOS.md`, `docs/CONFIGURACION_PERSISTENCIA.md`, `docs/INFORME_IMPLEMENTACION_BACKEND_PRODUCTOS.md`.  
**Supabase en vivo:** no se ejecutó conexión ni consulta de metadatos en esta auditoría.

---

## 1. Resumen ejecutivo

| Área | Veredicto global |
|---|---|
| Entidades JPA vs esquema documentado | **CORRECTO** (con verificaciones pendientes en `ddl-auto=validate` real) |
| Consultas `EXISTS` y columnas | **CORRECTO** respecto a Etapa 10.1 (resolución de esquema vía JDBC) |
| Transacciones y clasificación | **CORRECTO** en flujo nominal; riesgos de concurrencia y datos heredados |
| Exclusividad de clasificación | **CORRECTO** en altas/ediciones nuevas; **RIESGO** en listado y datos históricos |
| Validaciones de negocio | **CORRECTO** en lo solicitado; algunas **OBSERVACIONES** menores |
| Seguridad en 6 rutas | **CORRECTO** (filtro + controller) |
| `ddl-auto` / `open-in-view` / `show-sql` | **CORRECTO** en configuración; mapeo contra Supabase **REQUIERE VERIFICACIÓN** local |
| Conflictos de rutas tras quitar placeholder | **CORRECTO** |

**Conclusión:** El backend es coherente con la arquitectura acordada, las reglas de la Etapa 10.3 y el esquema descrito en la Etapa 10.1. Se recomienda un arranque controlado con perfil `local` y `validate` antes de la Etapa 10.4, y tener plan para productos históricos con clasificación inconsistente o unidades fuera de catálogo.

---

## 2. Entidades: `Producto`, `Perecedero`, `NoPerecedero`

### 2.1 `Producto`

| Aspecto | Hallazgo | Clasificación |
|---|---|---|
| Tabla `inventario.producto`, PK `id_producto` | `@Entity` + `schema = "inventario"`, `@GeneratedValue(IDENTITY)` | **CORRECTO** |
| Columnas: `id_categoria`, `codigo_interno` (20), `nombre` (150), `unidad_medida` (20) | Mapeo con `length` y `nullable` alineado al análisis 10.1 | **CORRECTO** |
| UNIQUE `uq_producto_codigo` | Declarada en `@UniqueConstraint` | **CORRECTO** |
| FK `fk_producto_categoria` | No mapeada como `@ManyToOne`; solo `Integer idCategoria` + validación vía `CategoriaService` | **CORRECTO** (patrón deliberado; integridad final en BD) |
| Sin Lombok `@Data` | Cumple regla del proyecto | **CORRECTO** |

### 2.2 `Perecedero` y `NoPerecedero`

| Aspecto | Hallazgo | Clasificación |
|---|---|---|
| PK compartida `id_producto` | `@Id` + `@MapsId` + `@OneToOne` + `@JoinColumn(id_producto)` | **CORRECTO** (patrón estándar PK/FK compartida) |
| `temp_recomendad` | `BigDecimal` con `precision = 5, scale = 2` → `numeric(5,2)` | **CORRECTO** |
| `vida_util` | `Integer`, NOT NULL; validación app `> 0` (CHECK en BD documentado en 10.1) | **CORRECTO** |
| `fech_fabricacion` | `LocalDate` → `date` | **CORRECTO** |
| `vida_util_anaquel` | `Integer`, validación app `> 0` | **CORRECTO** |
| Nombres físicos de columna | `temp_recomendad`, `fech_fabricacion`, `vida_util_anaquel` coinciden con 10.1 | **CORRECTO** |
| Persistencia de subtipo tras `Producto` guardado | Constructor recibe entidad `Producto` ya `saveAndFlush`; `@MapsId` asigna el mismo id | **CORRECTO** |

### 2.3 Validación Hibernate contra Supabase

| Aspecto | Hallazgo | Clasificación |
|---|---|---|
| `ddl-auto=validate` con identity `GENERATED ALWAYS` | No se ejecutó arranque contra Supabase en esta auditoría | **REQUIERE VERIFICACIÓN** (arranque local autorizado) |
| Subtipos sin FK explícita en anotaciones JPA | Hibernate suele inferir FK desde `@JoinColumn`; debe confirmarse en validate | **REQUIERE VERIFICACIÓN** |

---

## 3. Consultas `EXISTS` en `ProductoRepository`

Consultas nativas implementadas:

```java
lote           → id_producto
movimiento     → id_producto
inventario     → id_producto   // tabla de existencias, no el esquema
detalle_compra → id_producto
catalogo       → id_producto
```

### 3.1 Correspondencia con esquema (Etapa 10.1 / CONFIGURACION_PERSISTENCIA)

| Tabla | Columna usada | Documentación | Clasificación |
|---|---|---|---|
| `lote` | `id_producto` | Presente; además FK lotes→`perecedero` documentada | **CORRECTO** |
| `movimiento` | `id_producto` | Presente; puede ser NULL en filas ajenas al producto — `EXISTS` solo cuenta coincidencias | **CORRECTO** |
| `inventario` | `id_producto` | Tabla compuesta `(id_bodega, id_producto)`; columna existe | **CORRECTO** |
| `detalle_compra` | `id_producto` | Presente | **CORRECTO** |
| `catalogo` | `id_producto` | Presente | **CORRECTO** |

### 3.2 Calificación de esquema en SQL nativo

| Aspecto | Hallazgo | Clasificación |
|---|---|---|
| Tablas sin prefijo `inventario.` | Mismo patrón que controladores JDBC existentes (`lote`, `producto`, etc.) | **CORRECTO** si `search_path` o esquema de sesión incluye `inventario` |
| `FROM inventario` (nombre de tabla homónimo al esquema) | En PostgreSQL es referencia a **tabla**; con `search_path` adecuado apunta a `inventario.inventario` | **REQUIERE VERIFICACIÓN** en sesión JDBC real (no comprobado aquí) |
| `hibernate.default_schema=inventario` | Aplica a DDL/JPQL de entidades; **no** garantiza por sí solo el `search_path` de consultas nativas | **OBSERVACIÓN** |

### 3.3 Política de bloqueo por inventario

| Aspecto | Hallazgo | Clasificación |
|---|---|---|
| `tieneInventario` | Cualquier fila en `inventario`, sin filtrar `cantidad_actual > 0` | **CORRECTO** respecto a la implementación 10.3 (bloqueo más estricto que “solo stock positivo”) |
| Mensaje al usuario | Distingue inventario de movimientos/lotes/compras/catálogo | **CORRECTO** |

---

## 4. Control transaccional y concurrencia

| Aspecto | Hallazgo | Clasificación |
|---|---|---|
| Clase `ProductoServiceImpl` | `@Transactional(readOnly = true)` por defecto | **CORRECTO** |
| `registrar` / `actualizar` | `@Transactional` de escritura | **CORRECTO** |
| Alta: `saveAndFlush(producto)` + `saveAndFlush(subtipo)` | Misma transacción; fallo en subtipo → rollback del producto | **CORRECTO** |
| Cambio de clasificación: `deleteById` + `flush` + `saveAndFlush` nuevo subtipo | Misma transacción; fallo tras borrar → rollback restaura fila anterior | **CORRECTO** |
| Código duplicado | `existsBy...` previo + captura `DataIntegrityViolationException` | **CORRECTO** (alineado con Categorías) |
| Ediciones concurrentes del mismo producto | Sin `@Version` ni bloqueo optimista | **RIESGO** (última escritura gana; posible condición de carrera en código/clasificación) |
| Cambio de clasificación concurrente | Dos transacciones podrían competir; dependencias se revalidan pero sin bloqueo de fila | **RIESGO** bajo volumen; aceptable para MVP si se documenta |

---

## 5. Clasificación doble o incompleta

| Escenario | Comportamiento | Clasificación |
|---|---|---|
| Registro nuevo | Solo una rama en `guardarClasificacion` | **CORRECTO** |
| Formulario con campos de ambos tipos | `validarClasificacion` rechaza | **CORRECTO** |
| Tras persistencia exitosa | Una fila en `perecedero` **o** `no_perecedero` | **CORRECTO** |
| Lectura: 0 o 2 subtipos | `clasificacionExacta` lanza `BusinessException` | **CORRECTO** como detector; **RIESGO** en `listar()` — un solo producto inconsistente impide listar todo el catálogo |
| Edición de producto inconsistente | `actualizar` llama `clasificacionExacta` antes de aplicar cambios — no permite “reparar” por UI sin pasar por lectura previa | **OBSERVACIÓN** (datos heredados requieren intervención en BD o regla futura autorizada) |
| BD sin CHECK de exclusividad | Sigue siendo posible inconsistencia por SQL externo o datos preexistentes | **RIESGO** conocido; mitigado solo en operaciones vía servicio |

---

## 6. Validaciones de negocio

| Regla | Implementación | Clasificación |
|---|---|---|
| Código obligatorio, trim, ≤20, único | `textoObligatorio`, `existsByCodigoInterno(AndIdProductoNot)` | **CORRECTO** |
| Nombre obligatorio, trim, ≤150 | Igual que categorías | **CORRECTO** |
| Categoría obligatoria e existente | `idCategoria > 0` + `categoriaService.consultar` | **CORRECTO** |
| Unidad en alta | Catálogo cerrado (`UnidadMedidaCatalog`) | **CORRECTO** |
| Unidad histórica en edición | `conservaHistorica` si valor igual al almacenado; cambio exige catálogo | **CORRECTO** |
| Unidad histórica con espacios o distinta capitalización respecto al formulario | Comparación exacta tras `trim` del formulario, no del valor en BD | **OBSERVACIÓN** |
| Clasificación obligatoria y exclusiva en formulario | Validación por rama | **CORRECTO** |
| Temperatura | Obligatoria; rechazo por escala > 2 o magnitud > 999.99 (límite del tipo, no regla de negocio inventada) | **CORRECTO** |
| Temperaturas negativas | Permitidas si caben en `numeric(5,2)` | **CORRECTO** (sin límites inventados) |
| `vida_util` / `vida_util_anaquel` | `> 0` en servicio; CHECK en BD como red de seguridad | **CORRECTO** |
| Fecha de fabricación futura | Rechazada (`isAfter(LocalDate.now())`) | **CORRECTO** |
| Eliminación de productos | No implementada | **CORRECTO** |

---

## 7. Autorización: seis rutas y permisos

### 7.1 Matriz implementada

| Ruta | SecurityConfig | ProductosController | Clasificación |
|---|---|---|---|
| GET `/productos` | `ACCEDER` **y** `SELECT` | `SELECT` | **CORRECTO** |
| GET `/productos/nueva` | `INSERT` | `INSERT` | **CORRECTO** |
| POST `/productos/guardar` | `INSERT` | `INSERT` | **CORRECTO** + CSRF global |
| GET `/productos/editar/{id}` | `UPDATE` | `UPDATE` | **CORRECTO** |
| POST `/productos/actualizar/{id}` | `UPDATE` | `UPDATE` | **CORRECTO** + CSRF |
| GET `/productos/consultar/{id}` | `SELECT` | `SELECT` | **CORRECTO** |

### 7.2 Reglas genéricas `/productos/**`

| Aspecto | Hallazgo | Clasificación |
|---|---|---|
| Bucle `rutas[]` con `producto` + `ACCEDER` | Aplica a rutas no cubiertas por matchers específicos anteriores | **CORRECTO** (orden: reglas finas primero) |
| Controller no verifica `ACCEDER` en listado | Solo `SELECT`; acceso al listado ya exige ambos en filtro | **CORRECTO** |
| Sin rutas DELETE | Ningún mapping de eliminación | **CORRECTO** |

Pruebas automatizadas (`HomePageTest`, `ProductosControllerTest`) cubren parte de permisos y CSRF; no sustituyen prueba con `PermisoService` real contra BD.

---

## 8. Compatibilidad JPA (`validate`, `open-in-view`, `show-sql`)

| Propiedad | `application.properties` | Uso en módulo Productos | Clasificación |
|---|---|---|---|
| `ddl-auto=validate` | Activa | Entidades con `schema` y tipos declarados | **CORRECTO** en diseño |
| `open-in-view=false` | Activa | Controller solo recibe DTO; sin exposición de entidades lazy en vista | **CORRECTO** |
| `show-sql=false` | Activa | Sin dependencia de SQL en logs | **CORRECTO** |

| Verificación | Clasificación |
|---|---|
| `EntityManagerFactory` inicializa con las 3 entidades nuevas contra Supabase | **REQUIERE VERIFICACIÓN** (no ejecutada en esta auditoría) |

---

## 9. Rutas y placeholder

| Aspecto | Hallazgo | Clasificación |
|---|---|---|
| `ModuloController` | Ya no mapea `GET /productos`; solo proveedores/movimientos/reportes | **CORRECTO** |
| `ProductosController` | Único dueño de `/productos` y subrutas MVC | **CORRECTO** |
| `TrasladoCatalogoController` | `GET /api/traslados/productos` — prefijo distinto | **CORRECTO** (sin colisión) |
| `RecepcionController` | `GET /api/compras/productos` | **CORRECTO** |
| Sidebar | Sigue mostrando “Pendiente” en Productos | **OBSERVACIÓN** (Etapa 10.4; no afecta backend) |
| Vistas `productos`, `producto-form`, `producto-detalle` | Referenciadas pero no creadas | **OBSERVACIÓN** (esperado en 10.3) |

---

## 10. Arquitectura y capas

| Regla | Cumplimiento | Clasificación |
|---|---|---|
| Controller → Service → Repository | Respetado; controller sin repositorios | **CORRECTO** |
| DTO/form al modelo MVC | `ProductoListadoDto`, `ProductoDetalleDto`, `ProductoForm` | **CORRECTO** |
| Reuso `CategoriaService` | En validación y catálogos del formulario | **CORRECTO** |
| `ProductoService` condicionado a `spring.datasource.url` | Igual patrón que `CategoriaServiceImpl` | **CORRECTO** |
| `ProductosController` sin `@ConditionalOnProperty` | Requiere bean de servicio cuando hay datasource | **OBSERVACIÓN** (consistente con Categorías) |

---

## 11. Pruebas (evidencia indirecta)

| Evidencia | Alcance | Clasificación |
|---|---|---|
| `mvn -B clean verify` documentado en informe 10.3 (JDK 21, 82/83 tests OK) | Compilación + suite automatizada | **CORRECTO** (no re-ejecutado en esta auditoría) |
| H2 aislado en `ProductoRepositoryTest` | PK compartida y UNIQUE código | **CORRECTO** (no es Supabase) |
| Mocks en `ProductoServiceImplTest` | Reglas de negocio y dependencias | **CORRECTO** |
| Integración real de escritura en Supabase | No realizada | **REQUIERE VERIFICACIÓN** si se autoriza después |

---

## 12. Tabla consolidada de hallazgos

| ID | Hallazgo | Clasificación |
|---|---|---|
| H1 | Mapeo de columnas y tipos alineado con 10.1 | **CORRECTO** |
| H2 | PK compartida `@MapsId` coherente con subtipos | **CORRECTO** |
| H3 | EXISTS sobre tablas/columnas documentadas | **CORRECTO** |
| H4 | Resolución de esquema en SQL nativo depende de sesión JDBC | **REQUIERE VERIFICACIÓN** |
| H5 | Transacción evita producto huérfano o subtipo a medias en flujo nominal | **CORRECTO** |
| H6 | Sin control de concurrencia optimista | **RIESGO** |
| H7 | Listado falla entero ante un producto con 0/2 subtipos | **RIESGO** |
| H8 | Unidad histórica con discrepancia de espacios/casing vs formulario | **OBSERVACIÓN** |
| H9 | Bloqueo de cambio de clasificación con inventario aunque stock sea 0 | **CORRECTO** (política 10.3) |
| H10 | Seguridad en 6 rutas coherente con recurso `producto` | **CORRECTO** |
| H11 | Sin conflicto de mappings tras retirar placeholder | **CORRECTO** |
| H12 | `validate` contra Supabase no comprobado en esta auditoría | **REQUIERE VERIFICACIÓN** |

---

## 13. Recomendaciones previas a Etapa 10.4 (sin implementar aquí)

1. Arranque local con perfil `local` y confirmar que Hibernate **valida** las tres entidades sin error.  
2. Confirmar en una sesión JDBC que las consultas `EXISTS` resuelven tablas en `inventario`.  
3. Definir con negocio el tratamiento de productos históricos inconsistentes en listado (aislar fila vs bloquear pantalla).  
4. En 10.4: quitar “Pendiente” del sidebar y crear plantillas referenciadas por el controller.

---

## 14. Confirmaciones de restricciones de esta auditoría

- No se modificó código fuente ni pruebas.  
- No se modificó Supabase ni `config/application-local.properties`.  
- No se ejecutó SQL de escritura ni comandos Git.  
- No se conectó a Supabase para metadatos en esta sesión.

**Estado:** Validación documental completada. Listo para autorización de Etapa 10.4 con las verificaciones locales pendientes (H4, H12) antes de uso operativo real.
