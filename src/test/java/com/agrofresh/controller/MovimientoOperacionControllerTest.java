package com.agrofresh.controller;

import com.agrofresh.dto.MovimientoSalidaSolicitud;
import com.agrofresh.security.PermisoService;
import com.agrofresh.service.CategoriaService;
import com.agrofresh.service.MovimientoOperacionService;
import com.agrofresh.service.MovimientoService;
import com.agrofresh.service.ProductoService;
import com.agrofresh.service.ProveedorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MovimientoOperacionControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockBean private JdbcTemplate jdbcTemplate;
  @MockBean private PermisoService permisos;
  @MockBean private MovimientoService movimientoService;
  @MockBean private CategoriaService categoriaService;
  @MockBean private ProductoService productoService;
  @MockBean private ProveedorService proveedorService;
  @MockBean private MovimientoOperacionService operaciones;

  @BeforeEach
  void fixtures() {
    when(permisos.cuentaHabilitada(any(Authentication.class))).thenReturn(true);
    when(permisos.puede(any(), eq("movimiento"), eq("ACCEDER"))).thenReturn(true);
    when(permisos.puede(any(), eq("movimiento"), eq("INSERT"))).thenReturn(true);
  }

  @Test
  @WithMockUser(authorities = "ROLE_OPERADOR")
  void operativoNoAccedeBajas() throws Exception {
    mockMvc
        .perform(
            post("/api/movimientos/baja/dano/no-perecedero")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new com.agrofresh.dto.MovimientoBajaSolicitud(1, 1, 1, "m", null))))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(authorities = "ROLE_OPERADOR")
  void operadorConInsertRegistraSalida() throws Exception {
    when(operaciones.registrarSalidaNoPerecedero(any(), any())).thenReturn(42);
    mockMvc
        .perform(
            post("/api/movimientos/salida/no-perecedero")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new MovimientoSalidaSolicitud(1, 2, 3))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.idMovimiento").value(42));
  }
}
