package com.agrofresh.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.security.core.Authentication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Controller
public class ModuloController {
    private final JdbcTemplate jdbc;
    public ModuloController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/perfil")
    public String perfil(Authentication auth, Model model) {
        var usuarios = jdbc.queryForList(
            "SELECT u.username, u.nombre, u.apellido, u.correo, r.nombre AS rol " +
            "FROM usuario u JOIN rol r ON r.id_rol=u.id_rol WHERE u.username=?", auth.getName());
        if (usuarios.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        model.addAttribute("perfil", usuarios.get(0));
        return "perfil";
    }

    @GetMapping("/acceso-denegado")
    public String denegado() { return "acceso-denegado"; }
}
