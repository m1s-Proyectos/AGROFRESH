package com.agrofresh.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record MovimientoDetalleDto(
        Integer idMovimiento,
        OffsetDateTime fecha,
        String tipoMovimiento,
        Integer cantidad,
        Integer numLote,
        LocalDate fechaVencimientoLote,
        Integer idProducto,
        String codigoProducto,
        String nombreProducto,
        Integer idBodegaOrigen,
        String nombreBodegaOrigen,
        Integer idBodegaDestino,
        String nombreBodegaDestino,
        String nombreResponsable,
        String apellidoResponsable,
        boolean vinculoCompra,
        Integer idCompra,
        LocalDate fechaCompra,
        String nombreProveedor,
        String descripcionBitacora) {
}
