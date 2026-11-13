package com.agrofresh.controller;

import com.agrofresh.security.PermisoService;
import com.agrofresh.service.CategoriaService;
import com.agrofresh.service.MovimientoService;
import com.agrofresh.service.ProductoService;
import com.agrofresh.service.ProveedorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
class MovimientoFormViewsTest {

  @Autowired private MockMvc mockMvc;
  @MockBean private JdbcTemplate jdbcTemplate;
  @MockBean private PermisoService permisos;
  @MockBean private MovimientoService movimientoService;
  @MockBean private CategoriaService categoriaService;
  @MockBean private ProductoService productoService;
  @MockBean private ProveedorService proveedorService;

  @BeforeEach
  void fixtures() {
    when(permisos.cuentaHabilitada(any())).thenReturn(true);
    when(permisos.puede(any(), eq("movimiento"), eq("ACCEDER"))).thenReturn(true);
    when(permisos.puede(any(), eq("movimiento"), eq("SELECT"))).thenReturn(true);
    when(permisos.puede(any(), eq("movimiento"), eq("INSERT"))).thenReturn(true);
    when(permisos.puede(any(), eq("compra"), eq("ACCEDER"))).thenReturn(true);
    when(permisos.puede(any(), eq("traslado"), eq("ACCEDER"))).thenReturn(true);
  }

  @Test
  @WithMockUser(authorities = "ROLE_OPERADOR")
  void operadorAccedeSalidaNp() throws Exception {
    mockMvc
        .perform(get("/movimientos/salida/no-perecedero"))
        .andExpect(status().isOk())
        .andExpect(view().name("movimiento-salida-no-perecedero"));
  }

  @Test
  @WithMockUser(authorities = "ROLE_OPERADOR")
  void operadorNoAccedeBaja() throws Exception {
    mockMvc.perform(get("/movimientos/baja/dano/no-perecedero")).andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(authorities = "ROLE_ADMIN")
  void adminAccedeBajaVencimiento() throws Exception {
    mockMvc
        .perform(get("/movimientos/baja/vencimiento"))
        .andExpect(status().isOk())
        .andExpect(view().name("movimiento-baja-vencimiento"));
  }

  @Test
  @WithMockUser(authorities = "ROLE_OPERADOR")
  void operadorSinInsertNoAccedeSalida() throws Exception {
    when(permisos.puede(any(), eq("movimiento"), eq("INSERT"))).thenReturn(false);
    mockMvc.perform(get("/movimientos/salida/perecedero")).andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(authorities = "ROLE_OPERADOR")
  void panelRegistrarRequiereSelect() throws Exception {
    mockMvc
        .perform(get("/movimientos/registrar"))
        .andExpect(status().isOk())
        .andExpect(view().name("movimientos-registrar"));
  }
}
