package com.agrofresh.controller;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Consulta de existencias por bodega. Sin cambios de stock desde esta vista. */
@Controller
public class InventarioController {
    private final JdbcTemplate jdbc;

    public InventarioController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/inventario")
    public String consultar(@RequestParam(defaultValue = "") String buscar,
                            @RequestParam(required = false) Integer bodega,
                            @RequestParam(defaultValue = "todos") String estado,
                            Model model) {
        buscar = buscar.trim();
        if (buscar.length() > 100) buscar = buscar.substring(0, 100);
        if (!List.of("todos", "bajo", "agotado", "disponible").contains(estado)) estado = "todos";

        List<Map<String, Object>> bodegas = jdbc.queryForList(
            "SELECT id_bodega, nombre, ubicacion FROM bodega ORDER BY nombre");
        // Se agrega el filtro opcional solo si hay una bodega seleccionada.
        // Evita el error de PostgreSQL "could not determine data type of parameter $1"
        // producido por expresiones del tipo (? IS NULL).
        StringBuilder sql = new StringBuilder("SELECT i.id_bodega, b.nombre AS bodega, p.id_producto, "
            + "p.codigo_interno, p.nombre AS producto, p.unidad_medida, "
            + "i.cantidad_actual, i.stock_minimo, "
            + "CASE WHEN i.cantidad_actual = 0 THEN 'AGOTADO' "
            + "WHEN i.cantidad_actual < i.stock_minimo THEN 'BAJO' "
            + "ELSE 'DISPONIBLE' END AS estado "
            + "FROM inventario i JOIN producto p ON p.id_producto = i.id_producto "
            + "JOIN bodega b ON b.id_bodega = i.id_bodega WHERE 1=1 ");
        List<Object> parametros = new ArrayList<>();
        if (bodega != null) {
            sql.append("AND i.id_bodega = ? ");
            parametros.add(bodega);
        }
        if (!buscar.isBlank()) {
            sql.append("AND (p.nombre ILIKE ? OR p.codigo_interno ILIKE ?) ");
            parametros.add("%" + buscar + "%");
            parametros.add("%" + buscar + "%");
        }
        switch (estado) {
            case "bajo" -> sql.append("AND i.cantidad_actual > 0 AND i.cantidad_actual < i.stock_minimo ");
            case "agotado" -> sql.append("AND i.cantidad_actual = 0 ");
            case "disponible" -> sql.append("AND i.cantidad_actual > 0 AND i.cantidad_actual >= i.stock_minimo ");
            default -> { }
        }
        sql.append("ORDER BY b.nombre, p.nombre");
        List<Map<String, Object>> filas = jdbc.queryForList(sql.toString(), parametros.toArray());
        long bajas = filas.stream().filter(f -> "BAJO".equals(f.get("estado"))).count();
        long agotadas = filas.stream().filter(f -> "AGOTADO".equals(f.get("estado"))).count();
        model.addAttribute("bodegas", bodegas);
        model.addAttribute("filas", filas);
        model.addAttribute("buscar", buscar);
        model.addAttribute("bodegaSeleccionada", bodega);
        model.addAttribute("estadoSeleccionado", estado);
        model.addAttribute("totalFilas", filas.size());
        model.addAttribute("bajas", bajas);
        model.addAttribute("agotadas", agotadas);
        return "inventario";
    }
}
