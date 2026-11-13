package com.agrofresh.mapper;

import com.agrofresh.dto.MovimientoDetalleDto;
import com.agrofresh.dto.MovimientoListadoDto;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public final class MovimientoMapper {

    private MovimientoMapper() {
    }

    public static MovimientoListadoDto filaListado(ResultSet rs) throws SQLException {
        String nombre = rs.getString("nombre_usuario");
        String apellido = rs.getString("apellido_usuario");
        String responsable = combinarNombre(nombre, apellido);
        return new MovimientoListadoDto(
                rs.getInt("id_movimiento"),
                aOffset(rs.getTimestamp("fecha")),
                rs.getString("tipo_movimiento"),
                rs.getInt("cantidad"),
                nullableInt(rs, "num_lote"),
                nullableInt(rs, "id_producto"),
                rs.getString("codigo_interno"),
                rs.getString("nombre_producto"),
                rs.getInt("id_bodega"),
                rs.getString("nombre_bodega_origen"),
                nullableInt(rs, "id_bodega_destino"),
                rs.getString("nombre_bodega_destino"),
                responsable,
                rs.getBoolean("vinculo_compra"));
    }

    public static MovimientoDetalleDto filaDetalle(ResultSet rs) throws SQLException {
        return new MovimientoDetalleDto(
                rs.getInt("id_movimiento"),
                aOffset(rs.getTimestamp("fecha")),
                rs.getString("tipo_movimiento"),
                rs.getInt("cantidad"),
                nullableInt(rs, "num_lote"),
                toLocalDate(rs.getDate("fech_vencimiento")),
                nullableInt(rs, "id_producto"),
                rs.getString("codigo_interno"),
                rs.getString("nombre_producto"),
                rs.getInt("id_bodega"),
                rs.getString("nombre_bodega_origen"),
                nullableInt(rs, "id_bodega_destino"),
                rs.getString("nombre_bodega_destino"),
                rs.getString("nombre_usuario"),
                rs.getString("apellido_usuario"),
                rs.getBoolean("vinculo_compra"),
                nullableInt(rs, "id_compra"),
                toLocalDate(rs.getDate("fecha_compra")),
                rs.getString("nombre_proveedor"),
                rs.getString("descripcion_bitacora"));
    }

    private static String combinarNombre(String nombre, String apellido) {
        if (nombre == null && apellido == null) {
            return "—";
        }
        if (nombre == null) {
            return apellido;
        }
        if (apellido == null || apellido.isBlank()) {
            return nombre;
        }
        return nombre + " " + apellido;
    }

    private static OffsetDateTime aOffset(Timestamp fecha) {
        if (fecha == null) {
            return null;
        }
        return fecha.toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }

    private static LocalDate toLocalDate(java.sql.Date fecha) {
        return fecha == null ? null : fecha.toLocalDate();
    }

    private static Integer nullableInt(ResultSet rs, String columna) throws SQLException {
        int valor = rs.getInt(columna);
        return rs.wasNull() ? null : valor;
    }
}
