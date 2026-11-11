package com.agrofresh.service;

import com.agrofresh.dto.CategoriaDto;
import com.agrofresh.dto.ProductoDetalleDto;
import com.agrofresh.dto.ProductoForm;
import com.agrofresh.dto.ProductoListadoDto;
import java.util.List;

public interface ProductoService {
    List<ProductoListadoDto> listar();
    ProductoDetalleDto consultar(Integer idProducto);
    ProductoDetalleDto registrar(ProductoForm form);
    ProductoDetalleDto actualizar(Integer idProducto, ProductoForm form);
    List<CategoriaDto> categoriasDisponibles();
    List<String> unidadesDisponibles();
}
