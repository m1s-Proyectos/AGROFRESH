package com.agrofresh.controller;

import com.agrofresh.dto.ProveedorDetalleDto;
import com.agrofresh.dto.ProveedorForm;
import com.agrofresh.dto.ProveedorListadoDto;
import com.agrofresh.exception.BusinessException;
import com.agrofresh.security.PermisoService;
import com.agrofresh.service.ProveedorService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProveedoresControllerTest {

    private ProveedorService service;
    private PermisoService permisos;
    private ProveedoresController controller;
    private UsernamePasswordAuthenticationToken admin;

    @BeforeEach
    void setUp() {
        service = mock(ProveedorService.class);
        permisos = mock(PermisoService.class);
        controller = new ProveedoresController(service, permisos);
        admin = UsernamePasswordAuthenticationToken.authenticated(
                "admin", "none", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        when(permisos.puede(admin, "proveedor", "SELECT")).thenReturn(true);
        when(permisos.puede(admin, "proveedor", "INSERT")).thenReturn(true);
        when(permisos.puede(admin, "proveedor", "UPDATE")).thenReturn(true);
    }

    @Test
    void listadoUsaBusquedaYPermisos() {
        when(service.buscar("nit")).thenReturn(List.of(
                new ProveedorListadoDto(1, "P", null, "NIT", "c@d.co", "71111111", "Gravado")));
        ExtendedModelMap model = new ExtendedModelMap();

        assertEquals("proveedores", controller.listar("nit", model, admin));
        assertEquals(1, ((List<?>) model.getAttribute("proveedores")).size());
        verify(service).buscar("nit");
    }

    @Test
    void registroRedirigeAlDetalleConExito() {
        ProveedorForm form = new ProveedorForm();
        when(service.registrar(form)).thenReturn(detalle(9));
        RedirectAttributesModelMap mensajes = new RedirectAttributesModelMap();

        assertEquals("redirect:/proveedores/consultar/9", controller.guardar(form, admin, mensajes));
        assertEquals("Proveedor registrado correctamente.", mensajes.getFlashAttributes().get("exito"));
    }

    @Test
    void errorDeNegocioConservaFormulario() {
        ProveedorForm form = new ProveedorForm();
        when(service.registrar(form)).thenThrow(new BusinessException("NIT duplicado."));
        RedirectAttributesModelMap mensajes = new RedirectAttributesModelMap();

        assertEquals("redirect:/proveedores/nueva", controller.guardar(form, admin, mensajes));
        assertEquals(form, mensajes.getFlashAttributes().get("proveedorForm"));
    }

    @Test
    void deniegaEscrituraSinPermiso() {
        when(permisos.puede(admin, "proveedor", "UPDATE")).thenReturn(false);
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.actualizar(4, new ProveedorForm(), admin, new RedirectAttributesModelMap()));
        verify(service, never()).actualizar(any(), any());
    }

    @Test
    void operativoConSoloLecturaPuedeListarYConsultar() {
        var operativo = UsernamePasswordAuthenticationToken.authenticated(
                "operativo", "none", List.of(new SimpleGrantedAuthority("ROLE_OPERADOR")));
        when(permisos.puede(operativo, "proveedor", "SELECT")).thenReturn(true);
        when(permisos.puede(operativo, "proveedor", "INSERT")).thenReturn(false);
        when(permisos.puede(operativo, "proveedor", "UPDATE")).thenReturn(false);
        when(service.listar()).thenReturn(List.of(
                new ProveedorListadoDto(1, "P", null, "NIT", "c@d.co", "71111111", "Gravado")));
        when(service.consultar(1)).thenReturn(detalle(1));

        assertEquals("proveedores", controller.listar(null, new ExtendedModelMap(), operativo));
        assertEquals("proveedor-detalle", controller.consultar(1, new ExtendedModelMap(), operativo));
    }

    @Test
    void operativoSinInsertNoPuedeAbrirAlta() {
        var operativo = UsernamePasswordAuthenticationToken.authenticated(
                "operativo", "none", List.of(new SimpleGrantedAuthority("ROLE_OPERADOR")));
        when(permisos.puede(operativo, "proveedor", "INSERT")).thenReturn(false);

        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.nueva(new ExtendedModelMap(), operativo));
        verify(service, never()).registrar(any());
    }

    private static ProveedorDetalleDto detalle(int id) {
        return new ProveedorDetalleDto(id, "P", null, "NIT", "c@d.co", "Dir", "71111111", "Gravado");
    }
}
