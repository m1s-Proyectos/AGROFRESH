package com.agrofresh.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "no_perecedero", schema = "inventario")
public class NoPerecedero {

    @Id
    @Column(name = "id_producto", nullable = false, updatable = false)
    private Integer idProducto;

    @MapsId
    @OneToOne(optional = false)
    @JoinColumn(name = "id_producto", nullable = false, updatable = false)
    private Producto producto;

    @Column(name = "fech_fabricacion", nullable = false)
    private LocalDate fechaFabricacion;

    @Column(name = "vida_util_anaquel", nullable = false)
    private Integer vidaUtilAnaquel;

    protected NoPerecedero() {
        // Requerido por JPA.
    }

    public NoPerecedero(Producto producto, LocalDate fechaFabricacion, Integer vidaUtilAnaquel) {
        this.producto = producto;
        this.fechaFabricacion = fechaFabricacion;
        this.vidaUtilAnaquel = vidaUtilAnaquel;
    }

    public void actualizar(LocalDate fechaFabricacion, Integer vidaUtilAnaquel) {
        this.fechaFabricacion = fechaFabricacion;
        this.vidaUtilAnaquel = vidaUtilAnaquel;
    }

    public Integer getIdProducto() { return idProducto; }
    public Producto getProducto() { return producto; }
    public LocalDate getFechaFabricacion() { return fechaFabricacion; }
    public Integer getVidaUtilAnaquel() { return vidaUtilAnaquel; }
}
