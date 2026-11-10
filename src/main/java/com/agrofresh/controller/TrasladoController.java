package com.agrofresh.controller;

import com.agrofresh.security.PermisoService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class TrasladoController {
    private final PermisoService permisos;
    public TrasladoController(PermisoService permisos) { this.permisos = permisos; }

    @GetMapping("/traslados")
    public String mostrarTraslados(Authentication auth, Model model) {
        model.addAttribute("puedeConfirmar", permisos.puede(auth, "traslado", "INSERT"));
        return "traslados";
    }
}
