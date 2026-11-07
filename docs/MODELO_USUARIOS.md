# Modelo de usuarios AGROFRESH

## Resultado

Se implementó el modelo y la capa de consulta de usuarios usando como referencia el catálogo real de Supabase en `inventario.usuario`. No se modificó el esquema ni se leyeron valores de filas de usuario.

## Estructura real inspeccionada

| Columna | Tipo PostgreSQL | Nulabilidad / default |
|---|---|---|
| `id_usuario` | `integer` (`int4`) | NOT NULL; `GENERATED ALWAYS AS IDENTITY`; secuencia asociada |
| `username` | `varchar(50)` | NOT NULL; UNIQUE; sin default |
| `contrasena` | `varchar(150)` | NOT NULL; sin default |
| `nombre` | `varchar(100)` | NOT NULL; sin default |
| `apellido` | `varchar(100)` | NOT NULL; sin default |
| `correo` | `varchar(100)` | NOT NULL; sin default |
| `activo` | `boolean` | NOT NULL; default `true` |
| `rol` | `varchar(100)` | NULL; sin default |

La clave primaria es `id_usuario`. La restricción única es `uq_usuario_username` sobre `username`. No hay claves foráneas en `usuario`. No se consultaron filas ni contraseñas/hash.

## Diferencias con el SQL original

El SQL describe `id_usuario` como `SERIAL`, `username` sin UNIQUE y `activo` nullable sin default. El esquema real usa identity `GENERATED ALWAYS`, username único y activo `NOT NULL DEFAULT TRUE`. Conforme a la decisión del equipo, se mapeó el esquema remoto real; no se trataron estas diferencias como errores ni se modificó Supabase.

## Implementación

- `Usuario` mapea exclusivamente las ocho columnas reales de `inventario.usuario`; el id usa `GenerationType.IDENTITY`.
- `UsuarioRepository` ofrece acceso JPA y búsqueda por username.
- `UsuarioService` / `UsuarioServiceImpl` ofrecen solo consultas: listar, buscar por id y buscar por username. Las transacciones son de solo lectura.
- `UsuarioMapper` convierte manualmente a `UsuarioDto`, que excluye `contrasena` y cualquier hash.
- No se implementaron altas, actualizaciones, login, controladores, vistas, seguridad ni otros modelos. Por ello no se añadió lógica de BCrypt ni escritura de contraseñas.
- Se conservó `spring.jpa.hibernate.ddl-auto=validate`; `open-in-view=false` y `show-sql=false` permanecen activos. La implementación no usa generación/actualización del esquema.
- La autoconfiguración de persistencia ahora se habilita cuando existe `spring.datasource.url`, y el bean del servicio de usuarios se condiciona a esa configuración, para que la aplicación web sin datasource configurado pueda seguir arrancando.

## Compatibilidad y ejecución

- `mvn -B clean verify`, JDK 21.0.10: **BUILD SUCCESS**.
- Mapeo y consulta PostgreSQL ejecutados con perfil `local`; `ddl-auto=validate` permaneció activo y solo se consultó el metamodelo y un username aleatorio ausente. No se insertaron ni actualizaron datos.
- Aplicación iniciada con perfil `local`: repositorio detectado, `EntityManagerFactory` inicializado, `SELECT 1` exitoso, auditoría de 16 tablas completada y dashboard HTTP 200.
- El mapeo de la entidad fue validado por Hibernate contra Supabase; no se desactivó `validate`.

## Archivos

Nuevos: `src/main/java/com/agrofresh/model/Usuario.java`, `repository/UsuarioRepository.java`, `service/UsuarioService.java`, `service/impl/UsuarioServiceImpl.java`, `dto/UsuarioDto.java`, `mapper/UsuarioMapper.java` y este informe.

Modificados: `src/main/java/com/agrofresh/config/PersistenceProfileEnvironmentPostProcessor.java` para condicionar la autoconfiguración por la presencia de la URL JDBC.

No se modificó ni eliminó ningún archivo de configuración con credenciales, ni el esquema o los datos de Supabase.
