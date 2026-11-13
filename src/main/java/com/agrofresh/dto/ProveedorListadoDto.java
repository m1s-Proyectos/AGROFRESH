package com.agrofresh.dto;

public record ProveedorListadoDto(
        Integer idProveedor,
        String nombre,
        String dui,
        String nit,
        String correoElectronico,
        String telefono,
        String iva) {
}
