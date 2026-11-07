package com.agrofresh.mapper;

import com.agrofresh.dto.UsuarioDto;
import com.agrofresh.model.Usuario;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class UsuarioMapper {

    public UsuarioDto toDto(Usuario usuario) {
        Objects.requireNonNull(usuario, "usuario no puede ser null");
        return new UsuarioDto(
                usuario.getIdUsuario(),
                usuario.getUsername(),
                usuario.getNombre(),
                usuario.getApellido(),
                usuario.getCorreo(),
                usuario.isActivo(),
                usuario.getRol());
    }
}
