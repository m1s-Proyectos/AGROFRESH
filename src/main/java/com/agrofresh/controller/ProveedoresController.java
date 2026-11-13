package com.agrofresh.controller;

import com.agrofresh.dto.ProveedorDetalleDto;
import com.agrofresh.dto.ProveedorForm;
import com.agrofresh.exception.BusinessException;
import com.agrofresh.security.PermisoService;
import com.agrofresh.service.ProveedorService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ProveedoresController {

    private final ProveedorService proveedorService;
    private final PermisoService permisos;

    public ProveedoresController(ProveedorService proveedorService, PermisoService permisos) {
        this.proveedorService = proveedorService;
        this.permisos = permisos;
    }

    @GetMapping("/proveedores")
    public String listar(
            @RequestParam(required = false) String q,
            Model model,
            Authentication authentication) {
        exigirPermiso(authentication, "SELECT");
        var proveedores = (q != null && !q.trim().isEmpty())
                ? proveedorService.buscar(q)
                : proveedorService.listar();
        model.addAttribute("proveedores", proveedores);
        model.addAttribute("terminoBusqueda", q == null ? "" : q.trim());
        model.addAttribute("puedeRegistrar", permisos.puede(authentication, "proveedor", "INSERT"));
        model.addAttribute("puedeEditar", permisos.puede(authentication, "proveedor", "UPDATE"));
        return "proveedores";
    }

    @GetMapping("/proveedores/nueva")
    public String nueva(Model model, Authentication authentication) {
        exigirPermiso(authentication, "INSERT");
        if (!model.containsAttribute("proveedorForm")) {
            model.addAttribute("proveedorForm", new ProveedorForm());
        }
        return "proveedor-form";
    }

    @PostMapping("/proveedores/guardar")
    public String guardar(
            @ModelAttribute("proveedorForm") ProveedorForm form,
            Authentication authentication,
            RedirectAttributes mensajes) {
        exigirPermiso(authentication, "INSERT");
        try {
            ProveedorDetalleDto proveedor = proveedorService.registrar(form);
            mensajes.addFlashAttribute("exito", "Proveedor registrado correctamente.");
            return "redirect:/proveedores/consultar/" + proveedor.idProveedor();
        } catch (BusinessException exception) {
            mensajes.addFlashAttribute("error", exception.getMessage());
            mensajes.addFlashAttribute("proveedorForm", form);
            return "redirect:/proveedores/nueva";
        }
    }

    @GetMapping("/proveedores/editar/{id}")
    public String editar(@PathVariable Integer id, Model model, Authentication authentication) {
        exigirPermiso(authentication, "UPDATE");
        ProveedorDetalleDto proveedor = proveedorService.consultar(id);
        model.addAttribute("idProveedor", id);
        if (!model.containsAttribute("proveedorForm")) {
            model.addAttribute("proveedorForm", ProveedorForm.desde(proveedor));
        }
        return "proveedor-form";
    }

    @PostMapping("/proveedores/actualizar/{id}")
    public String actualizar(
            @PathVariable Integer id,
            @ModelAttribute("proveedorForm") ProveedorForm form,
            Authentication authentication,
            RedirectAttributes mensajes) {
        exigirPermiso(authentication, "UPDATE");
        try {
            ProveedorDetalleDto proveedor = proveedorService.actualizar(id, form);
            mensajes.addFlashAttribute("exito", "Proveedor actualizado correctamente.");
            return "redirect:/proveedores/consultar/" + proveedor.idProveedor();
        } catch (BusinessException exception) {
            mensajes.addFlashAttribute("error", exception.getMessage());
            mensajes.addFlashAttribute("proveedorForm", form);
            return "redirect:/proveedores/editar/" + id;
        }
    }

    @GetMapping("/proveedores/consultar/{id}")
    public String consultar(@PathVariable Integer id, Model model, Authentication authentication) {
        exigirPermiso(authentication, "SELECT");
        model.addAttribute("proveedor", proveedorService.consultar(id));
        model.addAttribute("puedeEditar", permisos.puede(authentication, "proveedor", "UPDATE"));
        return "proveedor-detalle";
    }

    private void exigirPermiso(Authentication authentication, String accion) {
        if (!permisos.puede(authentication, "proveedor", accion)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permiso para esta operación.");
        }
    }
}
