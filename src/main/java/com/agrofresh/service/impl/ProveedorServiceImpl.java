package com.agrofresh.service.impl;

import com.agrofresh.dto.ProveedorDetalleDto;
import com.agrofresh.dto.ProveedorForm;
import com.agrofresh.dto.ProveedorListadoDto;
import com.agrofresh.exception.BusinessException;
import com.agrofresh.exception.ResourceNotFoundException;
import com.agrofresh.mapper.ProveedorMapper;
import com.agrofresh.model.Proveedor;
import com.agrofresh.repository.ProveedorRepository;
import com.agrofresh.service.ProveedorService;
import com.agrofresh.util.ProveedorCorreoValidator;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
@Transactional(readOnly = true)
public class ProveedorServiceImpl implements ProveedorService {

    private static final int MAX_NOMBRE = 150;
    private static final int MAX_DUI = 10;
    private static final int MAX_NIT = 17;
    private static final int MAX_CORREO = 100;
    private static final int MAX_DIRECCION = 200;
    private static final int MAX_TELEFONO = 9;
    private static final int MAX_IVA = 50;

    private final ProveedorRepository proveedorRepository;
    private final ProveedorMapper mapper;

    public ProveedorServiceImpl(ProveedorRepository proveedorRepository, ProveedorMapper mapper) {
        this.proveedorRepository = proveedorRepository;
        this.mapper = mapper;
    }

    @Override
    public List<ProveedorListadoDto> listar() {
        try {
            return proveedorRepository.findAllByOrderByNombreAsc().stream()
                    .map(mapper::toListado)
                    .toList();
        } catch (DataAccessException exception) {
            throw new BusinessException("No se pudieron consultar los proveedores.", exception);
        }
    }

    @Override
    public List<ProveedorListadoDto> buscar(String termino) {
        String clave = termino == null ? "" : termino.trim();
        if (clave.isEmpty()) {
            return listar();
        }
        try {
            return proveedorRepository.buscarPorNombreNitODui(clave).stream()
                    .map(mapper::toListado)
                    .toList();
        } catch (DataAccessException exception) {
            throw new BusinessException("No se pudo buscar proveedores.", exception);
        }
    }

    @Override
    public ProveedorDetalleDto consultar(Integer idProveedor) {
        validarIdentificador(idProveedor);
        try {
            Proveedor proveedor = proveedorRepository.findById(idProveedor)
                    .orElseThrow(() -> new ResourceNotFoundException("El proveedor solicitado no existe."));
            return mapper.toDetalle(proveedor);
        } catch (DataAccessException exception) {
            throw new BusinessException("No se pudo consultar el proveedor.", exception);
        }
    }

    @Override
    @Transactional
    public ProveedorDetalleDto registrar(ProveedorForm form) {
        DatosProveedor datos = validar(form);
        try {
            if (proveedorRepository.existsByNit(datos.nit())) {
                throw new BusinessException("Ya existe un proveedor con ese NIT.");
            }
            if (datos.dui() != null && proveedorRepository.existsByDui(datos.dui())) {
                throw new BusinessException("Ya existe un proveedor con ese DUI.");
            }
            Proveedor guardado = proveedorRepository.saveAndFlush(new Proveedor(
                    datos.nombre(),
                    datos.dui(),
                    datos.nit(),
                    datos.correoElectronico(),
                    datos.direccion(),
                    datos.telefono(),
                    datos.iva()));
            return mapper.toDetalle(guardado);
        } catch (BusinessException exception) {
            throw exception;
        } catch (DataIntegrityViolationException exception) {
            throw traducirIntegridad(exception);
        } catch (DataAccessException exception) {
            throw new BusinessException("No se pudo guardar el proveedor. Inténtalo nuevamente.", exception);
        }
    }

    @Override
    @Transactional
    public ProveedorDetalleDto actualizar(Integer idProveedor, ProveedorForm form) {
        validarIdentificador(idProveedor);
        DatosProveedor datos = validar(form);
        try {
            Proveedor proveedor = proveedorRepository.findById(idProveedor)
                    .orElseThrow(() -> new ResourceNotFoundException("El proveedor solicitado no existe."));
            if (proveedorRepository.existsByNitAndIdProveedorNot(datos.nit(), idProveedor)) {
                throw new BusinessException("Ya existe otro proveedor con ese NIT.");
            }
            if (datos.dui() != null && proveedorRepository.existsByDuiAndIdProveedorNot(datos.dui(), idProveedor)) {
                throw new BusinessException("Ya existe otro proveedor con ese DUI.");
            }
            proveedor.actualizar(
                    datos.nombre(),
                    datos.dui(),
                    datos.nit(),
                    datos.correoElectronico(),
                    datos.direccion(),
                    datos.telefono(),
                    datos.iva());
            return mapper.toDetalle(proveedorRepository.saveAndFlush(proveedor));
        } catch (ResourceNotFoundException | BusinessException exception) {
            throw exception;
        } catch (DataIntegrityViolationException exception) {
            throw traducirIntegridad(exception);
        } catch (DataAccessException exception) {
            throw new BusinessException("No se pudo actualizar el proveedor. Inténtalo nuevamente.", exception);
        }
    }

    private static DatosProveedor validar(ProveedorForm form) {
        if (form == null) {
            throw new BusinessException("Los datos del proveedor son obligatorios.");
        }
        String nombre = textoObligatorio(form.getNombre(), "El nombre del proveedor es obligatorio.");
        if (nombre.length() > MAX_NOMBRE) {
            throw new BusinessException("El nombre no puede superar 150 caracteres.");
        }

        String nit = textoObligatorio(form.getNit(), "El NIT es obligatorio.");
        if (nit.length() > MAX_NIT) {
            throw new BusinessException("El NIT no puede superar 17 caracteres.");
        }

        String dui = normalizarDui(form.getDui());
        if (dui != null && dui.length() > MAX_DUI) {
            throw new BusinessException("El DUI no puede superar 10 caracteres.");
        }

        String correo = textoObligatorio(form.getCorreoElectronico(), "El correo electrónico es obligatorio.");
        if (correo.length() > MAX_CORREO) {
            throw new BusinessException("El correo no puede superar 100 caracteres.");
        }
        if (!ProveedorCorreoValidator.esValido(correo)) {
            throw new BusinessException("El correo electrónico no tiene un formato válido.");
        }

        String direccion = textoObligatorio(form.getDireccion(), "La dirección es obligatoria.");
        if (direccion.length() > MAX_DIRECCION) {
            throw new BusinessException("La dirección no puede superar 200 caracteres.");
        }

        String telefono = textoObligatorio(form.getTelefono(), "El teléfono es obligatorio.");
        if (telefono.length() > MAX_TELEFONO) {
            throw new BusinessException("El teléfono no puede superar 9 caracteres.");
        }

        String iva = textoObligatorio(form.getIva(), "El tipo de IVA es obligatorio.");
        if (iva.length() > MAX_IVA) {
            throw new BusinessException("El tipo de IVA no puede superar 50 caracteres.");
        }

        return new DatosProveedor(nombre, dui, nit, correo, direccion, telefono, iva);
    }

    private static String normalizarDui(String dui) {
        if (dui == null) {
            return null;
        }
        String limpio = dui.trim();
        return limpio.isEmpty() ? null : limpio;
    }

    private static String textoObligatorio(String valor, String mensaje) {
        if (valor == null) {
            throw new BusinessException(mensaje);
        }
        String limpio = valor.trim();
        if (limpio.isEmpty()) {
            throw new BusinessException(mensaje);
        }
        return limpio;
    }

    private static void validarIdentificador(Integer idProveedor) {
        if (idProveedor == null || idProveedor <= 0) {
            throw new ResourceNotFoundException("El proveedor solicitado no existe.");
        }
    }

    private static BusinessException traducirIntegridad(DataIntegrityViolationException exception) {
        String detalle = exception.getMostSpecificCause() != null
                ? exception.getMostSpecificCause().getMessage()
                : exception.getMessage();
        if (detalle != null) {
            String texto = detalle.toLowerCase();
            if (texto.contains("uq_proveedor_nit")) {
                return new BusinessException("Ya existe un proveedor con ese NIT.", exception);
            }
            if (texto.contains("uq_proveedor_dui")) {
                return new BusinessException("Ya existe un proveedor con ese DUI.", exception);
            }
            if (texto.contains("ck_proveedor_correo")) {
                return new BusinessException("El correo electrónico no tiene un formato válido.", exception);
            }
        }
        return new BusinessException("No se pudo guardar el proveedor por una restricción de integridad.", exception);
    }

    private record DatosProveedor(
            String nombre,
            String dui,
            String nit,
            String correoElectronico,
            String direccion,
            String telefono,
            String iva) {
    }
}
