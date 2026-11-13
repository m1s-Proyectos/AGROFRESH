# Informe de auditoría y cierre de seguridad — Módulo Proveedores (Etapa 11.3.1)

**Fecha:** 2026-11-10  
**Proyecto:** `C:\Escritorio\BAD215\AgroFresh\AgroFresh2026-desarrollo`  
**Referencias:** `docs/INFORME_IMPLEMENTACION_BACKEND_PROVEEDORES.md`, `docs/DISENO_TECNICO_MODULO_PROVEEDORES.md`, `docs/ANALISIS_MODULO_PROVEEDORES.md`

---

## 1. Estado inicial

| Aspecto | Estado antes de 11.3.1 |
|---|---|
| `ProveedoresController` | Autorización por `PermisoService` en cada método (`SELECT`, `INSERT`, `UPDATE`) sobre recurso `proveedor`. |
| `SecurityConfig` | Matcher genérico `/proveedores/**` → solo `proveedor.ACCEDER`; **sin** reglas HTTP finas por método (alta/edición/guardar). |
| Vistas Thymeleaf | No implementadas (etapa 11.4). |
| Permisos en Supabase | No verificados en vivo (pooler `EMAXCONNSESSION` en 11.1 y 11.3). |
| Pruebas | `HomePageTest` cubría 403/CSRF básicos; sin clase dedicada al filtro + controller para rol operativo lectura. |

---

## 2. Archivos inspeccionados

- `src/main/java/com/agrofresh/security/SecurityConfig.java`
- `src/main/java/com/agrofresh/security/PermisoService.java`
- `src/main/java/com/agrofresh/security/RoleAuthorityMapper.java`
- `src/main/java/com/agrofresh/controller/ProveedoresController.java`
- `src/main/java/com/agrofresh/controller/ProductosController.java` (patrón de referencia)
- `src/test/java/com/agrofresh/HomePageTest.java`
- `src/test/java/com/agrofresh/security/SecurityLoginTest.java`
- `src/test/java/com/agrofresh/controller/ProveedoresControllerTest.java`
- `docs/_audit_proveedor_permisos_only.py`
- `config/application-local.properties` (solo comprobación de no modificación; **no** se expone contenido).

---

## 3. Permisos encontrados en Supabase

**No verificados en esta etapa.**

Se intentó **una** consulta de solo lectura con `docs/_audit_proveedor_permisos_only.py` (SELECT sobre `inventario.permiso`, `rol_permiso`, `rol` para `recurso = 'proveedor'`).

**Resultado:** `psycopg2.OperationalError` — `(EMAXCONNSESSION) max clients reached in session mode - max clients are limited to pool_size: 15`.

Por restricción de la etapa **no** se declararon códigos ni filas de permisos. No se repitieron consultas en bucle ni se cerraron procesos externos.

**Hipótesis de diseño** (documentada en análisis 11.1, **pendiente de confirmación en BD**):

| Recurso | Acciones esperadas en aplicación |
|---|---|
| `proveedor` | `ACCEDER`, `SELECT`, `INSERT`, `UPDATE` (y posiblemente `DELETE` en catálogo, no usado en UI de este módulo) |

La aplicación invoca `fn_usuario_tiene_permiso(id_usuario, recurso, accion)` vía `PermisoService.puede`.

---

## 4. Matriz de autorización ADMINISTRADOR / OPERATIVO

### 4.1 Comportamiento objetivo (diseño del módulo)

| Acción | ADMINISTRADOR | OPERATIVO |
|---|---|---|
| Listar proveedores | Sí | Sí |
| Consultar detalle | Sí | Sí |
| Crear (formulario + POST) | Sí | No |
| Editar (formulario + POST) | Sí | No |

### 4.2 Mapeo técnico implementado (aplicación)

| Ruta | Método | Permiso exigido (`proveedor`) |
|---|---|---|
| `/proveedores` | GET | `ACCEDER` **y** `SELECT` |
| `/proveedores/consultar/**` | GET | `SELECT` |
| `/proveedores/nueva`, `/proveedores/guardar` | GET / POST | `INSERT` |
| `/proveedores/editar/**`, `/proveedores/actualizar/**` | GET / POST | `UPDATE` |

**Coincidencia con Supabase:** **no confirmada** hasta ejecutar la auditoría SELECT. Si la matriz real en `rol_permiso` difiere del diseño, la etapa debe permanecer bloqueada o parcial (véase conclusión).

---

## 5. Cambios realizados en `SecurityConfig`

Se añadieron matchers específicos por método HTTP, **en el mismo patrón que Productos/Categorías**:

- GET `/proveedores` → `ACCEDER` ∧ `SELECT`
- GET `/proveedores/consultar/**` → `SELECT`
- GET `/proveedores/nueva` y POST `/proveedores/guardar` → `INSERT`
- GET `/proveedores/editar/**` y POST `/proveedores/actualizar/**` → `UPDATE`

**Sin cambios:** `permitAll` en `/login` y estáticos, CSRF habilitado (form login), reglas de otros módulos, bucle genérico `ACCEDER` para rutas de módulo (sigue aplicando a rutas no cubiertas por matchers anteriores).

---

## 6. Cambios realizados en `ProveedoresController`

**Ningún cambio funcional de CRUD** en esta etapa. El controller ya aplicaba `exigirPermiso(authentication, accion)` con recurso `proveedor` y acciones alineadas al filtro HTTP.

---

## 7. Mecanismo de autorización utilizado

1. **Filtro Spring Security** (`SecurityConfig`): `AuthorizationDecision` con `permisos.puede(authentication, "proveedor", accion)`.
2. **Capa controller:** segunda validación con el mismo `PermisoService` (defensa en profundidad ante manipulación de URL).
3. **Base de datos:** `PermisoService` ejecuta JDBC contra `fn_usuario_tiene_permiso` y estado de usuario/rol activo.
4. **Roles Spring:** `ADMINISTRADOR` → `ROLE_ADMIN`, `OPERATIVO` → `ROLE_OPERADOR` (`RoleAuthorityMapper`).

No se duplicó lógica de permisos fuera de `PermisoService`.

---

## 8. Pruebas de seguridad implementadas

| Ámbito | Archivo | Cobertura |
|---|---|---|
| Filtro HTTP + CSRF + anónimo | `ProveedoresSecurityTest.java` (**nuevo**) | Redirección a login sin autenticación; 403 sin INSERT/UPDATE operativo; 403 sin SELECT/INSERT en filtros; POST sin CSRF → 403; POST con CSRF + INSERT → no 403 |
| Filtro (proveedores) | `HomePageTest.java` | Listado sin SELECT; escritura sin permisos; CSRF en guardar |
| Controller (permisos) | `ProveedoresControllerTest.java` | Lectura operativa; denegación INSERT/UPDATE; denegación sin UPDATE en actualizar |
| Regresión contexto | `SecurityLoginTest`, `ModuleViewsTest` | Mock de `ProveedorService` (sin cambios de lógica en esta etapa) |

**Nota:** Las pruebas integradas que **pasan** el filtro de seguridad pero requieren vista `proveedores.html` / `proveedor-detalle.html` no se ejecutan con éxito HTTP 200 hasta la etapa 11.4; la autorización positiva de lectura/escritura se valida en **pruebas unitarias del controller** y en POST con redirección (sin renderizado de plantilla).

---

## 9. Resultado de cada prueba (suite completa)

| Suite | Resultado |
|---|---|
| `ProveedoresSecurityTest` (8) | OK |
| `ProveedoresControllerTest` (6) | OK |
| Resto del proyecto | OK |
| `UsuarioRepositoryPostgresTest` | 1 omitido (habitual) |

---

## 10. Resultado de `mvn -B clean verify`

- **JDK:** 21  
- **Resultado:** `BUILD SUCCESS`  
- **Tests:** **111** ejecutados, **0** fallos, **0** errores, **1** omitido  

---

## 11. Resultado de la verificación local

Con aplicación en ejecución en el entorno del desarrollador (sin modificar `application-local.properties`):

| URL | Código observado (sin sesión) |
|---|---|
| `GET /login` | 200 |
| `GET /proveedores` | 302 → `/login` |

No se inició sesión con usuarios reales ni se ejecutaron POST de escritura en producción local.

---

## 12. Limitaciones y riesgos pendientes

1. **Permisos Supabase no auditados:** Riesgo de discrepancia entre `rol_permiso` real y reglas de aplicación; la matriz oficial no está cerrada.
2. **Pooler Supabase saturado:** Reintentar `docs/_audit_proveedor_permisos_only.py` cuando haya hueco en el pool (evitar instancias locales múltiples ocupando conexiones).
3. **Vistas ausentes:** Un usuario autorizado recibiría error de plantilla al listar hasta 11.4; la seguridad no sustituye la necesidad de vistas.
4. **Matcher genérico `ACCEDER`:** Rutas futuras bajo `/proveedores/**` no listadas explícitamente siguen exigiendo solo `ACCEDER` hasta añadir reglas finas.

---

## 13. Archivos creados y modificados

| Archivo | Acción |
|---|---|
| `docs/INFORME_AUDITORIA_SEGURIDAD_PROVEEDORES_11_3_1.md` | Creado |
| `src/main/java/com/agrofresh/security/SecurityConfig.java` | Modificado (reglas proveedores) |
| `src/test/java/com/agrofresh/controller/ProveedoresSecurityTest.java` | Creado |
| `src/test/java/com/agrofresh/controller/ProveedoresControllerTest.java` | Modificado (+2 pruebas operativo) |
| `src/test/java/com/agrofresh/HomePageTest.java` | Modificado en sesión previa (SELECT en listado proveedores) |
| `docs/_audit_proveedor_permisos_only.py` | Auxiliar auditoría (sin cambios de esquema; uso READ ONLY) |

---

## 14. Confirmación: sin escrituras en Supabase

No se ejecutó INSERT, UPDATE, DELETE, DDL ni migraciones. Solo se intentó conexión de lectura (fallida por límite de pool).

---

## 15. Confirmación: sin comandos Git

No se ejecutó ningún comando Git en esta etapa.

---

## 16. Conclusión

**COMPLETADA PARCIALMENTE**

**Motivo:** La implementación de seguridad en aplicación (SecurityConfig + controller + pruebas + regresión Maven) quedó alineada al patrón de Productos y al diseño ADMIN/OPERATIVO, pero **los permisos reales del recurso `proveedor` en Supabase no pudieron verificarse**. Hasta completar la Actividad A con éxito, **no** se debe dar por cerrada la conformidad matriz ↔ base de datos ni avanzar a la etapa 11.4 sin autorización expresa.

**Próximo paso recomendado:** Ejecutar una sola auditoría SELECT cuando el pooler lo permita; si la matriz difiere, documentar discrepancia y **no** alterar la BD desde el proyecto.
