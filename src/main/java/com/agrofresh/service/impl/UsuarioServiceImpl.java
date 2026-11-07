package com.agrofresh.service.impl;

import com.agrofresh.dto.UsuarioDto;
import com.agrofresh.exception.ResourceNotFoundException;
import com.agrofresh.mapper.UsuarioMapper;
import com.agrofresh.model.Usuario;
import com.agrofresh.repository.UsuarioRepository;
import com.agrofresh.service.UsuarioService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
@Transactional(readOnly = true)
public class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final UsuarioMapper usuarioMapper;

    public UsuarioServiceImpl(UsuarioRepository usuarioRepository, UsuarioMapper usuarioMapper) {
        this.usuarioRepository = usuarioRepository;
        this.usuarioMapper = usuarioMapper;
    }

    @Override
    public List<UsuarioDto> listar() {
        return usuarioRepository.findAll().stream()
                .map(usuarioMapper::toDto)
                .toList();
    }

    @Override
    public UsuarioDto obtenerPorId(Integer idUsuario) {
        Usuario usuario = usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new ResourceNotFoundException("El usuario solicitado no existe."));
        return usuarioMapper.toDto(usuario);
    }

    @Override
    public UsuarioDto obtenerPorUsername(String username) {
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("El usuario solicitado no existe."));
        return usuarioMapper.toDto(usuario);
    }
}
