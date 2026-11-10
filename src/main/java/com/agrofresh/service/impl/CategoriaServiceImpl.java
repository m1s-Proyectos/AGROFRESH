package com.agrofresh.service.impl;

import com.agrofresh.dto.CategoriaDto;
import com.agrofresh.dto.CategoriaForm;
import com.agrofresh.exception.BusinessException;
import com.agrofresh.exception.ResourceNotFoundException;
import com.agrofresh.mapper.CategoriaMapper;
import com.agrofresh.model.Categoria;
import com.agrofresh.repository.CategoriaRepository;
import com.agrofresh.service.CategoriaService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
@Transactional(readOnly = true)
public class CategoriaServiceImpl implements CategoriaService {

    private static final int MAX_NOMBRE = 150;
    private static final int MAX_DESCRIPCION = 200;

    private final CategoriaRepository categoriaRepository;
    private final CategoriaMapper categoriaMapper;

    public CategoriaServiceImpl(CategoriaRepository categoriaRepository, CategoriaMapper categoriaMapper) {
        this.categoriaRepository = categoriaRepository;
        this.categoriaMapper = categoriaMapper;
    }

    @Override
    public List<CategoriaDto> listar() {
        try {
            return categoriaRepository.findAllByOrderByNombreAsc().stream()
                    .map(categoriaMapper::toDto)
                    .toList();
        } catch (DataAccessException exception) {
            throw new BusinessException("No se pudieron consultar las categorías.", exception);
        }
    }

    @Override
    public CategoriaDto consultar(Integer idCategoria) {
        validarIdentificador(idCategoria);
        try {
            Categoria categoria = categoriaRepository.findById(idCategoria)
                    .orElseThrow(() -> new ResourceNotFoundException("La categoría solicitada no existe."));
            return categoriaMapper.toDto(categoria);
        } catch (DataAccessException exception) {
            throw new BusinessException("No se pudo consultar la categoría.", exception);
        }
    }

    @Override
    @Transactional
    public CategoriaDto registrar(CategoriaForm form) {
        DatosCategoria datos = validar(form);
        try {
            if (categoriaRepository.existsByNombre(datos.nombre())) {
                throw new BusinessException("Ya existe una categoría con ese nombre.");
            }
            return categoriaMapper.toDto(categoriaRepository.saveAndFlush(
                    new Categoria(datos.nombre(), datos.descripcion())));
        } catch (BusinessException exception) {
            throw exception;
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException("Ya existe una categoría con ese nombre.", exception);
        } catch (DataAccessException exception) {
            throw new BusinessException("No se pudo guardar la categoría. Inténtalo nuevamente.", exception);
        }
    }

    @Override
    @Transactional
    public CategoriaDto actualizar(Integer idCategoria, CategoriaForm form) {
        validarIdentificador(idCategoria);
        DatosCategoria datos = validar(form);
        try {
            Categoria categoria = categoriaRepository.findById(idCategoria)
                    .orElseThrow(() -> new ResourceNotFoundException("La categoría solicitada no existe."));
            if (categoriaRepository.existsByNombreAndIdCategoriaNot(datos.nombre(), idCategoria)) {
                throw new BusinessException("Ya existe otra categoría con ese nombre.");
            }
            categoria.actualizar(datos.nombre(), datos.descripcion());
            return categoriaMapper.toDto(categoriaRepository.saveAndFlush(categoria));
        } catch (ResourceNotFoundException | BusinessException exception) {
            throw exception;
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException("Ya existe otra categoría con ese nombre.", exception);
        } catch (DataAccessException exception) {
            throw new BusinessException("No se pudo actualizar la categoría. Inténtalo nuevamente.", exception);
        }
    }

    private static DatosCategoria validar(CategoriaForm form) {
        if (form == null || form.getNombre() == null) {
            throw new BusinessException("El nombre de la categoría es obligatorio.");
        }
        String nombre = form.getNombre().trim();
        if (nombre.isEmpty()) {
            throw new BusinessException("El nombre de la categoría no puede estar vacío.");
        }
        if (nombre.length() > MAX_NOMBRE) {
            throw new BusinessException("El nombre no puede superar 150 caracteres.");
        }
        String descripcion = form.getDescripcion();
        if (descripcion != null && descripcion.length() > MAX_DESCRIPCION) {
            throw new BusinessException("La descripción no puede superar 200 caracteres.");
        }
        return new DatosCategoria(nombre, descripcion);
    }

    private static void validarIdentificador(Integer idCategoria) {
        if (idCategoria == null || idCategoria <= 0) {
            throw new ResourceNotFoundException("La categoría solicitada no existe.");
        }
    }

    private record DatosCategoria(String nombre, String descripcion) {
    }
}
