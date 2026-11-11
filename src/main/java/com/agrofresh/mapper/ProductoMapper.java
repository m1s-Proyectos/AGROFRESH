package com.agrofresh.mapper;

import com.agrofresh.dto.CategoriaDto;
import com.agrofresh.dto.ClasificacionProducto;
import com.agrofresh.dto.ProductoDetalleDto;
import com.agrofresh.dto.ProductoListadoDto;
import com.agrofresh.model.NoPerecedero;
import com.agrofresh.model.Perecedero;
import com.agrofresh.model.Producto;
import org.springframework.stereotype.Component;

@Component
public class ProductoMapper {

    public ProductoListadoDto toListado(
            Producto producto, CategoriaDto categoria, ClasificacionProducto clasificacion) {
        return new ProductoListadoDto(
                producto.getIdProducto(),
                producto.getCodigoInterno(),
                producto.getNombre(),
                producto.getIdCategoria(),
                categoria.nombre(),
                producto.getUnidadMedida(),
                clasificacion);
    }

    public ProductoDetalleDto toDetalle(
            Producto producto,
            CategoriaDto categoria,
            ClasificacionProducto clasificacion,
            Perecedero perecedero,
            NoPerecedero noPerecedero) {
        return new ProductoDetalleDto(
                producto.getIdProducto(),
                producto.getCodigoInterno(),
                producto.getNombre(),
                producto.getIdCategoria(),
                categoria.nombre(),
                producto.getUnidadMedida(),
                clasificacion,
                perecedero == null ? null : perecedero.getTemperaturaRecomendada(),
                perecedero == null ? null : perecedero.getVidaUtil(),
                noPerecedero == null ? null : noPerecedero.getFechaFabricacion(),
                noPerecedero == null ? null : noPerecedero.getVidaUtilAnaquel());
    }
}
