package com.agrofresh.service;

import com.agrofresh.dto.CategoriaDto;
import com.agrofresh.dto.CategoriaForm;

import java.util.List;

public interface CategoriaService {

    List<CategoriaDto> listar();

    CategoriaDto consultar(Integer idCategoria);

    CategoriaDto registrar(CategoriaForm form);

    CategoriaDto actualizar(Integer idCategoria, CategoriaForm form);
}
