package com.agrofresh.controller;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Catálogos de solo lectura usados por el formulario de traslados. */
@RestController
@RequestMapping("/api/traslados")
public class TrasladoCatalogoController {
    private final ObjectProvider<JdbcTemplate> jdbcProvider;

    public TrasladoCatalogoController(ObjectProvider<JdbcTemplate> jdbcProvider) {
        this.jdbcProvider = jdbcProvider;
    }

    private JdbcTemplate db() {
        JdbcTemplate jdbc = jdbcProvider.getIfAvailable();
        if (jdbc == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "La base de datos no está configurada");
        }
        return jdbc;
    }

    @GetMapping("/bodegas")
    public List<Map<String,Object>> bodegas() {
        return db().queryForList("SELECT id_bodega AS id, nombre, ubicacion FROM bodega ORDER BY nombre");
    }

    @GetMapping("/productos")
    public List<Map<String,Object>> productos(
            @RequestParam int origen, @RequestParam String tipo) {
        if (!tipo.equals("PERECEDERO") && !tipo.equals("NO_PERECEDERO")) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo inválido");
        }
        String tabla = tipo.equals("PERECEDERO") ? "perecedero" : "no_perecedero";
        return db().queryForList(
            "SELECT p.id_producto AS id, p.nombre, p.codigo_interno, i.cantidad_actual AS stock " +
            "FROM producto p JOIN inventario i ON p.id_producto=i.id_producto " +
            "JOIN " + tabla + " t ON t.id_producto=p.id_producto " +
            "WHERE i.id_bodega=? AND i.cantidad_actual>0 ORDER BY p.nombre", origen);
    }

    @GetMapping("/lotes")
    public List<Map<String,Object>> lotes(@RequestParam int origen, @RequestParam int producto) {
        return db().queryForList(
            "SELECT num_lote AS id, fecha_vencimiento AS vencimiento, cantidad_disponible AS stock " +
            "FROM fn_lotes_disponibles(?, ?) ORDER BY fecha_vencimiento, num_lote", origen, producto);
    }
}
