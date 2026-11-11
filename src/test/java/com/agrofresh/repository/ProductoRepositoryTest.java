package com.agrofresh.repository;

import com.agrofresh.model.Categoria;
import com.agrofresh.model.NoPerecedero;
import com.agrofresh.model.Perecedero;
import com.agrofresh.model.Producto;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.default_schema=inventario",
    "spring.datasource.url=jdbc:h2:mem:producto-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS inventario"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProductoRepositoryTest {
    @Autowired ProductoRepository productos;
    @Autowired PerecederoRepository perecederos;
    @Autowired NoPerecederoRepository noPerecederos;
    @Autowired CategoriaRepository categorias;

    @Test
    void persisteProductoPerecederoConClaveCompartida() {
        Categoria categoria = categorias.saveAndFlush(new Categoria("Frutas", null));
        Producto producto = productos.saveAndFlush(
                new Producto(categoria.getIdCategoria(), "P-01", "Mango", "Kilogramo"));
        Perecedero perecedero = perecederos.saveAndFlush(
                new Perecedero(producto, new BigDecimal("8.50"), 12));

        assertThat(producto.getIdProducto()).isPositive();
        assertThat(perecedero.getIdProducto()).isEqualTo(producto.getIdProducto());
        assertThat(noPerecederos.findById(producto.getIdProducto())).isEmpty();
    }

    @Test
    void persisteProductoNoPerecederoYRespetaCodigoUnico() {
        Categoria categoria = categorias.saveAndFlush(new Categoria("Granos", null));
        Producto producto = productos.saveAndFlush(
                new Producto(categoria.getIdCategoria(), "NP-01", "Arroz", "Paquete"));
        NoPerecedero subtipo = noPerecederos.saveAndFlush(
                new NoPerecedero(producto, LocalDate.now(), 365));

        assertThat(subtipo.getIdProducto()).isEqualTo(producto.getIdProducto());
        assertThat(productos.existsByCodigoInternoAndIdProductoNot("NP-01", producto.getIdProducto()))
                .isFalse();
        assertThatThrownBy(() -> productos.saveAndFlush(
                new Producto(categoria.getIdCategoria(), "NP-01", "Duplicado", "Unidad")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
