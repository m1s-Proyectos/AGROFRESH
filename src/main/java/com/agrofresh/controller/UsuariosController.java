package com.agrofresh.controller;

import com.agrofresh.security.PermisoService;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Cuentas y roles: autorización por permisos almacenados en PostgreSQL. */
@Controller
public class UsuariosController {
    private static final Pattern USERNAME = Pattern.compile("[a-zA-Z0-9_.-]{3,50}");
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
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
            "u.activo, u.bloqueado, u.id_rol, r.nombre AS rol " +
            "FROM usuario u JOIN rol r ON r.id_rol=u.id_rol ORDER BY u.id_usuario");
        model.addAttribute("usuarios", usuarios);
        model.addAttribute("roles", jdbc.queryForList(
            "SELECT id_rol, nombre FROM rol WHERE activo=TRUE AND nombre IN ('ADMINISTRADOR', 'OPERATIVO') ORDER BY nombre"));
        model.addAttribute("puedeEditar", permisos.puede(auth, "usuario", "UPDATE"));
        model.addAttribute("puedeCrear", permisos.puede(auth, "usuario", "INSERT"));
        return "usuarios";
    }

    @PostMapping("/usuarios/nuevo")
    public String nuevo(@RequestParam String nombre, @RequestParam String apellido,
                        @RequestParam String username, @RequestParam String correo,
                        @RequestParam String contrasena, @RequestParam int idRol,
                        Authentication auth, RedirectAttributes mensajes) {
        exigir(auth, "INSERT");
        try {
            nombre = texto(nombre, "Nombre", 100);
            apellido = texto(apellido, "Apellido", 100);
            username = usuarioValido(username);
            correo = correoValido(correo);
            contrasenaValida(contrasena);
            // PostgreSQL valida el rol y los permisos y genera el hash bcrypt.
            jdbc.update("CALL sp_crear_usuario(?, ?, ?, ?, ?, ?, ?)",
                username, contrasena, nombre, apellido, correo, idRol, auth.getName());
            mensajes.addFlashAttribute("exito", "Usuario creado correctamente.");
        } catch (IllegalArgumentException ex) {
            recordarFormularioNuevo(mensajes, nombre, apellido, username, correo, idRol, ex.getMessage());
        } catch (DataAccessException ex) {
            recordarFormularioNuevo(mensajes, nombre, apellido, username, correo, idRol,
                errorDeBase(ex, "No se pudo crear el usuario. Revisa los campos e inténtalo de nuevo."));
        }
        return "redirect:/usuarios";
    }

    @PostMapping("/usuarios/{id}/editar")
    public String editar(@PathVariable int id, @RequestParam String nombre,
                         @RequestParam String apellido, @RequestParam String correo,
                         @RequestParam int idRol, Authentication auth,
                         RedirectAttributes mensajes) {
        exigir(auth, "UPDATE");
        try {
            if (id <= 0) throw new IllegalArgumentException("Usuario inválido.");
            nombre = texto(nombre, "Nombre", 100);
            apellido = texto(apellido, "Apellido", 100);
            correo = correoValido(correo);
            // PostgreSQL valida el usuario responsable, el rol y que no sea su propia cuenta.
            jdbc.update("CALL sp_actualizar_usuario(?, ?, ?, ?, ?, ?)",
                id, nombre, apellido, correo, idRol, auth.getName());
            mensajes.addFlashAttribute("exito", "Datos y rol del usuario actualizados.");
        } catch (IllegalArgumentException ex) {
            recordarFormularioEditar(mensajes, id, nombre, apellido, correo, idRol, ex.getMessage());
        } catch (DataAccessException ex) {
            recordarFormularioEditar(mensajes, id, nombre, apellido, correo, idRol,
                errorDeBase(ex, "No se pudo actualizar el usuario. Revisa los datos e inténtalo de nuevo."));
        }
        return "redirect:/usuarios";
    }

    @PostMapping("/usuarios/{id}/estado")
    public String cambiarEstado(@PathVariable int id, @RequestParam boolean activo,
                                Authentication auth, RedirectAttributes mensajes) {
        exigir(auth, "UPDATE");
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
        exigir(auth, "UPDATE");
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

    // Conservamos los campos públicos entre redirecciones, pero NUNCA la contraseña.
    private void recordarFormularioNuevo(RedirectAttributes mensajes, String nombre, String apellido,
                                         String username, String correo, int idRol, String error) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("nombre", nombre);
        datos.put("apellido", apellido);
        datos.put("username", username);
        datos.put("correo", correo);
        datos.put("idRol", idRol);
        mensajes.addFlashAttribute("nuevoDatos", datos);
        mensajes.addFlashAttribute("nuevoError", error);
    }

    private void recordarFormularioEditar(RedirectAttributes mensajes, int id, String nombre,
                                          String apellido, String correo, int idRol, String error) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("id", id);
        datos.put("nombre", nombre);
        datos.put("apellido", apellido);
        datos.put("correo", correo);
        datos.put("idRol", idRol);
        mensajes.addFlashAttribute("editarDatos", datos);
        mensajes.addFlashAttribute("editarError", error);
    }

    // Solo exponemos mensajes reconocidos: los detalles SQL internos nunca llegan al navegador.
    private String errorDeBase(DataAccessException ex, String mensajeGenerico) {
        Throwable causa = ex;
        while (causa != null) {
            String detalle = causa.getMessage();
            if (detalle != null) {
                String d = detalle.toLowerCase(Locale.ROOT);
                if (d.contains("nombre de usuario ya está registrado") ||
                    (d.contains("uq_usuario_username") && d.contains("duplicate key")))
                    return "Ese nombre de usuario ya está registrado. Elige otro.";
                if (d.contains("username inválido"))
                    return "El username debe contener entre 3 y 50 letras, números, puntos, guiones o guion bajo.";
                if (d.contains("correo electrónico inválido"))
                    return "Ingresa un correo electrónico válido.";
                if (d.contains("contraseña debe tener"))
                    return "La contraseña debe tener al menos 8 caracteres y no superar los 72 bytes.";
                if (d.contains("nombre y apellido son obligatorios"))
                    return "Nombre y apellido son obligatorios y no pueden superar 100 caracteres.";
                if (d.contains("rol seleccionado no es válido"))
                    return "El rol seleccionado no está disponible. Actualiza la página.";
                if (d.contains("no puedes editar tu propia cuenta"))
                    return "No puedes cambiar tu propia cuenta desde esta sección.";
                if (d.contains("no tienes permiso"))
                    return "No tienes permiso para realizar esta operación.";
                if (d.contains("usuario que intentas editar no existe"))
                    return "El usuario ya no existe. Actualiza la lista.";
                if (d.contains("duplicate key") && d.contains("correo"))
                    return "Ese correo electrónico ya está registrado.";
            }
            causa = causa.getCause();
        }
        return mensajeGenerico;
    }

    private void exigir(Authentication auth, String accion) {
        if (!permisos.puede(auth, "usuario", accion)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sin permiso para " + accion + " usuarios");
        }
    }

    private String texto(String value, String campo, int max) {
        if (value == null || value.isBlank() || value.trim().length() > max)
            throw new IllegalArgumentException(campo + ": obligatorio (máximo " + max + " caracteres).");
        return value.trim();
    }

    private String usuarioValido(String value) {
        String u = value == null ? "" : value.trim();
        if (!USERNAME.matcher(u).matches())
            throw new IllegalArgumentException("Username: utiliza de 3 a 50 letras, números, puntos, guiones o guion bajo.");
        return u;
    }

    private String correoValido(String value) {
        String c = texto(value, "Correo", 100);
        if (!EMAIL.matcher(c).matches()) throw new IllegalArgumentException("Ingresa un correo electrónico válido.");
        return c;
    }

    private void contrasenaValida(String value) {
        if (value == null || value.length() < 8 || value.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new IllegalArgumentException("La contraseña debe tener al menos 8 caracteres y no exceder 72 bytes para bcrypt.");
    }

}
