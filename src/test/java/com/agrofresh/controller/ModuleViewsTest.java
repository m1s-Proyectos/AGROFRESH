package com.agrofresh.controller;

import com.agrofresh.dto.CategoriaDto;
import com.agrofresh.dto.CategoriaForm;
import com.agrofresh.dto.ClasificacionProducto;
import com.agrofresh.dto.ProductoDetalleDto;
import com.agrofresh.dto.ProductoForm;
import com.agrofresh.dto.ProductoListadoDto;
import com.agrofresh.dto.ProveedorDetalleDto;
import com.agrofresh.dto.ProveedorForm;
import com.agrofresh.dto.ProveedorListadoDto;
import com.agrofresh.security.PermisoService;
import com.agrofresh.service.CategoriaService;
import com.agrofresh.service.ProductoService;
import com.agrofresh.service.MovimientoService;
import com.agrofresh.service.ProveedorService;
import com.agrofresh.dto.MovimientoDetalleDto;
import com.agrofresh.dto.MovimientoFiltro;
import com.agrofresh.dto.MovimientoListadoDto;
import java.time.OffsetDateTime;
import java.math.BigDecimal;
import java.time.LocalDate;
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

    @MockBean
    private ProductoService productoService;

    @MockBean
    private ProveedorService proveedorService;

    @MockBean
    private MovimientoService movimientoService;

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
        when(productoService.listar()).thenReturn(List.of(
                new ProductoListadoDto(3, "P-01", "Mango", 7, "Lácteos", "Kilogramo", ClasificacionProducto.PERECEDERO)));
        when(productoService.consultar(3)).thenReturn(new ProductoDetalleDto(
                3, "P-01", "Mango", 7, "Lácteos", "Kilogramo", ClasificacionProducto.PERECEDERO,
                new BigDecimal("8.50"), 12, null, null));
        when(productoService.categoriasDisponibles()).thenReturn(List.of(new CategoriaDto(7, "Lácteos", null)));
        when(productoService.unidadesDisponibles()).thenReturn(List.of("Unidad", "Kilogramo"));
        when(productoService.registrar(any(ProductoForm.class)))
                .thenReturn(new ProductoDetalleDto(
                        3, "P-01", "Mango", 7, "Lácteos", "Kilogramo", ClasificacionProducto.PERECEDERO,
                        new BigDecimal("8.50"), 12, null, null));
        when(productoService.actualizar(any(Integer.class), any(ProductoForm.class)))
                .thenReturn(new ProductoDetalleDto(
                        3, "P-01", "Mango", 7, "Lácteos", "Kilogramo", ClasificacionProducto.PERECEDERO,
                        new BigDecimal("8.50"), 12, null, null));
        when(proveedorService.listar()).thenReturn(List.of(
                new ProveedorListadoDto(5, "Agro Distribución", null, "0614-000000-000-0",
                        "contacto@agro.test", "71234567", "Gravado")));
        when(proveedorService.buscar(org.mockito.ArgumentMatchers.anyString())).thenReturn(List.of(
                new ProveedorListadoDto(5, "Agro Distribución", null, "0614-000000-000-0",
                        "contacto@agro.test", "71234567", "Gravado")));
        when(proveedorService.consultar(5)).thenReturn(new ProveedorDetalleDto(
                5, "Agro Distribución", null, "0614-000000-000-0",
                "contacto@agro.test", "Km 10 carretera norte", "71234567", "Gravado"));
        when(proveedorService.registrar(any(ProveedorForm.class)))
                .thenReturn(new ProveedorDetalleDto(
                        5, "Agro Distribución", null, "0614-000000-000-0",
                        "contacto@agro.test", "Km 10 carretera norte", "71234567", "Gravado"));
        when(proveedorService.actualizar(any(Integer.class), any(ProveedorForm.class)))
                .thenReturn(new ProveedorDetalleDto(
                        5, "Agro Distribución", null, "0614-000000-000-0",
                        "contacto@agro.test", "Km 10 carretera norte", "71234567", "Gravado"));
        MovimientoFiltro filtroMovimientos = new MovimientoFiltro(null, null, null, null, null, 0, 50);
        when(movimientoService.normalizarFiltro(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(filtroMovimientos);
        when(movimientoService.contar(filtroMovimientos)).thenReturn(1L);
        when(movimientoService.listar(filtroMovimientos)).thenReturn(List.of(new MovimientoListadoDto(
                10, OffsetDateTime.parse("2026-03-01T08:00:00-06:00"), "TRASLADO", 4, null, 3,
                "P-01", "Mango", 1, "Central", 2, "Norte", "Operador Uno", false)));
        when(movimientoService.tiposDisponibles()).thenReturn(List.of("ENTRADA", "TRASLADO"));
        when(movimientoService.bodegasParaFiltro()).thenReturn(List.of());
        when(movimientoService.productosParaFiltro()).thenReturn(List.of());
        when(movimientoService.consultar(10)).thenReturn(new MovimientoDetalleDto(
                10, OffsetDateTime.parse("2026-03-01T08:00:00-06:00"), "TRASLADO", 4, null, null,
                3, "P-01", "Mango", 1, "Central", 2, "Norte", "Operador", "Uno", false,
                null, null, null, null));
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
        org.springframework.test.web.servlet.MvcResult createResult = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/categorias/guardar")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .param("nombre", "Lácteos"))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl(
                        "/categorias/consultar/7"))
                .andReturn();
        mockMvc.perform(get("/categorias/consultar/7").flashAttrs(createResult.getFlashMap()))
                .andExpect(status().isOk())
                .andExpect(view().name("categoria-detalle"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("Categoría registrada correctamente.")));
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
    void rendersProductListCreateEditAndDetailViews() throws Exception {
        mockMvc.perform(get("/productos"))
                .andExpect(status().isOk())
                .andExpect(view().name("productos"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("Mango")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("Registrar producto")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(
                                "Productos</span><span class=\"nav-pending\">Pendiente</span>"))));
        mockMvc.perform(get("/productos/nueva"))
                .andExpect(status().isOk())
                .andExpect(view().name("producto-form"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("clasificacion")));
        mockMvc.perform(get("/productos/editar/3"))
                .andExpect(status().isOk())
                .andExpect(view().name("producto-form"));
        mockMvc.perform(get("/productos/consultar/3"))
                .andExpect(status().isOk())
                .andExpect(view().name("producto-detalle"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("P-01")));
    }

    @Test
    void productFormPostsWithCsrfToExistingRoutes() throws Exception {
        mockMvc.perform(get("/productos/nueva"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("_csrf")));
        MvcResult create = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/productos/guardar")
                                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                                .param("codigoInterno", "P-01")
                                .param("nombre", "Mango")
                                .param("idCategoria", "7")
                                .param("unidadMedida", "Kilogramo")
                                .param("clasificacion", "PERECEDERO")
                                .param("temperaturaRecomendada", "8.5")
                                .param("vidaUtil", "12"))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl(
                        "/productos/consultar/3"))
                .andReturn();
        mockMvc.perform(get("/productos/consultar/3").flashAttrs(create.getFlashMap()))
                .andExpect(status().isOk())
                .andExpect(view().name("producto-detalle"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("Producto registrado correctamente.")));
    }

    @Test
    @WithMockUser(authorities = "ROLE_OPERADOR")
    void hidesProductWriteActionsWhenInsertAndUpdatePermissionsAreMissing() throws Exception {
        when(permisos.puede(any(), org.mockito.ArgumentMatchers.eq("producto"),
                org.mockito.ArgumentMatchers.eq("INSERT"))).thenReturn(false);
        when(permisos.puede(any(), org.mockito.ArgumentMatchers.eq("producto"),
                org.mockito.ArgumentMatchers.eq("UPDATE"))).thenReturn(false);

        mockMvc.perform(get("/productos"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Registrar producto"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Editar"))));
    }

    @Test
    void rendersSupplierListCreateEditAndDetailViews() throws Exception {
        mockMvc.perform(get("/proveedores"))
                .andExpect(status().isOk())
                .andExpect(view().name("proveedores"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("Agro Distribución")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("Registrar proveedor")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("name=\"q\"")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(
                                "Proveedores</span><span class=\"nav-pending\">Pendiente</span>"))));
        mockMvc.perform(get("/proveedores").param("q", "Agro"))
                .andExpect(status().isOk())
                .andExpect(view().name("proveedores"));
        mockMvc.perform(get("/proveedores/nueva"))
                .andExpect(status().isOk())
                .andExpect(view().name("proveedor-form"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("correoElectronico")));
        mockMvc.perform(get("/proveedores/editar/5"))
                .andExpect(status().isOk())
                .andExpect(view().name("proveedor-form"));
        mockMvc.perform(get("/proveedores/consultar/5"))
                .andExpect(status().isOk())
                .andExpect(view().name("proveedor-detalle"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("0614-000000-000-0")));
    }

    @Test
    void supplierFormPostsWithCsrfToExistingRoutes() throws Exception {
        mockMvc.perform(get("/proveedores/nueva"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("_csrf")));
        MvcResult create = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/proveedores/guardar")
                                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                                .param("nombre", "Agro Distribución")
                                .param("nit", "0614-000000-000-0")
                                .param("correoElectronico", "contacto@agro.test")
                                .param("direccion", "Km 10 carretera norte")
                                .param("telefono", "71234567")
                                .param("iva", "Gravado"))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl(
                        "/proveedores/consultar/5"))
                .andReturn();
        mockMvc.perform(get("/proveedores/consultar/5").flashAttrs(create.getFlashMap()))
                .andExpect(status().isOk())
                .andExpect(view().name("proveedor-detalle"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("Proveedor registrado correctamente.")));
    }

    @Test
    void displaysServerValidationErrorBesideTheSupplierName() throws Exception {
        when(proveedorService.registrar(any(ProveedorForm.class)))
                .thenThrow(new com.agrofresh.exception.BusinessException(
                        "El nombre del proveedor es obligatorio."));
        MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/proveedores/guardar")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .param("nombre", "   ")
                        .param("nit", "0614-000000-000-0")
                        .param("correoElectronico", "contacto@agro.test")
                        .param("direccion", "Dir")
                        .param("telefono", "71234567")
                        .param("iva", "Gravado"))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .redirectedUrl("/proveedores/nueva"))
                .andReturn();

        mockMvc.perform(get("/proveedores/nueva").flashAttrs(result.getFlashMap()))
                .andExpect(status().isOk())
                .andExpect(view().name("proveedor-form"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("El nombre del proveedor es obligatorio.")));
    }

    @Test
    @WithMockUser(authorities = "ROLE_OPERADOR")
    void hidesSupplierWriteActionsWhenInsertAndUpdatePermissionsAreMissing() throws Exception {
        when(permisos.puede(any(), org.mockito.ArgumentMatchers.eq("proveedor"),
                org.mockito.ArgumentMatchers.eq("INSERT"))).thenReturn(false);
        when(permisos.puede(any(), org.mockito.ArgumentMatchers.eq("proveedor"),
                org.mockito.ArgumentMatchers.eq("UPDATE"))).thenReturn(false);

        mockMvc.perform(get("/proveedores"))
                .andExpect(status().isOk())
                .andExpect(view().name("proveedores"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Registrar proveedor"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Editar"))));
    }

    @Test
    void rendersMovementsHistoryAndDetailViews() throws Exception {
        mockMvc.perform(get("/movimientos"))
                .andExpect(status().isOk())
                .andExpect(view().name("movimientos"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("Historial de")));
        mockMvc.perform(get("/movimientos/consultar/10"))
                .andExpect(status().isOk())
                .andExpect(view().name("movimiento-detalle"));
        mockMvc.perform(get("/movimientos/registrar"))
                .andExpect(status().isOk())
                .andExpect(view().name("movimientos-registrar"));
        mockMvc.perform(get("/movimientos/salida/no-perecedero"))
                .andExpect(status().isOk())
                .andExpect(view().name("movimiento-salida-no-perecedero"));
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
