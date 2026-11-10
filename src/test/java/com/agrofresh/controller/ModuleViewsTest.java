package com.agrofresh.controller;

import com.agrofresh.dto.CategoriaDto;
import com.agrofresh.dto.CategoriaForm;
import com.agrofresh.security.PermisoService;
import com.agrofresh.service.CategoriaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(authorities = "ROLE_ADMIN")
class ModuleViewsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private PermisoService permisos;

    @MockBean
    private CategoriaService categoriaService;

    @BeforeEach
    void provideIsolatedReadFixtures() {
        when(permisos.cuentaHabilitada(any())).thenReturn(true);
        when(permisos.puede(any(), anyString(), anyString())).thenReturn(true);
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of());
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
        when(categoriaService.listar()).thenReturn(List.of(new CategoriaDto(7, "Lácteos", null)));
        when(categoriaService.consultar(7)).thenReturn(new CategoriaDto(7, "Lácteos", "Refrigerados"));
        when(categoriaService.registrar(any(CategoriaForm.class)))
                .thenReturn(new CategoriaDto(7, "Lácteos", null));
        when(categoriaService.actualizar(any(Integer.class), any(CategoriaForm.class)))
                .thenReturn(new CategoriaDto(7, "Lácteos", "Refrigerados"));
    }

    @Test
    void rendersUserManagementViewWithoutDatabaseRows() throws Exception {
        mockMvc.perform(get("/usuarios"))
                .andExpect(status().isOk())
                .andExpect(view().name("usuarios"));
    }

    @Test
    void rendersInventoryViewWithEmptyReadOnlyFixtures() throws Exception {
        mockMvc.perform(get("/inventario"))
                .andExpect(status().isOk())
                .andExpect(view().name("inventario"));
    }

    @Test
    void rendersTheRecoveredLotsTableWithEmptyReadOnlyFixtures() throws Exception {
        mockMvc.perform(get("/lotes"))
                .andExpect(status().isOk())
                .andExpect(view().name("lotes"));
    }

    @Test
    void rendersCategoryListAndShowsAuthorizedActions() throws Exception {
        mockMvc.perform(get("/categorias"))
                .andExpect(status().isOk())
                .andExpect(view().name("categorias"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("Lácteos")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("Registrar categoría")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("href=\"/categorias\"")));
    }

    @Test
    void rendersCategoryCreateEditAndDetailViews() throws Exception {
        mockMvc.perform(get("/categorias/nueva"))
                .andExpect(status().isOk())
                .andExpect(view().name("categoria-form"));
        mockMvc.perform(get("/categorias/editar/7"))
                .andExpect(status().isOk())
                .andExpect(view().name("categoria-form"));
        mockMvc.perform(get("/categorias/consultar/7"))
                .andExpect(status().isOk())
                .andExpect(view().name("categoria-detalle"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("Lácteos")));
    }

    @Test
    @WithMockUser(authorities = "ROLE_OPERADOR")
    void hidesCategoryWriteActionsWhenProductWritePermissionsAreMissing() throws Exception {
        when(permisos.puede(any(), org.mockito.ArgumentMatchers.eq("producto"),
                org.mockito.ArgumentMatchers.eq("INSERT"))).thenReturn(false);
        when(permisos.puede(any(), org.mockito.ArgumentMatchers.eq("producto"),
                org.mockito.ArgumentMatchers.eq("UPDATE"))).thenReturn(false);

        mockMvc.perform(get("/categorias"))
                .andExpect(status().isOk())
                .andExpect(view().name("categorias"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Registrar categoría"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Editar"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Eliminar"))));
    }

    @Test
    void displaysServerValidationErrorBesideTheCategoryName() throws Exception {
        when(categoriaService.registrar(any(CategoriaForm.class)))
                .thenThrow(new com.agrofresh.exception.BusinessException("El nombre de la categoría es obligatorio."));
        MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/categorias/guardar")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .param("nombre", "   "))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .redirectedUrl("/categorias/nueva"))
                .andReturn();

        mockMvc.perform(get("/categorias/nueva").flashAttrs(result.getFlashMap()))
                .andExpect(status().isOk())
                .andExpect(view().name("categoria-form"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("El nombre de la categoría es obligatorio.")));
    }

    @Test
    void categoryFormsPostWithCsrfAndUseTheExistingBackendRoutes() throws Exception {
        mockMvc.perform(get("/categorias/nueva"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("_csrf")));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/categorias/guardar")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .param("nombre", "Lácteos"))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl(
                        "/categorias/consultar/7"));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/categorias/actualizar/7")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .param("nombre", "Lácteos")
                        .param("descripcion", "Refrigerados"))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl(
                        "/categorias/consultar/7"));
    }

    @Test
    void rendersReceptionAndTransferViewsWithoutCallingBusinessProcedures() throws Exception {
        mockMvc.perform(get("/compras"))
                .andExpect(status().isOk())
                .andExpect(view().name("recepcion"));
        mockMvc.perform(get("/traslados"))
                .andExpect(status().isOk())
                .andExpect(view().name("traslados"));
    }

    @Test
    void rendersProfileAndErrorViewsWithSyntheticPresentationData() throws Exception {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(Map.of(
                "username", "mock-user",
                "nombre", "Usuario",
                "apellido", "Prueba",
                "correo", "mock@example.test",
                "rol", "ADMIN")));

        mockMvc.perform(get("/perfil"))
                .andExpect(status().isOk())
                .andExpect(view().name("perfil"));
        mockMvc.perform(get("/acceso-denegado"))
                .andExpect(status().isOk())
                .andExpect(view().name("acceso-denegado"));
    }
}
