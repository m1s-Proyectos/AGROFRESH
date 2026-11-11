package com.agrofresh.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, PermisoService permisos,
                                            LoginAttemptService intentos) throws Exception {
        var auth = http.authorizeHttpRequests(reglas -> {
            reglas.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/compras/confirmar")
                .access((authentication, request) -> new AuthorizationDecision(
                    permisos.puede(authentication.get(), "compra", "INSERT")));
            reglas.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/traslados/confirmar")
                .access((authentication, request) -> new AuthorizationDecision(
                    permisos.puede(authentication.get(), "traslado", "INSERT")));
            reglas.requestMatchers(org.springframework.http.HttpMethod.POST, "/usuarios/*/estado", "/usuarios/*/bloqueo")
                .access((authentication, request) -> new AuthorizationDecision(
                    permisos.puede(authentication.get(), "usuario", "UPDATE")));
            reglas.requestMatchers(org.springframework.http.HttpMethod.GET, "/categorias/nueva")
                .access((authentication, request) -> new AuthorizationDecision(
                    permisos.puede(authentication.get(), "producto", "INSERT")));
            reglas.requestMatchers(org.springframework.http.HttpMethod.POST, "/categorias/guardar")
                .access((authentication, request) -> new AuthorizationDecision(
                    permisos.puede(authentication.get(), "producto", "INSERT")));
            reglas.requestMatchers(org.springframework.http.HttpMethod.GET, "/categorias/editar/**")
                .access((authentication, request) -> new AuthorizationDecision(
                    permisos.puede(authentication.get(), "producto", "UPDATE")));
            reglas.requestMatchers(org.springframework.http.HttpMethod.POST, "/categorias/actualizar/**")
                .access((authentication, request) -> new AuthorizationDecision(
                    permisos.puede(authentication.get(), "producto", "UPDATE")));
            reglas.requestMatchers(org.springframework.http.HttpMethod.GET, "/categorias/consultar/**")
                .access((authentication, request) -> new AuthorizationDecision(
                    permisos.puede(authentication.get(), "producto", "SELECT")));
            reglas.requestMatchers(org.springframework.http.HttpMethod.GET, "/categorias")
                .access((authentication, request) -> new AuthorizationDecision(
                    permisos.puede(authentication.get(), "producto", "ACCEDER")
                            && permisos.puede(authentication.get(), "producto", "SELECT")));
            reglas.requestMatchers(org.springframework.http.HttpMethod.GET, "/productos/nueva")
                .access((authentication, request) -> new AuthorizationDecision(
                    permisos.puede(authentication.get(), "producto", "INSERT")));
            reglas.requestMatchers(org.springframework.http.HttpMethod.POST, "/productos/guardar")
                .access((authentication, request) -> new AuthorizationDecision(
                    permisos.puede(authentication.get(), "producto", "INSERT")));
            reglas.requestMatchers(org.springframework.http.HttpMethod.GET, "/productos/editar/**")
                .access((authentication, request) -> new AuthorizationDecision(
                    permisos.puede(authentication.get(), "producto", "UPDATE")));
            reglas.requestMatchers(org.springframework.http.HttpMethod.POST, "/productos/actualizar/**")
                .access((authentication, request) -> new AuthorizationDecision(
                    permisos.puede(authentication.get(), "producto", "UPDATE")));
            reglas.requestMatchers(org.springframework.http.HttpMethod.GET, "/productos/consultar/**")
                .access((authentication, request) -> new AuthorizationDecision(
                    permisos.puede(authentication.get(), "producto", "SELECT")));
            reglas.requestMatchers(org.springframework.http.HttpMethod.GET, "/productos")
                .access((authentication, request) -> new AuthorizationDecision(
                    permisos.puede(authentication.get(), "producto", "ACCEDER")
                            && permisos.puede(authentication.get(), "producto", "SELECT")));
            reglas.requestMatchers(org.springframework.http.HttpMethod.GET, "/proveedores/nueva")
                .access((authentication, request) -> new AuthorizationDecision(
                    permisos.puede(authentication.get(), "proveedor", "INSERT")));
            reglas.requestMatchers(org.springframework.http.HttpMethod.POST, "/proveedores/guardar")
                .access((authentication, request) -> new AuthorizationDecision(
                    permisos.puede(authentication.get(), "proveedor", "INSERT")));
            reglas.requestMatchers(org.springframework.http.HttpMethod.GET, "/proveedores/editar/**")
                .access((authentication, request) -> new AuthorizationDecision(
                    permisos.puede(authentication.get(), "proveedor", "UPDATE")));
            reglas.requestMatchers(org.springframework.http.HttpMethod.POST, "/proveedores/actualizar/**")
                .access((authentication, request) -> new AuthorizationDecision(
                    permisos.puede(authentication.get(), "proveedor", "UPDATE")));
            reglas.requestMatchers(org.springframework.http.HttpMethod.GET, "/proveedores/consultar/**")
                .access((authentication, request) -> new AuthorizationDecision(
                    permisos.puede(authentication.get(), "proveedor", "SELECT")));
            reglas.requestMatchers(org.springframework.http.HttpMethod.GET, "/proveedores")
                .access((authentication, request) -> new AuthorizationDecision(
                    permisos.puede(authentication.get(), "proveedor", "ACCEDER")
                            && permisos.puede(authentication.get(), "proveedor", "SELECT")));
            reglas.requestMatchers("/login", "/css/**", "/webjars/**", "/favicon.ico", "/error").permitAll();
            // Estas rutas se validan en el servidor, no solo en el menú.
            String[] rutas = {"usuarios", "productos", "proveedores", "inventario", "traslados", "compras", "lotes", "categorias", "movimientos", "reportes"};
            String[] recursos = {"usuario", "producto", "proveedor", "inventario", "traslado", "compra", "lote", "producto", "movimiento", "reporte"};
            // 'bodega' se controla más adelante cuando exista módulo propio.
            for (int i = 0; i < rutas.length; i++) {
                final String recurso = recursos[i];
                reglas.requestMatchers("/" + rutas[i], "/" + rutas[i] + "/**", "/api/" + rutas[i] + "/**")
                    .access((authentication, request) -> new AuthorizationDecision(
                        permisos.puede(authentication.get(), recurso, "ACCEDER")));
            }
            reglas.anyRequest().access((authentication, request) -> new AuthorizationDecision(
                permisos.cuentaHabilitada(authentication.get())));
        });
        return http
            .formLogin(login -> login.loginPage("/login").loginProcessingUrl("/login")
                .successHandler((request, response, authentication) -> {
                    intentos.registrarExito(authentication.getName());
                    response.sendRedirect(request.getContextPath() + "/");
                })
                .failureHandler((request, response, exception) -> {
                    // LockedException/DisabledException no cuentan como contraseña incorrecta.
                    if (exception instanceof BadCredentialsException) {
                        intentos.registrarFallo(request.getParameter("username"));
                    }
                    response.sendRedirect(request.getContextPath() + "/login?error");
                }).permitAll())
            .logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true).clearAuthentication(true).permitAll())
            .exceptionHandling(errors -> errors.accessDeniedPage("/acceso-denegado"))
            .build();
    }
}
