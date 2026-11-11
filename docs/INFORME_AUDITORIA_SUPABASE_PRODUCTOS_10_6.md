# Informe de auditoría Supabase — Módulo Productos (Etapa 10.6)

**Fecha:** 2026-11-10  
**Proyecto:** AGROFRESH — `C:\Escritorio\BAD215\AgroFresh\AgroFresh2026-desarrollo`  
**Tipo:** Solo lectura (metadatos + agregados). **Sin** INSERT/UPDATE/DELETE/DDL en Supabase.  
**Conexión:** perfil `local` existente (`config/application-local.properties`); credenciales **no** documentadas en este informe.  
**Evidencia cruda:** `docs/migrations/productos/_audit_output.txt` (generada localmente; evitar versionar si contiene nombre de rol de BD).

---

## 1. Resumen ejecutivo

| Área | Clasificación | Conclusión breve |
|---|---|---|
| Esquema `inventario` vs entidades JPA | **SIN PROBLEMAS** | Columnas, tipos, PK/FK, UNIQUE y CHECK alineados con `Producto`, `Perecedero`, `NoPerecedero` |
| `ddl-auto=validate` (perfil `local`) | **SIN PROBLEMAS** | `EntityManagerFactory` inicializado sin error de validación Hibernate |
| Consultas `EXISTS` en sesión real | **SIN PROBLEMAS** *con `search_path` actual* | Las cinco consultas resuelven correctamente; con `search_path = public` fallan |
| Integridad de clasificación (estructura) | **MEDIO** | No hay restricción BD que prohíba doble subtipo o producto sin subtipo; FK 1:1 por PK compartida |
| Datos históricos (agregados) | **SIN PROBLEMAS** | Conteos de anomalías = 0 en el entorno auditado |
| Migraciones obligatorias | **Ninguna** | Scripts opcionales en `docs/migrations/productos/` |

**Recomendación técnica:** **Autorizar uso del módulo Productos** contra este esquema Supabase sin migraciones estructurales previas. Opcionalmente, tras autorización, aplicar endurecimiento **MIG-10.6-OPT-01** y/o **OPT-02**, o calificar esquema en SQL nativo en código (fuera del alcance de esta etapa).

---

## 2. Metodología

1. Consultas SQL de solo lectura vía `docs/migrations/productos/_run_audit_readonly.py` (conexión tomada del archivo local, sin imprimir secretos).  
2. Catálogo SQL de referencia: `docs/migrations/productos/audit_readonly_queries.sql`.  
3. Arranque Spring Boot `spring-boot:run` con perfil `local` y puerto alterno **8081**; lectura de log hasta `Initialized JPA EntityManagerFactory`.  
4. Contraste manual entidades Java ↔ `information_schema` / `pg_constraint` / `pg_indexes`.

---

## 3. Estado real del esquema (PostgreSQL / `inventario`)

### 3.1 Tablas inspeccionadas

Todas presentes: `producto`, `perecedero`, `no_perecedero`, `categoria`, `lote`, `movimiento`, `inventario` (tabla de existencias), `detalle_compra`, `catalogo`.

**Sesión JDBC (misma familia de conexión que el proyecto):**

- `current_schema()` → `inventario`
- `search_path` → `inventario, public`

### 3.2 `inventario.producto`

| Columna | PostgreSQL | JPA `Producto` | Estado |
|---|---|---|---|
| `id_producto` | `integer`, NOT NULL, **IDENTITY**, PK | `@GeneratedValue(IDENTITY)` | **SIN PROBLEMAS** |
| `id_categoria` | `integer`, NOT NULL, FK → `categoria` | `Integer idCategoria` | **SIN PROBLEMAS** |
| `codigo_interno` | `varchar(20)`, NOT NULL, UNIQUE `uq_producto_codigo` | `length = 20` | **SIN PROBLEMAS** |
| `nombre` | `varchar(150)`, NOT NULL | `length = 150` | **SIN PROBLEMAS** |
| `unidad_medida` | `varchar(20)`, NOT NULL | `length = 20` | **SIN PROBLEMAS** |

Secuencia identity reportada: `inventario.producto_id_producto_seq`.

### 3.3 `inventario.perecedero`

| Columna | PostgreSQL | JPA `Perecedero` | Estado |
|---|---|---|---|
| `id_producto` | `integer`, NOT NULL, PK, **no identity** | `@Id` + `@MapsId` + `@OneToOne` | **SIN PROBLEMAS** |
| `temp_recomendad` | `numeric(5,2)`, NOT NULL | `BigDecimal precision=5 scale=2` | **SIN PROBLEMAS** |
| `vida_util` | `integer`, NOT NULL, CHECK `> 0` | `Integer vidaUtil` | **SIN PROBLEMAS** |

FK: `id_producto` → `producto(id_producto)` RESTRICT/RESTRICT.

### 3.4 `inventario.no_perecedero`

| Columna | PostgreSQL | JPA `NoPerecedero` | Estado |
|---|---|---|---|
| `id_producto` | `integer`, NOT NULL, PK, **no identity** | `@MapsId` | **SIN PROBLEMAS** |
| `fech_fabricacion` | `date`, NOT NULL | `LocalDate` | **SIN PROBLEMAS** |
| `vida_util_anaquel` | `integer`, NOT NULL, CHECK `> 0` | `Integer vidaUtilAnaquel` | **SIN PROBLEMAS** |

FK: `id_producto` → `producto(id_producto)` RESTRICT/RESTRICT.

### 3.5 `inventario.categoria` (dependencia del módulo)

| Columna | PostgreSQL | JPA `Categoria` | Estado |
|---|---|---|---|
| `id_categoria` | `integer`, IDENTITY, PK | `GenerationType.IDENTITY` | **SIN PROBLEMAS** |
| `nombre` | `varchar(150)`, UNIQUE | `length = 150` | **SIN PROBLEMAS** |
| `descripcion` | `varchar(200)`, nullable | `length = 200` | **SIN PROBLEMAS** |

### 3.6 Tablas de dependencia (bloqueo de cambio de clasificación)

| Tabla | Columna | FK / nota | Implicación para Productos |
|---|---|---|---|
| `lote` | `id_producto` | FK → **`perecedero(id_producto)`** (no a `producto`) | Solo productos perecederos pueden tener lotes; coherente con reglas de negocio |
| `movimiento` | `id_producto` | FK → `producto(id_producto)` | Dependencia histórica genérica |
| `inventario` | `id_producto` | FK → `producto`, PK compuesta con `id_bodega` | Dependencia de existencias |
| `detalle_compra` | `id_producto` | FK → `producto` | Dependencia en compras |
| `catalogo` | `id_producto` | FK → `producto`, PK compuesta con proveedor | Dependencia en catálogo |

Índices: PK/UNIQUE/FK indexados según `pg_indexes` (nombres legacy tipo `pertenece_a_categoria_fk`, `pk_producto`, etc.) — **SIN PROBLEMAS** para operación habitual.

---

## 4. Compatibilidad con entidades Java

| Tema | Hallazgo | Clasificación |
|---|---|---|
| `@MapsId` y PK compartida | Subtipos usan mismo `id_producto` que `producto`; sin identity en subtablas | **SIN PROBLEMAS** |
| Tipos `BigDecimal` / `LocalDate` | Coinciden con `numeric(5,2)` y `date` | **SIN PROBLEMAS** |
| Nombres de columna | `temp_recomendad`, `fech_fabricacion`, `vida_util_anaquel` mapeados | **SIN PROBLEMAS** |
| `schema = "inventario"` en `@Table` | Tablas en esquema correcto | **SIN PROBLEMAS** |
| Categoría solo por `id_categoria` | Igual que diseño; FK en BD | **SIN PROBLEMAS** |

---

## 5. Resultado `ddl-auto=validate`

**Ejecución:** `mvn spring-boot:run -Dspring-boot.run.profiles=local` (puerto **8081** por conflicto en 8080).

**Resultado:** Log contiene  
`Initialized JPA EntityManagerFactory for persistence unit 'default'`  
sin `SchemaManagementException` ni mensajes de validación fallida.

**Clasificación:** **SIN PROBLEMAS** — el mapeo Hibernate del módulo Productos (y el resto del modelo cargado) es compatible con el esquema físico auditado.

---

## 6. Resultado consultas `EXISTS` (`ProductoRepository`)

SQL auditado (id de prueba `-1`, sin filas reales):

```text
FROM lote / movimiento / inventario / detalle_compra / catalogo
WHERE id_producto = :id
```

### 6.1 Con `search_path` por defecto de la conexión (`inventario, public`)

| Consulta | Ejecuta sin error | Resultado (id=-1) |
|---|---|---|
| `lote` | Sí | `false` |
| `movimiento` | Sí | `false` |
| `inventario` (tabla) | Sí | `false` |
| `detalle_compra` | Sí | `false` |
| `catalogo` | Sí | `false` |

Variantes con prefijo `inventario.` — mismo comportamiento.

**Clasificación:** **SIN PROBLEMAS** en el entorno configurado.

### 6.2 Simulación `SET search_path TO public`

| Consulta `FROM lote` | Resultado |
|---|---|
| Sin prefijo | **ERROR:** `relation "lote" does not exist` |

**Clasificación:** **MEDIO** — riesgo de entorno distinto (rol sin `search_path`, otra URL de pooler, tests JDBC aislados). No afecta la sesión actual del proyecto, pero sí la portabilidad del SQL nativo.

**Mitigaciones propuestas (sin ejecutar):**

- **A (código, recomendada en 10.3/10.5):** calificar tablas `inventario.lote`, etc.  
- **B (BD opcional):** `MIG-10.6-OPT-01_search_path.sql` — fijar `search_path` en el rol de aplicación.

---

## 7. Integridad de clasificaciones (estructura)

| Garantía deseada | ¿La impone la BD hoy? | Evaluación |
|---|---|---|
| No perecedero y no perecedero simultáneos | **No** (solo dos tablas 1:1 independientes) | **MEDIO** — aplicación + datos actuales correctos |
| Todo producto con exactamente una clasificación | **No** — puede existir `producto` sin fila en subtipo | **MEDIO** |
| PK compartida coherente | **Sí** — PK subtipo = FK a `producto` | **SIN PROBLEMAS** |
| Perecedero obligatorio para `lote` | **Sí** — FK `lote` → `perecedero` | **SIN PROBLEMAS** (refuerza negocio) |

No se asume necesidad de migración: en el entorno auditado los agregados son 0 para todas las anomalías de clasificación (§ 8).

**Propuesta opcional:** `MIG-10.6-OPT-02_clasificacion_exclusiva.sql` (triggers) — solo si se desea defensa en profundidad frente a SQL manual o futuros clientes JDBC.

---

## 8. Datos históricos (solo agregados)

Sin listar códigos, nombres ni identificadores de filas.

| Métrica | Valor |
|---|---|
| Productos en `producto` | 30 |
| Filas en `perecedero` | 15 |
| Filas en `no_perecedero` | 15 |
| Productos sin clasificación | **0** |
| Productos con ambas clasificaciones | **0** |
| `perecedero` huérfano (sin `producto`) | **0** |
| `no_perecedero` huérfano | **0** |
| Productos con categoría inexistente | **0** |
| Productos distintos referenciados en `lote` | 15 |
| En `movimiento` | 26 |
| En tabla `inventario` | 30 |
| En `detalle_compra` | 30 |
| En `catalogo` | 30 |

**Interpretación:**

- Partición 15+15 = 30 confirma clasificación exclusiva **en datos actuales**.
- Alta proporción de productos con dependencias en inventario/compras/catálogo → muchos cambios de clasificación serán bloqueados por lógica de negocio y/o FK; coherente con `ProductoServiceImpl`.
- **No** se requiere script de saneamiento automático de datos.

**Clasificación datos históricos:** **SIN PROBLEMAS** (conteos de inconsistencia en cero).

---

## 9. Problemas y clasificación

| ID | Hallazgo | Clasificación | Bloquea el módulo hoy |
|---|---|---|---|
| P-01 | SQL nativo sin esquema depende de `search_path` | **MEDIO** | **No** (sesión actual incluye `inventario`) |
| P-02 | Sin trigger/CHECK de exclusividad de subtipos | **MEDIO** | **No** (datos limpios; validación en app) |
| P-03 | Posible producto sin subtipo si INSERT directo en BD | **MEDIO** | **No** en flujo normal de la app |
| P-04 | `listar()` sensible a inconsistencias futuras (Etapa 10.5) | **ALTO** (aplicación) | Solo si aparecen datos corruptos **fuera** de la app |
| — | Esquema vs JPA, validate, EXISTS con path actual | **SIN PROBLEMAS** | — |

**CRÍTICO:** ninguno detectado en esquema ni en datos agregados actuales.

---

## 10. Migraciones propuestas (no ejecutadas)

Ubicación: `docs/migrations/productos/`

| Migración | ¿Obligatoria? | Objetivo |
|---|---|---|
| *(ninguna estructural)* | No | El esquema actual soporta registrar/consultar/editar perecederos y no perecederos |
| **MIG-10.6-OPT-01** `search_path` | Opcional | Evitar fallo de `EXISTS` si cambia configuración del rol |
| **MIG-10.6-OPT-02** triggers exclusividad | Opcional | Endurecer integridad de clasificación en BD |

Cada script incluye verificaciones previas, SQL propuesto, verificación posterior y reversión documentada.

**IMPORTANTE:** Ningún script se ha aplicado en Supabase en esta etapa.

---

## 11. Riesgos e impacto

| Riesgo | Impacto | Mitigación propuesta |
|---|---|---|
| Cambio de `search_path` en Supabase/pooler | Consultas de dependencia fallan; edición sin bloqueo correcto | OPT-01 o calificar SQL en código |
| Inserción manual en BD de subtipos duplicados | Listado/consulta pueden lanzar error de negocio | OPT-02 + gobernanza de acceso SQL |
| Cambio perecedero → no perecedero con lotes | FK `lote` → `perecedero` + flags `exists` | Ya cubierto por diseño; migración de datos manual si algún día se requiere |
| Aplicar OPT-02 con conteos > 0 | Migración abortada por diseño | Resolver datos con plan autorizado (sin UPDATE automático en esta etapa) |

**Módulos ajenos:** migraciones opcionales no alteran tablas fuera de triggers en subtipos; OPT-01 solo afecta rol.

---

## 12. Pruebas pendientes (post-10.6)

| Prueba | Prioridad |
|---|---|
| `ProductoRepositoryPostgresTest` de solo lectura (`EXISTS` con id real conocido en entorno de prueba) | Media |
| QA manual autenticada alta perecedero / no perecedero | Media |
| Confirmar mismo `search_path` en conexión del pooler que usa Spring en producción | Baja (evidencia actual favorable) |

---

## 13. Capacidades del módulo frente a la BD auditada

| Capacidad | ¿Soportada por esquema + datos actuales? |
|---|---|
| Registrar productos perecederos | **Sí** |
| Registrar productos no perecederos | **Sí** |
| Consultar productos | **Sí** |
| Editar productos | **Sí** |
| Integridad de clasificaciones (flujo app) | **Sí** |
| Consultar categorías | **Sí** (tabla compatible) |
| Validar dependencias antes de cambiar clasificación | **Sí** (consultas resuelven; datos con dependencias presentes) |

---

## 14. Confirmación Supabase

- **No** se ejecutaron INSERT, UPDATE, DELETE, TRUNCATE, CREATE, ALTER ni DROP en Supabase durante la Etapa 10.6.
- **Sí** se ejecutaron consultas de metadatos y agregados en modo solo lectura.

---

## 15. Cierre

La auditoría real confirma que **no se requieren migraciones obligatorias** para operar el módulo Productos contra el esquema Supabase actual. Se entregan **dos migraciones opcionales** preparadas y sin ejecutar.

**DETENIDO — a la espera de autorización explícita para ejecutar migraciones o cambios en código/SQL.**
