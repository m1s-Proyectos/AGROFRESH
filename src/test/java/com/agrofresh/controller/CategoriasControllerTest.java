package com.agrofresh.controller;

import com.agrofresh.dto.CategoriaDto;
import com.agrofresh.dto.CategoriaForm;
import com.agrofresh.exception.BusinessException;
import com.agrofresh.security.PermisoService;
import com.agrofresh.service.CategoriaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class CategoriasControllerTest {

    private CategoriaService service;
    private PermisoService permisos;
    private CategoriasController controller;
    private UsernamePasswordAuthenticationToken admin;

    @BeforeEach
    void setUp() {
        service = mock(CategoriaService.class);
        permisos = mock(PermisoService.class);
        controller = new CategoriasController(service, permisos);
        admin = UsernamePasswordAuthenticationToken.authenticated(
                "test-admin", "not-a-password", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        when(permisos.puede(admin, "producto", "SELECT")).thenReturn(true);
        when(permisos.puede(admin, "producto", "INSERT")).thenReturn(true);
        when(permisos.puede(admin, "producto", "UPDATE")).thenReturn(true);
    }

    @Test
    void listReturnsDtosInTheModelAndUsesProductReadPermission() {
        List<CategoriaDto> categories = List.of(new CategoriaDto(1, "Lácteos", null));
        when(service.listar()).thenReturn(categories);
        var model = new ExtendedModelMap();

        assertEquals("categorias", controller.listar(model, admin));
        assertEquals(categories, model.getAttribute("categorias"));
        verify(permisos).puede(admin, "producto", "SELECT");
    }

    @Test
    void createStoresOnlyFormDataAndRedirectsToGeneratedId() {
        CategoriaForm form = new CategoriaForm("Frescos", null);
        when(service.registrar(form)).thenReturn(new CategoriaDto(12, "Frescos", null));
        var messages = new RedirectAttributesModelMap();

        assertEquals("redirect:/categorias/consultar/12", controller.guardar(form, admin, messages));
        assertEquals("Categoría registrada correctamente.", messages.getFlashAttributes().get("exito"));
        verify(service).registrar(form);
        verify(permisos).puede(admin, "producto", "INSERT");
    }

    @Test
    void convertsBusinessValidationErrorsToUserFacingFlashMessages() {
        CategoriaForm form = new CategoriaForm("Frescos", null);
        when(service.registrar(form)).thenThrow(new BusinessException("El nombre está duplicado."));
        var messages = new RedirectAttributesModelMap();

        assertEquals("redirect:/categorias/nueva", controller.guardar(form, admin, messages));
        assertEquals("El nombre está duplicado.", messages.getFlashAttributes().get("error"));
        verify(permisos).puede(admin, "producto", "INSERT");
    }

    @Test
    void deniesCreateWhenInsertPermissionIsMissing() {
        when(permisos.puede(admin, "producto", "INSERT")).thenReturn(false);

        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.guardar(new CategoriaForm("Frescos", null), admin,
                        new RedirectAttributesModelMap()));
        verify(service, never()).registrar(any(CategoriaForm.class));
    }

    @Test
    void editFormAndDetailUseDtosAndExistingIdentifiers() {
        CategoriaDto dto = new CategoriaDto(8, "Lácteos", "Refrigerados");
        when(service.consultar(8)).thenReturn(dto);
        var editModel = new ExtendedModelMap();
        var detailModel = new ExtendedModelMap();

        assertEquals("categoria-form", controller.editar(8, editModel, admin));
        assertEquals(8, editModel.getAttribute("idCategoria"));
        CategoriaForm form = (CategoriaForm) editModel.getAttribute("categoriaForm");
        assertEquals("Lácteos", form.getNombre());
        assertEquals("Refrigerados", form.getDescripcion());

        assertEquals("categoria-detalle", controller.consultar(8, detailModel, admin));
        assertEquals(dto, detailModel.getAttribute("categoria"));
        verify(permisos, times(2)).puede(admin, "producto", "UPDATE");
        verify(permisos).puede(admin, "producto", "SELECT");
    }

    @Test
    void updateDelegatesToServiceAndRedirectsWithoutChangingTheIdentifier() {
        CategoriaForm form = new CategoriaForm("Frescos", null);
        when(service.actualizar(8, form)).thenReturn(new CategoriaDto(8, "Frescos", null));
        var messages = new RedirectAttributesModelMap();

        assertEquals("redirect:/categorias/consultar/8", controller.actualizar(8, form, admin, messages));
        assertEquals("Categoría actualizada correctamente.", messages.getFlashAttributes().get("exito"));
        verify(service).actualizar(8, form);
        verify(permisos).puede(admin, "producto", "UPDATE");
    }
}
