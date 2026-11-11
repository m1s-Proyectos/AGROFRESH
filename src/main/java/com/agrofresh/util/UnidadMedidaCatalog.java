package com.agrofresh.util;

import java.util.List;
import java.util.Set;

public final class UnidadMedidaCatalog {
    public static final List<String> OPCIONES = List.of(
            "Unidad", "Kilogramo", "Gramo", "Litro", "Mililitro", "Caja", "Paquete");
    private static final Set<String> VALIDAS = Set.copyOf(OPCIONES);

    private UnidadMedidaCatalog() {
    }

    public static boolean esValida(String unidad) {
        return unidad != null && VALIDAS.contains(unidad);
    }
}
