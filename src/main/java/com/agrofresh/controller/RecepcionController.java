package com.agrofresh.controller;

import com.agrofresh.security.PermisoService;
import com.agrofresh.service.RecepcionService;
import com.agrofresh.service.RecepcionService.Solicitud;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Pantalla y API de recepción. El backend vuelve a validar cada dato. */
@Controller
public class RecepcionController {
    private final JdbcTemplate jdbc;
    private final PermisoService permisos;
    private final RecepcionService recepcion;

    public RecepcionController(JdbcTemplate jdbc, PermisoService permisos, RecepcionService recepcion) {
        this.jdbc = jdbc;
        this.permisos = permisos;
        this.recepcion = recepcion;
    }

    @GetMapping("/compras")
    public String pagina(Model model, Authentication auth) {
        model.addAttribute("puedeRegistrar", permisos.puede(auth, "compra", "INSERT"));
        return "recepcion";
    }

    @ResponseBody
    @GetMapping("/api/compras/bodegas")
    public List<Map<String, Object>> bodegas() {
        return jdbc.queryForList("SELECT id_bodega AS id, nombre, ubicacion FROM bodega ORDER BY nombre");
    }

    @ResponseBody
    @GetMapping("/api/compras/proveedores")
    public List<Map<String, Object>> proveedores() {
        return jdbc.queryForList("SELECT id_proveedor AS id, nombre FROM proveedor ORDER BY nombre");
    }

    @ResponseBody
    @GetMapping("/api/compras/productos")
    public List<Map<String, Object>> productos(@RequestParam Integer proveedor) {
        if (proveedor == null || proveedor <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        return jdbc.queryForList("""
            SELECT p.id_producto AS id, p.nombre, p.codigo_interno,
                   c.precio_vigente AS precio,
                   CASE WHEN pe.id_producto IS NOT NULL THEN 'PERECEDERO'
                        ELSE 'NO_PERECEDERO' END AS tipo,
                   np.fech_fabricacion AS fabricacion, np.vida_util_anaquel AS vida_util
            FROM catalogo c
            JOIN producto p ON p.id_producto = c.id_producto
            LEFT JOIN perecedero pe ON pe.id_producto = p.id_producto
            LEFT JOIN no_perecedero np ON np.id_producto = p.id_producto
            WHERE c.id_proveedor = ?
              AND ((pe.id_producto IS NOT NULL AND np.id_producto IS NULL)
                   OR (pe.id_producto IS NULL AND np.id_producto IS NOT NULL))
            ORDER BY p.nombre
            """, proveedor);
    }

    @ResponseBody
    @PostMapping("/api/compras/confirmar")
    public ResponseEntity<Map<String, Object>> confirmar(@RequestBody Solicitud solicitud, Authentication auth) {
        try {
            Integer id = recepcion.registrar(solicitud, auth);
            return ResponseEntity.ok(Map.of("mensaje", "Recepción registrada correctamente.", "idCompra", id));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("mensaje", ex.getMessage()));
        } catch (ResponseStatusException ex) {
            return ResponseEntity.status(ex.getStatusCode()).body(Map.of("mensaje",
                ex.getReason() == null ? "Acceso denegado." : ex.getReason()));
        } catch (DataAccessException ex) {
            Throwable cause = ex.getMostSpecificCause();
            if (cause instanceof org.postgresql.util.PSQLException pg &&
                "P0001".equals(pg.getSQLState()) && pg.getServerErrorMessage() != null) {
                return ResponseEntity.badRequest().body(Map.of("mensaje", pg.getServerErrorMessage().getMessage()));
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "mensaje", "No se pudo registrar la compra. Comprueba los procedimientos y la conexión PostgreSQL."));
        }
    }
}
