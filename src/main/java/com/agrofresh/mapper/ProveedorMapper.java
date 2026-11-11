package com.agrofresh.mapper;

import com.agrofresh.dto.ProveedorDetalleDto;
import com.agrofresh.dto.ProveedorListadoDto;
import com.agrofresh.model.Proveedor;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class ProveedorMapper {

    public ProveedorListadoDto toListado(Proveedor proveedor) {
        Objects.requireNonNull(proveedor, "proveedor no puede ser null");
        return new ProveedorListadoDto(
                proveedor.getIdProveedor(),
                proveedor.getNombre(),
                proveedor.getDui(),
                proveedor.getNit(),
                proveedor.getCorreoElectronico(),
                proveedor.getTelefono(),
                proveedor.getIva());
    }

    public ProveedorDetalleDto toDetalle(Proveedor proveedor) {
        Objects.requireNonNull(proveedor, "proveedor no puede ser null");
        return new ProveedorDetalleDto(
                proveedor.getIdProveedor(),
                proveedor.getNombre(),
                proveedor.getDui(),
                proveedor.getNit(),
                proveedor.getCorreoElectronico(),
                proveedor.getDireccion(),
                proveedor.getTelefono(),
                proveedor.getIva());
    }
}
