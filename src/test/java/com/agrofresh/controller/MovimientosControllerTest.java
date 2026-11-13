package com.agrofresh.controller;

import com.agrofresh.dto.MovimientoDetalleDto;
import com.agrofresh.dto.MovimientoFiltro;
import com.agrofresh.dto.MovimientoListadoDto;
import com.agrofresh.exception.ResourceNotFoundException;
import com.agrofresh.security.PermisoService;
import com.agrofresh.service.MovimientoService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.ui.ExtendedModelMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MovimientosControllerTest {

  private MovimientoService service;
  private PermisoService permisos;
  private MovimientosController controller;
  private UsernamePasswordAuthenticationToken usuario;

  @BeforeEach
  void setUp() {
    service = mock(MovimientoService.class);
    permisos = mock(PermisoService.class);
    controller = new MovimientosController(service, permisos);
    usuario =
        UsernamePasswordAuthenticationToken.authenticated(
            "op", "none", List.of(new SimpleGrantedAuthority("ROLE_OPERADOR")));
    when(permisos.puede(usuario, "movimiento", "SELECT")).thenReturn(true);
  }

  @Test
  void listadoAplicaFiltrosYExponeTotales() {
    MovimientoFiltro filtro =
        new MovimientoFiltro("ENTRADA", 2, 5, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), 0, 50);
    when(service.normalizarFiltro("ENTRADA", 2, 5, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), null, null))
        .thenReturn(filtro);
    when(service.contar(filtro)).thenReturn(3L);
    when(service.listar(filtro)).thenReturn(List.of(filaListado(1)));
    when(service.tiposDisponibles()).thenReturn(List.of("ENTRADA"));
    when(service.bodegasParaFiltro()).thenReturn(List.of());
    when(service.productosParaFiltro()).thenReturn(List.of());

    ExtendedModelMap model = new ExtendedModelMap();
    assertEquals(
        "movimientos",
        controller.listar(
            "ENTRADA", 2, 5, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), null, null, model, usuario));
    assertEquals(3L, model.getAttribute("totalRegistros"));
    verify(service).listar(filtro);
  }

  @Test
  void detalleConsultaServicio() {
    when(service.consultar(8)).thenReturn(detalle(8));
    ExtendedModelMap model = new ExtendedModelMap();
    assertEquals("movimiento-detalle", controller.consultar(8, model, usuario));
    assertEquals(8, ((MovimientoDetalleDto) model.getAttribute("movimiento")).idMovimiento());
  }

  @Test
  void deniegaSinPermisoSelect() {
    when(permisos.puede(usuario, "movimiento", "SELECT")).thenReturn(false);
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> controller.listar(null, null, null, null, null, null, null, new ExtendedModelMap(), usuario));
  }

  @Test
  void propagaNoEncontradoDelServicio() {
    when(service.consultar(99)).thenThrow(new ResourceNotFoundException("no"));
    assertThrows(
        ResourceNotFoundException.class,
        () -> controller.consultar(99, new ExtendedModelMap(), usuario));
  }

  private static MovimientoListadoDto filaListado(int id) {
    return new MovimientoListadoDto(
        id,
        OffsetDateTime.parse("2026-01-15T10:00:00-06:00"),
        "ENTRADA",
        10,
        null,
        3,
        "P-01",
        "Mango",
        1,
        "Central",
        null,
        null,
        "Ana López",
        true);
  }

  private static MovimientoDetalleDto detalle(int id) {
    return new MovimientoDetalleDto(
        id,
        OffsetDateTime.parse("2026-01-15T10:00:00-06:00"),
        "ENTRADA",
        10,
        null,
        null,
        3,
        "P-01",
        "Mango",
        1,
        "Central",
        null,
        null,
        "Ana",
        "López",
        true,
        4,
        LocalDate.of(2026, 1, 10),
        "Proveedor SA",
        null);
  }
}
