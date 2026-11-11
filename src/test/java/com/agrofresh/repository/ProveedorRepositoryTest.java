package com.agrofresh.repository;

import com.agrofresh.model.Proveedor;
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
    "spring.datasource.url=jdbc:h2:mem:proveedor-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS inventario"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProveedorRepositoryTest {

    @Autowired
    ProveedorRepository proveedores;

    @Test
    void persisteProveedorYRespetaNitUnico() {
        Proveedor uno = proveedores.saveAndFlush(new Proveedor(
                "Alpha", null, "NIT-001", "a@b.co", "Dir 1", "11111111", "Gravado"));
        Proveedor dos = proveedores.saveAndFlush(new Proveedor(
                "Beta", "DUI-1", "NIT-002", "b@c.co", "Dir 2", "22222222", "Exento"));

        assertThat(uno.getIdProveedor()).isPositive();
        assertThat(proveedores.existsByNitAndIdProveedorNot("NIT-001", uno.getIdProveedor())).isFalse();
        assertThat(proveedores.buscarPorNombreNitODui("NIT-00")).hasSize(2);

        assertThatThrownBy(() -> proveedores.saveAndFlush(new Proveedor(
                "Gamma", null, "NIT-001", "g@h.co", "Dir 3", "33333333", "Gravado")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
