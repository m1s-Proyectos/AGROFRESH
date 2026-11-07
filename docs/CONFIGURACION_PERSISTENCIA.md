# Configuración de persistencia AGROFRESH

## Estado

**Conexión y auditoría de lectura verificadas.** La conexión, `SELECT 1`, el perfil `local`, el esquema `inventario`, las 16 tablas y las comprobaciones técnicas quedaron confirmadas. La comparación del catálogo también detectó diferencias con el SQL original; están documentadas abajo y no se modificó el esquema.

## Cambios realizados

- Añadido `spring-boot-starter-data-jpa` y el driver PostgreSQL como dependencia runtime, sin fijar versiones fuera del BOM de Spring Boot 3.5.6.
- Establecido `ddl-auto=validate`, `open-in-view=false` y `show-sql=false` en `src/main/resources/application.properties`.
- Creada la configuración externa `config/application-local.properties`, excluida de Git. Sus valores no se copian a este informe ni se muestran en la salida de diagnóstico.
- Creada `config/application-local.example.properties`, sin secretos.
- Documentada la carga del perfil `local` mediante el mecanismo estándar de configuración externa de Spring Boot.
- Añadido un verificador local de conexión JDBC que ejecuta únicamente `SELECT 1` y clasifica éxito, credenciales rechazadas, error de red, base inaccesible y error no clasificado. No crea endpoints y no registra URLs, usuarios, contraseñas ni excepciones con datos sensibles.
- Añadida una auditoría de solo lectura del esquema activo mediante `information_schema` y `pg_catalog`, que enumera tablas, columnas, tipo PostgreSQL, nulabilidad, longitudes, precisión, escala, valores por defecto y definiciones de restricciones. Compara la lista de tablas con las 16 esperadas.
- Para conservar el inicio de la aplicación web sin datasource, la autoconfiguración de `DataSource` queda excluida si no se configura `spring.datasource.url`. La configuración externa del perfil `local` aporta esa URL y activa JPA/JDBC.

### Activación local

Con las propiedades de `config/application-local.properties` configuradas, ejecuta desde el directorio del proyecto:

```powershell
java -jar target/agrofresh-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

También puede iniciarse desde Maven con `mvn spring-boot:run "-Dspring-boot.run.profiles=local"`. Con ese perfil, Spring Boot carga automáticamente `config/application-local.properties`; `application-local.example.properties` es solo una plantilla y no se carga automáticamente. Spring Boot tampoco carga `.env` por defecto. La comprobación y auditoría se habilitan mediante `agrofresh.datasource-check.enabled=true` en la configuración local.

## Revalidación solicitada — resultados reales

- Validación redactada de credenciales: URL JDBC presente y sin marcadores ni signos angulares; puerto numérico; base indicada en la URL; SSL activo; usuario y contraseña presentes. No se mostraron sus valores. No había variables `DB_*` de entorno configuradas; Spring Boot utilizó la configuración local externa.
- Perfil `local` reconocido y `config/application-local.properties` cargado por Spring Boot. La sesión conectada corresponde a la base indicada por la URL (`current_database()` coincide; el nombre no se incluye en este informe).
- Esquema detectado: `inventario`; `current_schema()` devolvió `inventario` y `pg_catalog` confirmó que el esquema existe.
- `spring.jpa.hibernate.ddl-auto=validate`, `spring.jpa.open-in-view=false` y `spring.jpa.show-sql=false`: propiedades comprobadas. El `EntityManagerFactory` inicializó correctamente. Como el proyecto aún no tiene entidades JPA, Hibernate no validó físicamente las 16 tablas; la verificación estructural se hizo mediante el catálogo PostgreSQL.
- `SELECT 1`: **correcto**, confirmado por el `DatabaseConnectionVerifier`.
- Auditoría de solo lectura: **16 tablas base**, sin faltantes ni tablas inesperadas; **80 columnas y 63 restricciones** en el esquema activo.
- Tablas esperadas confirmadas: `usuario`, `proveedor`, `categoria`, `producto`, `perecedero`, `no_perecedero`, `compra`, `detalle_compra`, `catalogo`, `bodega`, `inventario`, `lote`, `movimiento`, `movimiento_bodega`, `movimiento_compra`, `bitacora`.
- Comparación viva con `../BAD2PROY.sql`: se encontraron todas las tablas y las claves primarias/foráneas declaradas en el SQL, pero el esquema **no coincide completamente** en columnas, tipos, defaults y restricciones adicionales (detalle en la subsección siguiente).
- Todas las consultas realizadas fueron `SELECT` a la base o a los catálogos. No se ejecutaron instrucciones que modifiquen datos o estructura.

### Revisión estática del SQL disponible

Se encontró `../BAD2PROY.sql`, fuera del directorio de la aplicación. Su lista de tablas contiene las 16 tablas esperadas. Esta revisión es únicamente del archivo; el script no se ejecutó. El archivo comienza con instrucciones `DROP INDEX`/`DROP TABLE` y luego contiene creación y alteración de tablas, por lo que **no debe ejecutarse** contra el esquema existente.

Las columnas y tipos declarados por el archivo son:

| Tabla | Columnas y tipos declarados |
|---|---|
| `bitacora` | `id_bitacora SERIAL`, `id_usuario INT4`, `fecha_hora DATE`, `tipo_accion VARCHAR(50)`, `tabla_afectada VARCHAR(50)`, `descripcion VARCHAR(200)` |
| `bodega` | `id_bodega SERIAL`, `id_usuario INT4`, `capacidad_maxima DECIMAL(10,2)`, `horario VARCHAR(100)` |
| `categoria` | `id_categoria SERIAL`, `nombre VARCHAR(150)`, `descripcion VARCHAR(200) NULL` |
| `catalogo` | `id_producto INT4`, `id_proveedor INT4`, `precio_vigente DECIMAL(10,2)`, `tiempo_entrega_dias INT4`, `calificacion DECIMAL(10,2) NULL` |
| `compra` | `id_compra SERIAL`, `id_proveedor INT4`, `fecha_compra DATE`, `total_compra DECIMAL(10,2)` |
| `detalle_compra` | `id_detalle_compra SERIAL`, `id_compra INT4`, `id_producto INT4`, `cantidad INT4`, `precio_unitario DECIMAL(10,2)`, `subtotal DECIMAL(10,2)` |
| `inventario` | `id_bodega INT4`, `id_producto INT4`, `cantidad_actual INT4`, `stock_minimo INT4` |
| `lote` | `num_lote INT4`, `id_bodega INT4`, `id_producto INT4`, `id_proveedor INT4`, `fech_vencimiento DATE`, `cantidad_recibida INT4` |
| `movimiento` | `id_movimiento SERIAL`, `fecha DATE`, `tipo_movimiento VARCHAR(50)`, `cantidad DECIMAL(10,2)`, `id_bodega INT4`, `id_usuario INT4`, `id_producto INT4 NULL`, `num_lote INT4 NULL` |
| `movimiento_bodega` | `id_movimiento INT4`, `id_bodega_destino INT4` |
| `movimiento_compra` | `id_movimiento INT4`, `id_detalle_compra INT4` |
| `no_perecedero` | `id_producto INT4`, `fech_fabricacion DATE`, `vida_util_anaquel INT4` |
| `perecedero` | `id_producto INT4`, `temp_recomendad DECIMAL(5,2)`, `vida_util INT4` |
| `producto` | `id_producto SERIAL`, `id_categoria INT4`, `codigo_interno VARCHAR(20)`, `nombre VARCHAR(150)`, `unidad_medida VARCHAR(20)` |
| `proveedor` | `id_proveedor SERIAL`, `nombre VARCHAR(150)`, `dui VARCHAR(10) NULL`, `nit VARCHAR(17)`, `correo_electronico VARCHAR(100)`, `direccion VARCHAR(200)`, `telefono VARCHAR(9)`, `iva VARCHAR(50)` |
| `usuario` | `id_usuario SERIAL`, `username VARCHAR(50)`, `contrasena VARCHAR(150)`, `nombre VARCHAR(100)`, `apellido VARCHAR(100)`, `correo VARCHAR(100)`, `activo BOOL NULL`, `rol VARCHAR(100) NULL` |

La revisión estática identifica 16 claves primarias, 23 claves foráneas declaradas con `ON DELETE RESTRICT` y `ON UPDATE RESTRICT`, y la restricción única compuesta `(id_compra, id_producto)` en `detalle_compra`. El script declara `SERIAL` en nueve columnas de identidad, lo que implica secuencias; no declara columnas identity `GENERATED ALWAYS AS IDENTITY`. Fuera de los defaults implícitos de `SERIAL`, no se observan valores por defecto explícitos.

### Diferencias observadas entre el catálogo remoto y el SQL original

- El catálogo remoto tiene 80 columnas; el SQL declara 77. Las tres columnas adicionales son `bodega.nombre`, `bodega.ubicacion` y `lote.cantidad_actual`.
- Tipos distintos: `bitacora.fecha_hora` y `movimiento.fecha` son `timestamp with time zone` (en el SQL son `DATE`); `movimiento.cantidad` es `integer` (en el SQL es `DECIMAL(10,2)`); `catalogo.calificacion` es `numeric(3,2)` (en el SQL es `DECIMAL(10,2)`).
- Defaults presentes en la base y ausentes en el SQL: `CURRENT_TIMESTAMP` para `bitacora.fecha_hora` y `movimiento.fecha`; `CURRENT_DATE` para `compra.fecha_compra`; `0` para `inventario.cantidad_actual`, `inventario.stock_minimo` y `lote.cantidad_actual`; `TRUE` para `usuario.activo`.
- Las nueve columnas de identidad del catálogo son `GENERATED ALWAYS AS IDENTITY`; el SQL describe esas columnas con `SERIAL`.
- El catálogo contiene 63 restricciones frente a 40 declaradas en el SQL: conserva las 16 PK, las 23 FK y la UNIQUE compuesta de `detalle_compra`, además de 18 `CHECK` y 5 `UNIQUE` adicionales. Estas últimas incluyen `usuario.username` único.
- En `usuario`, el catálogo real tiene `activo NOT NULL DEFAULT TRUE` y `username UNIQUE`, en concordancia con los requisitos funcionales comunicados, aunque difiere de la definición del SQL original.

No se alteró la base para resolver estas diferencias. Deben conservarse como hallazgos al preparar el futuro mapeo JPA.

## Ejecución

- Compilación Maven con JDK 21.0.10: **BUILD SUCCESS**.
- JAR ejecutable iniciado sin perfil `local`: dashboard HTTP **200** y recurso Bootstrap HTTP **200**.
- JAR ejecutable iniciado con perfil `local`: aplicación HTTP **200**, `EntityManagerFactory` inicializado, `SELECT 1` exitoso y auditoría completa.

## Archivos del proyecto

Modificados: `pom.xml`, `.gitignore`, `src/main/resources/application.properties`.

Creados: `config/application-local.properties` (ignorado por Git), `config/application-local.example.properties`, los componentes de diagnóstico en `src/main/java/com/agrofresh/config/` y este informe.

El archivo local de credenciales, el código Java y el esquema de Supabase permanecen sin cambios en la revisión documentada aquí.
