package com.agrofresh.controller;

import com.agrofresh.dto.CategoriaDto;
import com.agrofresh.dto.CategoriaForm;
import com.agrofresh.exception.BusinessException;
import com.agrofresh.security.PermisoService;
import com.agrofresh.service.CategoriaService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class CategoriasController {

    private final CategoriaService categoriaService;
    private final PermisoService permisos;

    public CategoriasController(CategoriaService categoriaService, PermisoService permisos) {
        this.categoriaService = categoriaService;
        this.permisos = permisos;
    }

    @GetMapping("/categorias")
    public String listar(Model model, Authentication authentication) {
        exigirPermiso(authentication, "producto", "SELECT");
        model.addAttribute("categorias", categoriaService.listar());
        model.addAttribute("puedeRegistrar", permisos.puede(authentication, "producto", "INSERT"));
        model.addAttribute("puedeEditar", permisos.puede(authentication, "producto", "UPDATE"));
        return "categorias";
    }

    @GetMapping("/categorias/nueva")
    public String nueva(Model model, Authentication authentication) {
        exigirPermiso(authentication, "producto", "INSERT");
        if (!model.containsAttribute("categoriaForm")) {
            model.addAttribute("categoriaForm", new CategoriaForm());
        }
        return "categoria-form";
    }

    @PostMapping("/categorias/guardar")
    public String guardar(@ModelAttribute("categoriaForm") CategoriaForm form,
                          Authentication authentication,
                          RedirectAttributes mensajes) {
        exigirPermiso(authentication, "producto", "INSERT");
        try {
            CategoriaDto categoria = categoriaService.registrar(form);
            mensajes.addFlashAttribute("exito", "Categoría registrada correctamente.");
            return "redirect:/categorias/consultar/" + categoria.idCategoria();
        } catch (BusinessException exception) {
            mensajes.addFlashAttribute("error", exception.getMessage());
            mensajes.addFlashAttribute("categoriaForm", form);
            return "redirect:/categorias/nueva";
        }
    }

    @GetMapping("/categorias/editar/{id}")
    public String editar(@PathVariable Integer id, Model model, Authentication authentication) {
        exigirPermiso(authentication, "producto", "UPDATE");
        CategoriaDto categoria = categoriaService.consultar(id);
        model.addAttribute("idCategoria", categoria.idCategoria());
        if (!model.containsAttribute("categoriaForm")) {
            model.addAttribute("categoriaForm", new CategoriaForm(categoria.nombre(), categoria.descripcion()));
        }
        return "categoria-form";
    }

    @PostMapping("/categorias/actualizar/{id}")
    public String actualizar(@PathVariable Integer id,
                             @ModelAttribute("categoriaForm") CategoriaForm form,
                             Authentication authentication,
                             RedirectAttributes mensajes) {
        exigirPermiso(authentication, "producto", "UPDATE");
        try {
            CategoriaDto categoria = categoriaService.actualizar(id, form);
            mensajes.addFlashAttribute("exito", "Categoría actualizada correctamente.");
            return "redirect:/categorias/consultar/" + categoria.idCategoria();
        } catch (BusinessException exception) {
            mensajes.addFlashAttribute("error", exception.getMessage());
            mensajes.addFlashAttribute("categoriaForm", form);
            return "redirect:/categorias/editar/" + id;
        }
    }

    @GetMapping("/categorias/consultar/{id}")
    public String consultar(@PathVariable Integer id, Model model, Authentication authentication) {
        exigirPermiso(authentication, "producto", "SELECT");
        model.addAttribute("categoria", categoriaService.consultar(id));
        model.addAttribute("puedeEditar", permisos.puede(authentication, "producto", "UPDATE"));
        return "categoria-detalle";
    }

    private void exigirPermiso(Authentication authentication, String recurso, String accion) {
        if (!permisos.puede(authentication, recurso, accion)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "No tienes permiso para esta operación.");
        }
    }
}
