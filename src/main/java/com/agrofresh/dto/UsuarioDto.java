package com.agrofresh.dto;

public record UsuarioDto(
        Integer idUsuario,
        String username,
        String nombre,
        String apellido,
        String correo,
        boolean activo,
        String rol) {
}
