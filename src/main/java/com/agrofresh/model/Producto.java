package com.agrofresh.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "producto",
        schema = "inventario",
        uniqueConstraints = @UniqueConstraint(name = "uq_producto_codigo", columnNames = "codigo_interno"))
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_producto", nullable = false, updatable = false)
    private Integer idProducto;

    @Column(name = "id_categoria", nullable = false)
    private Integer idCategoria;

    @Column(name = "codigo_interno", nullable = false, length = 20)
    private String codigoInterno;

    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    @Column(name = "unidad_medida", nullable = false, length = 20)
    private String unidadMedida;

    protected Producto() {
        // Requerido por JPA.
    }

    public Producto(Integer idCategoria, String codigoInterno, String nombre, String unidadMedida) {
        this.idCategoria = idCategoria;
        this.codigoInterno = codigoInterno;
        this.nombre = nombre;
        this.unidadMedida = unidadMedida;
    }

    public void actualizar(Integer idCategoria, String codigoInterno, String nombre, String unidadMedida) {
        this.idCategoria = idCategoria;
        this.codigoInterno = codigoInterno;
        this.nombre = nombre;
        this.unidadMedida = unidadMedida;
    }

    public Integer getIdProducto() { return idProducto; }
    public Integer getIdCategoria() { return idCategoria; }
    public String getCodigoInterno() { return codigoInterno; }
    public String getNombre() { return nombre; }
    public String getUnidadMedida() { return unidadMedida; }
}
