# Informe de validación integral — Módulo Productos (Etapa 10.5)

**Fecha:** 2026-11-10  
**Proyecto:** `C:\Escritorio\BAD215\AgroFresh\AgroFresh2026-desarrollo`  
**Alcance:** Validación de las etapas 10.1–10.4 (análisis, backend, interfaz) sin modificar código, Supabase ni `config/application-local.properties`.  
**Tipo de validación:** Pruebas automatizadas, arranque controlado con perfil `local`, comprobaciones HTTP sin autenticación y revisión de evidencias documentales previas.

---

## 1. Resumen ejecutivo

| Resultado global | Detalle |
|---|---|
| **BUILD Maven** | `mvn -B clean verify` con **JDK 21** → **BUILD SUCCESS** (86 tests; 0 fallos; 0 errores; **1 omitido**) |
| **Integración AGROFRESH** | Rutas `/productos` activas; placeholder de módulo retirado; sidebar actualizado; seguridad alineada al recurso `producto` |
| **Supabase** | **No** se ejecutaron INSERT, UPDATE ni DELETE. Arranque con `ddl-auto=validate` contra la BD configurada en perfil `local` **sin errores de validación de esquema** |
| **Pruebas visuales autenticadas** | **No realizadas** (sin credenciales de usuario autorizadas para esta etapa) |
| **Consultas `EXISTS` en PostgreSQL vivo** | **No ejecutadas de forma aislada** en esta etapa (ver § 5.4) |

**Veredicto:** El módulo es **integrable y coherente** con la arquitectura y las pruebas existentes. Quedan **riesgos documentados** (datos históricos inconsistentes, cobertura parcial en PostgreSQL real) que requieren decisión antes de correcciones.

---

## 2. Restriciones cumplidas

| Restricción | Cumplimiento |
|---|---|
| No modificar Supabase (datos/estructura) | Cumplido: solo lectura en arranque (`validate`, auditoría de metadatos, `SELECT 1`) |
| No POST/PUT reales de productos | Cumplido: escrituras solo en H2/MockMvc con mocks |
| No modificar `config/application-local.properties` | Cumplido |
| No comandos Git | Cumplido |
| No implementar funcionalidades ni corregir defectos automáticamente | Cumplido |
| No revelar credenciales en este informe | Cumplido |

---

## 3. Pruebas técnicas ejecutadas

### 3.1 Maven (`mvn -B clean verify`, JDK 21)

**Comando:** `mvn -B clean verify` con `JAVA_HOME` apuntando a JDK 21.

**Resultado:** `BUILD SUCCESS`

| Métrica | Valor |
|---|---|
| Tests totales | 86 |
| Fallos | 0 |
| Errores | 0 |
| Omitidos | 1 (`UsuarioRepositoryPostgresTest`, condicionado a `-Dagrofresh.postgres.it=true`) |

**Suites relevantes al módulo Productos:**

| Clase de prueba | Tests | Resultado |
|---|---|---|
| `ProductoServiceImplTest` | 7 | OK |
| `ProductoRepositoryTest` | 2 | OK (H2, esquema `inventario`) |
| `ProductosControllerTest` | 5 | OK |
| `ModuleViewsTest` (productos) | 4 métodos dentro de 13 | OK |
| `HomePageTest` (productos/permisos/CSRF) | 3 métodos dentro de 13 | OK |

### 3.2 Arranque con `ddl-auto=validate` (perfil `local`)

**Acción:** `mvn spring-boot:run -Dspring-boot.run.profiles=local` (JDK 21).

**Evidencias en log (sin credenciales):**

- `Initialized JPA EntityManagerFactory for persistence unit 'default'` — Hibernate completó `validate` sin excepción de esquema.
- `DatabaseSchemaAuditor` listó columnas y restricciones de `producto`, `perecedero` y `no_perecedero` (tipos, PK, FK a `categoria`, UNIQUE `codigo_interno`, CHECK de vida útil).
- Tomcat escuchó en puerto **8080**; el proceso de validación finalizó tras las comprobaciones HTTP (ver § 3.3).

**Configuración base:** `spring.jpa.hibernate.ddl-auto=validate` en `src/main/resources/application.properties` (el perfil `local` no altera este comportamiento en lo observado).

### 3.3 Comprobaciones HTTP sin sesión (solo lectura de respuestas)

| URL | Código HTTP | Observación |
|---|---|---|
| `GET /login` | 200 | Página de login accesible |
| `GET /productos` | 302 → `/login` | Ruta protegida; redirección a autenticación |
| `GET /productos/nueva` | 302 → `/login` | Idem |
| `GET /css/categorias.css` | 200 | Recursos estáticos del módulo disponibles |

**Herramienta:** `curl.exe` contra instancia local en 8080 durante el arranque de validación.

### 3.4 Pruebas visuales en navegador

**No realizadas** con usuario autenticado (ADMINISTRADOR / OPERATIVO). No se dispuso de credenciales autorizadas para esta etapa y no se ejecutaron altas ni ediciones reales.

**Evidencia alternativa:** validación de HTML vía MockMvc (`ModuleViewsTest`) y comprobaciones HTTP anteriores.

### 3.5 Consultas de dependencias (`EXISTS`) en JDBC real (Supabase)

**Estado:** **No ejecutadas explícitamente** en PostgreSQL en esta etapa.

**Motivo:** No existe una prueba de integración dedicada a `ProductoRepository` con perfil `local` (equivalente a `UsuarioRepositoryPostgresTest`). Las cinco consultas nativas `SELECT EXISTS(...)` se ejercitan en:

- Lógica de negocio con **mocks** (`ProductoServiceImplTest.bloqueaCambioPorCadaDependenciaHistorica`).
- Persistencia **H2** en modo PostgreSQL (`ProductoRepositoryTest`), sin tablas `lote`, `movimiento`, etc. del esquema completo.

**Lectura indirecta:** el arranque con `validate` y el `search_path`/esquema JDBC del perfil `local` confirman conectividad y mapeo JPA; **no** sustituye una ejecución real de cada `EXISTS` contra filas existentes.

**Propuesta (sin implementar):** añadir `ProductoRepositoryPostgresTest` de solo lectura (`@EnabledIfSystemProperty`) que invoque los cinco métodos con un `id_producto` inexistente (esperar `false`) y, si hay datos, un id con dependencia conocida en entorno de prueba — **requiere autorización**.

---

## 4. Matriz de validación funcional

Leyenda: **V** = validado con evidencia ejecutada; **P** = parcial (mock/plantilla/sin E2E real); **N** = no ejecutado en esta etapa.

| Requisito | Evidencia | Estado |
|---|---|---|
| Acceso al listado `/productos` | MockMvc: vista `productos`, datos fixture; HTTP sin auth → 302 login | **V** (listado autenticado: **P**) |
| Búsqueda de productos | `productos.html`: filtro JS en cliente (código/nombre); sin prueba automatizada del script | **P** |
| Formulario de registro `/productos/nueva` | MockMvc vista `producto-form`, token CSRF presente | **V** |
| Campos condicionales perecedero / no perecedero | JS en `producto-form.html` (`#camposPerecedero` / `#camposNoPerecedero`); no hay test Selenium/Playwright | **P** |
| Validaciones de formulario (servidor) | `ProductoServiceImplTest`, POST mock en `ModuleViewsTest` | **V** |
| Edición `/productos/editar/{id}` | MockMvc + `ProductosControllerTest` | **V** |
| Detalle `/productos/consultar/{id}` | MockMvc + mensaje flash éxito tras POST mock | **V** |
| Navegación de regreso al listado | Enlaces en plantillas (`producto-detalle`, formulario); no assert explícito de “Volver” en test | **P** |
| Conservación de unidades históricas en edición | `ProductoServiceImplTest.conservaUnidadHistorica...`; `prepararCatalogos` en controller | **V** |
| Mensajes de éxito y error | MockMvc flash; plantillas `th:if` éxito/error | **V** |

---

## 5. Seguridad

### 5.1 Matriz de permisos (evidencia automatizada)

| Escenario | Evidencia | Estado |
|---|---|---|
| Sin autenticación | HTTP 302 a `/login` en `/productos` y `/productos/nueva` | **V** |
| OPERATIVO: listado sin SELECT | `HomePageTest.productListRequiresAccessAndSelectPermissions` → 403 | **V** |
| OPERATIVO: sin INSERT/UPDATE (rutas y UI) | `HomePageTest`, `ModuleViewsTest.hidesProductWriteActions...` (sin “Registrar producto” ni “Editar”) | **V** |
| ADMIN: POST sin CSRF | `HomePageTest.productPostRequiresCsrf` → 403 | **V** |
| Controller: denegar antes del servicio | `ProductosControllerTest.deniegaEscrituraSinPermiso...` | **V** |
| `SecurityConfig`: reglas por método y acción en rutas producto | Revisión estática + coherencia con tests | **V** |
| Usuario sin permiso ACCEDER | Cubierto por cadena genérica `/productos/**` + tests de permiso SELECT | **P** (sin prueba E2E contra `/acceso-denegado`) |

### 5.2 CSRF

- Formularios POST incluyen `_csrf` (`ModuleViewsTest.productFormPostsWithCsrfToExistingRoutes`).
- POST sin token → rechazo (Spring Security).

### 5.3 Doble capa servidor

- Filtro HTTP (`SecurityConfig`) + `PermisoService` en `ProductosController.exigirPermiso`.

---

## 6. Integridad y reglas de negocio

| Regla | Evidencia | Estado |
|---|---|---|
| Código interno único | `ProductoServiceImplTest`, `ProductoRepositoryTest` (violación UNIQUE en H2) | **V** |
| Categoría existente | `ProductoServiceImplTest` rechazo categoría inexistente | **V** |
| Clasificación exclusiva (alta/edición) | Tests de mezcla y ambos nulos | **V** |
| Fecha de fabricación no futura | `ProductoServiceImplTest` | **V** |
| Bloqueo cambio de clasificación con dependencias | Mock de cinco `exists*` en servicio | **V** |
| Productos históricos inconsistentes | `clasificacionExacta` lanza `BusinessException` en listado/consulta | **V** (comportamiento) / **RIESGO** (operación) |

**Tratamiento de inconsistencias:** el servicio **no corrige** datos automáticamente; muestra error de negocio — alineado a la restricción de la etapa.

---

## 7. Estado de integración con AGROFRESH

| Aspecto | Estado |
|---|---|
| Menú lateral Productos (sin “Pendiente”) | Validado en MockMvc (`ModuleViewsTest`) |
| Estilo y layout compartido con Categorías | Plantillas y `categorias.css` |
| Recurso de permisos `producto` (ACCEDER, SELECT, INSERT, UPDATE) | `SecurityConfig` + controller |
| Sin conflicto con `ModuloController` placeholder | Ruta real registrada en `ProductosController` |
| Categorías como dependencia de catálogo | `categoriasDisponibles()` en flujos de formulario |

---

## 8. Problemas y riesgos (sin corrección aplicada)

### 8.1 ALTA — Listado completo falla si un producto tiene clasificación no exclusiva

**Descripción:** En `ProductoServiceImpl.listar()`, cada fila invoca `clasificacionExacta`. Si un producto histórico tiene 0 o 2 subtipos, se lanza `BusinessException` y puede impedir cargar **todo** el listado.

**Gravedad:** **Alta** (impacto operativo con datos heredados).

**Propuesta:** En listado, mapear filas inconsistentes a un DTO con clasificación `null` o etiqueta “Revisión requerida”, registrando log interno; reservar `clasificacionExacta` estricta para consulta/edición. **Requiere autorización** para cambiar regla de presentación.

### 8.2 MEDIA — Consultas nativas `EXISTS` sin prefijo `inventario.`

**Descripción:** `ProductoRepository` usa nombres de tabla sin esquema (`lote`, `movimiento`, etc.). Funcionan si el `search_path` JDBC incluye el esquema correcto; en otro entorno podrían fallar o apuntar a tablas equivocadas.

**Gravedad:** **Media**.

**Propuesta:** Calificar tablas (`inventario.lote`, …) o documentar obligatoriedad del `search_path`; añadir IT PostgreSQL de solo lectura. **Requiere autorización**.

### 8.3 MEDIA — Cobertura PostgreSQL real de dependencias

**Descripción:** Las reglas de bloqueo por dependencia no se verificaron contra Supabase en esta etapa.

**Gravedad:** **Media** (riesgo de regresión en despliegue).

**Propuesta:** Ver § 3.5.

### 8.4 BAJA — Búsqueda solo en cliente

**Descripción:** Con muchos productos, el filtro no reduce carga servidor; es coherente con Categorías pero limita escalabilidad.

**Gravedad:** **Baja** (diseño conocido).

**Propuesta:** Mantener hasta requisito explícito de búsqueda servidor.

### 8.5 BAJA — Validación visual y roles reales

**Descripción:** Sin sesión ADMIN/OPERATIVO en navegador no se capturaron pantallas ni se validó `fn_usuario_tiene_permiso` en vivo para productos.

**Gravedad:** **Baja** (gap de QA manual).

**Propuesta:** Checklist manual autorizado con usuarios de prueba en entorno controlado.

---

## 9. Confirmación Supabase

| Acción | ¿Ejecutada? |
|---|---|
| INSERT / UPDATE / DELETE de productos o subtipos | **No** |
| DDL (CREATE/ALTER/DROP) | **No** |
| Hibernate `validate` al arranque | **Sí** (solo comparación metadatos) |
| Auditoría de metadatos (`DatabaseSchemaAuditor`) | **Sí** (consultas de catálogo de sistema, solo lectura) |

**Confirmación:** Supabase **no fue modificado** en datos ni estructura durante la Etapa 10.5.

---

## 10. Referencias cruzadas

| Documento | Uso en esta validación |
|---|---|
| `docs/ANALISIS_MODULO_PRODUCTOS.md` | Requisitos y permisos |
| `docs/DISENO_TECNICO_MODULO_PRODUCTOS.md` | Arquitectura esperada |
| `docs/INFORME_IMPLEMENTACION_BACKEND_PRODUCTOS.md` | Alcance backend |
| `docs/INFORME_IMPLEMENTACION_INTERFAZ_PRODUCTOS.md` | Alcance interfaz |
| `docs/VALIDACION_TECNICA_BACKEND_PRODUCTOS.md` | Hallazgos previos (coherentes con este informe) |

---

## 11. Conclusión y siguiente paso

El módulo Productos **supera la verificación Maven integral**, **pasa validación de esquema JPA** contra la base configurada en perfil `local`, y cuenta con **cobertura sólida** en servicio, controller y vistas vía MockMvc para flujos nominales, permisos y CSRF.

**Pendiente de autorización del equipo:**

1. Decidir correcciones para productos históricos inconsistentes en listado (§ 8.1).
2. Autorizar prueba de integración PostgreSQL de solo lectura para `EXISTS` (§ 3.5 / 8.3).
3. Opcional: QA manual autenticada y capturas de pantalla.

**Estado de la etapa:** validación integral **documentada**; **sin cambios de código** aplicados. **Se detiene aquí a la espera de autorización.**
