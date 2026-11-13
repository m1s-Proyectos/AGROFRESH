package com.agrofresh.dto;

import java.time.OffsetDateTime;

public record MovimientoListadoDto(
        Integer idMovimiento,
        OffsetDateTime fecha,
        String tipoMovimiento,
        Integer cantidad,
        Integer numLote,
        Integer idProducto,
        String codigoProducto,
        String nombreProducto,
        Integer idBodegaOrigen,
        String nombreBodegaOrigen,
        Integer idBodegaDestino,
        String nombreBodegaDestino,
        String nombreResponsable,
        boolean vinculoCompra) {
}
