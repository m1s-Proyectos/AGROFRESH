package com.agrofresh.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProductoDetalleDto(
        Integer idProducto,
        String codigoInterno,
        String nombre,
        Integer idCategoria,
        String nombreCategoria,
        String unidadMedida,
        ClasificacionProducto clasificacion,
        BigDecimal temperaturaRecomendada,
        Integer vidaUtil,
        LocalDate fechaFabricacion,
        Integer vidaUtilAnaquel) {
}
