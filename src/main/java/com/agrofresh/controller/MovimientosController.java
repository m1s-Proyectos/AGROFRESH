package com.agrofresh.controller;

import com.agrofresh.dto.MovimientoFiltro;
import com.agrofresh.security.PermisoService;
import com.agrofresh.service.MovimientoService;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class MovimientosController {

  private final MovimientoService movimientoService;
  private final PermisoService permisos;

  public MovimientosController(MovimientoService movimientoService, PermisoService permisos) {
    this.movimientoService = movimientoService;
    this.permisos = permisos;
  }

  @GetMapping("/movimientos")
  public String listar(
      @RequestParam(required = false) String tipo,
      @RequestParam(required = false) Integer bodega,
      @RequestParam(required = false) Integer producto,
      @RequestParam(required = false) LocalDate desde,
      @RequestParam(required = false) LocalDate hasta,
      @RequestParam(required = false) Integer pagina,
      @RequestParam(required = false) Integer tamano,
      Model model,
      Authentication authentication) {
    exigirPermiso(authentication, "SELECT");
    MovimientoFiltro filtro =
        movimientoService.normalizarFiltro(tipo, bodega, producto, desde, hasta, pagina, tamano);
    long total = movimientoService.contar(filtro);
    model.addAttribute("movimientos", movimientoService.listar(filtro));
    model.addAttribute("filtroTipo", filtro.tipo() == null ? "" : filtro.tipo());
    model.addAttribute("filtroBodega", filtro.idBodega());
    model.addAttribute("filtroProducto", filtro.idProducto());
    model.addAttribute("filtroDesde", filtro.fechaDesde());
    model.addAttribute("filtroHasta", filtro.fechaHasta());
    model.addAttribute("pagina", filtro.pagina());
    model.addAttribute("tamanoPagina", filtro.tamanoPagina());
    model.addAttribute("totalRegistros", total);
    model.addAttribute("hayMasPaginas", total > (long) filtro.offset() + filtro.tamanoPagina());
    model.addAttribute("tiposMovimiento", movimientoService.tiposDisponibles());
    model.addAttribute("bodegas", movimientoService.bodegasParaFiltro());
    model.addAttribute("productos", movimientoService.productosParaFiltro());
    return "movimientos";
  }

  @GetMapping("/movimientos/registrar")
  public String panelRegistrar(Authentication authentication) {
    exigirPermiso(authentication, "SELECT");
    return "movimientos-registrar";
  }

  @GetMapping("/movimientos/consultar/{id}")
  public String consultar(@PathVariable int id, Model model, Authentication authentication) {
    exigirPermiso(authentication, "SELECT");
    model.addAttribute("movimiento", movimientoService.consultar(id));
    return "movimiento-detalle";
  }

  @GetMapping("/movimientos/salida/no-perecedero")
  public String formularioSalidaNoPerecedero(Authentication authentication) {
    exigirPermiso(authentication, "INSERT");
    return "movimiento-salida-no-perecedero";
  }

  @GetMapping("/movimientos/salida/perecedero")
  public String formularioSalidaPerecedero(Authentication authentication) {
    exigirPermiso(authentication, "INSERT");
    return "movimiento-salida-perecedero";
  }

  @GetMapping("/movimientos/baja/dano/no-perecedero")
  public String formularioBajaDanoNoPerecedero(Authentication authentication) {
    exigirAdministrador(authentication);
    return "movimiento-baja-dano-no-perecedero";
  }

  @GetMapping("/movimientos/baja/dano/perecedero")
  public String formularioBajaDanoPerecedero(Authentication authentication) {
    exigirAdministrador(authentication);
    return "movimiento-baja-dano-perecedero";
  }

  @GetMapping("/movimientos/baja/vencimiento")
  public String formularioBajaVencimiento(Authentication authentication) {
    exigirAdministrador(authentication);
    return "movimiento-baja-vencimiento";
  }

  private void exigirAdministrador(Authentication authentication) {
    if (!esAdministrador(authentication)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Solo un administrador puede registrar bajas.");
    }
  }

  private boolean esAdministrador(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      return false;
    }
    return authentication.getAuthorities().stream()
        .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
  }

  private void exigirPermiso(Authentication authentication, String accion) {
    if (!permisos.puede(authentication, "movimiento", accion)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permiso para esta operación.");
    }
  }
}
