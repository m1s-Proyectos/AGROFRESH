package com.agrofresh.service.impl;

import com.agrofresh.dto.CategoriaDto;
import com.agrofresh.dto.CategoriaForm;
import com.agrofresh.exception.BusinessException;
import com.agrofresh.exception.ResourceNotFoundException;
import com.agrofresh.mapper.CategoriaMapper;
import com.agrofresh.model.Categoria;
import com.agrofresh.repository.CategoriaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CategoriaServiceImplTest {

    private CategoriaRepository repository;
    private CategoriaServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(CategoriaRepository.class);
        service = new CategoriaServiceImpl(repository, new CategoriaMapper());
    }

    @Test
    void registersTrimmedNameAndAllowsNullOrEmptyDescription() {
        when(repository.existsByNombre("Lácteos")).thenReturn(false);
        when(repository.saveAndFlush(any(Categoria.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CategoriaDto result = service.registrar(new CategoriaForm("  Lácteos  ", null));

        assertEquals("Lácteos", result.nombre());
        assertNull(result.descripcion());
        verify(repository).existsByNombre("Lácteos");

        when(repository.existsByNombre("Frescos")).thenReturn(false);
        CategoriaDto emptyDescription = service.registrar(new CategoriaForm("Frescos", ""));
        assertEquals("", emptyDescription.descripcion());
    }

    @Test
    void rejectsNullEmptyAndWhitespaceOnlyNames() {
        assertThrows(BusinessException.class, () -> service.registrar(new CategoriaForm(null, null)));
        assertThrows(BusinessException.class, () -> service.registrar(new CategoriaForm("", null)));
        assertThrows(BusinessException.class, () -> service.registrar(new CategoriaForm("   ", null)));
        verify(repository, never()).saveAndFlush(any(Categoria.class));
    }

    @Test
    void rejectsNameOver150Characters() {
        assertThrows(BusinessException.class,
                () -> service.registrar(new CategoriaForm("n".repeat(151), null)));
        verify(repository, never()).saveAndFlush(any(Categoria.class));
    }

    @Test
    void rejectsDescriptionOver200Characters() {
        when(repository.existsByNombre("Abarrotes")).thenReturn(false);
        assertThrows(BusinessException.class,
                () -> service.registrar(new CategoriaForm("Abarrotes", "d".repeat(201))));
        verify(repository, never()).saveAndFlush(any(Categoria.class));
    }

    @Test
    void rejectsDuplicateNameBeforeSave() {
        when(repository.existsByNombre("Enlatados")).thenReturn(true);

        assertThrows(BusinessException.class,
                () -> service.registrar(new CategoriaForm(" Enlatados ", null)));
        verify(repository, never()).save(any(Categoria.class));
    }

    @Test
    void translatesUniqueConstraintRaceWithoutLeakingPersistenceDetails() {
        when(repository.existsByNombre("Embutidos")).thenReturn(false);
        when(repository.saveAndFlush(any(Categoria.class)))
                .thenThrow(new DataIntegrityViolationException("test-only constraint detail"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.registrar(new CategoriaForm("Embutidos", null)));

        assertEquals("Ya existe una categoría con ese nombre.", exception.getMessage());
    }

    @Test
    void editsExistingCategoryAndAllowsItsCurrentName() {
        Categoria category = new Categoria("Lácteos", null);
        when(repository.findById(8)).thenReturn(Optional.of(category));
        when(repository.existsByNombreAndIdCategoriaNot("Lácteos", 8)).thenReturn(false);
        when(repository.saveAndFlush(category)).thenReturn(category);

        CategoriaDto result = service.actualizar(8, new CategoriaForm("Lácteos", "Refrigerados"));

        assertEquals("Lácteos", result.nombre());
        assertEquals("Refrigerados", result.descripcion());
        verify(repository).existsByNombreAndIdCategoriaNot("Lácteos", 8);
    }

    @Test
    void rejectsDuplicateOfAnotherCategory() {
        when(repository.findById(8)).thenReturn(Optional.of(new Categoria("Lácteos", null)));
        when(repository.existsByNombreAndIdCategoriaNot("Frescos", 8)).thenReturn(true);

        assertThrows(BusinessException.class,
                () -> service.actualizar(8, new CategoriaForm("Frescos", null)));
        verify(repository, never()).saveAndFlush(any(Categoria.class));
    }

    @Test
    void reportsUnknownCategoryForConsultAndEdit() {
        when(repository.findById(404)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.consultar(404));
        assertThrows(ResourceNotFoundException.class,
                () -> service.actualizar(404, new CategoriaForm("Frescos", null)));
    }

    @Test
    void listsDtosInRepositoryNameOrder() {
        when(repository.findAllByOrderByNombreAsc()).thenReturn(List.of(
                new Categoria("Abarrotes", null), new Categoria("Frescos", "Verduras")));

        assertEquals(List.of("Abarrotes", "Frescos"),
                service.listar().stream().map(CategoriaDto::nombre).toList());
    }
}
