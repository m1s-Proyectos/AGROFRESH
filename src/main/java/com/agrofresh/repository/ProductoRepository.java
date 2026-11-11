package com.agrofresh.repository;

import com.agrofresh.model.Producto;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductoRepository extends JpaRepository<Producto, Integer> {

    boolean existsByCodigoInterno(String codigoInterno);

    boolean existsByCodigoInternoAndIdProductoNot(String codigoInterno, Integer idProducto);

    List<Producto> findAllByOrderByNombreAsc();

    @Query(value = "SELECT EXISTS(SELECT 1 FROM lote WHERE id_producto = :id)", nativeQuery = true)
    boolean tieneLotes(@Param("id") Integer idProducto);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM movimiento WHERE id_producto = :id)", nativeQuery = true)
    boolean tieneMovimientos(@Param("id") Integer idProducto);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM inventario WHERE id_producto = :id)", nativeQuery = true)
    boolean tieneInventario(@Param("id") Integer idProducto);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM detalle_compra WHERE id_producto = :id)", nativeQuery = true)
    boolean tieneCompras(@Param("id") Integer idProducto);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM catalogo WHERE id_producto = :id)", nativeQuery = true)
    boolean tieneCatalogo(@Param("id") Integer idProducto);
}
