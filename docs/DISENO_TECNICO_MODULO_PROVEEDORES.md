# Diseño técnico — Módulo Proveedores (Etapa 11.2)

**Fecha:** 2026-11-10  
**Proyecto:** `C:\Escritorio\BAD215\AgroFresh\AgroFresh2026-desarrollo`  
**Fuentes:** `docs/ANALISIS_MODULO_PROVEEDORES.md` (Etapa 11.1), patrones de Categorías y Productos, esquema Supabase auditado (metadatos 2026-11-10).  
**Estado:** Solo diseño. Sin implementación, sin cambios en Supabase ni en código de producción.

Leyenda:

| Marca | Significado |
|---|---|
| **Confirmada** | Aprobada por el usuario o evidencia directa del esquema / análisis 11.1. |
| **Propuesta** | Coherente con el proyecto; implementar salvo objeción en 11.3. |
| **Pendiente** | Requiere dato o autorización antes o durante la implementación. |

---

## 1. Resumen del diseño

El módulo Proveedores será un **vertical slice JPA + MVC** alineado con **Categorías** (tabla plana) y **Productos** (seguridad fina por ruta):

```
Vista Thymeleaf → ProveedoresController → ProveedorService → ProveedorRepository → inventario.proveedor
```

**Alcance funcional (11.3+):**

- Listar, buscar (en cliente), registrar, editar y consultar proveedores.
- **Sin eliminación** (ni UI ni servicio).
- **Sin** modificar Compras, Recepción, Lotes ni consultas JDBC existentes.
- **Sin** alterar el esquema de Supabase.

**Integración puntual prevista:**

- Retirar `GET /proveedores` de `ModuloController`.
- Añadir reglas finas en `SecurityConfig` para recurso **`proveedor`** (no reutilizar `producto` como Categorías).
- Quitar badge “Pendiente” en `sidebar.html` al implementar la interfaz.

---

## 2. Decisiones funcionales confirmadas (estructura PostgreSQL)

| Columna | Tipo / restricción | Regla |
|---|---|---|
| `id_proveedor` | IDENTITY, PK | Generado por BD; **nunca** asignar en alta. |
| `nombre` | `varchar(150) NOT NULL` | Obligatorio. |
| `dui` | `varchar(10) NULL`, UNIQUE `uq_proveedor_dui` | **Opcional**; único si tiene valor. |
| `nit` | `varchar(17) NOT NULL`, UNIQUE `uq_proveedor_nit` | **Obligatorio**; único. |
| `correo_electronico` | `varchar(100) NOT NULL`, CHECK `ck_proveedor_correo` | Obligatorio; formato validado en BD. |
| `direccion` | `varchar(200) NOT NULL` | Obligatorio. |
| `telefono` | `varchar(9) NOT NULL` | Obligatorio; máximo 9 caracteres en columna. |
| `iva` | `varchar(50) NOT NULL` | Obligatorio; máximo 50; **sin CHECK** en BD (ver §2.8). |

No existe regla “DUI o NIT” en el esquema: **NIT siempre obligatorio**, DUI complementario opcional.

---

## 3. Reglas de negocio (capa de servicio)

### 3.1 Nombre — **Confirmada**

| Regla | Detalle |
|---|---|
| Obligatorio | Rechazar `null`, vacío o solo espacios tras `trim()`. |
| Longitud | Máximo 150. |
| Unicidad | El esquema **no** exige UNIQUE en nombre; se permiten homónimos. |
| Edición | Permitir conservar el mismo nombre. |

### 3.2 NIT — **Confirmada**

| Regla | Detalle |
|---|---|
| Obligatorio | Rechazar vacío tras `trim()`. |
| Longitud | Máximo 17. |
| Unicidad | `existsByNit` en alta; `existsByNitAndIdProveedorNot` en edición. |
| Concurrencia | Si la comprobación previa pasa y PostgreSQL dispara UNIQUE → `BusinessException` genérica (“Ya existe un proveedor con ese NIT.”). |
| Formato | **Pendiente:** no se imponen máscaras ni dígitos verificadores (no confirmados en requisitos). |

### 3.3 DUI — **Confirmada**

| Regla | Detalle |
|---|---|
| Opcional | Si el usuario deja vacío → persistir **`null`** (no cadena vacía), para aprovechar UNIQUE de PostgreSQL con múltiples NULL. |
| Longitud | Si informado, máximo 10 tras `trim()`. |
| Unicidad | Solo si hay valor: `existsByDui` / `existsByDuiAndIdProveedorNot`. |
| Edición | Conservar el mismo DUI sin falso duplicado; conservar “sin DUI” (`null`). |
| Formato | **Pendiente** (sin reglas inventadas). |

### 3.4 Correo electrónico — **Confirmada**

| Regla | Detalle |
|---|---|
| Obligatorio | Tras `trim()`. |
| Longitud | Máximo 100. |
| Formato | Validar en Java con patrón **equivalente** al CHECK de PostgreSQL (insensible a mayúsculas): |

```text
^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,63}$
```

| Unicidad | **No** hay UNIQUE en BD; **no** validar duplicado de correo salvo decisión futura. |

### 3.5 Dirección — **Confirmada**

Obligatoria; `trim()`; máximo 200. Sin restricción de formato en BD.

### 3.6 Teléfono — **Confirmada**

Obligatorio; `trim()`; máximo 9. **Pendiente:** máscara o solo dígitos (no confirmado en requisitos).

### 3.7 Tipo de IVA — **Confirmada** (obligatoriedad y longitud) + **Pendiente** (catálogo)

| Evidencia | Conclusión |
|---|---|
| Columna `iva varchar(50) NOT NULL` | Campo obligatorio, máximo 50. |
| Sin CHECK en BD | Cualquier texto válido puede persistirse si pasa validación de aplicación. |
| Búsqueda en código y documentación | **No** existe `TipoIvaCatalog`, enum ni lista institucional en el repositorio. |

**Diseño Etapa 11.3 (Propuesta hasta nueva evidencia):**

- Formulario: **campo de texto** o **`<select>` poblado solo tras auditoría de solo lectura** (`SELECT DISTINCT iva …` con autorización), sin inventar opciones en el diseño.
- Servicio: obligatorio + `trim()` + rechazo vacío + máximo 50.
- **Pendiente explícito:** si tras `docs/_audit_proveedor_11_1.py` (o consulta equivalente) aparece un conjunto pequeño y estable de valores históricos, el usuario puede **autorizar** un catálogo cerrado en Java (estilo `UnidadMedidaCatalog`) **replicando exactamente** esos literales, sin añadir valores nuevos de facto inventados.

**Prohibido en 11.3:** hardcodear `Gravado`, `Exento`, contribuyente, etc., sin fuente confirmada.

### 3.8 Eliminación — **Confirmada**

Fuera de alcance. Sin rutas DELETE, sin botones, sin uso de `proveedor.DELETE` en UI aunque existiera en BD.

### 3.9 Edición con historial — **Confirmada** / **Propuesta**

| Aspecto | Diseño |
|---|---|
| FKs `compra`, `catalogo`, `lote` | `RESTRICT`; no bloquean UPDATE de columnas descriptivas del mismo `id_proveedor`. |
| Cambio de `id_proveedor` | Imposible (PK + identity). |
| Edición de nombre, dirección, teléfono, correo, IVA, DUI, NIT | **Permitida** si UNIQUE/CHECK lo permiten (mismo patrón que datos descriptivos de Producto). |
| Bloqueo de NIT/DUI con historial | **No** exigido por esquema; **no** implementar bloqueo salvo autorización explícita posterior. |

No se consultarán ni mostrarán dependencias en detalle (compras/lotes); el módulo no altera tablas hijas.

---

## 4. Arquitectura y componentes

### 4.1 Flujo

```
proveedores.html | proveedor-form.html | proveedor-detalle.html
        ↓
ProveedoresController     ← PermisoService (recurso "proveedor"), CSRF, flash
        ↓
ProveedorService / Impl   ← validaciones, normalización DUI null, integridad
        ↓
ProveedorRepository
        ↓
inventario.proveedor (Supabase)
```

### 4.2 Tabla de componentes — **Propuesta**

| Artefacto | Paquete | Responsabilidad |
|---|---|---|
| `Proveedor` | `model` | Entidad JPA; sin Lombok `@Data`. |
| `ProveedorRepository` | `repository` | JPA + métodos `exists` para NIT/DUI. |
| `ProveedorListadoDto` | `dto` | Grilla (id, nombre, nit, dui, correo, teléfono, iva). |
| `ProveedorDetalleDto` | `dto` | Ficha completa + dirección. |
| `ProveedorForm` | `dto` | Binding formulario alta/edición. |
| `ProveedorMapper` | `mapper` | Entity ↔ DTO; sin entidades en vista. |
| `ProveedorService` | `service` | Contrato transaccional. |
| `ProveedorServiceImpl` | `service.impl` | Validación y persistencia. |
| `ProveedoresController` | `controller` | MVC; no usa repository. |
| `ProveedorCorreoValidator` o constante en servicio | `service` / `util` | Regex alineado al CHECK (opcional clase util). |

**Condicionamiento:** `@ConditionalOnProperty(prefix = "spring.datasource", name = "url")` en servicio (patrón Categorías/Productos).

**No** crear catálogo IVA en `util` hasta resolución de §3.7.

---

## 5. Entidad JPA `Proveedor` — **Propuesta**

```java
@Entity
@Table(
    name = "proveedor",
    schema = "inventario",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_proveedor_dui", columnNames = "dui"),
        @UniqueConstraint(name = "uq_proveedor_nit", columnNames = "nit")
    })
```

| Atributo Java | Columna | Anotaciones |
|---|---|---|
| `idProveedor` | `id_proveedor` | `@Id`, `@GeneratedValue(IDENTITY)`, `updatable = false` |
| `nombre` | `nombre` | `nullable = false`, `length = 150` |
| `dui` | `dui` | `length = 10`, nullable |
| `nit` | `nit` | `nullable = false`, `length = 17` |
| `correoElectronico` | `correo_electronico` | `nullable = false`, `length = 100` |
| `direccion` | `direccion` | `nullable = false`, `length = 200` |
| `telefono` | `telefono` | `nullable = false`, `length = 9` |
| `iva` | `iva` | `nullable = false`, `length = 50` |

Constructor protegido vacío; constructor de negocio o métodos `actualizar(...)` como en `Categoria`/`Producto`. Sin relaciones JPA a `compra`/`catalogo`/`lote` en v1.

---

## 6. Repository — **Propuesta**

```java
public interface ProveedorRepository extends JpaRepository<Proveedor, Integer> {
    List<Proveedor> findAllByOrderByNombreAsc();

    boolean existsByNit(String nit);
    boolean existsByNitAndIdProveedorNot(String nit, Integer idProveedor);

    boolean existsByDui(String dui);
    boolean existsByDuiAndIdProveedorNot(String dui, Integer idProveedor);
}
```

**Nota:** los métodos `existsByDui` deben invocarse solo con DUI normalizado no nulo.

---

## 7. DTO y formulario

### 7.1 `ProveedorListadoDto` — **Propuesta**

Record o clase inmutable: `idProveedor`, `nombre`, `nit`, `dui` (puede ser null en UI como “—”), `correoElectronico`, `telefono`, `iva`.

### 7.2 `ProveedorDetalleDto` — **Propuesta**

Incluye `direccion` y el resto de campos; sin exponer entidad.

### 7.3 `ProveedorForm` — **Propuesta**

Campos alineados al formulario:

- `nombre`, `nit`, `dui`, `correoElectronico`, `direccion`, `telefono`, `iva`

Sin `idProveedor` en alta; en edición el id va en path (`/proveedores/editar/{id}`). Método estático `desde(ProveedorDetalleDto)` para precargar edición (patrón `ProductoForm`).

### 7.4 Mapper — **Propuesta**

- `toListado(Proveedor)`
- `toDetalle(Proveedor)`
- El servicio construye/actualiza entidad desde `ProveedorForm` tras validar.

---

## 8. Servicio `ProveedorService` — **Propuesta**

| Método | Descripción |
|---|---|
| `listar()` | Lista ordenada por nombre → DTO listado. |
| `consultar(Integer id)` | Detalle; `ResourceNotFoundException` si no existe. |
| `registrar(ProveedorForm)` | Alta transaccional. |
| `actualizar(Integer id, ProveedorForm)` | Edición transaccional. |

**Implementación (`ProveedorServiceImpl`):**

1. Normalizar strings (`trim()`).
2. Convertir DUI en blanco → `null`.
3. Validar reglas §3.
4. Comprobar duplicados NIT/DUI con exclusión de id en edición.
5. `saveAndFlush`; capturar `DataIntegrityViolationException` → mensajes de negocio (NIT/DUI duplicado, correo inválido si el CHECK falla).
6. `BusinessException` para errores de validación con mensajes en español, sin filtrar SQL.

**No** incluir métodos `eliminar` ni consultas de dependencia para bloquear edición.

---

## 9. Controller `ProveedoresController` — **Propuesta**

Recurso de permiso: **`proveedor`** (no `producto`).

```java
private void exigirPermiso(Authentication authentication, String accion) {
    if (!permisos.puede(authentication, "proveedor", accion)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "...");
    }
}
```

| Método HTTP | Ruta | Permiso | Resultado |
|---|---|---|---|
| GET | `/proveedores` | SELECT (+ ACCEDER vía SecurityConfig) | `proveedores` |
| GET | `/proveedores/nueva` | INSERT | `proveedor-form` |
| POST | `/proveedores/guardar` | INSERT | redirect `/proveedores/consultar/{id}` + flash éxito |
| GET | `/proveedores/editar/{id}` | UPDATE | `proveedor-form` |
| POST | `/proveedores/actualizar/{id}` | UPDATE | redirect detalle + flash |
| GET | `/proveedores/consultar/{id}` | SELECT | `proveedor-detalle` |

Modelo en listado:

- `proveedores`, `puedeRegistrar` (`INSERT`), `puedeEditar` (`UPDATE`).

Manejo de errores: `BusinessException` → redirect a nueva/edición con flash `error` y `proveedorForm` (patrón Productos/Categorías).

---

## 10. Seguridad y permisos

### 10.1 Limitación Etapa 11.1 — **Confirmada**

La consulta en vivo a `permiso` / `rol_permiso` para `recurso = 'proveedor'` **no se completó** (pooler Supabase saturado). **No se asumen** códigos ni asignaciones no verificadas.

**Antes de codificar 11.3:** ejecutar `docs/_audit_proveedor_11_1.py` (sección permisos) o equivalente en transacción `READ ONLY` y adjuntar resultado al informe de implementación.

### 10.2 Matriz de diseño — **Propuesta** (sujeta a confirmación en BD)

Asumiendo el mismo patrón que `producto` y `usuario` en AGROFRESH:

| Operación | Permiso |
|---|---|
| Entrar al módulo / menú | `proveedor.ACCEDER` |
| Listar / buscar / consultar | `proveedor.SELECT` |
| Registrar | `proveedor.INSERT` |
| Editar | `proveedor.UPDATE` |

| Rol (Spring `ROLE_*`) | Comportamiento esperado **si** la BD sigue el patrón habitual |
|---|---|
| `ROLE_ADMIN` (ADMINISTRADOR) | ACCEDER, SELECT, INSERT, UPDATE (DELETE fuera de UI). |
| `ROLE_OPERADOR` (OPERATIVO) | ACCEDER, SELECT (sin INSERT/UPDATE). |

**Autoridades:** las mismas que el resto del sistema (`ROLE_ADMIN`, `ROLE_OPERADOR`); la autorización fina la resuelve `fn_usuario_tiene_permiso` vía `PermisoService`.

### 10.3 `SecurityConfig` — **Propuesta** (espejo de Productos, recurso `proveedor`)

Añadir **antes** del bucle genérico de rutas:

| Matcher | Permiso |
|---|---|
| `GET /proveedores` | `proveedor.ACCEDER` **y** `proveedor.SELECT` |
| `GET /proveedores/nueva` | `proveedor.INSERT` |
| `POST /proveedores/guardar` | `proveedor.INSERT` |
| `GET /proveedores/editar/**` | `proveedor.UPDATE` |
| `POST /proveedores/actualizar/**` | `proveedor.UPDATE` |
| `GET /proveedores/consultar/**` | `proveedor.SELECT` |

El bucle existente `proveedores` + recurso `proveedor` mantiene **ACCEDER** para subrutas no listadas; las reglas finas prevalecen para las rutas CRUD.

**Sin cambios** en:

- `GET /api/compras/proveedores` (sigue bajo `/compras/**` → permiso `compra.ACCEDER`).

### 10.4 CSRF y denegación — **Confirmada**

POST con token CSRF. Denegación coherente con `403` / `acceso-denegado` según configuración actual.

---

## 11. Rutas y conflictos — **Confirmada**

| Mapping actual | Acción en 11.3 |
|---|---|
| `ModuloController` → `GET /proveedores` (placeholder) | **Eliminar** del array de pendientes (dejar solo movimientos/reportes). |
| `ProveedoresController` → rutas §9 | Registrar controlador nuevo. |
| `RecepcionController` → `/api/compras/proveedores` | **Sin cambios**. |
| `LotesController` JOIN `proveedor` | **Sin cambios**. |

No hay colisión de path entre `/proveedores/**` y APIs bajo `/api/compras/**`.

---

## 12. Diseño de interfaz — **Propuesta**

**Stack:** Thymeleaf, Bootstrap 5.3.3, `categorias.css` / layout header-sidebar-footer (como Productos).

### 12.1 Listado `proveedores.html`

- Columnas: nombre, NIT, DUI (mostrar “—” si null), correo, teléfono, tipo IVA.
- Toolbar: búsqueda en cliente (`input type="search"`) filtrando por **nombre, NIT y DUI** (mismo enfoque que `productos.html` / `categorias.html`).
- CTA “Registrar proveedor” si `puedeRegistrar`.
- Acciones por fila: Consultar; Editar si `puedeEditar`.
- Sin eliminar.

### 12.2 Formulario `proveedor-form.html` (alta/edición compartida)

| Campo UI | Obligatorio en UI | Ayuda UX |
|---|---|---|
| Nombre | Sí | — |
| NIT | Sí | Etiqueta “NIT (obligatorio)” |
| DUI | No | Etiqueta “DUI (opcional)” |
| Correo electrónico | Sí | `type="email"` solo como ayuda; validación en servidor |
| Dirección | Sí | — |
| Teléfono | Sí | `maxlength="9"` |
| Tipo de IVA | Sí | Texto libre **o** select pendiente de §3.7 |

- CSRF oculto, `novalidate` (validación servidor, patrón Categorías).
- Mensajes flash éxito/error.
- Enlace “Volver al listado” → `/proveedores`.

### 12.3 Detalle `proveedor-detalle.html`

- Ficha con todos los campos.
- Botón Editar si `puedeEditar`.
- Sin datos de compras/catálogo/lotes (fuera de alcance).

### 12.4 Sidebar

- `sidebar('proveedores')`, quitar `<span class="nav-pending">` al completar interfaz.

---

## 13. Manejo de errores — **Confirmada**

- `BusinessException` → mensaje usuario.
- `ResourceNotFoundException` → 404 en consulta/edición con id inválido.
- `DataIntegrityViolationException` → traducir a duplicado NIT/DUI o correo inválido.
- `GlobalExceptionHandler` existente; sin nueva dependencia obligatoria.

---

## 14. Plan de pruebas (Etapa 11.3, sin escrituras en Supabase)

| Área | Casos |
|---|---|
| **Servicio** (`ProveedorServiceImplTest`) | Registro válido; NIT obligatorio; DUI omitido → null; DUI duplicado; NIT duplicado; correo inválido; longitudes máximas; edición conservando NIT/DUI propios; actualización con NIT ajeno duplicado rechazada. |
| **Repository** (`ProveedorRepositoryTest`, H2 `MODE=PostgreSQL`, schema `inventario`) | Persistencia identity; UNIQUE NIT/DUI. |
| **Controller unitario** | Permisos denegados antes de servicio; flash éxito; redirect URLs. |
| **MockMvc** (`ModuleViewsTest` / `HomePageTest`) | Vistas renderizadas; CSRF POST guardar; OPERADOR sin botones escritura; SELECT sin INSERT → 403 en `/nueva`. |
| **Búsqueda** | Contenido HTML incluye script de filtro; prueba manual o assert de presencia de `#buscarProveedores` (opcional). |
| **Integración Supabase** | Solo `ddl-auto=validate` tras implementar entidad; **sin** INSERT/UPDATE real en Supabase en pruebas automatizadas. |

---

## 15. Riesgos y mitigaciones

| Riesgo | Severidad | Mitigación en diseño |
|---|---|---|
| Permisos `proveedor` no verificados en vivo | Media | Ejecutar script 11.1 antes de 11.3; ajustar matriz si la BD difiere. |
| IVA sin catálogo confirmado | Media | Campo texto + §3.7 Pendiente; no inventar opciones. |
| Pooler saturado en validación | Baja | Pruebas locales con perfil `local` controlado. |
| Colisión `ModuloController` | Media | Retirar mapping en el mismo PR que el controller. |
| Recepción depende de listado JDBC | Baja | No tocar `RecepcionController`; CRUD JPA independiente. |
| Concurrencia en UNIQUE NIT/DUI | Baja | exists + captura `DataIntegrityViolationException`. |

---

## 16. Dependencias con otros módulos

| Módulo | Relación |
|---|---|
| **Compras / Recepción** | Lee `proveedor`; no modificar. |
| **Lotes** | JOIN por `id_proveedor`; no modificar. |
| **Catálogo (`catalogo`)** | FK a proveedor; edición descriptiva no altera PK. |
| **Productos / Categorías** | Solo patrón arquitectónico; sin cambios cruzados. |
| **Usuarios / permisos** | Usa `PermisoService` existente; sin nuevos permisos en Supabase. |

---

## 17. Archivos a crear o tocar (referencia 11.3 — no ejecutar en 11.2)

**Nuevos:**

- `model/Proveedor.java`
- `repository/ProveedorRepository.java`
- `dto/ProveedorListadoDto.java`, `ProveedorDetalleDto.java`, `ProveedorForm.java`
- `mapper/ProveedorMapper.java`
- `service/ProveedorService.java`, `service/impl/ProveedorServiceImpl.java`
- `controller/ProveedoresController.java`
- `templates/proveedores.html`, `proveedor-form.html`, `proveedor-detalle.html`
- Tests asociados

**Modificar (mínimo):**

- `ModuloController.java` — quitar `/proveedores`.
- `SecurityConfig.java` — reglas finas §10.3.
- `sidebar.html` — quitar pendiente.
- `ModuleViewsTest`, `HomePageTest`, `SecurityLoginTest` — mocks `ProveedorService` si aplica.

**Sin tocar:** `RecepcionController`, `LotesController`, `application-local.properties`, Supabase.

---

## 18. Decisiones pendientes

| # | Tema | Acción sugerida |
|---|---|---|
| 1 | Matriz exacta `permiso`/`rol_permiso` para `proveedor` | Consulta solo lectura antes de 11.3. |
| 2 | Catálogo o valores admitidos de **IVA** | Consulta `DISTINCT iva` agregada; autorizar catálogo Java solo si la lista es oficial. |
| 3 | Formato DUI / NIT / teléfono | Mantener solo longitud hasta requisito formal. |
| 4 | Unicidad de correo | No implementar sin autorización (no hay UNIQUE en BD). |
| 5 | Inmutabilidad de NIT/DUI con compras | No implementar (usuario confirmó edición descriptiva con RESTRICT FK). |

---

## 19. Recomendaciones para la Etapa 11.3

1. Confirmar permisos en Supabase (script 11.1).
2. Decidir control UI de IVA (texto vs select con valores auditados).
3. Implementar backend y pruebas unitarias/H2; `mvn verify`.
4. Implementar vistas y MockMvc; validar CSRF y roles.
5. Arranque `local` + `ddl-auto=validate`.
6. Retirar placeholder y actualizar sidebar.

---

## 20. Condición de cierre Etapa 11.2

- Documento entregado: **`docs/DISENO_TECNICO_MODULO_PROVEEDORES.md`**.
- **Sin** implementación de código de producción.
- **Sin** cambios en Supabase.
- **Detenido — esperar autorización expresa para la Etapa 11.3.**
