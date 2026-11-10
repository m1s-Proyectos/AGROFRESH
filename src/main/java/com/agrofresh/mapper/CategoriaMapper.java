package com.agrofresh.mapper;

import com.agrofresh.dto.CategoriaDto;
import com.agrofresh.model.Categoria;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class CategoriaMapper {

    public CategoriaDto toDto(Categoria categoria) {
        Objects.requireNonNull(categoria, "categoria no puede ser null");
        return new CategoriaDto(
                categoria.getIdCategoria(),
                categoria.getNombre(),
                categoria.getDescripcion());
    }
}
