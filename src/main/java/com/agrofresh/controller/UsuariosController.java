package com.agrofresh.controller;

import com.agrofresh.security.PermisoService;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Gestión mínima del estado de cuentas, sin borrar usuarios ni añadir roles. */
@Controller
public class UsuariosController {
    private final JdbcTemplate jdbc;
    private final PermisoService permisos;

    public UsuariosController(JdbcTemplate jdbc, PermisoService permisos) {
        this.jdbc = jdbc;
        this.permisos = permisos;
    }

    @GetMapping("/usuarios")
    public String listado(Model model, Authentication auth) {
        List<Map<String, Object>> usuarios = jdbc.queryForList(
            "SELECT u.id_usuario, u.username, u.nombre, u.apellido, u.correo, " +
            "u.activo, u.bloqueado, r.nombre AS rol " +
            "FROM usuario u JOIN rol r ON r.id_rol=u.id_rol ORDER BY u.id_usuario");
        model.addAttribute("usuarios", usuarios);
        model.addAttribute("puedeEditar", permisos.puede(auth, "usuario", "UPDATE"));
        return "usuarios";
    }

    @PostMapping("/usuarios/{id}/estado")
    public String cambiarEstado(@PathVariable int id, @RequestParam boolean activo,
                                Authentication auth, RedirectAttributes mensajes) {
        exigirPermiso(auth);
        if (id <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        int modificados = jdbc.update(
            "UPDATE usuario SET activo=? WHERE id_usuario=? AND username<>?",
            activo, id, auth.getName());
        if (modificados != 1) {
            mensajes.addFlashAttribute("error", "No se modificó el usuario. No puedes desactivar tu propia cuenta.");
        } else {
            mensajes.addFlashAttribute("exito", activo ? "Usuario reactivado." : "Usuario dado de baja.");
        }
        return "redirect:/usuarios";
    }

    @PostMapping("/usuarios/{id}/bloqueo")
    public String cambiarBloqueo(@PathVariable int id, @RequestParam boolean bloqueado,
                                  Authentication auth, RedirectAttributes mensajes) {
        exigirPermiso(auth);
        if (id <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        int modificados = jdbc.update(
            "UPDATE usuario SET bloqueado=?, intentos_fallidos=0 WHERE id_usuario=? AND username<>?",
            bloqueado, id, auth.getName());
        if (modificados != 1) {
            mensajes.addFlashAttribute("error", "No se modificó el usuario. No puedes bloquear tu propia cuenta.");
        } else {
            mensajes.addFlashAttribute("exito", bloqueado ? "Usuario bloqueado." : "Usuario desbloqueado.");
        }
        return "redirect:/usuarios";
    }

    private void exigirPermiso(Authentication auth) {
        if (!permisos.puede(auth, "usuario", "UPDATE")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No autorizado para gestionar usuarios");
        }
    }
}
