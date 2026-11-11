package com.agrofresh.service.impl;

import com.agrofresh.dto.ProveedorDetalleDto;
import com.agrofresh.dto.ProveedorForm;
import com.agrofresh.exception.BusinessException;
import com.agrofresh.exception.ResourceNotFoundException;
import com.agrofresh.mapper.ProveedorMapper;
import com.agrofresh.model.Proveedor;
import com.agrofresh.repository.ProveedorRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProveedorServiceImplTest {

    private ProveedorRepository repository;
    private ProveedorServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(ProveedorRepository.class);
        service = new ProveedorServiceImpl(repository, new ProveedorMapper());
    }

    @Test
    void registraProveedorValidoConDuiOpcional() {
        ProveedorForm form = formularioBase();
        form.setDui("   ");
        when(repository.existsByNit("0614-123456-001-2")).thenReturn(false);
        when(repository.saveAndFlush(any(Proveedor.class))).thenAnswer(inv -> conId(inv.getArgument(0), 3));

        ProveedorDetalleDto result = service.registrar(form);

        assertEquals(3, result.idProveedor());
        assertNull(result.dui());
        verify(repository).saveAndFlush(any(Proveedor.class));
    }

    @Test
    void rechazaNitObligatorioDuplicadoCorreoInvalidoIvaVacioYLongitudes() {
        ProveedorForm sinNit = formularioBase();
        sinNit.setNit("  ");
        assertThrows(BusinessException.class, () -> service.registrar(sinNit));

        ProveedorForm dup = formularioBase();
        when(repository.existsByNit(dup.getNit().trim())).thenReturn(true);
        assertThrows(BusinessException.class, () -> service.registrar(dup));

        ProveedorForm duiDup = formularioBase();
        duiDup.setDui("12345678-9");
        when(repository.existsByNit(duiDup.getNit().trim())).thenReturn(false);
        when(repository.existsByDui("12345678-9")).thenReturn(true);
        assertThrows(BusinessException.class, () -> service.registrar(duiDup));

        ProveedorForm correoMalo = formularioBase();
        correoMalo.setCorreoElectronico("no-es-correo");
        assertThrows(BusinessException.class, () -> service.registrar(correoMalo));

        ProveedorForm ivaVacio = formularioBase();
        ivaVacio.setIva("   ");
        assertThrows(BusinessException.class, () -> service.registrar(ivaVacio));

        ProveedorForm largo = formularioBase();
        largo.setNombre("n".repeat(151));
        assertThrows(BusinessException.class, () -> service.registrar(largo));
    }

    @Test
    void edicionConservaNitYDuiExcluyendoPropioId() {
        ProveedorForm form = formularioBase();
        form.setNit("0614-999999-999-9");
        form.setDui("99999999-9");
        Proveedor existente = conId(new Proveedor(
                "Old", "99999999-9", "0614-999999-999-9", "a@b.co", "Dir", "22222222", "Gravado"), 8);
        when(repository.findById(8)).thenReturn(Optional.of(existente));
        when(repository.existsByNitAndIdProveedorNot("0614-999999-999-9", 8)).thenReturn(false);
        when(repository.existsByDuiAndIdProveedorNot("99999999-9", 8)).thenReturn(false);
        when(repository.saveAndFlush(existente)).thenReturn(existente);

        ProveedorDetalleDto actualizado = service.actualizar(8, form);

        assertEquals("0614-999999-999-9", actualizado.nit());
        assertEquals("99999999-9", actualizado.dui());
    }

    @Test
    void edicionRechazaNitDuplicadoDeOtroProveedor() {
        ProveedorForm form = formularioBase();
        Proveedor existente = conId(new Proveedor(
                "X", null, "0614-111111-111-1", "x@y.co", "D", "11111111", "Exento"), 5);
        when(repository.findById(5)).thenReturn(Optional.of(existente));
        when(repository.existsByNitAndIdProveedorNot(form.getNit().trim(), 5)).thenReturn(true);

        assertThrows(BusinessException.class, () -> service.actualizar(5, form));
    }

    @Test
    void consultaYListadoBusqueda() {
        Proveedor p = conId(new Proveedor(
                "Distribuidora", null, "0614-000000-000-0", "p@q.co", "Calle", "70000000", "Gravado"), 2);
        when(repository.findAllByOrderByNombreAsc()).thenReturn(List.of(p));
        assertEquals(1, service.listar().size());

        when(repository.buscarPorNombreNitODui("dist")).thenReturn(List.of(p));
        assertEquals(1, service.buscar(" dist ").size());
        assertEquals(1, service.buscar("").size());
    }

    @Test
    void proveedorInexistenteLanzaResourceNotFound() {
        when(repository.findById(404)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.consultar(404));
    }

    @Test
    void integridadUniqueNitSeTraduceAMensajeDeNegocio() {
        ProveedorForm form = formularioBase();
        when(repository.existsByNit(form.getNit().trim())).thenReturn(false);
        when(repository.saveAndFlush(any(Proveedor.class)))
                .thenThrow(new DataIntegrityViolationException("uq_proveedor_nit"));

        BusinessException error = assertThrows(BusinessException.class, () -> service.registrar(form));
        assertEquals("Ya existe un proveedor con ese NIT.", error.getMessage());
    }

    private static ProveedorForm formularioBase() {
        ProveedorForm form = new ProveedorForm();
        form.setNombre("Proveedor SA");
        form.setNit(" 0614-123456-001-2 ");
        form.setCorreoElectronico("contacto@proveedor.test");
        form.setDireccion("San Salvador");
        form.setTelefono("71234567");
        form.setIva("Gravado");
        return form;
    }

    private static Proveedor conId(Proveedor proveedor, int id) {
        org.springframework.test.util.ReflectionTestUtils.setField(proveedor, "idProveedor", id);
        return proveedor;
    }
}
