package com.agrofresh.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;

/** La BD es la fuente de verdad de los permisos. Sin registro, no hay acceso. */
@Service
public class PermisoService {
    private final JdbcTemplate jdbc;
    public PermisoService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** También protege páginas generales cuando una cuenta se desactiva o bloquea durante una sesión. */
    public boolean cuentaHabilitada(Authentication auth) {
        if (!tieneAutoridadPermitida(auth)) return false;
        try {
            return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT u.activo AND NOT u.bloqueado AND r.activo " +
                "FROM usuario u JOIN rol r ON r.id_rol=u.id_rol WHERE u.username=?",
                Boolean.class, auth.getName()));
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            return false;
        }
    }

    public boolean puede(Authentication auth, String recurso, String accion) {
        if (!tieneAutoridadPermitida(auth)) return false;
        try {
            return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT COALESCE(fn_usuario_tiene_permiso(u.id_usuario, ?, ?), FALSE) " +
                "FROM usuario u JOIN rol r ON r.id_rol=u.id_rol " +
                "WHERE u.username=? AND u.activo=TRUE AND u.bloqueado=FALSE AND r.activo=TRUE",
                Boolean.class, recurso, accion, auth.getName()));
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return false;
        }
    }

    private boolean tieneAutoridadPermitida(Authentication auth) {
        return auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())
                && auth.getAuthorities().stream()
                .anyMatch(authority -> RoleAuthorityMapper.isAllowedAuthority(authority.getAuthority()));
    }
}
