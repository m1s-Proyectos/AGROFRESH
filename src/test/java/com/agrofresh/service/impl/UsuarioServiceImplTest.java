package com.agrofresh.service.impl;

import com.agrofresh.dto.UsuarioDto;
import com.agrofresh.exception.ResourceNotFoundException;
import com.agrofresh.mapper.UsuarioMapper;
import com.agrofresh.model.Usuario;
import com.agrofresh.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UsuarioServiceImplTest {

    private final UsuarioRepository repository = mock(UsuarioRepository.class);
    private final UsuarioServiceImpl service = new UsuarioServiceImpl(repository, new UsuarioMapper());

    @Test
    void listsUsersAsDtosWithoutPasswordHashes() {
        Usuario usuario = new Usuario(
                "admin", "synthetic-test-hash", "Alicia", "Mora", "a@example.test", "ADMIN");
        when(repository.findAll()).thenReturn(List.of(usuario));

        List<UsuarioDto> result = service.listar();

        assertThat(result).containsExactly(new UsuarioDto(
                null, "admin", "Alicia", "Mora", "a@example.test", true, "ADMIN"));
        verify(repository).findAll();
    }

    @Test
    void findsUserByIdAndReturnsDto() {
        Usuario usuario = new Usuario(
                "operador", "synthetic-test-hash", "Ana", "Ríos", "ana@example.test", "OPERADOR");
        ReflectionTestUtils.setField(usuario, "idUsuario", 17);
        when(repository.findById(17)).thenReturn(Optional.of(usuario));

        assertThat(service.obtenerPorId(17)).isEqualTo(new UsuarioDto(
                17, "operador", "Ana", "Ríos", "ana@example.test", true, "OPERADOR"));
        verify(repository).findById(17);
    }

    @Test
    void findsUserByUsernameAndReturnsDto() {
        Usuario usuario = new Usuario(
                "operador", "synthetic-test-hash", "Ana", "Ríos", "ana@example.test", null);
        when(repository.findByUsername("operador")).thenReturn(Optional.of(usuario));

        assertThat(service.obtenerPorUsername("operador")).isEqualTo(new UsuarioDto(
                null, "operador", "Ana", "Ríos", "ana@example.test", true, null));
        verify(repository).findByUsername("operador");
    }

    @Test
    void throwsNotFoundWhenUsernameDoesNotExist() {
        when(repository.findByUsername("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtenerPorUsername("missing"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("El usuario solicitado no existe.");
    }
}
