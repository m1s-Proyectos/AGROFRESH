package com.agrofresh.controller;

import com.agrofresh.dto.MovimientoDetalleDto;
import com.agrofresh.dto.MovimientoFiltro;
import com.agrofresh.dto.MovimientoListadoDto;
import com.agrofresh.security.PermisoService;
import com.agrofresh.service.CategoriaService;
import com.agrofresh.service.MovimientoService;
import com.agrofresh.service.ProductoService;
import com.agrofresh.service.ProveedorService;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MovimientosSecurityTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private JdbcTemplate jdbcTemplate;
  @MockBean private PermisoService permisos;
  @MockBean private MovimientoService movimientoService;
  @MockBean private CategoriaService categoriaService;
  @MockBean private ProductoService productoService;
  @MockBean private ProveedorService proveedorService;

  @BeforeEach
  void fixtures() {
    when(permisos.cuentaHabilitada(any(Authentication.class))).thenReturn(true);
    MovimientoFiltro filtro = new MovimientoFiltro(null, null, null, null, null, 0, 50);
    when(movimientoService.normalizarFiltro(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(filtro);
    when(movimientoService.contar(filtro)).thenReturn(0L);
    when(movimientoService.listar(filtro)).thenReturn(List.of());
    when(movimientoService.tiposDisponibles()).thenReturn(List.of("ENTRADA"));
    when(movimientoService.bodegasParaFiltro()).thenReturn(List.of());
    when(movimientoService.productosParaFiltro()).thenReturn(List.of());
    when(movimientoService.consultar(1))
        .thenReturn(
            new MovimientoDetalleDto(
                1,
                OffsetDateTime.now(),
                "ENTRADA",
                5,
                null,
                null,
                1,
                "P",
                "Prod",
                1,
                "Bod",
                null,
                null,
                "U",
                "S",
                false,
                null,
                null,
                null,
                null));
  }

  @Test
  void visitanteDebeAutenticarse() throws Exception {
    mockMvc
        .perform(get("/movimientos"))
        .andExpect(status().is3xxRedirection())
        .andExpect(header().string("Location", endsWith("/login")));
  }

  @Test
  @WithMockUser(authorities = "ROLE_ADMIN")
  void listadoExigeAccederYSelect() throws Exception {
    when(permisos.puede(any(), eq("movimiento"), eq("ACCEDER"))).thenReturn(true);
    when(permisos.puede(any(), eq("movimiento"), eq("SELECT"))).thenReturn(false);
    mockMvc.perform(get("/movimientos")).andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(authorities = "ROLE_ADMIN")
  void detalleExigeSelect() throws Exception {
    when(permisos.puede(any(), eq("movimiento"), eq("SELECT"))).thenReturn(false);
    mockMvc.perform(get("/movimientos/consultar/1")).andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(authorities = "ROLE_OPERADOR")
  void operativoConPermisosAccedeAlHistorial() throws Exception {
    when(permisos.puede(any(), eq("movimiento"), eq("ACCEDER"))).thenReturn(true);
    when(permisos.puede(any(), eq("movimiento"), eq("SELECT"))).thenReturn(true);
    mockMvc.perform(get("/movimientos")).andExpect(status().isOk());
  }
}
