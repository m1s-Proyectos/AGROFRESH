# AGROFRESH

AGROFRESH es una aplicación web para apoyar la gestión de inventario agrícola. La versión actual proporciona un dashboard inicial y una base de persistencia para consultar usuarios. Las opciones de los demás módulos todavía se muestran como pendientes.

## Tecnologías

- Java 21 y Spring Boot 3.5.6.
- Spring MVC y Thymeleaf.
- Spring Data JPA e Hibernate.
- PostgreSQL en Supabase.
- Bootstrap 5.3.3.
- Maven.
- SLF4J para registros.

La aplicación sigue una arquitectura MVC por capas: `controller`, `service`, `repository`, `model`, `dto` y `mapper`. Las entidades JPA representan la persistencia; los DTO son los objetos de intercambio con la capa web.

## Requisitos previos

Instala o configura:

- JDK 21.
- Apache Maven instalado y disponible en `PATH`; en este entorno se comprobó Maven 3.9.16. Este repositorio no incluye Maven Wrapper (`mvnw.cmd`).
- NetBeans 28 es opcional; también puede utilizarse otra versión compatible con proyectos Maven y Java 21.
- Git, si el equipo proporciona una URL de repositorio para obtener el código.
- Una cuenta/proyecto de Supabase con acceso a PostgreSQL y al esquema requerido.
- Un navegador web moderno.

Desde PowerShell, comprueba las versiones:

```powershell
java -version
mvn -version
```

La salida de `mvn -version` también indica qué Java está usando Maven. Debe mostrar Java 21. Si muestra otra versión, configura `JAVA_HOME` para que apunte al JDK 21 y abre una nueva terminal antes de continuar.

## Obtener y abrir el proyecto

1. Obtén el código desde el repositorio o medio de distribución que indique el equipo. La URL del repositorio debe solicitarse al responsable; no está configurada en esta copia.
2. Ubica la carpeta raíz, donde se encuentra `pom.xml`:

   `C:\Escritorio\BAD215\AgroFresh\AgroFresh2026`

3. En NetBeans, selecciona **File > Open Project** y abre esa carpeta. NetBeans debe reconocer el `pom.xml` como proyecto Maven.
4. Espera a que Maven resuelva las dependencias declaradas.
5. La clase principal de Spring Boot es `com.agrofresh.AgroFreshApplication`.

Para abrir la carpeta desde PowerShell:

```powershell
Set-Location -LiteralPath 'C:\Escritorio\BAD215\AgroFresh\AgroFresh2026'
```

## Configurar una conexión propia a Supabase

Cada integrante debe usar sus propias credenciales autorizadas de Supabase. No reutilices ni copies las credenciales de otra persona.

Obtén del panel de Supabase los parámetros de conexión PostgreSQL correspondientes a tu proyecto:

- Host.
- Puerto indicado por Supabase (puede variar entre conexión directa y pooler).
- Nombre de la base de datos.
- Usuario.
- Contraseña de base de datos.
- Requisito SSL.

El proyecto espera estos valores en el archivo externo `config/application-local.properties`. Spring Boot lo carga al activar el perfil `local`, desde la carpeta `config` relativa al directorio raíz del proyecto. No hace falta añadir un `spring.config.import` adicional. Spring Boot no carga archivos `.env` automáticamente.

Si el archivo local aún no existe, crea una copia del ejemplo sin sobrescribir un archivo existente:

```powershell
if (-not (Test-Path -LiteralPath '.\config\application-local.properties')) {
    Copy-Item -LiteralPath '.\config\application-local.example.properties' `
        -Destination '.\config\application-local.properties'
}
notepad .\config\application-local.properties
```

El ejemplo usa marcadores ilustrativos; reemplaza cada uno con los valores de tu propio proyecto Supabase:

```properties
spring.datasource.url=jdbc:postgresql://<DB_HOST>:<DB_PORT>/<DB_NAME>?sslmode=require
spring.datasource.username=<DB_USER>
spring.datasource.password=<DB_PASSWORD>
agrofresh.datasource-check.enabled=true
```

Los marcadores `<...>` son texto de ejemplo, no variables que Spring sustituya automáticamente. Usa el host, puerto, nombre, usuario, contraseña y parámetro SSL que entrega Supabase. No guardes valores personales en `src/main/resources`, no los pegues en documentación y no compartas el archivo local.

`.gitignore` ya excluye `config/application-local.properties`; no quites esa regla ni subas el archivo. El proyecto no depende de variables `DB_*` ni de `.env` para esta configuración.

## Base de datos y esquema requerido

La aplicación espera que la conexión resuelva el esquema activo como **`inventario`**. Al iniciar con el perfil local y la comprobación habilitada, el diagnóstico ejecuta `SELECT 1` y consulta los metadatos de solo lectura; compara las tablas presentes con estas 16 tablas esperadas:

`usuario`, `proveedor`, `categoria`, `producto`, `perecedero`, `no_perecedero`, `compra`, `detalle_compra`, `catalogo`, `bodega`, `inventario`, `lote`, `movimiento`, `movimiento_bodega`, `movimiento_compra`, `bitacora`.

Hibernate está configurado con `spring.jpa.hibernate.ddl-auto=validate`, `spring.jpa.open-in-view=false` y `spring.jpa.show-sql=false`. `validate` comprueba las tablas mapeadas por entidades; no crea ni actualiza el esquema. La auditoría del catálogo consulta por separado tablas, columnas y restricciones.

El repositorio **no contiene un procedimiento reproducible y seguro para aprovisionar desde cero las 16 tablas**. Un proyecto Supabase vacío no basta para ejecutar esta aplicación. Antes de conectarte, solicita al equipo responsable el esquema vigente y el procedimiento autorizado para preparar tu base. No ejecutes automáticamente `BAD2PROY.sql` ni scripts históricos que contengan instrucciones destructivas o que no coincidan con el esquema Supabase actual. Supabase es la fuente de verdad; esta aplicación no modifica el esquema.

## Ejecutar la aplicación en el puerto 8080

Desde PowerShell, en la raíz del proyecto y después de completar el archivo local:

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

El proyecto incluye `spring-boot-maven-plugin`; este objetivo activa el perfil `local`. No se configura `server.port`, por lo que Spring Boot usa el puerto HTTP predeterminado **8080**.

Cuando el arranque sea correcto, la consola debe indicar mensajes equivalentes a:

```text
Tomcat started on port 8080 (http)
Started AgroFreshApplication
```

Con `local` activo también debe aparecer el perfil `local` y, si la conexión está operativa, el mensaje `Conexión PostgreSQL verificada mediante SELECT 1.`. La auditoría indica cuántas tablas encontró y lista las que falten o no se esperaban.

Después abre:

<http://localhost:8080>

La terminal debe permanecer abierta mientras la aplicación esté funcionando. Para detenerla, vuelve a esa terminal y presiona **Ctrl + C**.

## Ejecutar desde NetBeans

Configura el proyecto para utilizar el JDK 21 y confirma en la terminal de NetBeans que `mvn -version` muestra esa versión de Java. El proyecto es Maven y no contiene una configuración de acciones específica en `nbproject`.

La forma recomendada y comprobada de arrancarlo es desde la terminal integrada de NetBeans con el mismo objetivo:

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

También se puede invocar el objetivo Maven `spring-boot:run` desde las acciones Maven del IDE, pasando el perfil `local`. No uses una acción de `exec-maven-plugin` que intente ejecutar literalmente `${start-class}`; el proyecto se inicia mediante el plugin de Spring Boot.

## Solución de problemas

| Problema | Qué comprobar |
|---|---|
| El puerto 8080 está ocupado | Identifica el proceso que escucha en el puerto con `Get-NetTCPConnection -LocalPort 8080 -State Listen`. Cierra únicamente un proceso que reconozcas o coordina la liberación del puerto. |
| Error de conexión a Supabase | Comprueba conectividad de red y verifica host, puerto, base y SSL con los datos de tu proyecto. No compartas la URL completa en reportes si contiene información privada. |
| Credenciales rechazadas | Confirma que el usuario y la contraseña sean los de la conexión PostgreSQL de tu propio proyecto. Si la contraseña fue restablecida, actualiza únicamente tu archivo local. |
| El perfil `local` no aparece activo | Ejecuta el comando completo `mvn spring-boot:run "-Dspring-boot.run.profiles=local"` desde la raíz del proyecto. |
| Maven usa un JDK incorrecto | Revisa `java -version` y `mvn -version`; configura `JAVA_HOME` al JDK 21 y reinicia la terminal o el IDE. |
| `mvn` no se reconoce | Instala Apache Maven y agrega su carpeta `bin` al `PATH`. No hay `mvnw.cmd` en el repositorio. |
| Falta `application-local.properties` | Cópialo desde `config/application-local.example.properties` y completa tus propios valores. Comprueba que el directorio actual sea la raíz del proyecto. |
| Hibernate informa tablas o columnas incompatibles | Comprueba que la conexión utiliza el esquema `inventario` vigente y que la tabla `usuario` es compatible con el modelo. No desactives `validate` ni ejecutes `CREATE`, `ALTER`, `DROP` como solución automática; solicita al responsable del esquema que revise la diferencia. |
| NetBeans muestra `${start-class}` o no encuentra la clase principal | No ejecutes `exec:java`; ejecuta el goal `spring-boot:run` con perfil `local`. La clase principal es `com.agrofresh.AgroFreshApplication`. |
| Maven terminó, pero el navegador no abre la página | Confirma que aparezca el mensaje de Tomcat en el puerto 8080, que el proceso siga activo y que navegues a `http://localhost:8080/`. Revisa también si el puerto está ocupado. |

No desactives controles de seguridad ni cambies el esquema para ocultar errores de conexión o validación.

## Estado actual

La aplicación dispone de dashboard, configuración y diagnóstico de conexión PostgreSQL, auditoría de metadatos y una entidad/capa de consulta para usuarios. La barra lateral identifica los demás módulos como pendientes. **No están disponibles la autenticación, el inicio de sesión ni el CRUD de usuarios**; tampoco están implementados los módulos funcionales de productos, proveedores, compras, inventario, lotes, movimientos o reportes.
