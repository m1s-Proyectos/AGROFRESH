package com.agrofresh.repository;

import com.agrofresh.dto.MovimientoCatalogoDto;
import com.agrofresh.dto.MovimientoDetalleDto;
import com.agrofresh.dto.MovimientoFiltro;
import com.agrofresh.dto.MovimientoListadoDto;
import com.agrofresh.mapper.MovimientoMapper;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MovimientoRepository {

  private static final String FROM_LISTADO =
      " FROM movimiento m "
          + "LEFT JOIN producto p ON p.id_producto = m.id_producto "
          + "LEFT JOIN bodega bo ON bo.id_bodega = m.id_bodega "
          + "LEFT JOIN movimiento_bodega mb ON mb.id_movimiento = m.id_movimiento "
          + "LEFT JOIN bodega bd ON bd.id_bodega = mb.id_bodega_destino "
          + "JOIN usuario u ON u.id_usuario = m.id_usuario ";

  private static final String SELECT_LISTADO =
      "SELECT m.id_movimiento, m.fecha, m.tipo_movimiento, m.cantidad, m.num_lote, m.id_producto, "
          + "p.codigo_interno, p.nombre AS nombre_producto, m.id_bodega, bo.nombre AS nombre_bodega_origen, "
          + "mb.id_bodega_destino, bd.nombre AS nombre_bodega_destino, "
          + "u.nombre AS nombre_usuario, u.apellido AS apellido_usuario, "
          + "EXISTS (SELECT 1 FROM movimiento_compra mc WHERE mc.id_movimiento = m.id_movimiento) AS vinculo_compra "
          + FROM_LISTADO;

  private static final String SELECT_DETALLE =
      "SELECT m.id_movimiento, m.fecha, m.tipo_movimiento, m.cantidad, m.num_lote, "
          + "l.fech_vencimiento, m.id_producto, p.codigo_interno, p.nombre AS nombre_producto, "
          + "m.id_bodega, bo.nombre AS nombre_bodega_origen, "
          + "mb.id_bodega_destino, bd.nombre AS nombre_bodega_destino, "
          + "u.nombre AS nombre_usuario, u.apellido AS apellido_usuario, "
          + "EXISTS (SELECT 1 FROM movimiento_compra mc WHERE mc.id_movimiento = m.id_movimiento) AS vinculo_compra, "
          + "(SELECT c.id_compra FROM movimiento_compra mc "
          + "JOIN detalle_compra dc ON dc.id_detalle_compra = mc.id_detalle_compra "
          + "JOIN compra c ON c.id_compra = dc.id_compra "
          + "WHERE mc.id_movimiento = m.id_movimiento ORDER BY c.id_compra LIMIT 1) AS id_compra, "
          + "(SELECT c.fecha_compra FROM movimiento_compra mc "
          + "JOIN detalle_compra dc ON dc.id_detalle_compra = mc.id_detalle_compra "
          + "JOIN compra c ON c.id_compra = dc.id_compra "
          + "WHERE mc.id_movimiento = m.id_movimiento ORDER BY c.id_compra LIMIT 1) AS fecha_compra, "
          + "(SELECT pr.nombre FROM movimiento_compra mc "
          + "JOIN detalle_compra dc ON dc.id_detalle_compra = mc.id_detalle_compra "
          + "JOIN compra c ON c.id_compra = dc.id_compra "
          + "JOIN proveedor pr ON pr.id_proveedor = c.id_proveedor "
          + "WHERE mc.id_movimiento = m.id_movimiento ORDER BY c.id_compra LIMIT 1) AS nombre_proveedor, "
          + "(SELECT b.descripcion FROM bitacora b "
          + "WHERE b.tabla_afectada = 'movimiento' "
          + "AND b.descripcion LIKE '%movimiento #' || CAST(m.id_movimiento AS text) || '%' "
          + "ORDER BY b.id_bitacora DESC LIMIT 1) AS descripcion_bitacora "
          + "FROM movimiento m "
          + "LEFT JOIN producto p ON p.id_producto = m.id_producto "
          + "LEFT JOIN bodega bo ON bo.id_bodega = m.id_bodega "
          + "LEFT JOIN movimiento_bodega mb ON mb.id_movimiento = m.id_movimiento "
          + "LEFT JOIN bodega bd ON bd.id_bodega = mb.id_bodega_destino "
          + "JOIN usuario u ON u.id_usuario = m.id_usuario "
          + "LEFT JOIN lote l ON l.num_lote = m.num_lote AND l.id_bodega = m.id_bodega AND l.id_producto = m.id_producto "
          + "WHERE m.id_movimiento = ?";

  private final JdbcTemplate jdbc;

  public MovimientoRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<MovimientoListadoDto> listar(MovimientoFiltro filtro) {
    ConsultaPartes partes = construirFiltros(filtro);
    String sql = SELECT_LISTADO + partes.where + " ORDER BY m.fecha DESC, m.id_movimiento DESC LIMIT ? OFFSET ?";
    List<Object> parametros = new ArrayList<>(partes.parametros);
    parametros.add(filtro.tamanoPagina());
    parametros.add(filtro.offset());
    return jdbc.query(sql, (rs, rowNum) -> MovimientoMapper.filaListado(rs), parametros.toArray());
  }

  public long contar(MovimientoFiltro filtro) {
    ConsultaPartes partes = construirFiltros(filtro);
    String sql = "SELECT COUNT(DISTINCT m.id_movimiento) " + FROM_LISTADO + partes.where;
    Long total = jdbc.queryForObject(sql, Long.class, partes.parametros.toArray());
    return total == null ? 0L : total;
  }

  public Optional<MovimientoDetalleDto> buscarDetalle(int idMovimiento) {
    List<MovimientoDetalleDto> filas =
        jdbc.query(SELECT_DETALLE, (rs, rowNum) -> MovimientoMapper.filaDetalle(rs), idMovimiento);
    return filas.isEmpty() ? Optional.empty() : Optional.of(filas.getFirst());
  }

  public List<MovimientoCatalogoDto> listarBodegas() {
    return jdbc.query(
        "SELECT id_bodega, nombre FROM bodega ORDER BY nombre",
        (rs, rowNum) -> new MovimientoCatalogoDto(rs.getInt("id_bodega"), rs.getString("nombre")));
  }

  public List<MovimientoCatalogoDto> listarProductos() {
    return jdbc.query(
        "SELECT id_producto, codigo_interno, nombre FROM producto ORDER BY nombre",
        (rs, rowNum) -> new MovimientoCatalogoDto(
            rs.getInt("id_producto"),
            rs.getString("codigo_interno") + " — " + rs.getString("nombre")));
  }

  private ConsultaPartes construirFiltros(MovimientoFiltro filtro) {
    ConsultaPartes partes = new ConsultaPartes();
    if (filtro.tipo() != null && !filtro.tipo().isBlank()) {
      partes.where.append("AND m.tipo_movimiento = ? ");
      partes.parametros.add(filtro.tipo());
    }
    if (filtro.idProducto() != null) {
      partes.where.append("AND m.id_producto = ? ");
      partes.parametros.add(filtro.idProducto());
    }
    if (filtro.idBodega() != null) {
      partes.where.append("AND (m.id_bodega = ? OR mb.id_bodega_destino = ?) ");
      partes.parametros.add(filtro.idBodega());
      partes.parametros.add(filtro.idBodega());
    }
    if (filtro.fechaDesde() != null) {
      partes.where.append("AND m.fecha >= ? ");
      partes.parametros.add(Timestamp.valueOf(filtro.fechaDesde().atStartOfDay()));
    }
    if (filtro.fechaHasta() != null) {
      LocalDate diaSiguiente = filtro.fechaHasta().plusDays(1);
      partes.where.append("AND m.fecha < ? ");
      partes.parametros.add(Timestamp.valueOf(diaSiguiente.atStartOfDay()));
    }
    return partes;
  }

  private static final class ConsultaPartes {
    private final StringBuilder where = new StringBuilder(" WHERE 1=1 ");
    private final List<Object> parametros = new ArrayList<>();
  }
}
