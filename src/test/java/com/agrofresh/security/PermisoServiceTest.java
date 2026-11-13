package com.agrofresh.security;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PermisoServiceTest {

    @Test
    void delegatesAllowedRolePermissionCheckToTheDatabaseFunction() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PermisoService service = new PermisoService(jdbc);
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                "unit-test-user", "not-a-password", List.of(new SimpleGrantedAuthority("ROLE_OPERADOR")));
        when(jdbc.queryForObject(contains("fn_usuario_tiene_permiso"), eq(Boolean.class),
                eq("traslado"), eq("INSERT"), eq("unit-test-user"))).thenReturn(true);

        assertTrue(service.puede(authentication, "traslado", "INSERT"));
    }

    @Test
    void rejectsAnUnrecognizedAuthorityBeforeConsultingTheDatabase() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PermisoService service = new PermisoService(jdbc);
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                "unit-test-user", "not-a-password", List.of(new SimpleGrantedAuthority("ROLE_OTHER")));

        assertFalse(service.puede(authentication, "usuario", "UPDATE"));
        verifyNoInteractions(jdbc);
    }
}
