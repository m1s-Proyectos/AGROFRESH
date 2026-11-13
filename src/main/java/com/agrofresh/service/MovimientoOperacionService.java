package com.agrofresh.service;

import com.agrofresh.dto.MovimientoBajaSolicitud;
import com.agrofresh.dto.MovimientoSalidaSolicitud;
import org.springframework.security.core.Authentication;

public interface MovimientoOperacionService {

  int registrarSalidaNoPerecedero(MovimientoSalidaSolicitud solicitud, Authentication auth);

  int registrarSalidaPerecedero(MovimientoSalidaSolicitud solicitud, Authentication auth);

  int registrarBajaDanoNoPerecedero(MovimientoBajaSolicitud solicitud, Authentication auth);

  int registrarBajaDanoPerecedero(MovimientoBajaSolicitud solicitud, Authentication auth);

  int registrarBajaVencimiento(MovimientoBajaSolicitud solicitud, Authentication auth);
}
