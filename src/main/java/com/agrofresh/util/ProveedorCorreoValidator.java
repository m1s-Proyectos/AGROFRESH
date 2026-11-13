package com.agrofresh.util;

import java.util.regex.Pattern;

/** Alineado a inventario.proveedor.ck_proveedor_correo en PostgreSQL (~*). */
public final class ProveedorCorreoValidator {

    private static final Pattern FORMATO = Pattern.compile(
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,63}$", Pattern.CASE_INSENSITIVE);

    private ProveedorCorreoValidator() {}

    public static boolean esValido(String correo) {
        return correo != null && !correo.isEmpty() && FORMATO.matcher(correo).matches();
    }
}
