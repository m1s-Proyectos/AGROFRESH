package com.agrofresh.service;

import com.agrofresh.security.PermisoService;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Una compra puede incluir varios productos; todos se registran o ninguno. */
@Service
public class RecepcionService {
    private final JdbcTemplate jdbc;
    private final PermisoService permisos;
    public RecepcionService(JdbcTemplate jdbc, PermisoService permisos) {
        this.jdbc = jdbc;
        this.permisos = permisos;
    }

    public record Linea(Integer producto, Integer cantidad, LocalDate vencimiento) {}
    public record Solicitud(Integer proveedor, Integer bodega, List<Linea> lineas) {}

    @Transactional(rollbackFor = Exception.class)
    public Integer registrar(Solicitud s, Authentication auth) {
        if (!permisos.puede(auth, "compra", "INSERT")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sin permiso para registrar compras.");
        }
        if (s == null || s.proveedor() == null || s.proveedor() <= 0 ||
                s.bodega() == null || s.bodega() <= 0 || s.lineas() == null ||
                s.lineas().isEmpty() || s.lineas().size() > 50) {
            throw new IllegalArgumentException("Selecciona proveedor, bodega y de 1 a 50 productos.");
        }
        Set<Integer> usados = new HashSet<>();
        for (Linea l : s.lineas()) {
            if (l == null || l.producto() == null || l.producto() <= 0 ||
                    l.cantidad() == null || l.cantidad() <= 0 || !usados.add(l.producto())) {
                throw new IllegalArgumentException("Revisa las cantidades y evita repetir productos en la compra.");
            }
        }
        Integer idUsuario = jdbc.queryForObject(
            "SELECT id_usuario FROM usuario WHERE username=? AND activo=TRUE AND bloqueado=FALSE",
            Integer.class, auth.getName());
        if (idUsuario == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuario no autorizado.");

        // El procedimiento devuelve el parámetro INOUT como una fila de resultados.
        Integer idCompra = jdbc.queryForObject("CALL sp_crear_compra(?, NULL)", Integer.class, s.proveedor());
        if (idCompra == null) throw new IllegalStateException("La compra no devolvió identificador.");
        for (Linea l : s.lineas()) {
            boolean perecedero = Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM perecedero WHERE id_producto=?)", Boolean.class, l.producto()));
            boolean noPerecedero = Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM no_perecedero WHERE id_producto=?)", Boolean.class, l.producto()));
            if (perecedero == noPerecedero) {
                throw new IllegalArgumentException("El producto " + l.producto() + " no tiene un tipo válido.");
            }
            if (perecedero) {
                if (l.vencimiento() == null || !l.vencimiento().isAfter(LocalDate.now())) {
                    throw new IllegalArgumentException("Indica una fecha de vencimiento futura para cada perecedero.");
                }
                jdbc.update("CALL sp_recibir_perecedero(?, ?, ?, ?, ?, ?)",
                    idCompra, s.bodega(), l.producto(), l.cantidad(), l.vencimiento(), idUsuario);
            } else {
                jdbc.update("CALL sp_recibir_no_perecedero(?, ?, ?, ?, ?)",
                    idCompra, s.bodega(), l.producto(), l.cantidad(), idUsuario);
            }
        }
        return idCompra;
    }
}
