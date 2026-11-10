# Informe — Fase 8: Validación funcional controlada

**Fecha:** 2026-11-10  
**Proyecto:** `C:\Escritorio\BAD215\AgroFresh\AgroFresh2026-desarrollo`  
**Rama conocida:** `desarrollo-agrofresh` (no se consultó Git en esta fase, según la restricción).

## 1. Entorno

- Ruta de trabajo confirmada: proyecto destino indicado.
- `config/application-local.properties`: existe. No se leyó ni imprimió su contenido.
- Java predeterminado de la consola: 25.0.4. Para la validación solicitada se seleccionó explícitamente JDK 21.0.10 en `JAVA_HOME` y `PATH` del proceso Maven.
- Maven: 3.9.16.
- Spring Boot: 3.5.6.
- `pom.xml`: `java.version=21`; incluye Web, Security, Thymeleaf, JPA, PostgreSQL, Bootstrap 5.3.3 y dependencias de test.
- `application.properties`: conserva `ddl-auto=validate`, `open-in-view=false` y `show-sql=false`.
- Perfil de ejecución validado: `local`.
- Puerto esperado: 8080.

## 2. Compilación y pruebas

Comando ejecutado con JDK 21.0.10: `mvn -B clean verify`.

- **Resultado:** BUILD SUCCESS.
- **Pruebas:** 36 contabilizadas; 35 exitosas, 0 fallidas, 0 errores, 1 omitida.
- La omitida es `UsuarioRepositoryPostgresTest`, opt-in y condicionada por propiedad; no se habilitó contra Supabase.
- Las pruebas nuevas de Lotes y vistas usan mocks. No se usaron cuentas reales ni se hicieron escrituras a Supabase.

En el primer intento `clean` no pudo eliminar el JAR porque lo tenía abierto la instancia local. Se identificó esa instancia como el JAR del proyecto destino (PID 67620, JDK 21, perfil `local`), se verificaron sus respuestas HTTP y se detuvo ese proceso concreto para poder compilar. Tras la compilación se inició la versión actualizada.

Avisos no bloqueantes: uso de APIs de test `@MockBean` marcadas deprecated, API deprecada en `SessionModelAdvice` y carga dinámica de agente Mockito/Byte Buddy.

## 3. Estado de ejecución local

La instancia actual es el proyecto destino, ejecutado con JDK 21.0.10 y perfil `local`, PID **19688**, escuchando en 8080. Permanece en ejecución.

Respuestas HTTP comprobadas:

| Ruta/recurso | Resultado | Interpretación |
|---|---:|---|
| `GET /login` | 200 | Login público renderizado |
| `GET /` sin sesión | 302 → `/login` | Dashboard protegido |
| `GET /usuarios` sin sesión | 302 → `/login` | Ruta protegida |
| `GET /inventario` sin sesión | 302 → `/login` | Ruta protegida |
| `GET /compras` sin sesión | 302 → `/login` | Ruta protegida |
| `GET /traslados` sin sesión | 302 → `/login` | Ruta protegida |
| `GET /lotes` sin sesión | 302 → `/login` | Ruta protegida |
| CSS de Lotes e Inventario | 200 | Recursos encontrados |
| Bootstrap WebJar | 200 | Recurso encontrado |

No se inició sesión con cuentas reales.

## 4. Matriz de validación por área

| Área | Estado | Evidencia | Pendiente |
|---|---|---|---|
| Compilación | VALIDADO | `mvn -B clean verify`, JDK 21.0.10, BUILD SUCCESS | Ninguno para la compilación actual |
| Autenticación | VALIDADO PARCIALMENTE | Login público HTTP 200; pruebas de login con UserDetails/BCrypt sintéticos y mocks | Inicio de sesión con cuenta de prueba autorizada; compatibilidad de hashes guardados |
| Roles | VALIDADO | Pruebas del mapeo `ADMINISTRADOR → ROLE_ADMIN`, `OPERATIVO → ROLE_OPERADOR` | No se inspeccionaron usuarios reales ni sus asignaciones |
| Permisos | VALIDADO PARCIALMENTE | Pruebas mock de `PermisoService`; rutas anónimas redirigen a login; función y reglas revisadas estáticamente | Autorización de cada cuenta real sin iniciar sesión real |
| Usuarios | VALIDADO PARCIALMENTE | Vista renderizada con fixtures; handlers de login y CSRF con mocks | Altas/modificaciones reales y protección del último administrador |
| Inventario | VALIDADO PARCIALMENTE | Vista renderizada con fixtures; consulta cotejada estáticamente con columnas conocidas | Consulta real autenticada y filtros con datos, sin exponer filas |
| Recepción | VALIDADO PARCIALMENTE | Vista renderizada con mocks; parámetros cotejados con firmas ya auditadas | Prueba funcional de compra en entorno aislado; no se ejecutó ningún procedimiento |
| Traslados | VALIDADO PARCIALMENTE | Vista y rutas protegidas; dependencia de función/procedimientos cotejada | Prueba funcional en base aislada; no se ejecutaron procedimientos |
| Lotes | VALIDADO PARCIALMENTE | Vista MockMvc; pruebas del controlador/filtros; `SELECT COUNT(*)` real en solo lectura con 47 coincidencias | Vista autenticada con datos, validación visual del navegador y filtros contra datos reales |
| Supabase | VALIDADO PARCIALMENTE | Sesión de solo lectura; consulta agregada de Lotes correcta; esquema y firmas cubiertos por la auditoría previa | No se probaron operaciones de escritura ni rutinas de negocio |
| Ejecución local | VALIDADO | Instancia JDK 21/perfil local responde HTTP; login y rutas protegidas comprobados | Ninguno para las rutas públicas y redirecciones comprobadas |

## 5. Autenticación y permisos

- `SecurityConfig` usa form login propio, mantiene CSRF y protege las rutas de módulos.
- La lógica usa `BCryptPasswordEncoder`; las pruebas verifican el encoder con un fixture generado en memoria. No se leyeron hashes existentes, por lo que no se puede afirmar que las contraseñas almacenadas sean BCrypt.
- Se conserva el mapeo explícito de autoridades y el control de permisos por `fn_usuario_tiene_permiso`.
- Los tests cubren login correcto/incorrecto con mocks, estado inactivo/bloqueado, rechazo de autoridades desconocidas y CSRF. No autentican cuentas reales.
- No se consultaron filas de usuarios ni permisos personales durante esta fase.

## 6. Validación de vistas y recursos

MockMvc renderiza con datos simulados las vistas de login, dashboard, usuarios, inventario, recepción, traslados, perfil, acceso denegado y Lotes. Se comprobó la compatibilidad del fragmento `sidebar(activePage)` en las plantillas incorporadas.

No se ejecutó un navegador ni se validó el JavaScript visualmente. El script de filtros de Lotes es JavaScript inline con `requestSubmit`; su comportamiento interactivo queda pendiente de prueba manual.

## 7. Consultas y compatibilidad con Supabase

La aplicación ya tiene el perfil local y el esquema conocido `inventario`. No se repitió la auditoría completa de todas las tablas porque existe evidencia reciente. Las consultas se revisaron en el código; no se imprimieron filas personales.

| Archivo/método | Elementos requeridos | Validación y resultado |
|---|---|---|
| `AgroFreshUserDetailsService.loadUserByUsername` | `usuario` unido a `rol`; columnas de estado y rol | Consulta estática compatible con metadatos ya auditados. No ejecutada para evitar leer contraseñas ni usuarios. |
| `PermisoService.puede` | `usuario`, `rol`, `fn_usuario_tiene_permiso` | Llamada estática identificada; no ejecutada para una cuenta real. |
| `InventarioController.consultar` | `inventario`, `producto`, `bodega` y sus columnas de existencias/código | Columnas cotejadas con metadatos anteriores; vista probada con mocks, consulta no ejecutada en esta fase. |
| `RecepcionController` / `RecepcionService` | `catalogo`, proveedor, producto, tipos de producto y procedimientos `sp_*` | Firmas contrastadas con la auditoría previa; ningún procedimiento ejecutado. |
| `TrasladoCatalogoController` / `TrasladoService` | Bodegas, producto, inventario, `fn_lotes_disponibles` y procedimientos de traslado | Uso de la función en lectura estática; no se invocaron función ni procedimientos en esta fase. |
| `LotesController.consultar` | `lote.fech_vencimiento`, `lote.cantidad_actual`, producto, bodega y proveedor | Consulta equivalente ejecutada como `SELECT COUNT(*)` dentro de transacción `read only`: correcta, esquema `inventario`, 47 coincidencias; no se devolvieron filas. |

La tabla Lotes no llama a `fn_lotes_disponibles`: necesita listar también vencidos y sin stock. Esa función permanece usada por el catálogo de traslados para los lotes disponibles. No se alteraron su firma ni la estructura de tablas.

## 8. Riesgos pendientes

| Hallazgo | Nivel | Estado |
|---|---|---|
| No se protegió ni probó la regla del último administrador activo | ALTO | Pendiente de autorización y diseño de prueba aislada |
| Formato BCrypt de contraseñas guardadas no verificado | PENDIENTE DE AUTORIZACIÓN | Requiere cuenta de prueba no productiva; no se leyeron hashes |
| Operaciones reales de recepción/traslado/administración no probadas | PENDIENTE DE AUTORIZACIÓN | Requieren entorno aislado o autorización específica |
| JDBC directo y `Map` en algunos controladores/vistas | MEDIO | Deuda técnica preexistente; no se refactorizó en esta fase |
| Filtros JavaScript de Lotes no probados en navegador | BAJO | Prueba manual pendiente |

## 9. Archivos inspeccionados y modificados

Se inspeccionaron los controladores, la plantilla y CSS de Lotes, los controladores de inventario/traslados, la configuración de seguridad, el sidebar, el dashboard, las propiedades base, el POM y las pruebas existentes.

Durante esta fase solo se creó `docs/INFORME_FASE_8_VALIDACION_FUNCIONAL.md`. Maven regeneró artefactos bajo `target/`. No se modificaron fuentes ni configuración local.

## 10. Git, configuración y datos

- No se ejecutaron comandos Git ni se cambió la rama.
- `config/application-local.properties` existe; no se leyó ni modificó.
- Supabase no fue modificada. La comprobación real fue un SELECT agregado en transacción de solo lectura; no se ejecutaron INSERT/UPDATE/DELETE, migraciones ni rutinas de negocio.
- No se leyeron contraseñas, hashes, tokens ni filas personales.

## 11. Conclusión

La compilación, las pruebas automatizadas, la protección de rutas sin sesión, el login público y el renderizado MockMvc de las vistas principales están comprobados. Lotes tiene además una consulta agregada real de solo lectura compatible con el esquema. La autenticación con cuentas existentes y las operaciones que escriben datos permanecen sin validar por las restricciones de seguridad.
