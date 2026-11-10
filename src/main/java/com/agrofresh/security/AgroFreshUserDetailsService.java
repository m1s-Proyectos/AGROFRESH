package com.agrofresh.security;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Valida la identidad contra las tablas existentes usuario y rol. */
@Service
public class AgroFreshUserDetailsService implements UserDetailsService {
    private final JdbcTemplate jdbc;

    public AgroFreshUserDetailsService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var matches = jdbc.query(
            "SELECT u.username, u.contrasena, u.activo, u.bloqueado, r.nombre AS rol, r.activo AS rol_activo " +
            "FROM usuario u JOIN rol r ON r.id_rol = u.id_rol WHERE u.username = ?",
            (rs, row) -> User.withUsername(rs.getString("username"))
                .password(rs.getString("contrasena"))
                .disabled(!rs.getBoolean("activo") || !rs.getBoolean("rol_activo"))
                .accountLocked(rs.getBoolean("bloqueado"))
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + rs.getString("rol").toUpperCase())))
                .build(), username);
        if (matches.size() != 1) {
            throw new UsernameNotFoundException("Credenciales incorrectas");
        }
        return matches.get(0);
    }
}
