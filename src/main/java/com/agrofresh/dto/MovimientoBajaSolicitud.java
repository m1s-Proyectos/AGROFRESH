package com.agrofresh.dto;

/**
 * Datos para bajas administrativas.
 *
 * @param numLote obligatorio para perecedero y baja por vencimiento; ignorado en no perecedero
 */
public record MovimientoBajaSolicitud(
    Integer bodega, Integer producto, Integer cantidad, String motivo, Integer numLote) {}
