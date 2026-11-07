package com.agrofresh.service;

import com.agrofresh.dto.UsuarioDto;

import java.util.List;

public interface UsuarioService {

    List<UsuarioDto> listar();

    UsuarioDto obtenerPorId(Integer idUsuario);

    UsuarioDto obtenerPorUsername(String username);
}
