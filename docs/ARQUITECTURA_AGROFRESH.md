# Arquitectura base de AGROFRESH

## Alcance implementado

- Proyecto Maven con Spring Boot 3.5.6 y compilación configurada para Java 21.
- Spring MVC, Thymeleaf, Bootstrap 5.3.3 mediante WebJars y registro con SLF4J.
- Paquete raíz `com.agrofresh`, con la estructura inicial de capas documentada mediante `package-info.java`; no se agregaron clases vacías de módulos futuros.
- Página principal de dashboard con header, sidebar, footer y navegación visual. Los módulos sin implementar aparecen deshabilitados y marcados como pendientes.
- Manejo MVC de `ResourceNotFoundException` y `BusinessException`, con páginas de error y mensajes que no exponen detalles internos.

## Inicio de la aplicación

`mvn spring-boot:run` inicia la aplicación web y muestra el dashboard en `/`.

## Archivos

El detalle de los archivos creados está disponible en el historial del proyecto. En esta configuración inicial no se incluyeron entidades, repositorios funcionales, autenticación ni CRUD.
