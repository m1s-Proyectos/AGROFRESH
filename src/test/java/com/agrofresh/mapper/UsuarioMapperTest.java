package com.agrofresh.mapper;

import com.agrofresh.dto.UsuarioDto;
import com.agrofresh.model.Usuario;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class UsuarioMapperTest {

    private final UsuarioMapper mapper = new UsuarioMapper();

    @Test
    void mapsOnlyPresentationFieldsAndNeverCopiesPasswordHash() {
        Usuario usuario = new Usuario(
                "operador",
                "synthetic-test-hash",
                "Ana",
                "Ríos",
                "ana@example.test",
                null);

        UsuarioDto dto = mapper.toDto(usuario);

        assertThat(dto).isEqualTo(new UsuarioDto(
                null, "operador", "Ana", "Ríos", "ana@example.test", true, null));
        assertThat(Arrays.stream(UsuarioDto.class.getRecordComponents())
                .map(component -> component.getName()))
                .doesNotContain("contrasena", "password", "hash");
    }

    @Test
    void rejectsNullEntity() {
        assertThatNullPointerException()
                .isThrownBy(() -> mapper.toDto(null))
                .withMessage("usuario no puede ser null");
    }
}
