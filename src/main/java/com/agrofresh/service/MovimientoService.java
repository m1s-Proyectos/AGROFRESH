package com.agrofresh.service;

import com.agrofresh.dto.MovimientoCatalogoDto;
import com.agrofresh.dto.MovimientoDetalleDto;
import com.agrofresh.dto.MovimientoFiltro;
import com.agrofresh.dto.MovimientoListadoDto;
import java.util.List;

public interface MovimientoService {

  List<String> tiposDisponibles();

  List<MovimientoCatalogoDto> bodegasParaFiltro();

  List<MovimientoCatalogoDto> productosParaFiltro();

  MovimientoFiltro normalizarFiltro(
      String tipo,
      Integer idBodega,
      Integer idProducto,
      java.time.LocalDate fechaDesde,
      java.time.LocalDate fechaHasta,
      Integer pagina,
      Integer tamano);

  List<MovimientoListadoDto> listar(MovimientoFiltro filtro);

  long contar(MovimientoFiltro filtro);

  MovimientoDetalleDto consultar(int idMovimiento);
}
