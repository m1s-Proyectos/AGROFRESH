package com.agrofresh.controller;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.ui.ExtendedModelMap;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class LotesControllerTest {

    @Test
    void filtersByWarehouseProductSearchAndStateUsingTheCompanionsQuery() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var controller = new LotesController(jdbc);
        var model = new ExtendedModelMap();
        List<Map<String, Object>> filas = List.of(
                Map.of("estado", "PROXIMO"),
                Map.of("estado", "VENCIDO"),
                Map.of("estado", "SIN_STOCK"));
        when(jdbc.queryForList(anyString())).thenReturn(List.of());
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(filas);

        String view = controller.consultar("lote-8", 12, "proximo", model);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> parameters = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).queryForList(sql.capture(), parameters.capture());
        assertEquals("lotes", view);
        assertTrue(sql.getValue().contains("JOIN producto p"));
        assertTrue(sql.getValue().contains("JOIN bodega b"));
        assertTrue(sql.getValue().contains("JOIN proveedor pr"));
        assertTrue(sql.getValue().contains("l.fech_vencimiento"));
        assertTrue(sql.getValue().contains("l.cantidad_actual"));
        assertTrue(sql.getValue().contains("CURRENT_DATE + 7"));
        assertTrue(sql.getValue().contains("ORDER BY l.fech_vencimiento ASC"));
        assertArrayEquals(new Object[]{12, "%lote-8%", "%lote-8%", "%lote-8%"}, parameters.getValue());
        assertEquals(3, model.getAttribute("totalFilas"));
        assertEquals(1L, model.getAttribute("proximos"));
        assertEquals(1L, model.getAttribute("vencidos"));
    }

    @Test
    void normalizesUnknownStateAndBoundsSearchLength() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var controller = new LotesController(jdbc);
        var model = new ExtendedModelMap();
        when(jdbc.queryForList(anyString())).thenReturn(List.of());
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        String longSearch = "x".repeat(120);
        controller.consultar(longSearch, null, "not-a-state", model);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> parameters = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).queryForList(sql.capture(), parameters.capture());
        assertEquals("todos", model.getAttribute("estadoSeleccionado"));
        assertEquals(100, ((String) model.getAttribute("buscar")).length());
        assertEquals(3, parameters.getValue().length);
        assertTrue(sql.getValue().contains("CAST(l.num_lote AS TEXT) ILIKE ?"));
        assertFalse(sql.getValue().contains("AND l.cantidad_actual = 0"));
    }
}
