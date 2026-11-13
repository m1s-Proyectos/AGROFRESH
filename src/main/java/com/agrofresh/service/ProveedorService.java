package com.agrofresh.service;

import com.agrofresh.dto.ProveedorDetalleDto;
import com.agrofresh.dto.ProveedorForm;
import com.agrofresh.dto.ProveedorListadoDto;
import java.util.List;

public interface ProveedorService {

    List<ProveedorListadoDto> listar();

    List<ProveedorListadoDto> buscar(String termino);

    ProveedorDetalleDto consultar(Integer idProveedor);

    ProveedorDetalleDto registrar(ProveedorForm form);

    ProveedorDetalleDto actualizar(Integer idProveedor, ProveedorForm form);
}
