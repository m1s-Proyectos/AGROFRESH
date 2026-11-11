package com.agrofresh.dto;

public record ProveedorDetalleDto(
        Integer idProveedor,
        String nombre,
        String dui,
        String nit,
        String correoElectronico,
        String direccion,
        String telefono,
        String iva) {
}
