package com.agrofresh.controller;

import com.agrofresh.service.TrasladoService;
import com.agrofresh.service.TrasladoService.Solicitud;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/traslados")
public class TrasladoOperacionController {
    private final TrasladoService service;
    public TrasladoOperacionController(TrasladoService service) { this.service = service; }

    @PostMapping("/confirmar")
    public ResponseEntity<Map<String, String>> confirmar(@RequestBody Solicitud solicitud,
                                                         Authentication usuario) {
        try {
            service.trasladar(solicitud, usuario);
            return ResponseEntity.ok(Map.of("mensaje", "Traslado registrado correctamente."));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("mensaje", ex.getMessage()));
        } catch (ResponseStatusException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("mensaje", ex.getReason() == null ? "Acceso denegado." : ex.getReason()));
        } catch (DataAccessException ex) {
            // Mostramos errores de reglas de negocio de PostgreSQL sin revelar detalles SQL internos.
            Throwable cause = ex.getMostSpecificCause();
            if (cause instanceof org.postgresql.util.PSQLException pg
                    && pg.getSQLState() != null && pg.getSQLState().equals("P0001")) {
                return ResponseEntity.badRequest().body(Map.of("mensaje", pg.getServerErrorMessage() != null
                    && pg.getServerErrorMessage().getMessage() != null
                    ? pg.getServerErrorMessage().getMessage() : "El traslado no cumple las validaciones."));
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("mensaje", "No se pudo completar el traslado. Revisa la conexión y los procedimientos de PostgreSQL."));
        }
    }
}
