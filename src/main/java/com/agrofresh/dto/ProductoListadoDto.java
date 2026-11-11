package com.agrofresh.dto;

public record ProductoListadoDto(
        Integer idProducto,
        String codigoInterno,
        String nombre,
        Integer idCategoria,
        String nombreCategoria,
        String unidadMedida,
        ClasificacionProducto clasificacion) {
}
