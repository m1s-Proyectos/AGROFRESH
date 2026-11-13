package com.agrofresh.service.impl;

import com.agrofresh.dto.MovimientoBajaSolicitud;
import com.agrofresh.dto.MovimientoSalidaSolicitud;
import com.agrofresh.repository.MovimientoOperacionRepository;
import com.agrofresh.security.PermisoService;
import com.agrofresh.service.MovimientoOperacionService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MovimientoOperacionServiceImpl implements MovimientoOperacionService {

  private static final int MOTIVO_MAX = 500;

  private final MovimientoOperacionRepository operaciones;
  private final JdbcTemplate jdbc;
  private final PermisoService permisos;

  public MovimientoOperacionServiceImpl(
      MovimientoOperacionRepository operaciones, JdbcTemplate jdbc, PermisoService permisos) {
    this.operaciones = operaciones;
    this.jdbc = jdbc;
    this.permisos = permisos;
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public int registrarSalidaNoPerecedero(MovimientoSalidaSolicitud solicitud, Authentication auth) {
    exigirPermisoSalida(auth);
    int idUsuario = resolverIdUsuario(auth);
    validarSalidaComun(solicitud);
    return requerirMovimiento(
        operaciones.callSalidaNoPerecedero(
            solicitud.bodega(), solicitud.producto(), solicitud.cantidad(), idUsuario));
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public int registrarSalidaPerecedero(MovimientoSalidaSolicitud solicitud, Authentication auth) {
    exigirPermisoSalida(auth);
    int idUsuario = resolverIdUsuario(auth);
    validarSalidaComun(solicitud);
    return requerirMovimiento(
        operaciones.callSalidaPerecedero(
            solicitud.bodega(), solicitud.producto(), solicitud.cantidad(), idUsuario));
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public int registrarBajaDanoNoPerecedero(MovimientoBajaSolicitud solicitud, Authentication auth) {
    exigirAdministrador(auth);
    int idUsuario = resolverIdUsuario(auth);
    validarBajaComun(solicitud, false);
    String motivo = normalizarMotivo(solicitud.motivo());
    return requerirMovimiento(
        operaciones.callBajaDanoNoPerecedero(
            solicitud.bodega(), solicitud.producto(), solicitud.cantidad(), idUsuario, motivo));
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public int registrarBajaDanoPerecedero(MovimientoBajaSolicitud solicitud, Authentication auth) {
    exigirAdministrador(auth);
    int idUsuario = resolverIdUsuario(auth);
    validarBajaComun(solicitud, true);
    String motivo = normalizarMotivo(solicitud.motivo());
    return requerirMovimiento(
        operaciones.callBajaDanoPerecedero(
            solicitud.bodega(),
            solicitud.producto(),
            solicitud.numLote(),
            solicitud.cantidad(),
            idUsuario,
            motivo));
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public int registrarBajaVencimiento(MovimientoBajaSolicitud solicitud, Authentication auth) {
    exigirAdministrador(auth);
    int idUsuario = resolverIdUsuario(auth);
    validarBajaComun(solicitud, true);
    String motivo = normalizarMotivo(solicitud.motivo());
    return requerirMovimiento(
        operaciones.callBajaVencimiento(
            solicitud.bodega(),
            solicitud.producto(),
            solicitud.numLote(),
            solicitud.cantidad(),
            idUsuario,
            motivo));
  }

  private void exigirPermisoSalida(Authentication auth) {
    if (!permisos.puede(auth, "movimiento", "INSERT")) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Sin permiso para registrar salidas.");
    }
  }

  /** Doble capa: Spring ROLE_ADMIN y validación en PostgreSQL vía fn_usuario_es_administrador. */
  private void exigirAdministrador(Authentication auth) {
    if (!esAdministrador(auth)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Solo un administrador puede registrar bajas.");
    }
  }

  private boolean esAdministrador(Authentication auth) {
    if (auth == null || !auth.isAuthenticated()) {
      return false;
    }
    for (GrantedAuthority authority : auth.getAuthorities()) {
      if ("ROLE_ADMIN".equals(authority.getAuthority())) {
        return true;
      }
    }
    return false;
  }

  private int resolverIdUsuario(Authentication auth) {
    if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuario no autorizado.");
    }
    Integer idUsuario =
        jdbc.queryForObject(
            "SELECT id_usuario FROM usuario WHERE username = ? AND activo = TRUE AND bloqueado = FALSE",
            Integer.class,
            auth.getName());
    if (idUsuario == null) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuario no autorizado.");
    }
    return idUsuario;
  }

  private void validarSalidaComun(MovimientoSalidaSolicitud solicitud) {
    if (solicitud == null
        || solicitud.bodega() == null
        || solicitud.producto() == null
        || solicitud.cantidad() == null
        || solicitud.bodega() <= 0
        || solicitud.producto() <= 0
        || solicitud.cantidad() <= 0) {
      throw new IllegalArgumentException(
          "Indica bodega, producto y una cantidad mayor que cero.");
    }
  }

  private void validarBajaComun(MovimientoBajaSolicitud solicitud, boolean requiereLote) {
    if (solicitud == null
        || solicitud.bodega() == null
        || solicitud.producto() == null
        || solicitud.cantidad() == null
        || solicitud.bodega() <= 0
        || solicitud.producto() <= 0
        || solicitud.cantidad() <= 0) {
      throw new IllegalArgumentException(
          "Indica bodega, producto y una cantidad mayor que cero.");
    }
    if (requiereLote && (solicitud.numLote() == null || solicitud.numLote() <= 0)) {
      throw new IllegalArgumentException("Indica un lote válido.");
    }
    normalizarMotivo(solicitud.motivo());
  }

  private String normalizarMotivo(String motivo) {
    if (motivo == null || motivo.isBlank()) {
      throw new IllegalArgumentException("El motivo de la baja es obligatorio.");
    }
    String limpio = motivo.trim();
    if (limpio.length() > MOTIVO_MAX) {
      throw new IllegalArgumentException(
          "El motivo no puede superar " + MOTIVO_MAX + " caracteres.");
    }
    return limpio;
  }

  private int requerirMovimiento(Integer idMovimiento) {
    if (idMovimiento == null || idMovimiento <= 0) {
      throw new IllegalStateException("El procedimiento no devolvió un movimiento válido.");
    }
    return idMovimiento;
  }
}
