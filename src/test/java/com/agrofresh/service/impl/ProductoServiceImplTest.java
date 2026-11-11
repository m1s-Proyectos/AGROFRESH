package com.agrofresh.service.impl;

import com.agrofresh.dto.CategoriaDto;
import com.agrofresh.dto.ClasificacionProducto;
import com.agrofresh.dto.ProductoDetalleDto;
import com.agrofresh.dto.ProductoForm;
import com.agrofresh.exception.BusinessException;
import com.agrofresh.exception.ResourceNotFoundException;
import com.agrofresh.mapper.ProductoMapper;
import com.agrofresh.model.NoPerecedero;
import com.agrofresh.model.Perecedero;
import com.agrofresh.model.Producto;
import com.agrofresh.repository.NoPerecederoRepository;
import com.agrofresh.repository.PerecederoRepository;
import com.agrofresh.repository.ProductoRepository;
import com.agrofresh.service.CategoriaService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductoServiceImplTest {
    private ProductoRepository productos;
    private PerecederoRepository perecederos;
    private NoPerecederoRepository noPerecederos;
    private CategoriaService categorias;
    private ProductoServiceImpl service;

    @BeforeEach
    void setUp() {
        productos = mock(ProductoRepository.class);
        perecederos = mock(PerecederoRepository.class);
        noPerecederos = mock(NoPerecederoRepository.class);
        categorias = mock(CategoriaService.class);
        service = new ProductoServiceImpl(
                productos, perecederos, noPerecederos, categorias, new ProductoMapper());
        when(categorias.consultar(2)).thenReturn(new CategoriaDto(2, "Frutas", null));
    }

    @Test
    void registraPerecederoValidoEnUnaSolaClasificacion() {
        ProductoForm form = perecedero(" P-01 ", " Mango ", "Kilogramo");
        when(productos.existsByCodigoInterno("P-01")).thenReturn(false);
        when(productos.saveAndFlush(any(Producto.class))).thenAnswer(invocation -> conId(invocation.getArgument(0), 10));
        when(perecederos.findById(10)).thenAnswer(invocation ->
                Optional.of(new Perecedero(productoGuardado(), new BigDecimal("8.50"), 12)));

        ProductoDetalleDto result = service.registrar(form);

        assertEquals("P-01", result.codigoInterno());
        assertEquals("Mango", result.nombre());
        assertEquals(ClasificacionProducto.PERECEDERO, result.clasificacion());
        verify(perecederos).saveAndFlush(any(Perecedero.class));
        verify(noPerecederos, never()).saveAndFlush(any(NoPerecedero.class));
    }

    @Test
    void registraNoPerecederoValido() {
        ProductoForm form = noPerecedero("NP-01", "Arroz", "Paquete");
        when(productos.saveAndFlush(any(Producto.class))).thenAnswer(invocation -> conId(invocation.getArgument(0), 11));
        when(noPerecederos.findById(11)).thenAnswer(invocation ->
                Optional.of(new NoPerecedero(productoGuardado(), LocalDate.now().minusDays(1), 365)));

        ProductoDetalleDto result = service.registrar(form);

        assertEquals(ClasificacionProducto.NO_PERECEDERO, result.clasificacion());
        verify(noPerecederos).saveAndFlush(any(NoPerecedero.class));
    }

    @Test
    void rechazaCodigoDuplicadoNombreInvalidoCategoriaInexistenteYUnidadArbitraria() {
        ProductoForm duplicate = perecedero("P-01", "Mango", "Kilogramo");
        when(productos.existsByCodigoInterno("P-01")).thenReturn(true);
        assertThrows(BusinessException.class, () -> service.registrar(duplicate));

        ProductoForm emptyName = perecedero("P-02", "   ", "Kilogramo");
        assertThrows(BusinessException.class, () -> service.registrar(emptyName));

        ProductoForm longName = perecedero("P-02", "n".repeat(151), "Kilogramo");
        assertThrows(BusinessException.class, () -> service.registrar(longName));

        ProductoForm unknownCategory = perecedero("P-02", "Mango", "Kilogramo");
        unknownCategory.setIdCategoria(404);
        when(categorias.consultar(404)).thenThrow(new ResourceNotFoundException("No existe"));
        assertThrows(ResourceNotFoundException.class, () -> service.registrar(unknownCategory));

        ProductoForm badUnit = perecedero("P-02", "Mango", "Saco");
        assertThrows(BusinessException.class, () -> service.registrar(badUnit));
    }

    @Test
    void conservaUnidadHistoricaPeroNoPermiteCambiarlaPorOtraArbitraria() {
        Producto producto = conId(new Producto(2, "H-01", "Histórico", "Saco"), 20);
        when(productos.findById(20)).thenReturn(Optional.of(producto));
        when(noPerecederos.findById(20)).thenReturn(
                Optional.of(new NoPerecedero(producto, LocalDate.now().minusYears(1), 500)));
        when(productos.saveAndFlush(producto)).thenReturn(producto);

        ProductoForm conservar = noPerecedero("H-01", "Histórico", "Saco");
        service.actualizar(20, conservar);

        ProductoForm cambiar = noPerecedero("H-01", "Histórico", "Bulto");
        assertThrows(BusinessException.class, () -> service.actualizar(20, cambiar));
    }

    @Test
    void permiteCambioDeClasificacionSinDependencias() {
        Producto producto = conId(new Producto(2, "P-03", "Producto", "Unidad"), 30);
        when(productos.findById(30)).thenReturn(Optional.of(producto));
        when(perecederos.findById(30))
                .thenReturn(Optional.of(new Perecedero(producto, BigDecimal.ONE, 10)))
                .thenReturn(Optional.empty());
        when(noPerecederos.findById(30))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new NoPerecedero(producto, LocalDate.now(), 100)));
        when(productos.saveAndFlush(producto)).thenReturn(producto);

        ProductoDetalleDto result = service.actualizar(
                30, noPerecedero("P-03", "Producto", "Unidad"));

        assertEquals(ClasificacionProducto.NO_PERECEDERO, result.clasificacion());
        verify(perecederos).deleteById(30);
        verify(noPerecederos).saveAndFlush(any(NoPerecedero.class));
    }

    @Test
    void bloqueaCambioPorCadaDependenciaHistorica() {
        assertCambioBloqueadoPor("lote");
        assertCambioBloqueadoPor("movimiento");
        assertCambioBloqueadoPor("inventario");
        assertCambioBloqueadoPor("compra");
        assertCambioBloqueadoPor("catalogo");
    }

    @Test
    void rechazaClasificacionMixtaFechaFuturaYEstadoPersistidoNoExclusivo() {
        ProductoForm mixed = perecedero("P-04", "Mixto", "Unidad");
        mixed.setFechaFabricacion(LocalDate.now());
        mixed.setVidaUtilAnaquel(10);
        assertThrows(BusinessException.class, () -> service.registrar(mixed));

        ProductoForm future = noPerecedero("P-05", "Futuro", "Unidad");
        future.setFechaFabricacion(LocalDate.now().plusDays(1));
        assertThrows(BusinessException.class, () -> service.registrar(future));

        Producto producto = conId(new Producto(2, "X", "Inconsistente", "Unidad"), 99);
        when(productos.findById(99)).thenReturn(Optional.of(producto));
        when(perecederos.findById(99)).thenReturn(
                Optional.of(new Perecedero(producto, BigDecimal.ZERO, 1)));
        when(noPerecederos.findById(99)).thenReturn(
                Optional.of(new NoPerecedero(producto, LocalDate.now(), 1)));
        assertThrows(BusinessException.class, () -> service.consultar(99));
    }

    private void assertCambioBloqueadoPor(String dependencia) {
        Producto producto = conId(new Producto(2, "D-" + dependencia, "Producto", "Unidad"), 40);
        when(productos.findById(40)).thenReturn(Optional.of(producto));
        when(perecederos.findById(40)).thenReturn(
                Optional.of(new Perecedero(producto, BigDecimal.ONE, 10)));
        when(noPerecederos.findById(40)).thenReturn(Optional.empty());
        when(productos.tieneLotes(40)).thenReturn("lote".equals(dependencia));
        when(productos.tieneMovimientos(40)).thenReturn("movimiento".equals(dependencia));
        when(productos.tieneInventario(40)).thenReturn("inventario".equals(dependencia));
        when(productos.tieneCompras(40)).thenReturn("compra".equals(dependencia));
        when(productos.tieneCatalogo(40)).thenReturn("catalogo".equals(dependencia));

        assertThrows(BusinessException.class,
                () -> service.actualizar(40, noPerecedero("D-" + dependencia, "Producto", "Unidad")));
        verify(noPerecederos, never()).saveAndFlush(any(NoPerecedero.class));
    }

    private Producto productoGuardado() {
        return conId(new Producto(2, "ref", "ref", "Unidad"), 10);
    }

    private static Producto conId(Producto producto, int id) {
        ReflectionTestUtils.setField(producto, "idProducto", id);
        return producto;
    }

    private static ProductoForm perecedero(String codigo, String nombre, String unidad) {
        ProductoForm form = base(codigo, nombre, unidad);
        form.setClasificacion(ClasificacionProducto.PERECEDERO);
        form.setTemperaturaRecomendada(new BigDecimal("8.50"));
        form.setVidaUtil(12);
        return form;
    }

    private static ProductoForm noPerecedero(String codigo, String nombre, String unidad) {
        ProductoForm form = base(codigo, nombre, unidad);
        form.setClasificacion(ClasificacionProducto.NO_PERECEDERO);
        form.setFechaFabricacion(LocalDate.now().minusDays(1));
        form.setVidaUtilAnaquel(365);
        return form;
    }

    private static ProductoForm base(String codigo, String nombre, String unidad) {
        ProductoForm form = new ProductoForm();
        form.setCodigoInterno(codigo);
        form.setNombre(nombre);
        form.setIdCategoria(2);
        form.setUnidadMedida(unidad);
        return form;
    }
}
