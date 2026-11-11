package com.agrofresh.controller;

import com.agrofresh.dto.ProductoDetalleDto;
import com.agrofresh.dto.ProductoForm;
import com.agrofresh.exception.BusinessException;
import com.agrofresh.security.PermisoService;
import com.agrofresh.service.ProductoService;
import com.agrofresh.util.UnidadMedidaCatalog;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ProductosController {
    private final ProductoService productoService;
    private final PermisoService permisos;

    public ProductosController(ProductoService productoService, PermisoService permisos) {
        this.productoService = productoService;
        this.permisos = permisos;
    }

    @GetMapping("/productos")
    public String listar(Model model, Authentication authentication) {
        exigirPermiso(authentication, "SELECT");
        model.addAttribute("productos", productoService.listar());
        model.addAttribute("puedeRegistrar", permisos.puede(authentication, "producto", "INSERT"));
        model.addAttribute("puedeEditar", permisos.puede(authentication, "producto", "UPDATE"));
        return "productos";
    }

    @GetMapping("/productos/nueva")
    public String nueva(Model model, Authentication authentication) {
        exigirPermiso(authentication, "INSERT");
        ProductoForm form = model.containsAttribute("productoForm")
                ? (ProductoForm) model.getAttribute("productoForm")
                : new ProductoForm();
        if (!model.containsAttribute("productoForm")) {
            model.addAttribute("productoForm", form);
        }
        prepararCatalogos(model, form);
        return "producto-form";
    }

    @PostMapping("/productos/guardar")
    public String guardar(
            @ModelAttribute("productoForm") ProductoForm form,
            Authentication authentication,
            RedirectAttributes mensajes) {
        exigirPermiso(authentication, "INSERT");
        try {
            ProductoDetalleDto producto = productoService.registrar(form);
            mensajes.addFlashAttribute("exito", "Producto registrado correctamente.");
            return "redirect:/productos/consultar/" + producto.idProducto();
        } catch (BusinessException exception) {
            mensajes.addFlashAttribute("error", exception.getMessage());
            mensajes.addFlashAttribute("productoForm", form);
            return "redirect:/productos/nueva";
        }
    }

    @GetMapping("/productos/editar/{id}")
    public String editar(@PathVariable Integer id, Model model, Authentication authentication) {
        exigirPermiso(authentication, "UPDATE");
        ProductoDetalleDto producto = productoService.consultar(id);
        model.addAttribute("idProducto", id);
        ProductoForm form = model.containsAttribute("productoForm")
                ? (ProductoForm) model.getAttribute("productoForm")
                : ProductoForm.desde(producto);
        if (!model.containsAttribute("productoForm")) {
            model.addAttribute("productoForm", form);
        }
        prepararCatalogos(model, form);
        return "producto-form";
    }

    @PostMapping("/productos/actualizar/{id}")
    public String actualizar(
            @PathVariable Integer id,
            @ModelAttribute("productoForm") ProductoForm form,
            Authentication authentication,
            RedirectAttributes mensajes) {
        exigirPermiso(authentication, "UPDATE");
        try {
            ProductoDetalleDto producto = productoService.actualizar(id, form);
            mensajes.addFlashAttribute("exito", "Producto actualizado correctamente.");
            return "redirect:/productos/consultar/" + producto.idProducto();
        } catch (BusinessException exception) {
            mensajes.addFlashAttribute("error", exception.getMessage());
            mensajes.addFlashAttribute("productoForm", form);
            return "redirect:/productos/editar/" + id;
        }
    }

    @GetMapping("/productos/consultar/{id}")
    public String consultar(@PathVariable Integer id, Model model, Authentication authentication) {
        exigirPermiso(authentication, "SELECT");
        model.addAttribute("producto", productoService.consultar(id));
        model.addAttribute("puedeEditar", permisos.puede(authentication, "producto", "UPDATE"));
        return "producto-detalle";
    }

    private void prepararCatalogos(Model model, ProductoForm form) {
        model.addAttribute("categorias", productoService.categoriasDisponibles());
        List<String> unidades = new ArrayList<>(productoService.unidadesDisponibles());
        if (form != null && form.getUnidadMedida() != null) {
            String actual = form.getUnidadMedida().trim();
            if (!actual.isEmpty() && !unidades.contains(actual)) {
                unidades.add(0, actual);
            }
        }
        model.addAttribute("unidades", unidades);
        model.addAttribute("unidadEsHistorica", form != null && form.getUnidadMedida() != null
                && !UnidadMedidaCatalog.esValida(form.getUnidadMedida().trim()));
    }

    private void exigirPermiso(Authentication authentication, String accion) {
        if (!permisos.puede(authentication, "producto", accion)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permiso para esta operación.");
        }
    }
}
