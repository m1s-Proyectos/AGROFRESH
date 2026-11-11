package com.agrofresh.controller;

import com.agrofresh.dto.ProveedorDetalleDto;
import com.agrofresh.dto.ProveedorListadoDto;
import com.agrofresh.security.PermisoService;
import com.agrofresh.service.CategoriaService;
import com.agrofresh.service.ProductoService;
import com.agrofresh.service.ProveedorService;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProveedoresSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private PermisoService permisos;

    @MockBean
    private ProveedorService proveedorService;

    @MockBean
    private CategoriaService categoriaService;

    @MockBean
    private ProductoService productoService;

    @BeforeEach
    void habilitarCuenta() {
        when(permisos.cuentaHabilitada(any(Authentication.class))).thenReturn(true);
        when(proveedorService.listar()).thenReturn(List.of(
                new ProveedorListadoDto(1, "P", null, "NIT", "c@d.co", "71111111", "Gravado")));
        when(proveedorService.consultar(1)).thenReturn(new ProveedorDetalleDto(
                1, "P", null, "NIT", "c@d.co", "Dir", "71111111", "Gravado"));
    }

    @Test
    void visitanteDebeAutenticarseParaListar() throws Exception {
        mockMvc.perform(get("/proveedores"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", endsWith("/login")));
    }

    @Test
    void visitanteNoPuedeAccederARutasDeEscritura() throws Exception {
        mockMvc.perform(get("/proveedores/nueva"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", endsWith("/login")));
        mockMvc.perform(post("/proveedores/guardar").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", endsWith("/login")));
        mockMvc.perform(get("/proveedores/editar/1"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/proveedores/actualizar/1").with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void listadoExigeAccederYSelectEnElFiltro() throws Exception {
        when(permisos.puede(any(), eq("proveedor"), eq("ACCEDER"))).thenReturn(true);
        when(permisos.puede(any(), eq("proveedor"), eq("SELECT"))).thenReturn(false);

        mockMvc.perform(get("/proveedores")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void detalleExigeSelectEnElFiltro() throws Exception {
        when(permisos.puede(any(), eq("proveedor"), eq("SELECT"))).thenReturn(false);

        mockMvc.perform(get("/proveedores/consultar/1")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void formulariosDeEscrituraExigenInsertYUpdateEnElFiltro() throws Exception {
        when(permisos.puede(any(), eq("proveedor"), eq("INSERT"))).thenReturn(false);
        when(permisos.puede(any(), eq("proveedor"), eq("UPDATE"))).thenReturn(false);

        mockMvc.perform(get("/proveedores/nueva")).andExpect(status().isForbidden());
        mockMvc.perform(get("/proveedores/editar/1")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_OPERADOR")
    void operativoSinEscrituraRecibe403EnAltaYEdicion() throws Exception {
        when(permisos.puede(any(), eq("proveedor"), eq("INSERT"))).thenReturn(false);
        when(permisos.puede(any(), eq("proveedor"), eq("UPDATE"))).thenReturn(false);

        mockMvc.perform(get("/proveedores/nueva")).andExpect(status().isForbidden());
        mockMvc.perform(get("/proveedores/editar/1")).andExpect(status().isForbidden());
        mockMvc.perform(post("/proveedores/guardar").with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(post("/proveedores/actualizar/1").with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void postSinCsrfRechazadoConPermisoDeInsercion() throws Exception {
        when(permisos.puede(any(), eq("proveedor"), eq("INSERT"))).thenReturn(true);

        mockMvc.perform(post("/proveedores/guardar")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void postConCsrfSuperaProteccionCuandoHayInsert() throws Exception {
        when(permisos.puede(any(), eq("proveedor"), eq("INSERT"))).thenReturn(true);
        when(proveedorService.registrar(any())).thenReturn(new ProveedorDetalleDto(
                2, "N", null, "NIT", "a@b.co", "D", "71111111", "Gravado"));

        int status = mockMvc.perform(post("/proveedores/guardar").with(csrf())
                        .param("nombre", "Nuevo")
                        .param("nit", "0614-000000-000-0")
                        .param("correoElectronico", "a@b.co")
                        .param("direccion", "Dir")
                        .param("telefono", "71111111")
                        .param("iva", "Gravado"))
                .andReturn()
                .getResponse()
                .getStatus();
        org.junit.jupiter.api.Assertions.assertNotEquals(403, status);
    }
}
