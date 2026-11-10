package com.agrofresh.controller;

import java.time.LocalDate;
import com.agrofresh.security.PermisoService;
import java.util.Map;
import java.util.LinkedHashMap;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(annotations = Controller.class)
public class SessionModelAdvice {
    private final JdbcTemplate jdbc;
    private final PermisoService permisos;
    public SessionModelAdvice(JdbcTemplate jdbc, PermisoService permisos) { this.jdbc = jdbc; this.permisos = permisos; }

    @ModelAttribute("sesion")
    public Object sesion() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) return null;
        String username = authentication.getName();
        String nombre = jdbc.query(
            "SELECT nombre || ' ' || apellido FROM usuario WHERE username = ?",
            rs -> rs.next() ? rs.getString(1) : username, username);
        String rol = authentication.getAuthorities().stream()
            .map(a -> a.getAuthority()).filter(a -> a.startsWith("ROLE_"))
            .map(a -> a.substring(5)).findFirst().orElse("");
        String fecha = LocalDate.now(ZoneId.of("America/El_Salvador"))
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy", new Locale("es", "SV")));
        return new SesionVista(username, nombre, rol, fecha);
    }

    @ModelAttribute("accesos")
    public Map<String, Boolean> accesos() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Map<String, Boolean> mapa = new LinkedHashMap<>();
        for (String recurso : new String[]{"usuario", "producto", "proveedor", "inventario", "traslado", "compra", "lote", "movimiento", "reporte"}) {
            mapa.put(recurso, permisos.puede(auth, recurso, "ACCEDER"));
        }
        return mapa;
    }

    public record SesionVista(String username, String nombre, String rol, String fecha) {}
}
