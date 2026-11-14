package com.agrofresh.dto;

import java.time.LocalDate;

public record MovimientoFiltro(
        String tipo,
        Integer idBodega,
        Integer idProducto,
        LocalDate fechaDesde,
        LocalDate fechaHasta,
        int pagina,
        int tamanoPagina) {

    public static final int TAMANO_DEFECTO = 50;
    public static final int TAMANO_MAXIMO = 200;

    public int offset() {
        return Math.max(0, pagina) * tamanoPagina;
    }
}
