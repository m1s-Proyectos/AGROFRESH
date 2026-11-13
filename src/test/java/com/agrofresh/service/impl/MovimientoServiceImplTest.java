package com.agrofresh.service.impl;

import com.agrofresh.dto.MovimientoDetalleDto;
import com.agrofresh.dto.MovimientoFiltro;
import com.agrofresh.exception.ResourceNotFoundException;
import com.agrofresh.repository.MovimientoRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MovimientoServiceImplTest {

  private MovimientoRepository repository;
  private MovimientoServiceImpl service;

  @BeforeEach
  void setUp() {
    repository = mock(MovimientoRepository.class);
    service = new MovimientoServiceImpl(repository);
  }

  @Test
  void normalizaTipoInvalidoYFechasInvertidas() {
    MovimientoFiltro filtro =
        service.normalizarFiltro(
            "INVALIDO", null, null, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1), -1, 500);
    assertNull(filtro.tipo());
    assertEquals(LocalDate.of(2026, 2, 1), filtro.fechaDesde());
    assertEquals(LocalDate.of(2026, 2, 1), filtro.fechaHasta());
    assertEquals(0, filtro.pagina());
    assertEquals(MovimientoFiltro.TAMANO_MAXIMO, filtro.tamanoPagina());
  }

  @Test
  void consultaDelegaEnRepositorio() {
    MovimientoDetalleDto dto =
        new MovimientoDetalleDto(
            2,
            OffsetDateTime.now(),
            "TRASLADO",
            3,
            1,
            null,
            4,
            "C",
            "N",
            1,
            "A",
            2,
            "B",
            "U",
            "S",
            false,
            null,
            null,
            null,
            null);
    when(repository.buscarDetalle(2)).thenReturn(Optional.of(dto));
    assertEquals(2, service.consultar(2).idMovimiento());
  }

  @Test
  void consultaInexistenteLanzaExcepcion() {
    when(repository.buscarDetalle(404)).thenReturn(Optional.empty());
    assertThrows(ResourceNotFoundException.class, () -> service.consultar(404));
  }

  @Test
  void listarUsaFiltroNormalizado() {
    MovimientoFiltro filtro = new MovimientoFiltro("SALIDA", null, null, null, null, 0, 50);
    when(repository.listar(filtro)).thenReturn(java.util.List.of());
    service.listar(filtro);
    verify(repository).listar(filtro);
  }

  @Test
  void contarUsaRepositorio() {
    MovimientoFiltro filtro = new MovimientoFiltro(null, 3, null, null, null, 0, 50);
    when(repository.contar(filtro)).thenReturn(7L);
    assertEquals(7L, service.contar(filtro));
  }
}
