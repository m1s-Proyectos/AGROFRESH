package com.agrofresh;

import com.agrofresh.dto.CategoriaDto;
import com.agrofresh.dto.CategoriaForm;
import com.agrofresh.security.PermisoService;
import com.agrofresh.service.CategoriaService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
class HomePageTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private PermisoService permisos;

    @MockBean
    private CategoriaService categoriaService;

    @Test
    void loginPageIsPublic() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"));
    }

    @Test
    void anonymousDashboardRequestRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", endsWith("/login")));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void authenticatedAllowedRoleRendersTheExistingDashboard() throws Exception {
        when(permisos.cuentaHabilitada(any(Authentication.class))).thenReturn(true);
        when(permisos.puede(any(), anyString(), anyString())).thenReturn(false);

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_OPERADOR")
    void userManagementRouteStillRequiresTheDatabasePermission() throws Exception {
        when(permisos.cuentaHabilitada(any(Authentication.class))).thenReturn(true);
        when(permisos.puede(any(), anyString(), anyString())).thenReturn(false);

        mockMvc.perform(get("/usuarios"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void userManagementPostIsProtectedByCsrf() throws Exception {
        when(permisos.cuentaHabilitada(any(Authentication.class))).thenReturn(true);
        when(permisos.puede(any(), anyString(), anyString())).thenReturn(true);

        mockMvc.perform(post("/usuarios/12/estado").param("activo", "false"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_OPERADOR")
    void operatorCannotOpenCategoryCreationWithoutInsertPermission() throws Exception {
        when(permisos.cuentaHabilitada(any(Authentication.class))).thenReturn(true);
        when(permisos.puede(any(), eq("producto"), eq("INSERT"))).thenReturn(false);

        mockMvc.perform(get("/categorias/nueva"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_OPERADOR")
    void categoryListRequiresBothAccessAndSelectPermissions() throws Exception {
        when(permisos.puede(any(), eq("producto"), eq("ACCEDER"))).thenReturn(true);
        when(permisos.puede(any(), eq("producto"), eq("SELECT"))).thenReturn(false);
        when(permisos.cuentaHabilitada(any(Authentication.class))).thenReturn(true);

        mockMvc.perform(get("/categorias"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_OPERADOR")
    void operatorCannotSubmitCategoryUpdatesWithoutUpdatePermission() throws Exception {
        when(permisos.puede(any(), eq("producto"), eq("UPDATE"))).thenReturn(false);
        when(permisos.cuentaHabilitada(any(Authentication.class))).thenReturn(true);

        mockMvc.perform(post("/categorias/actualizar/5").with(csrf())
                        .param("nombre", "Frescos"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void administratorPermissionPassesTheCategoryCreationFilter() throws Exception {
        when(permisos.puede(any(), eq("producto"), eq("INSERT"))).thenReturn(true);

        mockMvc.perform(get("/categorias/nueva"))
                .andExpect(status().isOk())
                .andExpect(view().name("categoria-form"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void categoryPostRequiresCsrfAndAnInsertPermission() throws Exception {
        when(permisos.puede(any(), eq("producto"), eq("INSERT"))).thenReturn(true);
        when(categoriaService.registrar(any(CategoriaForm.class)))
                .thenReturn(new CategoriaDto(7, "Frescos", null));

        mockMvc.perform(post("/categorias/guardar").param("nombre", "Frescos"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/categorias/guardar").with(csrf()).param("nombre", "Frescos"))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .redirectedUrl("/categorias/consultar/7"));
    }
}
