package com.agrofresh.controller;

import com.agrofresh.dto.CategoriaDto;
import com.agrofresh.dto.ClasificacionProducto;
import com.agrofresh.dto.ProductoDetalleDto;
import com.agrofresh.dto.ProductoForm;
import com.agrofresh.exception.BusinessException;
import com.agrofresh.security.PermisoService;
import com.agrofresh.service.ProductoService;
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

class ProductosControllerTest {
    private ProductoService service;
    private PermisoService permisos;
    private ProductosController controller;
    private UsernamePasswordAuthenticationToken admin;

    @BeforeEach
    void setUp() {
        service = mock(ProductoService.class);
        permisos = mock(PermisoService.class);
        controller = new ProductosController(service, permisos);
        admin = UsernamePasswordAuthenticationToken.authenticated(
                "admin", "none", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        when(permisos.puede(admin, "producto", "SELECT")).thenReturn(true);
        when(permisos.puede(admin, "producto", "INSERT")).thenReturn(true);
        when(permisos.puede(admin, "producto", "UPDATE")).thenReturn(true);
    }

    @Test
    void listadoUsaDtoYPermisoSelect() {
        var productos = List.of(new com.agrofresh.dto.ProductoListadoDto(
                1, "P-1", "Mango", 2, "Frutas", "Unidad", ClasificacionProducto.PERECEDERO));
        when(service.listar()).thenReturn(productos);
        ExtendedModelMap model = new ExtendedModelMap();

        assertEquals("productos", controller.listar(model, admin));
        assertEquals(productos, model.getAttribute("productos"));
        verify(permisos).puede(admin, "producto", "SELECT");
    }

    @Test
    void formularioCargaCatalogosYRegistroRedirigeAlDetalle() {
        when(service.categoriasDisponibles()).thenReturn(List.of(new CategoriaDto(2, "Frutas", null)));
        when(service.unidadesDisponibles()).thenReturn(List.of("Unidad"));
        ExtendedModelMap model = new ExtendedModelMap();
        assertEquals("producto-form", controller.nueva(model, admin));
        assertEquals(List.of("Unidad"), model.getAttribute("unidades"));

        ProductoForm form = new ProductoForm();
        when(service.registrar(form)).thenReturn(detalle(12));
        RedirectAttributesModelMap messages = new RedirectAttributesModelMap();
        assertEquals("redirect:/productos/consultar/12", controller.guardar(form, admin, messages));
        assertEquals("Producto registrado correctamente.", messages.getFlashAttributes().get("exito"));
    }

    @Test
    void errorDeNegocioConservaFormularioYNoExponePersistencia() {
        ProductoForm form = new ProductoForm();
        when(service.registrar(form)).thenThrow(new BusinessException("Código duplicado."));
        RedirectAttributesModelMap messages = new RedirectAttributesModelMap();

        assertEquals("redirect:/productos/nueva", controller.guardar(form, admin, messages));
        assertEquals("Código duplicado.", messages.getFlashAttributes().get("error"));
        assertEquals(form, messages.getFlashAttributes().get("productoForm"));
    }

    @Test
    void editarYConsultarUsanDtoSinExponerEntidades() {
        when(service.consultar(8)).thenReturn(detalle(8));
        ExtendedModelMap editModel = new ExtendedModelMap();
        ExtendedModelMap detailModel = new ExtendedModelMap();

        assertEquals("producto-form", controller.editar(8, editModel, admin));
        assertEquals("P-8", ((ProductoForm) editModel.getAttribute("productoForm")).getCodigoInterno());
        assertEquals("producto-detalle", controller.consultar(8, detailModel, admin));
        assertEquals(detalle(8), detailModel.getAttribute("producto"));
    }

    @Test
    void deniegaEscrituraSinPermisoAntesDeInvocarServicio() {
        when(permisos.puede(admin, "producto", "UPDATE")).thenReturn(false);

        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.actualizar(
                        8, new ProductoForm(), admin, new RedirectAttributesModelMap()));
        verify(service, never()).actualizar(any(), any());
    }

    private static ProductoDetalleDto detalle(int id) {
        return new ProductoDetalleDto(
                id, "P-" + id, "Mango", 2, "Frutas", "Unidad",
                ClasificacionProducto.PERECEDERO, java.math.BigDecimal.ONE, 10, null, null);
    }
}
