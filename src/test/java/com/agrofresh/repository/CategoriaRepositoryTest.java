package com.agrofresh.repository;

import com.agrofresh.model.Categoria;
import com.agrofresh.dto.CategoriaDto;
import com.agrofresh.dto.CategoriaForm;
import com.agrofresh.mapper.CategoriaMapper;
import com.agrofresh.service.impl.CategoriaServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.default_schema=inventario",
        "spring.datasource.url=jdbc:h2:mem:categoria-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS inventario"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CategoriaRepositoryTest {

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Test
    void identityAndNameLookupsWorkInTheIsolatedDatabase() {
        Categoria saved = categoriaRepository.saveAndFlush(new Categoria("Lácteos", null));

        assertThat(saved.getIdCategoria()).isPositive();
        assertThat(categoriaRepository.findById(saved.getIdCategoria()))
                .get().extracting(Categoria::getNombre).isEqualTo("Lácteos");
        assertThat(categoriaRepository.existsByNombre("Lácteos")).isTrue();
        assertThat(categoriaRepository.existsByNombreAndIdCategoriaNot("Lácteos", saved.getIdCategoria()))
                .isFalse();
    }

    @Test
    void listsCategoriesAlphabeticallyByName() {
        categoriaRepository.save(new Categoria("Zeta", "Última"));
        categoriaRepository.save(new Categoria("Abarrotes", null));
        categoriaRepository.save(new Categoria("Frescos", ""));

        List<String> names = categoriaRepository.findAllByOrderByNombreAsc().stream()
                .map(Categoria::getNombre)
                .toList();

        assertThat(names).containsExactly("Abarrotes", "Frescos", "Zeta");
    }

    @Test
    void uniqueNameConstraintRejectsAnExactDuplicate() {
        categoriaRepository.saveAndFlush(new Categoria("Lácteos", null));

        assertThatThrownBy(() -> categoriaRepository.saveAndFlush(new Categoria("Lácteos", "Duplicada")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void serviceEditPreservesDatabaseGeneratedIdentifier() {
        Categoria original = categoriaRepository.saveAndFlush(new Categoria("Frescos", null));
        Integer originalId = original.getIdCategoria();
        var service = new CategoriaServiceImpl(categoriaRepository, new CategoriaMapper());

        CategoriaDto updated = service.actualizar(originalId, new CategoriaForm("Frescos", "Temporada"));

        assertThat(updated.idCategoria()).isEqualTo(originalId);
        assertThat(updated.descripcion()).isEqualTo("Temporada");
    }
}
