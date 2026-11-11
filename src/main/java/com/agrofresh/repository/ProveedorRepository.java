package com.agrofresh.repository;

import com.agrofresh.model.Proveedor;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProveedorRepository extends JpaRepository<Proveedor, Integer> {

    List<Proveedor> findAllByOrderByNombreAsc();

    boolean existsByNit(String nit);

    boolean existsByNitAndIdProveedorNot(String nit, Integer idProveedor);

    boolean existsByDui(String dui);

    boolean existsByDuiAndIdProveedorNot(String dui, Integer idProveedor);

    @Query("""
            SELECT p FROM Proveedor p
            WHERE LOWER(p.nombre) LIKE LOWER(CONCAT('%', :termino, '%'))
               OR LOWER(p.nit) LIKE LOWER(CONCAT('%', :termino, '%'))
               OR (p.dui IS NOT NULL AND LOWER(p.dui) LIKE LOWER(CONCAT('%', :termino, '%')))
            ORDER BY p.nombre ASC
            """)
    List<Proveedor> buscarPorNombreNitODui(@Param("termino") String termino);
}
