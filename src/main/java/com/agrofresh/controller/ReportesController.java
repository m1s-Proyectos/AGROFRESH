package com.agrofresh.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Reportes de lectura: las reglas y cálculos viven en vistas y funciones PostgreSQL. */
@Controller
public class ReportesController {
    private final JdbcTemplate jdbc;
    public ReportesController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/reportes")
    public String mostrar(@RequestParam(defaultValue = "existencias") String tipo,
                          @RequestParam(required = false) String desde,
                          @RequestParam(required = false) String hasta,
                          @RequestParam(required = false) Integer bodega,
                          @RequestParam(required = false) Integer proveedor,
                          @RequestParam(defaultValue = "") String buscar,
                          Model model) {
        if (!List.of("existencias","vencimientos","movimientos","compras").contains(tipo)) tipo="existencias";
        LocalDate hoy=LocalDate.now(java.time.ZoneId.of("America/El_Salvador"));
        LocalDate inicio, fin;
        String error=null;
        try {
            inicio=(desde==null || desde.isBlank()) ? hoy.minusDays(30) : LocalDate.parse(desde);
            fin=(hasta==null || hasta.isBlank()) ? hoy : LocalDate.parse(hasta);
            if (inicio.isAfter(fin)) throw new IllegalArgumentException("La fecha inicial no puede ser posterior a la final.");
        } catch (DateTimeParseException | IllegalArgumentException ex) {
            inicio=hoy.minusDays(30); fin=hoy;
            error="Selecciona un rango de fechas válido.";
        }
        if (buscar==null) buscar="";
        buscar=buscar.trim();
        if (buscar.length()>100) buscar=buscar.substring(0,100);
        List<Map<String,Object>> filas=List.of();
        BigDecimal total=BigDecimal.ZERO;
        try {
            model.addAttribute("bodegas",jdbc.queryForList("SELECT id_bodega,nombre FROM bodega ORDER BY nombre"));
            model.addAttribute("proveedores",jdbc.queryForList("SELECT id_proveedor,nombre FROM proveedor ORDER BY nombre"));
            switch (tipo) {
                case "existencias" -> filas=jdbc.queryForList("""
                    SELECT * FROM v_reporte_existencias
                    WHERE (?::integer IS NULL OR id_bodega=?::integer)
                      AND (producto ILIKE ? OR codigo_interno ILIKE ?)
                    ORDER BY bodega, producto
                    """,bodega,bodega,"%"+buscar+"%","%"+buscar+"%");
                case "vencimientos" -> filas=jdbc.queryForList("""
                    SELECT * FROM v_reporte_vencimientos
                    WHERE (?::integer IS NULL OR id_bodega=?::integer)
                      AND (producto ILIKE ? OR codigo_interno ILIKE ?)
                    ORDER BY fech_vencimiento,producto
                    """,bodega,bodega,"%"+buscar+"%","%"+buscar+"%");
                case "movimientos" -> filas=jdbc.queryForList(
                    "SELECT * FROM fn_reporte_movimientos(?::date,?::date,?::integer)",
                    inicio,fin,bodega);
                case "compras" -> {
                    filas=jdbc.queryForList("SELECT * FROM fn_reporte_compras(?::date,?::date,?::integer)",
                            inicio,fin,proveedor);
                    if (!filas.isEmpty() && filas.get(0).get("total_periodo") instanceof BigDecimal monto) total=monto;
                }
                default -> { }
            }
        } catch (DataAccessException ex) {
            error="No se pudo consultar el reporte. Comprueba que instalaste las vistas y funciones SQL en el esquema correcto.";
            model.addAttribute("bodegas",List.of());
            model.addAttribute("proveedores",List.of());
        }
        model.addAttribute("tipo",tipo);
        model.addAttribute("desde",inicio.toString());
        model.addAttribute("hasta",fin.toString());
        model.addAttribute("bodega",bodega);
        model.addAttribute("proveedor",proveedor);
        model.addAttribute("buscar",buscar);
        model.addAttribute("filas",filas);
        model.addAttribute("total",total);
        model.addAttribute("cantidad",filas.size());
        model.addAttribute("error",error);
        return "reportes";
    }
}
