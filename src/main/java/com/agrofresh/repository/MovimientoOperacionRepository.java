package com.agrofresh.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Invocación de procedimientos de salida y baja (sin lógica de inventario en Java). */
@Repository
public class MovimientoOperacionRepository {

  private final JdbcTemplate jdbc;

  public MovimientoOperacionRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Integer callSalidaNoPerecedero(
      int idBodega, int idProducto, int cantidad, int idUsuario) {
    return jdbc.queryForObject(
        "CALL sp_salida_no_perecedero(?, ?, ?, ?, NULL)",
        Integer.class,
        idBodega,
        idProducto,
        cantidad,
        idUsuario);
  }

  public Integer callSalidaPerecedero(int idBodega, int idProducto, int cantidad, int idUsuario) {
    return jdbc.queryForObject(
        "CALL sp_salida_perecedero(?, ?, ?, ?, NULL)",
        Integer.class,
        idBodega,
        idProducto,
        cantidad,
        idUsuario);
  }

  public Integer callBajaDanoNoPerecedero(
      int idBodega, int idProducto, int cantidad, int idUsuario, String motivo) {
    return jdbc.queryForObject(
        "CALL sp_baja_dano_no_perecedero(?, ?, ?, ?, ?, NULL)",
        Integer.class,
        idBodega,
        idProducto,
        cantidad,
        idUsuario,
        motivo);
  }

  public Integer callBajaDanoPerecedero(
      int idBodega,
      int idProducto,
      int numLote,
      int cantidad,
      int idUsuario,
      String motivo) {
    return jdbc.queryForObject(
        "CALL sp_baja_dano_perecedero(?, ?, ?, ?, ?, ?, NULL)",
        Integer.class,
        idBodega,
        idProducto,
        numLote,
        cantidad,
        idUsuario,
        motivo);
  }

  public Integer callBajaVencimiento(
      int idBodega,
      int idProducto,
      int numLote,
      int cantidad,
      int idUsuario,
      String motivo) {
    return jdbc.queryForObject(
        "CALL sp_baja_vencimiento(?, ?, ?, ?, ?, ?, NULL)",
        Integer.class,
        idBodega,
        idProducto,
        numLote,
        cantidad,
        idUsuario,
        motivo);
  }
}
