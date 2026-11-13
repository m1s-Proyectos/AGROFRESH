package com.agrofresh.dto;

/** Datos para salida de inventario (no incluye id_usuario; lo resuelve el servidor). */
public record MovimientoSalidaSolicitud(Integer bodega, Integer producto, Integer cantidad) {}
