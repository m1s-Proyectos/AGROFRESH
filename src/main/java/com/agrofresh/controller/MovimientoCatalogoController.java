package com.agrofresh.controller;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Catálogos de solo lectura para formularios de salida y baja del módulo movimientos. */
@RestController
@RequestMapping("/api/movimientos")
public class MovimientoCatalogoController {

  private final ObjectProvider<JdbcTemplate> jdbcProvider;

  public MovimientoCatalogoController(ObjectProvider<JdbcTemplate> jdbcProvider) {
    this.jdbcProvider = jdbcProvider;
  }

  private JdbcTemplate db() {
    JdbcTemplate jdbc = jdbcProvider.getIfAvailable();
    if (jdbc == null) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "La base de datos no está configurada");
    }
    return jdbc;
  }

  @GetMapping("/bodegas")
  public List<Map<String, Object>> bodegas() {
    return db()
        .queryForList("SELECT id_bodega AS id, nombre, ubicacion FROM bodega ORDER BY nombre");
  }

  @GetMapping("/productos")
  public List<Map<String, Object>> productos(
      @RequestParam int bodega, @RequestParam String tipo) {
    if (!"PERECEDERO".equals(tipo) && !"NO_PERECEDERO".equals(tipo)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo inválido");
    }
    String tabla = "PERECEDERO".equals(tipo) ? "perecedero" : "no_perecedero";
    return db()
        .queryForList(
            "SELECT p.id_producto AS id, p.nombre, p.codigo_interno, i.cantidad_actual AS stock "
                + "FROM producto p JOIN inventario i ON p.id_producto=i.id_producto "
                + "JOIN "
                + tabla
                + " t ON t.id_producto=p.id_producto "
                + "WHERE i.id_bodega=? AND i.cantidad_actual>0 ORDER BY p.nombre",
            bodega);
  }

  /** Lotes vigentes con stock (información; salida perecedera no exige elegir lote). */
  @GetMapping("/lotes-disponibles")
  public List<Map<String, Object>> lotesDisponibles(
      @RequestParam int bodega, @RequestParam int producto) {
    return db()
        .queryForList(
            "SELECT num_lote AS id, fecha_vencimiento AS vencimiento, cantidad_disponible AS stock "
                + "FROM fn_lotes_disponibles(?, ?) ORDER BY fecha_vencimiento, num_lote",
            bodega,
            producto);
  }

  /** Lotes con existencias en bodega (baja por daño perecedero). */
  @GetMapping("/lotes-con-stock")
  public List<Map<String, Object>> lotesConStock(
      @RequestParam int bodega, @RequestParam int producto) {
    return db()
        .queryForList(
            "SELECT num_lote AS id, fech_vencimiento AS vencimiento, cantidad_actual AS stock "
                + "FROM lote WHERE id_bodega=? AND id_producto=? AND cantidad_actual>0 "
                + "ORDER BY fech_vencimiento, num_lote",
            bodega,
            producto);
  }

  /** Lotes vencidos con stock (baja por vencimiento). */
  @GetMapping("/lotes-vencidos")
  public List<Map<String, Object>> lotesVencidos(
      @RequestParam int bodega, @RequestParam int producto) {
    return db()
        .queryForList(
            "SELECT num_lote AS id, fech_vencimiento AS vencimiento, cantidad_actual AS stock "
                + "FROM lote WHERE id_bodega=? AND id_producto=? AND cantidad_actual>0 "
                + "AND fech_vencimiento <= CURRENT_DATE ORDER BY fech_vencimiento, num_lote",
            bodega,
            producto);
  }
}
