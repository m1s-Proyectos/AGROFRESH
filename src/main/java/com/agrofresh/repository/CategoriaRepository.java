package com.agrofresh.repository;

import com.agrofresh.model.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoriaRepository extends JpaRepository<Categoria, Integer> {

    boolean existsByNombre(String nombre);

    boolean existsByNombreAndIdCategoriaNot(String nombre, Integer idCategoria);

    java.util.List<Categoria> findAllByOrderByNombreAsc();
}
