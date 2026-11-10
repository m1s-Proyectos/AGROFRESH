package com.agrofresh.service;

import com.agrofresh.security.PermisoService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Ejecuta los procedimientos existentes de PostgreSQL; no duplica su lógica de stock. */
@Service
public class TrasladoService {
    private final JdbcTemplate jdbc;
    private final PermisoService permisos;

    public TrasladoService(JdbcTemplate jdbc, PermisoService permisos) {
        this.jdbc = jdbc;
        this.permisos = permisos;
    }

    public record Solicitud(Integer origen, Integer destino, Integer producto,
                           Integer lote, Integer cantidad, String tipo) {}

    @Transactional
    public void trasladar(Solicitud s, Authentication auth) {
        if (!permisos.puede(auth, "traslado", "INSERT")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sin permiso para realizar traslados.");
        }
        if (s == null || s.origen() == null || s.destino() == null || s.producto() == null
                || s.cantidad() == null || s.origen() <= 0 || s.destino() <= 0
                || s.producto() <= 0 || s.cantidad() <= 0 || s.origen().equals(s.destino())) {
            throw new IllegalArgumentException("Selecciona bodegas distintas, producto y cantidad válida.");
        }
        // El identificador del usuario se obtiene del login; el navegador nunca lo envía.
        Integer idUsuario = jdbc.queryForObject(
            "SELECT id_usuario FROM usuario WHERE username = ? AND activo = TRUE",
            Integer.class, auth.getName());
        if (idUsuario == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuario no autorizado.");
        }
        boolean esPerecedero = Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM perecedero WHERE id_producto=?)", Boolean.class, s.producto()));
        boolean esNoPerecedero = Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM no_perecedero WHERE id_producto=?)", Boolean.class, s.producto()));
        if (esPerecedero == esNoPerecedero) {
            throw new IllegalArgumentException("El producto no tiene un tipo válido.");
        }
        // Evita un tipo manipulado o una selección desactualizada en el cliente.
        if (!((esPerecedero && "PERECEDERO".equals(s.tipo())) ||
              (esNoPerecedero && "NO_PERECEDERO".equals(s.tipo())))) {
            throw new IllegalArgumentException("El tipo seleccionado no corresponde al producto.");
        }
        if (esPerecedero) {
            if (s.lote() == null || s.lote() <= 0) {
                throw new IllegalArgumentException("Selecciona un lote válido.");
            }
            jdbc.update("CALL sp_trasladar_perecedero(?, ?, ?, ?, ?, ?)",
                s.origen(), s.destino(), s.producto(), s.lote(), s.cantidad(), idUsuario);
        } else {
            jdbc.update("CALL sp_trasladar_no_perecedero(?, ?, ?, ?, ?)",
                s.origen(), s.destino(), s.producto(), s.cantidad(), idUsuario);
        }
    }
}
