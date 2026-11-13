package com.agrofresh.controller;

import com.agrofresh.dto.MovimientoBajaSolicitud;
import com.agrofresh.dto.MovimientoSalidaSolicitud;
import com.agrofresh.service.MovimientoOperacionService;
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
@RequestMapping("/api/movimientos")
public class MovimientoOperacionController {

  private final MovimientoOperacionService operaciones;

  public MovimientoOperacionController(MovimientoOperacionService operaciones) {
    this.operaciones = operaciones;
  }

  @PostMapping("/salida/no-perecedero")
  public ResponseEntity<Map<String, Object>> salidaNoPerecedero(
      @RequestBody MovimientoSalidaSolicitud solicitud, Authentication auth) {
    return ejecutar(() -> operaciones.registrarSalidaNoPerecedero(solicitud, auth));
  }

  @PostMapping("/salida/perecedero")
  public ResponseEntity<Map<String, Object>> salidaPerecedero(
      @RequestBody MovimientoSalidaSolicitud solicitud, Authentication auth) {
    return ejecutar(() -> operaciones.registrarSalidaPerecedero(solicitud, auth));
  }

  @PostMapping("/baja/dano/no-perecedero")
  public ResponseEntity<Map<String, Object>> bajaDanoNoPerecedero(
      @RequestBody MovimientoBajaSolicitud solicitud, Authentication auth) {
    return ejecutar(() -> operaciones.registrarBajaDanoNoPerecedero(solicitud, auth));
  }

  @PostMapping("/baja/dano/perecedero")
  public ResponseEntity<Map<String, Object>> bajaDanoPerecedero(
      @RequestBody MovimientoBajaSolicitud solicitud, Authentication auth) {
    return ejecutar(() -> operaciones.registrarBajaDanoPerecedero(solicitud, auth));
  }

  @PostMapping("/baja/vencimiento")
  public ResponseEntity<Map<String, Object>> bajaVencimiento(
      @RequestBody MovimientoBajaSolicitud solicitud, Authentication auth) {
    return ejecutar(() -> operaciones.registrarBajaVencimiento(solicitud, auth));
  }

  private ResponseEntity<Map<String, Object>> ejecutar(Operacion operacion) {
    try {
      int idMovimiento = operacion.ejecutar();
      return ResponseEntity.ok(
          Map.of(
              "mensaje", "Operación registrada correctamente.",
              "idMovimiento", idMovimiento));
    } catch (IllegalArgumentException ex) {
      return ResponseEntity.badRequest().body(Map.of("mensaje", ex.getMessage()));
    } catch (ResponseStatusException ex) {
      return ResponseEntity.status(ex.getStatusCode())
          .body(Map.of("mensaje", ex.getReason() == null ? "Acceso denegado." : ex.getReason()));
    } catch (DataAccessException ex) {
      Throwable cause = ex.getMostSpecificCause();
      if (cause instanceof org.postgresql.util.PSQLException pg
          && "P0001".equals(pg.getSQLState())
          && pg.getServerErrorMessage() != null
          && pg.getServerErrorMessage().getMessage() != null) {
        return ResponseEntity.badRequest()
            .body(Map.of("mensaje", pg.getServerErrorMessage().getMessage()));
      }
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(
              Map.of(
                  "mensaje",
                  "No se pudo completar la operación. Revisa la conexión y los procedimientos de PostgreSQL."));
    }
  }

  @FunctionalInterface
  private interface Operacion {
    int ejecutar();
  }
}
