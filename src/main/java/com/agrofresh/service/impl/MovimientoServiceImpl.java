package com.agrofresh.service.impl;

import com.agrofresh.dto.MovimientoCatalogoDto;
import com.agrofresh.dto.MovimientoDetalleDto;
import com.agrofresh.dto.MovimientoFiltro;
import com.agrofresh.dto.MovimientoListadoDto;
import com.agrofresh.exception.ResourceNotFoundException;
import com.agrofresh.repository.MovimientoRepository;
import com.agrofresh.service.MovimientoService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MovimientoServiceImpl implements MovimientoService {

  private static final List<String> TIPOS_VALIDOS =
      List.of("ENTRADA", "SALIDA", "TRASLADO", "BAJA_VENCIMIENTO", "BAJA_DAÑO");

  private final MovimientoRepository movimientos;

  public MovimientoServiceImpl(MovimientoRepository movimientos) {
    this.movimientos = movimientos;
  }

  @Override
  public List<String> tiposDisponibles() {
    return TIPOS_VALIDOS;
  }

  @Override
  @Transactional(readOnly = true)
  public List<MovimientoCatalogoDto> bodegasParaFiltro() {
    return movimientos.listarBodegas();
  }

  @Override
  @Transactional(readOnly = true)
  public List<MovimientoCatalogoDto> productosParaFiltro() {
    return movimientos.listarProductos();
  }

  @Override
  public MovimientoFiltro normalizarFiltro(
      String tipo,
      Integer idBodega,
      Integer idProducto,
      LocalDate fechaDesde,
      LocalDate fechaHasta,
      Integer pagina,
      Integer tamano) {
    String tipoNormalizado = null;
    if (tipo != null && !tipo.isBlank()) {
      String valor = tipo.trim();
      if (TIPOS_VALIDOS.contains(valor)) {
        tipoNormalizado = valor;
      }
    }
    int paginaNormalizada = pagina == null || pagina < 0 ? 0 : pagina;
    int tamanoNormalizado = tamano == null ? MovimientoFiltro.TAMANO_DEFECTO : tamano;
    if (tamanoNormalizado < 1) {
      tamanoNormalizado = MovimientoFiltro.TAMANO_DEFECTO;
    }
    if (tamanoNormalizado > MovimientoFiltro.TAMANO_MAXIMO) {
      tamanoNormalizado = MovimientoFiltro.TAMANO_MAXIMO;
    }
    LocalDate desde = fechaDesde;
    LocalDate hasta = fechaHasta;
    if (desde != null && hasta != null && hasta.isBefore(desde)) {
      hasta = desde;
    }
    return new MovimientoFiltro(
        tipoNormalizado,
        idBodega,
        idProducto,
        desde,
        hasta,
        paginaNormalizada,
        tamanoNormalizado);
  }

  @Override
  @Transactional(readOnly = true)
  public List<MovimientoListadoDto> listar(MovimientoFiltro filtro) {
    return movimientos.listar(filtro);
  }

  @Override
  @Transactional(readOnly = true)
  public long contar(MovimientoFiltro filtro) {
    return movimientos.contar(filtro);
  }

  @Override
  @Transactional(readOnly = true)
  public MovimientoDetalleDto consultar(int idMovimiento) {
    return movimientos
        .buscarDetalle(idMovimiento)
        .orElseThrow(() -> new ResourceNotFoundException("El movimiento solicitado no existe."));
  }
}
