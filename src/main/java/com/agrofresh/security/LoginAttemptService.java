package com.agrofresh.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Contador de errores por cuenta (no cuenta usuarios inexistentes). */
@Service
public class LoginAttemptService {
    private final JdbcTemplate jdbc;

    public LoginAttemptService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Una sola operación SQL: evita perder incrementos cuando hay varios intentos. */
    public void registrarFallo(String username) {
        if (username == null || username.isBlank()) return;
        jdbc.update("UPDATE usuario SET intentos_fallidos = intentos_fallidos + 1, " +
            "bloqueado = (intentos_fallidos + 1 >= 3) " +
            "WHERE username = ? AND activo = TRUE AND bloqueado = FALSE", username);
    }

    public void registrarExito(String username) {
        jdbc.update("UPDATE usuario SET intentos_fallidos = 0 WHERE username = ?", username);
    }
}
