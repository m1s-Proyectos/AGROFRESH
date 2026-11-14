package com.agrofresh.service.impl;

import com.agrofresh.dto.MovimientoBajaSolicitud;
import com.agrofresh.dto.MovimientoSalidaSolicitud;
import com.agrofresh.repository.MovimientoOperacionRepository;
import com.agrofresh.security.PermisoService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MovimientoOperacionServiceImplTest {

  private MovimientoOperacionRepository repo;
  private JdbcTemplate jdbc;
  private PermisoService permisos;
  private MovimientoOperacionServiceImpl service;
  private UsernamePasswordAuthenticationToken operador;
  private UsernamePasswordAuthenticationToken admin;

  @BeforeEach
  void setUp() {
    repo = mock(MovimientoOperacionRepository.class);
    jdbc = mock(JdbcTemplate.class);
    permisos = mock(PermisoService.class);
    service = new MovimientoOperacionServiceImpl(repo, jdbc, permisos);
    operador =
        UsernamePasswordAuthenticationToken.authenticated(
            "op", "x", List.of(new SimpleGrantedAuthority("ROLE_OPERADOR")));
    admin =
        UsernamePasswordAuthenticationToken.authenticated(
            "admin", "x", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    when(jdbc.queryForObject(anyString(), eq(Integer.class), anyString())).thenReturn(3);
  }

  @Test
  void salidaNpInvocaSpYDevuelveId() {
    when(permisos.puede(operador, "movimiento", "INSERT")).thenReturn(true);
    when(repo.callSalidaNoPerecedero(1, 2, 5, 3)).thenReturn(99);
    int id =
        service.registrarSalidaNoPerecedero(new MovimientoSalidaSolicitud(1, 2, 5), operador);
    assertEquals(99, id);
    verify(repo).callSalidaNoPerecedero(1, 2, 5, 3);
  }

  @Test
  void salidaSinPermisoRechaza() {
    when(permisos.puede(operador, "movimiento", "INSERT")).thenReturn(false);
    assertThrows(
        ResponseStatusException.class,
        () ->
            service.registrarSalidaNoPerecedero(
                new MovimientoSalidaSolicitud(1, 2, 1), operador));
    verify(repo, never()).callSalidaNoPerecedero(anyInt(), anyInt(), anyInt(), anyInt());
  }

  @Test
  void bajaRequiereAdmin() {
    assertThrows(
        ResponseStatusException.class,
        () ->
            service.registrarBajaDanoNoPerecedero(
                new MovimientoBajaSolicitud(1, 2, 1, "motivo", null), operador));
    verify(repo, never()).callBajaDanoNoPerecedero(anyInt(), anyInt(), anyInt(), anyInt(), any());
  }

  @Test
  void bajaAdminInvocaSp() {
    when(repo.callBajaVencimiento(1, 2, 10, 2, 3, "vencido")).thenReturn(50);
    int id =
        service.registrarBajaVencimiento(
            new MovimientoBajaSolicitud(1, 2, 2, "vencido", 10), admin);
    assertEquals(50, id);
  }

  @Test
  void motivoVacioRechazaAntesDelSp() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.registrarBajaDanoPerecedero(
                new MovimientoBajaSolicitud(1, 2, 1, "  ", 5), admin));
  }
}
