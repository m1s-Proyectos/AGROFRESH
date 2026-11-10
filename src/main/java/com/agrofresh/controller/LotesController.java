package com.agrofresh.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Consulta de lotes: sin operaciones de escritura ni cambios de existencias. */
@Controller
public class LotesController {
    private final JdbcTemplate jdbc;

    public LotesController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/lotes")
    public String consultar(@RequestParam(defaultValue = "") String buscar,
                            @RequestParam(required = false) Integer bodega,
                            @RequestParam(defaultValue = "todos") String estado,
                            Model model) {
        buscar = buscar.trim();
        if (buscar.length() > 100) buscar = buscar.substring(0, 100);
        if (!List.of("todos", "vigente", "proximo", "vencido", "sin_stock").contains(estado)) estado = "todos";

        List<Map<String, Object>> bodegas = jdbc.queryForList(
            "SELECT id_bodega, nombre, ubicacion FROM bodega ORDER BY nombre");

        StringBuilder sql = new StringBuilder("""
            SELECT l.num_lote, l.id_producto, l.id_bodega, l.id_proveedor,
                   p.nombre AS producto, p.codigo_interno, b.nombre AS bodega,
                   pr.nombre AS proveedor, l.fech_vencimiento,
                   TO_CHAR(l.fech_vencimiento, 'DD/MM/YYYY') AS fecha_formateada,
                   l.cantidad_recibida, l.cantidad_actual,
                   (l.fech_vencimiento - CURRENT_DATE) AS dias_restantes,
                   CASE WHEN l.cantidad_actual = 0 THEN 'SIN_STOCK'
                        WHEN l.fech_vencimiento <= CURRENT_DATE THEN 'VENCIDO'
                        WHEN l.fech_vencimiento <= CURRENT_DATE + 7 THEN 'PROXIMO'
                        ELSE 'VIGENTE' END AS estado
            FROM lote l
            JOIN producto p ON p.id_producto = l.id_producto
            JOIN bodega b ON b.id_bodega = l.id_bodega
            JOIN proveedor pr ON pr.id_proveedor = l.id_proveedor
            WHERE 1=1
            """);
        List<Object> params = new ArrayList<>();
        if (bodega != null) {
            sql.append(" AND l.id_bodega = ?");
            params.add(bodega);
        }
        if (!buscar.isBlank()) {
            sql.append(" AND (p.nombre ILIKE ? OR p.codigo_interno ILIKE ? OR CAST(l.num_lote AS TEXT) ILIKE ?)");
            String termino = "%" + buscar + "%";
            params.add(termino); params.add(termino); params.add(termino);
        }
        switch (estado) {
            case "vigente" -> sql.append(" AND l.cantidad_actual > 0 AND l.fech_vencimiento > CURRENT_DATE + 7");
            case "proximo" -> sql.append(" AND l.cantidad_actual > 0 AND l.fech_vencimiento > CURRENT_DATE AND l.fech_vencimiento <= CURRENT_DATE + 7");
            case "vencido" -> sql.append(" AND l.cantidad_actual > 0 AND l.fech_vencimiento <= CURRENT_DATE");
            case "sin_stock" -> sql.append(" AND l.cantidad_actual = 0");
            default -> { }
        }
        sql.append(" ORDER BY l.fech_vencimiento ASC, p.nombre, b.nombre, l.num_lote");
        List<Map<String, Object>> filas = jdbc.queryForList(sql.toString(), params.toArray());
        model.addAttribute("bodegas", bodegas);
        model.addAttribute("filas", filas);
        model.addAttribute("buscar", buscar);
        model.addAttribute("bodegaSeleccionada", bodega);
        model.addAttribute("estadoSeleccionado", estado);
        model.addAttribute("totalFilas", filas.size());
        model.addAttribute("proximos", filas.stream().filter(f -> "PROXIMO".equals(f.get("estado"))).count());
        model.addAttribute("vencidos", filas.stream().filter(f -> "VENCIDO".equals(f.get("estado"))).count());
        return "lotes";
    }
}
