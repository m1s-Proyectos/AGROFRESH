package com.agrofresh.repository;

import com.agrofresh.model.Usuario;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(showSql = false)
@ActiveProfiles("local")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfSystemProperty(named = "agrofresh.postgres.it", matches = "true")
class UsuarioRepositoryPostgresTest {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void mapsTheSupabaseUsuarioEntityAndQueriesByUsernameReadOnly() {
        List<String> mappedAttributes = entityManager.getMetamodel().entity(Usuario.class)
                .getAttributes().stream()
                .map(attribute -> attribute.getName())
                .sorted()
                .toList();

        assertThat(mappedAttributes).containsExactly(
                "activo", "apellido", "contrasena", "correo", "idUsuario", "nombre", "rol", "username");

        String absentUsername = "agrofresh-read-only-" + UUID.randomUUID();
        assertThat(usuarioRepository.findByUsername(absentUsername)).isEmpty();
    }
}
